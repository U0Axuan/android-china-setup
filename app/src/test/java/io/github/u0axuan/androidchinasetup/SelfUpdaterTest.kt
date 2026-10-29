package io.github.u0axuan.androidchinasetup

import org.junit.Assert.*
import org.junit.Test

class SelfUpdaterTest {
    @Test fun newerPatchAndMajorVersions() {
        assertTrue(SelfUpdater.isNewer("v0.4.1", "0.4.0"))
        assertTrue(SelfUpdater.isNewer("v1.0.0", "0.4.0"))
        assertTrue(SelfUpdater.isNewer("v0.4.0", "0.4.0-dev"))
    }

    @Test fun avoidFalseUpdates() {
        assertFalse(SelfUpdater.isNewer("v0.3.0", "0.3.0"))
        assertFalse(SelfUpdater.isNewer("v0.3.5", "0.4.0-dev"))
        assertFalse(SelfUpdater.isNewer("v0.4.0", "0.4.1"))
        assertFalse(SelfUpdater.isNewer("v1.0.0", "1.0.0"))
    }
}
