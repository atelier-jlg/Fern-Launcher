package com.atelierjlg.fern.data

import java.util.UUID

/*
 * Toutes les modifications de la configuration.
 *
 * Ce sont des fonctions « pures » : elles prennent une config et en rendent une nouvelle,
 * sans rien modifier en place (comme les tuples en Python). C'est plus sûr, et facile à tester
 * (voir app/src/test/…/ConfigEditsTest.kt).
 */

fun newId(): String = UUID.randomUUID().toString().take(8)

/** Désigne un emplacement d'appli : dans le dock, ou dans un bloc d'une page. */
sealed class SlotRef {
    data class Dock(val index: Int) : SlotRef()
    data class Block(val pageId: String, val blockId: String, val index: Int) : SlotRef()
    data class Radial(val index: Int) : SlotRef()
}

// ─── Outils internes ────────────────────────────────────────────────────────

private fun LauncherConfig.updateActiveSpace(transform: (Space) -> Space): LauncherConfig {
    val active = activeSpace
    return copy(spaces = spaces.map { if (it.id == active.id) transform(it) else it })
}

private fun LauncherConfig.updatePage(pageId: String, transform: (HomePage) -> HomePage) =
    updateActiveSpace { space ->
        space.copy(pages = space.pages.map { if (it.id == pageId) transform(it) else it })
    }

private fun LauncherConfig.updateBlock(pageId: String, blockId: String, transform: (HomeBlock) -> HomeBlock) =
    updatePage(pageId) { page ->
        page.copy(blocks = page.blocks.map { if (it.id == blockId) transform(it) else it })
    }

private fun <T> List<T>.move(index: Int, delta: Int): List<T> {
    val target = index + delta
    if (index !in indices || target !in indices) return this
    return toMutableList().apply { add(target, removeAt(index)) }
}

private fun List<String?>.withSlot(index: Int, key: String?): List<String?> {
    val padded = this + List((index + 1 - size).coerceAtLeast(0)) { null }
    return padded.mapIndexed { i, old -> if (i == index) key else old }
}

/** Les emplacements d'applis d'un bloc (null si le bloc n'en a pas). */
val HomeBlock.slots: List<String?>?
    get() = when (this) {
        is AppRowBlock -> apps
        is PackBlock -> apps
        else -> null
    }

private fun HomeBlock.withSlots(apps: List<String?>): HomeBlock = when (this) {
    is AppRowBlock -> copy(apps = apps)
    is PackBlock -> copy(apps = apps)
    else -> this
}

// ─── Emplacements d'applis ──────────────────────────────────────────────────

/** Range une appli (ou vide l'emplacement avec `key = null`). */
fun LauncherConfig.setSlot(ref: SlotRef, key: String?): LauncherConfig = when (ref) {
    is SlotRef.Dock -> updateActiveSpace { it.copy(dock = it.dock.withSlot(ref.index, key)) }
    is SlotRef.Block -> updateBlock(ref.pageId, ref.blockId) { block ->
        block.slots?.let { block.withSlots(it.withSlot(ref.index, key)) } ?: block
    }
    is SlotRef.Radial -> copy(gestures = gestures.copy(radialApps = gestures.radialApps.withSlot(ref.index, key)))
}

fun LauncherConfig.slotValue(ref: SlotRef): String? = when (ref) {
    is SlotRef.Dock -> activeSpace.dock.getOrNull(ref.index)
    is SlotRef.Block -> activeSpace.pages.firstOrNull { it.id == ref.pageId }
        ?.blocks?.firstOrNull { it.id == ref.blockId }
        ?.slots?.getOrNull(ref.index)
    is SlotRef.Radial -> gestures.radialApps.getOrNull(ref.index)
}

/** Une destination pour « Ajouter à… » depuis le tiroir. */
data class Destination(val label: String, val ref: SlotRef)

