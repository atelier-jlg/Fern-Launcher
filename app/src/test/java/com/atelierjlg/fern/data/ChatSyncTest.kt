package com.atelierjlg.fern.data

import com.atelierjlg.fern.widgets.ChatRelay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Partage du widget Chat entre deux Fern (via ntfy). */
class ChatSyncTest {

    private val day = "2026-10-06"
    private val shared = LauncherConfig(chat = ChatSettings(sync = ChatSync(enabled = true, topic = "fern-chat-test1234")))

    @Test
    fun `cocher prepare un message pour l'autre`() {
        val c = shared.toggleChoreShared(day, 0, deviceId = "moi")
        assertEquals(setOf(0), c.chat.done[day])
        assertEquals(listOf(ChatEvent(day, 0, "Gamelle", true, "moi")), c.chat.sync.pending)
    }

    @Test
    fun `sans partage, rien en attente`() {
        assertTrue(LauncherConfig().toggleChoreShared(day, 0, "moi").chat.sync.pending.isEmpty())
    }

    @Test
    fun `recevoir coche la case et ignore nos propres messages`() {
        val events = listOf(
            ChatEvent(day, 0, "Gamelle", true, "elle"),
            ChatEvent(day, 1, "Litière", true, "moi"),
        )
        val c = shared.applyChatEvents(events, lastId = "abc", deviceId = "moi")
        assertEquals(setOf(0), c.chat.done[day])
        assertEquals("abc", c.chat.sync.lastId)
    }

    @Test
    fun `tache retrouvee par son nom`() {
        val mine = shared.copy(chat = shared.chat.copy(chores = listOf("Litière", "Gamelle")))
        val c = mine.applyChatEvents(listOf(ChatEvent(day, 0, "gamelle", true, "elle")), "x", "moi")
        assertEquals(setOf(1), c.chat.done[day])
    }

    @Test
    fun `decocher a distance`() {
        val c = shared.setChore(day, 0, true).applyChatEvents(listOf(ChatEvent(day, 0, "Gamelle", false, "elle")), "x", "moi")
        assertEquals(emptySet<Int>(), c.chat.done[day])
    }

    @Test
    fun `envoyes retires de la file`() {
        val c = shared.toggleChoreShared(day, 0, "moi").toggleChoreShared(day, 1, "moi")
        val sent = listOf(c.chat.sync.pending.first())
        assertEquals(1, c.removeSentChatEvents(sent).chat.sync.pending.size)
    }

    @Test
    fun `lecture de la reponse ntfy`() {
        val event = ChatEvent(day, 0, "Gamelle", true, "elle")
        val message = ChatRelay.encode(event).replace("\"", "\\\"")
        val body = """
            {"id":"a1","time":1,"event":"open","topic":"t"}
            {"id":"b2","time":2,"event":"message","topic":"t","message":"$message"}
            {"id":"c3","time":3,"event":"message","topic":"t","message":"pas du json"}
        """.trimIndent()
        val (events, lastId) = ChatRelay.parsePoll(body)
        assertEquals(listOf(event), events)
        assertEquals("c3", lastId)
    }

    @Test
    fun `codes de partage`() {
        assertEquals("fern-chat-abcdefgh", ChatRelay.parseCode(" https://ntfy.sh/fern-chat-abcdefgh/ "))
        assertEquals("fern-chat-abcdefgh", ChatRelay.parseCode("fern-chat-abcdefgh"))
        assertNull(ChatRelay.parseCode("trop court"))
        assertTrue(ChatRelay.newTopic().startsWith("fern-chat-"))
        assertEquals(30, ChatRelay.newTopic().length)
    }
}
