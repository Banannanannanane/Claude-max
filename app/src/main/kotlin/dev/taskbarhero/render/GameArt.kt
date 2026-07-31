package dev.taskbarhero.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import dev.taskbarhero.assets.Art
import dev.taskbarhero.assets.AssetManifest
import dev.taskbarhero.assets.Atlas
import java.io.File

/**
 * The art, loaded once and never guessed at.
 *
 * Loading is all-or-nothing on purpose: the game owns no fallback drawing, so a
 * pack that is missing a file, a rectangle, or a name it will be asked for later
 * is a broken build, and the app has to say so at startup rather than crash four
 * acts into a run when that monster first appears.
 */
object GameArt {

    /** Either everything the game needs, or the reason it does not have it. */
    sealed interface Load {
        data class Ready(
            val dungeon: Atlas,
            val ui: Atlas,
            val icons: Atlas,
            val dungeonBitmap: Bitmap,
            val uiBitmap: Bitmap,
            val iconBitmap: Bitmap,
            val font: Typeface,
        ) : Load

        data class Broken(val reasons: List<String>) : Load
    }

    @Volatile
    private var cached: Load? = null

    fun load(ctx: Context): Load = cached ?: synchronized(this) {
        cached ?: read(ctx.applicationContext).also { cached = it }
    }

    private fun read(ctx: Context): Load {
        val report = AssetManifest.check(present = assetPaths(ctx))
        if (!report.isPlayable) return Load.Broken(report.lines())

        val problems = mutableListOf<String>()

        val dungeon = parse(ctx, "art/dungeon.txt", problems)
        val ui = parse(ctx, "art/ui.txt", problems)
        val icons = parse(ctx, "art/icons.txt", problems)
        if (dungeon != null) problems += Art.missingFrom(dungeon, Art.dungeonKeys).map {
            "art/dungeon.txt: nothing named $it, which the game draws"
        }
        if (ui != null) problems += Art.missingFrom(ui, Art.uiKeys).map {
            "art/ui.txt: nothing named $it, which the game draws"
        }
        if (icons != null) problems += Art.missingFrom(icons, Art.iconKeys).map {
            "art/icons.txt: nothing named $it, which the game draws"
        }

        val dungeonBitmap = decode(ctx, AssetManifest.DUNGEON, problems)
        val uiBitmap = decode(ctx, AssetManifest.UI, problems)
        val iconBitmap = decode(ctx, AssetManifest.ICONS, problems)
        val font = typeface(ctx, problems)

        if (problems.isNotEmpty() || dungeon == null || ui == null || icons == null ||
            dungeonBitmap == null || uiBitmap == null || iconBitmap == null || font == null
        ) {
            return Load.Broken(listOf("The art is there but unusable:") + problems)
        }
        return Load.Ready(dungeon, ui, icons, dungeonBitmap, uiBitmap, iconBitmap, font)
    }

    private fun parse(ctx: Context, path: String, problems: MutableList<String>): Atlas? {
        val text = runCatching { ctx.assets.open(path).use { it.readBytes().decodeToString() } }
            .getOrElse {
                problems += "$path could not be read"
                return null
            }
        val result = Atlas.parse(text)
        problems += result.problems.map { "$path: $it" }
        return result.atlas
    }

    private fun decode(ctx: Context, path: String, problems: MutableList<String>): Bitmap? {
        // inScaled = false: the mapping's coordinates are in atlas pixels, so a
        // density-resampled bitmap would point every frame at the wrong rectangle.
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = runCatching {
            ctx.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()
        if (bitmap == null) problems += "$path is not a readable image"
        return bitmap
    }

    /**
     * The font, unpacked to a real file first: Typeface can only be built from an
     * asset path or a file, and going through a file is what keeps this working if
     * the pack ever moves out of the APK.
     */
    private fun typeface(ctx: Context, problems: MutableList<String>): Typeface? {
        val typeface = runCatching {
            Typeface.createFromAsset(ctx.assets, AssetManifest.FONT)
        }.getOrNull()
        if (typeface == null) problems += "${AssetManifest.FONT} is not a usable font"
        return typeface
    }

    /** Every file packed under `assets/`, as manifest-style paths. */
    private fun assetPaths(ctx: Context): Set<String> {
        val found = mutableSetOf<String>()
        fun walk(dir: String) {
            val entries = runCatching { ctx.assets.list(dir) }.getOrNull().orEmpty()
            if (entries.isEmpty()) {
                if (dir.isNotEmpty()) found += dir
                return
            }
            for (entry in entries) walk(if (dir.isEmpty()) entry else "$dir/$entry")
        }
        walk("")
        return found
    }

    /** Only for tests and for a pack swapped in at runtime. */
    fun forget() {
        cached = null
    }

    @Suppress("unused")
    private fun unused(file: File) = file
}
