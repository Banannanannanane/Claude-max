package dev.taskbarhero.preview

import dev.taskbarhero.engine.SpriteKey
import dev.taskbarhero.paint.MappedSheet
import dev.taskbarhero.paint.Sheet
import dev.taskbarhero.paint.SheetMapping
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The previewer's copy of the app's asset loader: the same two files, read from
 * disk instead of the APK. Without this the previews would show the built-in art
 * while the phone showed the pack — and the whole point of the previewer is that
 * what it renders is what ships.
 */
class DropInArt(val image: BufferedImage, val sheet: Sheet) {

    companion object {
        private const val IMAGE = "sprites.png"
        private const val MAPPING = "sprites.txt"

        /**
         * Loads the pack from [dir], or from the app's assets folder found by
         * walking up from the working directory. Returns null — quietly, past a
         * note on stdout — whenever anything is missing, so the previewer still
         * runs in a checkout with no pack.
         */
        fun load(dir: File? = null): DropInArt? {
            val assets = dir ?: findAssets() ?: run {
                println("no assets folder found — drawing the built-in art")
                return null
            }
            val png = File(assets, IMAGE)
            val txt = File(assets, MAPPING)
            if (!png.isFile || !txt.isFile) {
                println("${assets.path}: needs both $IMAGE and $MAPPING — drawing the built-in art")
                return null
            }

            val parsed = SheetMapping.parse(txt.readText())
            for (problem in parsed.problems) println("$MAPPING: $problem")
            if (!parsed.isUsable) {
                println("$MAPPING: no usable frames — drawing the built-in art")
                return null
            }

            val image = runCatching { ImageIO.read(png) }.getOrNull()
            if (image == null) {
                println("${png.path}: not a readable image — drawing the built-in art")
                return null
            }
            report(parsed.sheet, image)
            return DropInArt(image, parsed.sheet)
        }

        /**
         * Says what the pack covers and what it does not. A name the game never
         * asks for is a typo that would silently keep the built-in sprite, and a
         * missing one is a hole the previews would not obviously show.
         */
        private fun report(sheet: MappedSheet, image: BufferedImage) {
            println(
                "art: ${sheet.size} sprites, ${sheet.frameCount} frames, " +
                    "on a ${sheet.unit}px grid, from ${image.width}x${image.height}",
            )
            val wanted = SpriteKey.entries.map { it.name }.toSet()
            (wanted - sheet.keys).sorted().forEach { println("  $it: no frame, keeping the built-in art") }
            (sheet.keys - wanted).sorted().forEach { println("  $it: not a sprite the game asks for") }
            for ((name, frame) in wanted.intersect(sheet.keys).sorted().mapNotNull { n -> sheet.frame(n)?.let { n to it } }) {
                val off = frame.x < 0 || frame.y < 0 ||
                    frame.x + frame.width > image.width || frame.y + frame.height > image.height
                if (off) println("  $name: $frame falls outside the image")
            }
        }

        private fun findAssets(): File? {
            var dir: File? = File(".").absoluteFile
            while (dir != null) {
                val assets = File(dir, "app/src/main/assets")
                if (assets.isDirectory) return assets
                dir = dir.parentFile
            }
            return null
        }
    }
}
