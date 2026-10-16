package com.atelierjlg.fern.keyboard.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExtrasTest {
    // ─── Presse-papiers ───────────────────────────────────────────────────────

    @Test
    fun garderLesCinqDerniers() {
        val h = ClipHistory(keep = 5)
        (1..7).forEach { h.add("texte $it", it.toLong()) }
        assertEquals(listOf("texte 7", "texte 6", "texte 5", "texte 4", "texte 3"), h.entries.map { it.text })
    }

    @Test
    fun lesEpinglesRestent() {
        val h = ClipHistory(keep = 2)
        h.add("adresse", 1)
        h.togglePin("adresse")
        (1..5).forEach { h.add("x$it", 10L + it) }
        assertEquals("adresse", h.entries.first().text)
        assertTrue(h.entries.first().pinned)
        assertEquals(3, h.entries.size)
        h.clearUnpinned()
        assertEquals(listOf("adresse"), h.entries.map { it.text })
    }

    @Test
    fun unTexteRecopieRemonte() {
        val h = ClipHistory()
        h.add("a", 1)
        h.add("b", 2)
        assertTrue(h.add("a", 3))
        assertEquals(listOf("a", "b"), h.entries.map { it.text })
        assertFalse(h.add("a", 4))
    }

    @Test
    fun sauvegardeAvecRetoursALaLigne() {
        val h = ClipHistory()
        h.add("ligne 1\nligne 2\tavec tab \\ et barre", 5)
        h.add("autre", 6)
        h.togglePin("autre")
        val back = ClipHistory.parse(h.serialize(), 5)
        assertEquals(h.entries, back.entries)
    }

    // ─── Emoji ───────────────────────────────────────────────────────────────

    private val emojis by lazy {
        File("src/main/assets/emoji_fr.tsv").useLines { EmojiIndex.parse(it.toList().asSequence()) }
    }

    @Test
    fun rechercheEnFrancais() {
        assertTrue(emojis.all.size > 1500)
        assertTrue(emojis.search("chat").take(3).any { it.char == "🐈" || it.char == "🐱" })
        assertTrue(emojis.search("coeur").isNotEmpty())   // sans accent ni œ
        assertTrue(emojis.search("FÊTE").isNotEmpty())
        assertTrue(emojis.search("mdr").any { it.char == "😂" })
        assertTrue(emojis.search("zzzqqq").isEmpty())
    }

    @Test
    fun emojiPourUnMot() {
        assertNotNull(emojis.forWord("chat"))
        assertEquals(null, emojis.forWord("le"))
    }

    @Test
    fun teinteDePeau() {
        val pouce = emojis.all.first { it.char.startsWith("👍") }
        assertEquals("👍🏽", EmojiIndex.withSkinTone(pouce, 3))
        assertEquals(pouce.char, EmojiIndex.withSkinTone(pouce, 0))
        val chat = emojis.all.first { it.char == "🐈" }
        assertEquals("🐈", EmojiIndex.withSkinTone(chat, 3))
    }

    @Test
    fun favorisEtRecents() {
        val m = EmojiMemory()
        assertTrue(m.toggleFavorite("🐈"))
        m.used("😂")
        m.used("🐈")
        m.used("😂")
        assertEquals(listOf("😂", "🐈"), m.recents)
        val back = EmojiMemory.parse(m.serialize())
        assertEquals(listOf("🐈"), back.favorites)
        assertEquals(listOf("😂", "🐈"), back.recents)
        assertFalse(back.toggleFavorite("🐈"))
        assertTrue(back.favorites.isEmpty())
    }
}