/** Liste les endroits où il reste une place libre (premier emplacement vide de chaque zone). */
fun LauncherConfig.freeDestinations(): List<Destination> {
    val space = activeSpace
    val result = mutableListOf<Destination>()
    val dockFree = (0 until DOCK_SIZE).firstOrNull { space.dock.getOrNull(it) == null }
    if (dockFree != null) result += Destination("Dock", SlotRef.Dock(dockFree))
    for (page in space.pages) {
        for (block in page.blocks) {
            val slots = block.slots ?: continue
            val free = (0 until PACK_SIZE).firstOrNull { slots.getOrNull(it) == null } ?: continue
            val name = when (block) {
                is PackBlock -> "${page.title} · ${block.title}"
                else -> "${page.title} · rangée d'applis"
            }
            result += Destination(name, SlotRef.Block(page.id, block.id, free))
        }
    }
    return result
}

// ─── Blocs ──────────────────────────────────────────────────────────────────

fun LauncherConfig.addBlock(pageId: String, block: HomeBlock) =
    updatePage(pageId) { it.copy(blocks = it.blocks + block) }

fun LauncherConfig.removeBlock(pageId: String, blockId: String) =
    updatePage(pageId) { page -> page.copy(blocks = page.blocks.filterNot { it.id == blockId }) }

fun LauncherConfig.moveBlock(pageId: String, blockId: String, delta: Int) =
    updatePage(pageId) { page ->
        page.copy(blocks = page.blocks.move(page.blocks.indexOfFirst { it.id == blockId }, delta))
    }

fun LauncherConfig.renamePack(pageId: String, blockId: String, title: String) =
    updateBlock(pageId, blockId) { block -> if (block is PackBlock) block.copy(title = title) else block }

// ─── Pages ──────────────────────────────────────────────────────────────────

fun LauncherConfig.addPage(title: String): LauncherConfig =
    updateActiveSpace { it.copy(pages = it.pages + HomePage(id = newId(), title = title)) }

/** Supprime une page (on garde toujours au moins une page). */
fun LauncherConfig.removePage(pageId: String): LauncherConfig =
    updateActiveSpace { space ->
        if (space.pages.size <= 1) space else space.copy(pages = space.pages.filterNot { it.id == pageId })
    }

fun LauncherConfig.renamePage(pageId: String, title: String) =
    updatePage(pageId) { it.copy(title = title) }

fun LauncherConfig.movePage(pageId: String, delta: Int) =
    updateActiveSpace { space ->
        space.copy(pages = space.pages.move(space.pages.indexOfFirst { it.id == pageId }, delta))
    }

// ─── Applis ─────────────────────────────────────────────────────────────────

fun LauncherConfig.countLaunch(key: String) =
    copy(launchCounts = launchCounts + (key to (launchCounts[key] ?: 0) + 1))

fun LauncherConfig.setHidden(key: String, hidden: Boolean) =
    copy(hiddenApps = if (hidden) hiddenApps + key else hiddenApps - key)

/** Renomme une appli ; un nom vide rend le nom d'origine. */
fun LauncherConfig.renameApp(key: String, name: String) =
    copy(renamedApps = if (name.isBlank()) renamedApps - key else renamedApps + (key to name.trim()))

// ─── Tiroir ─────────────────────────────────────────────────────────────────

fun LauncherConfig.updateDrawer(transform: (DrawerSettings) -> DrawerSettings) =
    copy(drawer = transform(drawer))

fun LauncherConfig.updateSearch(transform: (SearchSettings) -> SearchSettings) =
    copy(search = transform(search))

fun LauncherConfig.updateGestures(transform: (GestureSettings) -> GestureSettings) =
    copy(gestures = transform(gestures))

// ─── Thèmes ─────────────────────────────────────────────────────────────────

fun LauncherConfig.setThemeColor(key: String, hex: String) =
    if (!isValidHex(hex)) this else copy(theme = theme.copy(colors = theme.colors.with(key, hex.uppercase())))

fun LauncherConfig.applyTheme(theme: NamedTheme) = copy(theme = theme)

/** Enregistre le thème actif sous un nom (remplace un thème du même nom). */
fun LauncherConfig.saveTheme(name: String): LauncherConfig {
    val named = theme.copy(name = name.trim().ifEmpty { "Mon thème" })
    return copy(theme = named, savedThemes = savedThemes.filterNot { it.name == named.name } + named)
}

