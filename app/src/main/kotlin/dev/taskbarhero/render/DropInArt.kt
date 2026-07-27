package dev.taskbarhero.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import dev.taskbarhero.paint.MappedSheet
import dev.taskbarhero.paint.SheetMapping

/**
 * The sprite pack in `app/src/main/assets/` — the one that ships, or whatever the
 * player drops in its place.
 *
 * Nothing here is required: with no files, or a broken mapping, the game draws
 * its built-in sprites and never mentions it again. That is the point — a widget
 * must not fail to render because an asset pack is missing a line.
 *
 * See docs/ASSETS.md for the file names and the mapping format.
 */
object DropInArt {

    private const val IMAGE = "sprites.png"
    private const val MAPPING = "sprites.txt"
    private const val TAG = "TaskbarHero"

    private var loaded = false
    private var bitmap: Bitmap? = null
    private var sheet: MappedSheet? = null

    /** The sheet image, or null when the player has not provided one. */
    @Synchronized
    fun bitmap(ctx: Context): Bitmap? {
        load(ctx)
        return bitmap
    }

    /** The frame mapping, or null when there is nothing usable to map. */
    @Synchronized
    fun sheet(ctx: Context): MappedSheet? {
        load(ctx)
        return sheet
    }

    @Synchronized
    private fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val assets = ctx.applicationContext.assets
        val names = runCatching { assets.list("")?.toSet().orEmpty() }.getOrDefault(emptySet())
        if (IMAGE !in names || MAPPING !in names) return

        // inScaled = false: the mapping's coordinates are in atlas pixels, so a
        // density-resampled bitmap would point every frame at the wrong rectangle.
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val image = runCatching {
            assets.open(IMAGE).use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull() ?: run {
            Log.w(TAG, "$IMAGE could not be decoded; keeping the built-in art")
            return
        }

        val text = runCatching { assets.open(MAPPING).use { it.readBytes().decodeToString() } }
            .getOrNull() ?: return
        val result = SheetMapping.parse(text)
        result.problems.forEach { Log.w(TAG, "$MAPPING $it") }
        if (!result.isUsable) {
            Log.w(TAG, "$MAPPING mapped nothing; keeping the built-in art")
            return
        }

        bitmap = image
        sheet = result.sheet
        Log.i(TAG, "drop-in art: ${result.sheet.size} frames from $IMAGE")
    }
}
