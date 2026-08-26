/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionUtilsTest {

    @Test
    fun `plain dotted app versions compare numerically`() {
        assertTrue(compareVersions("20.40.45", "21.01.23") < 0)
        assertTrue(compareVersions("21.01.23", "20.40.45") > 0)
        assertEquals(0, compareVersions("19.47.53", "19.47.53"))
        // Segment count differs, missing segments read as 0.
        assertTrue(compareVersions("1.2", "1.2.1") < 0)
        assertEquals(0, compareVersions("1.2", "1.2.0"))
    }

    @Test
    fun `leading v prefix is ignored`() {
        assertEquals(0, compareVersions("v1.38.0", "1.38.0"))
        assertEquals(0, compareVersions("V1.38.0", "v1.38.0"))
        assertTrue(compareVersions("v1.39.0", "v1.38.0") > 0)
    }

    @Test
    fun `stable outranks a pre-release of the same base`() {
        assertTrue(compareVersions("1.39.0", "1.39.0-dev.4") > 0)
        assertTrue(compareVersions("1.39.0-dev.4", "1.39.0") < 0)
        assertFalse(isNewerVersion("1.39.0-dev.5", "1.39.0"))
        assertTrue(isNewerVersion("1.39.0", "1.39.0-dev.5"))
    }

    /** The regression that motivated the port: the old comparator cored both to 1.39.0. */
    @Test
    fun `pre-release ordinals compare numerically not lexically`() {
        assertTrue(compareVersions("1.39.0-dev.4", "1.39.0-dev.10") < 0)
        assertTrue(isNewerVersion("1.39.0-dev.10", "1.39.0-dev.4"))
        assertFalse(isNewerVersion("1.39.0-dev.4", "1.39.0-dev.10"))
        assertTrue(isNewerVersion("v1.39.0-dev.9", "v1.39.0-dev.2"))
    }

    @Test
    fun `base version wins over pre-release status`() {
        assertTrue(isNewerVersion("1.39.0-dev.1", "1.38.0"))
        assertTrue(isNewerVersion("1.39.0-dev.1", "1.38.9"))
        assertFalse(isNewerVersion("1.38.0", "1.39.0-dev.1"))
    }

    @Test
    fun `blank and unknown never report an update`() {
        assertFalse(isNewerVersion(null, "1.0.0"))
        assertFalse(isNewerVersion("1.0.0", null))
        assertFalse(isNewerVersion("", "1.0.0"))
        assertFalse(isNewerVersion("  ", "1.0.0"))
        assertFalse(isNewerVersion("unknown", "1.0.0"))
        assertFalse(isNewerVersion("1.0.0", "UNKNOWN"))
    }

    @Test
    fun `equal versions are never newer`() {
        assertFalse(isNewerVersion("1.38.0", "1.38.0"))
        assertFalse(isNewerVersion("v1.38.0", "1.38.0"))
        assertFalse(isNewerVersion("1.39.0-dev.4", "1.39.0-dev.4"))
    }

    @Test
    fun `other pre-release keywords are recognised`() {
        assertTrue(compareVersions("2.0.0", "2.0.0-beta.1") > 0)
        assertTrue(compareVersions("2.0.0", "2.0.0-rc.1") > 0)
        assertTrue(compareVersions("2.0.0-alpha.1", "2.0.0-alpha.2") < 0)
    }
}
