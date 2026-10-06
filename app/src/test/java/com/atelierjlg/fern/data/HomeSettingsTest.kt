package com.atelierjlg.fern.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSettingsTest {
    @Test
    fun `espace en haut - valeurs successives puis retour a 0`() {
        var c = LauncherConfig()
        assertEquals(21, c.home.topSpacePercent)
        c = c.cycleTopSpace()
        assertEquals(29, c.home.topSpacePercent)
        c = c.cycleTopSpace().cycleTopSpace()
        assertEquals(0, c.home.topSpacePercent)
    }
}