fun LauncherConfig.deleteSavedTheme(name: String) =
    copy(savedThemes = savedThemes.filterNot { it.name == name })

// ─── Spaces ─────────────────────────────────────────────────────────────────

fun LauncherConfig.setActiveSpace(spaceId: String) =
    if (spaces.any { it.id == spaceId }) copy(activeSpaceId = spaceId) else this

fun LauncherConfig.nextSpace(): LauncherConfig {
    val index = spaces.indexOfFirst { it.id == activeSpace.id }
    return copy(activeSpaceId = spaces[(index + 1) % spaces.size].id)
}

/** Crée un Space ; s'il est « copié », il reprend les pages et le dock du Space actif. */
fun LauncherConfig.addSpace(name: String, copyCurrent: Boolean): LauncherConfig {
    val base = if (copyCurrent) activeSpace else Space()
    val space = base.copy(
        id = newId(),
        name = name.trim().ifEmpty { "Space" },
        // Nouveaux identifiants de pages/blocs pour ne pas mélanger avec l'original.
        // Les widgets Android ne sont pas copiés : chacun n'existe qu'à un seul endroit.
        pages = base.pages.map { page ->
            page.copy(id = newId(), blocks = page.blocks.filterNot { it is AppWidgetBlock }.map { it.withNewId() })
        },
    )
    return copy(spaces = spaces + space, activeSpaceId = space.id)
}

private fun HomeBlock.withNewId(): HomeBlock = when (this) {
    is ClockBlock -> copy(id = newId())
    is AppRowBlock -> copy(id = newId())
    is PackBlock -> copy(id = newId())
    is AppWidgetBlock -> copy(id = newId())
    is SkyBlock -> copy(id = newId())
    is MusicBlock -> copy(id = newId())
    is ContextBlock -> copy(id = newId())
    is SpacerBlock -> copy(id = newId())
}

fun LauncherConfig.renameSpace(spaceId: String, name: String) =
    copy(spaces = spaces.map { if (it.id == spaceId) it.copy(name = name.trim().ifEmpty { it.name }) else it })

/** Supprime un Space (il en reste toujours au moins un) et ses règles de planning. */
fun LauncherConfig.removeSpace(spaceId: String): LauncherConfig {
    if (spaces.size <= 1) return this
    val remaining = spaces.filterNot { it.id == spaceId }
    return copy(
        spaces = remaining,
        activeSpaceId = if (activeSpaceId == spaceId) remaining.first().id else activeSpaceId,
        spaceSchedule = spaceSchedule.copy(rules = spaceSchedule.rules.filterNot { it.spaceId == spaceId }),
    )
}

/** Donne au Space actif son propre thème (ou null pour reprendre le thème général). */
fun LauncherConfig.setSpaceTheme(spaceId: String, theme: NamedTheme?) =
    copy(spaces = spaces.map { if (it.id == spaceId) it.copy(theme = theme) else it })

fun LauncherConfig.updateSchedule(transform: (SpaceSchedule) -> SpaceSchedule) =
    copy(spaceSchedule = transform(spaceSchedule))

/** La règle qui s'applique à ce moment-là (la dernière de la liste gagne), ou null. */
fun activeRuleAt(rules: List<SpaceRule>, dayOfWeek: Int, minuteOfDay: Int): SpaceRule? =
    rules.lastOrNull { rule ->
        if (rule.startMinute <= rule.endMinute) {
            dayOfWeek in rule.days && minuteOfDay >= rule.startMinute && minuteOfDay < rule.endMinute
        } else {
            // Passe minuit : 22 h → 7 h. Avant minuit, on regarde le jour même ; après, la veille.
            val yesterday = if (dayOfWeek == 1) 7 else dayOfWeek - 1
            (dayOfWeek in rule.days && minuteOfDay >= rule.startMinute) ||
                (yesterday in rule.days && minuteOfDay < rule.endMinute)
        }
    }

// ─── Focus ──────────────────────────────────────────────────────────────────

