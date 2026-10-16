package com.atelierjlg.fern.keyboard.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemeLibraryTest {
    private fun lib() = MemeLibrary().apply {
        add(MemeLibrary.Meme("a", "a.png", "image/png", listOf("chat", "dodo"), added = 1))
        add(MemeLibrary.Meme("b", "b.gif", "image/gif", listOf("lundi", "fatigué"), added = 2))
        add(MemeLibrary.Meme("c", "c.webp", "image/webp", listOf("chat", "fête"), added = 3, fromFern = "x.img"))
    }

    @Test
    fun rechercheParMotCle() {
        val l = lib()
        assertEquals(setOf("a", "c"), l.search("cha").map { it.id }.toSet())
        assertEquals(listOf("b"), l.search("FATIGUE").map { it.id })
        assertEquals(3, l.search("").size)
    }

    @Test
    fun lesPlusUtilisesDabord() {
        val l = lib()
        l.used("a")
        l.used("a")
        assertEquals("a", l.all.first().id)
        assertEquals(listOf("chat", "dodo", "fatigué", "fête", "lundi"), l.topTags(10))
    }

    @Test
    fun motsCles() {
        assertEquals(listOf("chat", "lundi", "fatigue"), MemeLibrary.parseTags("Chat, lundi  #Fatigue, chat"))
        val l = lib()
        l.setTags("b", "trop, MDR")
        assertEquals(listOf("trop", "mdr"), l.all.first { it.id == "b" }.tags)
    }

    @Test
    fun sauvegarde() {
        val l = lib()
        l.used("c")
        val back = MemeLibrary.parse(l.serialize())
        assertEquals(l.all, back.all)
        assertTrue(back.hasFernSticker("x.img"))
    }

    @Test
    fun typeDImage() {
        assertEquals("image/png", MemeLibrary.sniffMime(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())))
        assertEquals("image/gif", MemeLibrary.sniffMime("GIF89a".toByteArray()))
        assertEquals("image/webp", MemeLibrary.sniffMime("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.ISO_8859_1)))
        assertEquals("image/jpeg", MemeLibrary.sniffMime(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
    }
}
