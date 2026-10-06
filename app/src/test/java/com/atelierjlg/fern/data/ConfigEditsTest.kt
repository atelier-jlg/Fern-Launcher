package com.atelierjlg.fern.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests automatiques : lancés par la CI à chaque compilation. */
class ConfigEditsTest {

    private fun sample(): LauncherConfig {
        val page = HomePage(
            id = "p1",
            title = "Journée",
            blocks = listOf(
                PackBlock(id = "a", title = "Organisation", apps = listOf("x@0", null, null, null)),
                PackBlock(id = "b", title = "Travail"),
                ClockBlock(id = "c"),
            ),
        )
        return LauncherConfig(spaces = listOf(Space(pages = listOf(page))))
    }

    @Test
    fun setSlotFillsAndClears() {
        val ref = SlotRef.Block("p1", "a", 2)
        val filled = sample().setSlot(ref, "y@0")
        assertEquals("y@0", filled.slotValue(ref))
        assertNull(filled.setSlot(ref, null).slotValue(ref))
    }

    @Test
    fun dockSlot() {
        val config = sample().setSlot(SlotRef.Dock(3), "cam@0")
        assertEquals(listOf(null, null, null, "cam@0"), config.activeSpace.dock)
    }

    @Test
    fun radialSlot() {
        val config = sample().setSlot(SlotRef.Radial(2), "app@0")
        assertEquals("app@0", config.slotValue(SlotRef.Radial(2)))
        assertEquals(RADIAL_SIZE, config.gestures.radialApps.size)
    }

    @Test
    fun moveBlockStaysInBounds() {
        val moved = sample().moveBlock("p1", "a", +1)
        assertEquals(listOf("b", "a", "c"), moved.activeSpace.pages[0].blocks.map { it.id })
        val unchanged = sample().moveBlock("p1", "a", -1)
        assertEquals(listOf("a", "b", "c"), unchanged.activeSpace.pages[0].blocks.map { it.id })
    }

    @Test
    fun cannotRemoveLastPage() {
        val config = sample().removePage("p1")
        assertEquals(1, config.activeSpace.pages.size)
    }

    @Test
    fun freeDestinationsListsFirstFreeSlot() {
        val dests = sample().freeDestinations()
        assertEquals(SlotRef.Dock(0), dests.first().ref)
        assertTrue(dests.any { it.ref == SlotRef.Block("p1", "a", 1) })
        assertTrue(dests.any { it.ref == SlotRef.Block("p1", "b", 0) })
    }

    @Test
    fun renameAndHide() {
        val config = sample().renameApp("x@0", "  Mon appli ").setHidden("x@0", true)
        assertEquals("Mon appli", config.renamedApps["x@0"])
        assertTrue("x@0" in config.hiddenApps)
        assertNull(config.renameApp("x@0", "").renamedApps["x@0"])
    }

    @Test
    fun themeEdits() {
        val config = sample().setThemeColor("creme", "#ffffff").setThemeColor("nuit", "pas une couleur")
        assertEquals("#FFFFFF", config.theme.colors.creme)
        assertEquals(ThemeColors().nuit, config.theme.colors.nuit)
        val saved = config.saveTheme("Clair").saveTheme("Clair")
        assertEquals(1, saved.savedThemes.size)
        assertEquals("Clair", saved.theme.name)
    }

    @Test
    fun themeFileRoundTrip() {
        val file = ThemeFile(theme = ThemePresets.NuitTeal)
        val text = FernJson.encodeToString(ThemeFile.serializer(), file)
        assertEquals(file, FernJson.decodeFromString(ThemeFile.serializer(), text))
    }

    @Test
    fun spaces() {
        val two = sample().addSpace("Travail", copyCurrent = true)
        assertEquals(2, two.spaces.size)
        assertEquals("Travail", two.activeSpace.name)
        // La copie a les mêmes blocs mais de nouveaux identifiants.
        assertEquals(3, two.activeSpace.pages[0].blocks.size)
        assertTrue(two.activeSpace.pages[0].id != "p1")
        val back = two.nextSpace()
        assertEquals(DEFAULT_SPACE_ID, back.activeSpace.id)
        val one = two.removeSpace(two.spaces[1].id)
        assertEquals(1, one.spaces.size)
        assertEquals(one, one.removeSpace(one.spaces[0].id))
    }

    @Test
    fun scheduleRules() {
        val work = SpaceRule("r1", "travail", days = setOf(1, 2, 3, 4, 5), startMinute = 8 * 60, endMinute = 18 * 60)
        val night = SpaceRule("r2", "nuit", days = (1..7).toSet(), startMinute = 22 * 60, endMinute = 7 * 60)
        val rules = listOf(work, night)
        assertEquals("travail", activeRuleAt(rules, 1, 9 * 60)?.spaceId)
        assertNull(activeRuleAt(rules, 6, 9 * 60))
        assertEquals("nuit", activeRuleAt(rules, 3, 23 * 60)?.spaceId)
        assertEquals("nuit", activeRuleAt(rules, 4, 6 * 60)?.spaceId)
        assertNull(activeRuleAt(rules, 4, 19 * 60))
    }

    @Test
    fun focusHidesBlockedApps() {
        val config = sample().setHidden("a@0", true).updateFocus { it.copy(blockedApps = setOf("insta@0")) }
        assertEquals(setOf("a@0"), config.currentlyHidden)
        assertEquals(setOf("a@0", "insta@0"), config.updateFocus { it.copy(enabled = true) }.currentlyHidden)
    }

    @Test
    fun jsonRoundTrip() {
        val config = sample().countLaunch("x@0").countLaunch("x@0")
        val text = FernJson.encodeToString(LauncherConfig.serializer(), config)
        val back = FernJson.decodeFromString(LauncherConfig.serializer(), text)
        assertEquals(config, back)
        assertEquals(2, back.launchCounts["x@0"])
    }

    @Test
    fun oldFileWithUnknownFieldsStillLoads() {
        val text = """{"schema":1,"champInconnu":42,"spaces":[{"id":"perso","name":"Perso"}]}"""
        val config = FernJson.decodeFromString(LauncherConfig.serializer(), text)
        assertEquals("Perso", config.activeSpace.name)
    }
}
