package com.atelierjlg.fern.keyboard.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDictionaryTest {
    @Test
    fun unMotDevientConnuApresDeuxFois() {
        val d = UserDictionary()
        d.learn("Mewtwo")
        assertEquals(0, d.weight("mewtwo"))
        d.learn("mewtwo")
        assertTrue(d.weight("mewtwo") >= UserDictionary.KNOWN)
        // La forme avec majuscule est gardée.
        assertEquals(listOf("Mewtwo"), d.knownWords())
    }

    @Test
    fun confirmeTouteDeSuite() {
        val d = UserDictionary()
        d.learn("Inès", confirmed = true)
        assertTrue(d.weight("inès") > 0)
    }

    @Test
    fun motSuivant() {
        val d = UserDictionary()
        repeat(3) { d.learnPair("on", "capte") }
        d.learnPair("on", "voit")
        assertEquals(listOf("capte", "voit"), d.next("On", 3))
        val p = Predictor.next("on", "se", d)
        assertTrue("voit" in p)
    }

    @Test
    fun sauvegardeEtRelecture() {
        val d = UserDictionary()
        d.learn("Inès", confirmed = true)
        d.learnPair("à", "demain")
        val back = UserDictionary.parse(d.serialize())
        assertTrue(back.weight("inès") > 0)
        assertEquals(listOf("demain"), back.next("à", 3))
    }

    @Test
    fun elisionsEtCasse() {
        assertEquals("m'" to "ennuie", Words.splitElision("m'ennuie"))
        assertEquals("Qu'" to "est-ce", Words.splitElision("Qu'est-ce"))
        assertEquals("" to "aujourd'hui", Words.splitElision("aujourd'hui"))
        assertEquals("j’" to "ai", Words.splitElision("j’ai"))
        assertEquals("BONJOUR", Words.matchCase("BONJORU", "bonjour"))
        assertEquals("Été", Words.matchCase("Ete", "été"))
        assertEquals("ennuie", Words.trailingWord("je m'ennuie, ennuie"))
    }
}