fun LauncherConfig.updateFocus(transform: (FocusSettings) -> FocusSettings) = copy(focus = transform(focus))

/** Les applis cachées en ce moment : masquées + bloquées par le mode Focus. */
val LauncherConfig.currentlyHidden: Set<String>
    get() = if (focus.enabled) hiddenApps + focus.blockedApps else hiddenApps

// ─── Widgets ────────────────────────────────────────────────────────────────

fun LauncherConfig.updateBlockById(pageId: String, blockId: String, transform: (HomeBlock) -> HomeBlock) =
    updateBlock(pageId, blockId, transform)

/** Tous les numéros de widgets Android utilisés (tous Spaces confondus). */
val LauncherConfig.usedAppWidgetIds: Set<Int>
    get() = spaces.flatMap { it.pages }.flatMap { it.blocks }.filterIsInstance<AppWidgetBlock>()
        .map { it.appWidgetId }.toSet()

fun LauncherConfig.updatePlace(transform: (PlaceSettings) -> PlaceSettings) = copy(place = transform(place))

// ─── Icônes & familles ──────────────────────────────────────────────────────

fun LauncherConfig.updateIcons(transform: (IconSettings) -> IconSettings) = copy(icons = transform(icons))

/** Range une appli dans une famille (null = revenir au classement automatique). */
fun LauncherConfig.setAppFamily(key: String, family: Family?) =
    copy(appFamilies = if (family == null) appFamilies - key else appFamilies + (key to family))

// ─── Demi-largeur & stickers ────────────────────────────────────────────────

/** Les éléments qui tiennent en demi-largeur (2×2) et peuvent se mettre côte à côte. */
val HomeBlock.isHalf: Boolean
    get() = when (this) {
        is PackBlock, is SpacerBlock -> true
        is AppWidgetBlock -> half
        is SkyBlock -> half
        is MusicBlock -> half
        is ContextBlock -> half
        is ClockBlock, is AppRowBlock -> false
    }

/** Peut-on passer cet élément de pleine largeur à demi-largeur (et inversement) ? */
val HomeBlock.canToggleHalf: Boolean
    get() = this is AppWidgetBlock || this is SkyBlock || this is MusicBlock || this is ContextBlock

fun LauncherConfig.toggleHalf(pageId: String, blockId: String) = updateBlock(pageId, blockId) { b ->
    when (b) {
        is AppWidgetBlock -> b.copy(half = !b.half)
        is SkyBlock -> b.copy(half = !b.half)
        is MusicBlock -> b.copy(half = !b.half)
        is ContextBlock -> b.copy(half = !b.half)
        else -> b
    }
}

/** Regroupe les blocs en lignes : deux éléments en demi-largeur consécutifs partagent une ligne. */
fun groupRows(blocks: List<HomeBlock>): List<List<HomeBlock>> {
    val rows = mutableListOf<List<HomeBlock>>()
    var pending: HomeBlock? = null
    for (block in blocks) {
        if (block.isHalf) {
            val waiting = pending
            if (waiting == null) {
                pending = block
            } else {
                rows.add(listOf(waiting, block))
                pending = null
            }
        } else {
            pending?.let { rows.add(listOf(it)) }
            pending = null
            rows.add(listOf(block))
        }
    }
    pending?.let { rows.add(listOf(it)) }
    return rows
}

fun LauncherConfig.addSticker(pageId: String, sticker: Sticker) =
    updatePage(pageId) { it.copy(stickers = it.stickers + sticker) }

fun LauncherConfig.updateSticker(pageId: String, sticker: Sticker) =
    updatePage(pageId) { page -> page.copy(stickers = page.stickers.map { if (it.id == sticker.id) sticker else it }) }

fun LauncherConfig.removeSticker(pageId: String, stickerId: String) =
    updatePage(pageId) { page -> page.copy(stickers = page.stickers.filterNot { it.id == stickerId }) }

/** Tous les fichiers de stickers encore utilisés (tous Spaces confondus). */
val LauncherConfig.usedStickerFiles: Set<String>
    get() = spaces.flatMap { it.pages }.flatMap { it.stickers }.map { it.file }.toSet()
