package com.atelierjlg.fern.keyboard.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La correction, testée avec le VRAI dictionnaire (src/main/assets/dico_fr.txt.gz).
 * Ajoute ici les phrases que le clavier massacre : elles deviennent des tests qui doivent passer.
 */
class CorrectorTest {
    companion object {
        private val dict: Dictionary by lazy {
            File("src/main/assets/dico_fr.txt.gz").inputStream().use { Dictionary.load(it) }
        }
        private val corrector by lazy { Corrector(dict) }
    }

    @Test
    fun leDictionnaireEstLa() {
        assertTrue(dict.size > 300_000)
        assertTrue(dict.contains("bonjour"))
        assertTrue(dict.contains("Été"))
        assertEquals(-1, dict.frequencyOf("bonjoru"))
    }

    @Test
    fun onNeToucheJamaisAuxMotsConnus() {
        val connus = listOf(
            "m'ennuie", "M'ennuie", "t'inquiète", "qu'est-ce", "aujourd'hui", "peut-être", "dis-moi", "va-t-il",
            "ouais", "jsp", "chui", "mdr", "tkt", "wesh", "kiffe", "chelou", "Jules", "myalgie",
            "SNCF", "20h", "3ème", "ennuyée", "ESB",
        )
        for (w in connus) {
            assertTrue("$w devrait être connu", corrector.isKnown(w))
            assertNull("$w ne doit pas être corrigé", corrector.autoCorrect(w))
        }
    }

    @Test
    fun accentsOublies() {
        assertEquals("déjà", corrector.autoCorrect("deja"))
        assertEquals("été", corrector.autoCorrect("ete"))
        assertEquals("très", corrector.autoCorrect("tres"))
        assertEquals("français", corrector.autoCorrect("francais"))
        assertEquals("Déjà", corrector.autoCorrect("Deja"))
    }

    @Test
    fun fautesDeFrappe() {
        assertEquals("bonjour", corrector.autoCorrect("bonjoru"))
        assertEquals("Bonjour", corrector.autoCorrect("Bonjoru"))
        assertEquals("maintenant", corrector.autoCorrect("maintenat"))
        assertEquals("aujourd'hui", corrector.autoCorrect("aujourdhui"))
        assertEquals("m'ennuie", corrector.autoCorrect("m'ennuei"))
        assertEquals("appeler", corrector.autoCorrect("apeler"))
        assertEquals("quelque", corrector.autoCorrect("qeulque"))
        assertEquals("demain", corrector.autoCorrect("demian"))
        assertEquals("rendez-vous", corrector.autoCorrect("rendezvous"))
        assertEquals("quelqu'un", corrector.autoCorrect("quelquun"))
        assertEquals("quelque chose", corrector.autoCorrect("quelquechose"))
        assertEquals("Quelqu'un", corrector.autoCorrect("Quelquun"))
    }

    @Test
    fun apostrophesEtExpressionsOubliees() {
        assertEquals("j'ai", corrector.autoCorrect("jai"))
        assertEquals("c'est", corrector.autoCorrect("cest"))
        assertEquals("J'arrive", corrector.autoCorrect("Jarrive"))
        assertEquals("d'accord", corrector.autoCorrect("daccord"))
        assertEquals("n'importe", corrector.autoCorrect("nimporte"))
        assertEquals("bien sûr", corrector.autoCorrect("biensur"))
        assertEquals("ça va", corrector.autoCorrect("sava"))
        assertEquals("parce que", corrector.autoCorrect("parceque"))
    }

    @Test
    fun formesParleesGardees() {
        for (w in listOf("resto", "appart", "dispo", "koi", "ke", "ciné", "aprèm", "apéro")) {
            assertNull("$w ne doit pas être corrigé", corrector.autoCorrect(w))
        }
    }

    @Test
    fun prudenceSurLesMotsCourts() {
        // Deux lettres : on ne devine pas (sauf un accent).
        assertNull(corrector.autoCorrect("zx"))
    }

    @Test
    fun completions() {
        assertTrue(corrector.completions("aujour").first().startsWith("aujourd"))
        assertTrue("bonjour" in corrector.completions("bonj"))
        // L'élision est gardée devant.
        assertTrue(corrector.completions("m'enn").all { it.startsWith("m'enn") })
    }

    @Test
    fun barreDeSuggestions() {
        val s = corrector.strip("bonjoru")
        assertEquals("bonjour", s.middle)
        assertTrue(s.middleIsCorrection)
        assertEquals("bonjoru", s.left)
        val k = corrector.strip("bonj")
        assertEquals("bonj", k.middle)
        assertTrue(!k.middleIsCorrection)
    }

    @Test
    fun motsAppris() {
        val user = UserDictionary()
        val c = Corrector(dict, ExtraWords { user.weight(it) }, { user.knownWords().map { it.lowercase() } })
        assertTrue(c.autoCorrect("Inessa") == null || !c.isKnown("Inessa"))
        user.learn("Inessa", confirmed = true)
        assertTrue(c.isKnown("Inessa"))
        assertNull(c.autoCorrect("Inessa"))
    }

    @Test
    fun voisinsAzerty() {
        assertTrue(Keyboard.adjacent('a', 'z'))
        assertTrue(Keyboard.adjacent('a', 'q'))
        assertTrue(Keyboard.adjacent('w', 's'))
        assertTrue(!Keyboard.adjacent('a', 'p'))
        assertTrue(Corrector.distance("bonjoyr", "bonjour", 2f) < Corrector.distance("bonjoar", "bonjour", 2f))
    }
}
