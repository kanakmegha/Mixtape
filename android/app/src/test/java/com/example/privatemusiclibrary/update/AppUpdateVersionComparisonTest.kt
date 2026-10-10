package com.example.privatemusiclibrary.update

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpdateVersionComparisonTest {
    @Test
    fun newerPatchVersionIsDetected() {
        assertEquals(1, compareVersions("1.0.1", "1.0.0"))
    }

    @Test
    fun olderVersionIsNotNewer() {
        assertEquals(-1, compareVersions("1.0.0", "1.0.1"))
    }

    @Test
    fun multiDigitVersionComponentsCompareNumerically() {
        assertEquals(1, compareVersions("1.0.10", "1.0.9"))
    }

    @Test
    fun leadingVIsIgnored() {
        assertEquals(0, compareVersions("v1.2.0", "1.2"))
    }

    @Test
    fun missingTrailingComponentsAreZero() {
        assertEquals(0, compareVersions("1.2", "1.2.0"))
    }
}
