package dev.taskbarhero.assets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssetManifestTest {

    @Test
    fun `every atlas has a mapping beside it`() {
        val atlases = AssetManifest.all.filter { it.kind == AssetManifest.Kind.ATLAS }
        for (atlas in atlases) {
            val mapping = atlas.path.removeSuffix(".png") + ".txt"
            assertTrue(
                AssetManifest.all.any { it.path == mapping },
                "${atlas.path} has no $mapping — an atlas nobody can read is not an asset",
            )
        }
    }

    @Test
    fun `nothing is listed twice, and everything says what it is for`() {
        val paths = AssetManifest.all.map { it.path }
        assertEquals(paths.size, paths.toSet().size, "a file is required twice")
        for (asset in AssetManifest.all) {
            assertTrue(asset.path.startsWith("art/"), "${asset.path} is outside art/")
            assertTrue(asset.why.length > 30, "${asset.path} does not explain itself")
        }
    }

    @Test
    fun `an empty install is not playable, and names every file it wants`() {
        val report = AssetManifest.check(present = emptySet())

        assertFalse(report.isPlayable)
        assertEquals(AssetManifest.required, report.missing)
        val text = report.lines().joinToString("\n")
        for (asset in AssetManifest.required) {
            assertTrue(asset.path in text, "${asset.path} is missing from the error screen")
            assertTrue(asset.why in text, "${asset.path} is listed without saying why")
        }
    }

    @Test
    fun `the required set is enough to play`() {
        val report = AssetManifest.check(present = AssetManifest.required.map { it.path }.toSet())

        assertTrue(report.isPlayable)
        assertEquals("All required art is present.", report.lines().single())
    }

    @Test
    fun `one missing file is enough to stop the app`() {
        // The whole point of the policy: no fallback, no placeholder, no "mostly".
        for (asset in AssetManifest.required) {
            val present = AssetManifest.all.map { it.path }.toSet() - asset.path
            val report = AssetManifest.check(present)
            assertFalse(report.isPlayable, "${asset.path} can go missing without anyone noticing")
            assertEquals(listOf(asset), report.missing)
        }
    }
}
