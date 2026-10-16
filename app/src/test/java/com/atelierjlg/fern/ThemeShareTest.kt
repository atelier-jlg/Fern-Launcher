package com.atelierjlg.fern

import com.atelierjlg.fern.data.ThemePresets
import com.atelierjlg.fern.data.ThemeShare
import com.atelierjlg.fern.theme.parseSharedTheme
import com.atelierjlg.fern.theme.toFernColors
import com.atelierjlg.fern.ui.theme.toFernColors
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ce que Fern publie doit être lu à l'identique par Fern Messages et Fern Contact. */
class ThemeShareTest {
    @Test
    fun lesApplisSoeursLisentLeThemeDeFern() {
        ThemePresets.all.forEach { preset ->
            val received = parseSharedTheme(ThemeShare.encode(preset))!!
            assertEquals(preset.name, received.name)
            val sent = preset.colors.toFernColors()
            val read = received.colors.toFernColors()
            assertEquals(sent.copy(families = emptyMap()), read)
        }
    }
}
