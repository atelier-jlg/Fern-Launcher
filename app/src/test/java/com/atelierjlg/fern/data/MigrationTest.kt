package com.atelierjlg.fern.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationTest {

    @Test
    fun `ancien fichier - la litiere est retiree`() {
        val text = """{"schema":1,"chat":{"chores":["Gamelle","Litière"],"rules":[{"freq":"DeuxParJour"},{"freq":"Hebdomadaire"}]}}"""
        val c = FernJson.decodeFromString(LauncherConfig.serializer(), text).migrate()
        assertEquals(listOf("Gamelle"), c.chat.chores)
        assertEquals(listOf(ChoreRule(ChoreFreq.DeuxParJour)), c.chat.rules)
        assertEquals(CURRENT_SCHEMA, c.schema)
    }

    @Test
    fun `fichier recent - on ne touche a rien`() {
        val c = LauncherConfig(chat = ChatSettings(chores = listOf("Gamelle", "Litière"))).migrate()
        assertEquals(listOf("Gamelle", "Litière"), c.chat.chores)
    }

    @Test
    fun `par defaut - seulement la gamelle`() {
        assertEquals(listOf("Gamelle"), LauncherConfig().chat.chores)
    }
}
