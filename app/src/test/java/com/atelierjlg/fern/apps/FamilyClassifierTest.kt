package com.atelierjlg.fern.apps

import com.atelierjlg.fern.data.Family
import org.junit.Assert.assertEquals
import org.junit.Test

class FamilyClassifierTest {

    private fun c(pkg: String, label: String, category: Int = -1, system: Boolean = false) =
        FamilyClassifier.classify(pkg, label, category, system)

    @Test
    fun knownApps() {
        assertEquals(Family.Communication, c("com.whatsapp", "WhatsApp"))
        assertEquals(Family.Communication, c("org.thoughtcrime.securesms", "Signal"))
        assertEquals(Family.Social, c("com.instagram.android", "Instagram"))
        assertEquals(Family.Argent, c("de.number26.android", "N26"))
        assertEquals(Family.Argent, c("com.tricount.android", "tricount"))
        assertEquals(Family.Urgence, c("fr.appelles", "App-Elles"))
        assertEquals(Family.Organisation, c("md.obsidian", "Obsidian"))
        assertEquals(Family.Organisation, c("com.ichi2.anki", "AnkiDroid"))
        assertEquals(Family.Dehors, c("org.mozilla.firefox", "Firefox"))
        assertEquals(Family.Dehors, c("fr.meteo", "Météo-France"))
        assertEquals(Family.Loisirs, c("org.lineageos.aperture", "Appareil photo"))
        assertEquals(Family.Admin, c("com.android.settings", "Paramètres"))
    }

    @Test
    fun fallbacks() {
        assertEquals(Family.Social, c("x.y", "Truc", category = 4))
        assertEquals(Family.Loisirs, c("x.y", "Truc", category = 1))
        assertEquals(Family.Admin, c("x.y", "Truc", system = true))
        assertEquals(Family.Organisation, c("x.y", "Truc"))
    }
}
