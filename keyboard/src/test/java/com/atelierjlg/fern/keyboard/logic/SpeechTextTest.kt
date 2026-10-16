package com.atelierjlg.fern.keyboard.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextTest {
    @Test
    fun nettoyage() {
        assertEquals("Franchement je m'ennuie, on se voit ce soir ?", SpeechText.clean(" [Musique] Franchement je m'ennuie , on se voit ce soir ? "))
        assertEquals("Oui.", SpeechText.clean("Oui. Sous-titres réalisés par la communauté d'Amara.org"))
        assertEquals("C'est drôle", SpeechText.clean("C'est drôle (rires)"))
        assertEquals("", SpeechText.clean("♪♪ [Applaudissements]"))
    }

    @Test
    fun insertion() {
        // Champ vide : majuscule.
        assertEquals("Bonjour", SpeechText.forInsertion("bonjour", ""))
        // Collé à un mot : une espace et une minuscule.
        assertEquals(" on se voit", SpeechText.forInsertion("On se voit", "Salut"))
        // Après une fin de phrase : majuscule.
        assertEquals(" Demain ?", SpeechText.forInsertion("demain ?", "Ok."))
        assertEquals("Demain", SpeechText.forInsertion("demain", "Ok. "))
        // Sigle gardé.
        assertEquals(" ESB demain", SpeechText.forInsertion("ESB demain", "cours à l'"))
    }

    @Test
    fun audio() {
        val f = SpeechText.toFloat(shortArrayOf(0, 16384, -32768))
        assertEquals(0f, f[0], 0f)
        assertEquals(0.5f, f[1], 0.001f)
        assertEquals(-1f, f[2], 0f)
        assertEquals(0f, SpeechText.level(ShortArray(10), 10), 0f)
        assertTrue(SpeechText.level(ShortArray(10) { 8000 }, 10) > 0.5f)
    }
}
