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
        is AppBlock -> listOf(app)
        else -> null
    }

private fun HomeBlock.withSlots(apps: List<String?>): HomeBlock = when (this) {
    is AppRowBlock -> copy(apps = apps)
    is PackBlock -> copy(apps = apps)
    is AppBlock -> copy(app = apps.firstOrNull())
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
                is AppBlock -> "${page.title} · emplacement d'appli"
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
    is AppBlock -> copy(id = newId())
    is AlternanceBlock -> copy(id = newId())
    is RevisionsBlock -> copy(id = newId())
    is CarnetBlock -> copy(id = newId())
    is MaisonBlock -> copy(id = newId())
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

/** Une ligne de l'accueil fait 4 colonnes. */
const val ROW_COLUMNS = 4

/**
 * Largeur d'un élément, en colonnes sur 4 : 4 = ligne entière, 2 = demi (2×2), 1 = quart.
 * Les éléments consécutifs se rangent sur la même ligne tant qu'il reste de la place.
 */
val HomeBlock.span: Int
    get() = when (this) {
        is PackBlock -> 2
        is AppBlock -> 1
        is SpacerBlock -> span.coerceIn(1, ROW_COLUMNS)
        is AppWidgetBlock -> if (columns in 1..ROW_COLUMNS) columns else if (half) 2 else 4
        is SkyBlock -> if (half) 2 else 4
        is MusicBlock -> if (half) 2 else 4
        is ContextBlock -> if (half) 2 else 4
        is AlternanceBlock -> if (half) 2 else 4
        is RevisionsBlock -> if (half) 2 else 4
        is CarnetBlock -> if (half) 2 else 4
        is MaisonBlock -> if (half) 2 else 4
        is ClockBlock, is AppRowBlock -> 4
    }

/** Peut-on changer la largeur de cet élément (bouton ⇔) ? */
val HomeBlock.canChangeWidth: Boolean
    get() = this is AppWidgetBlock || this is SkyBlock || this is MusicBlock || this is ContextBlock ||
        this is SpacerBlock || this is AlternanceBlock || this is RevisionsBlock || this is CarnetBlock ||
        this is MaisonBlock

/** Bouton ⇔ : widgets entier ↔ demi ; espaces entier → demi → quart → entier. */
fun LauncherConfig.cycleWidth(pageId: String, blockId: String) = updateBlock(pageId, blockId) { b ->
    when (b) {
        is AppWidgetBlock -> b.copy(half = !b.half)
        is SkyBlock -> b.copy(half = !b.half)
        is MusicBlock -> b.copy(half = !b.half)
        is ContextBlock -> b.copy(half = !b.half)
        is SpacerBlock -> b.copy(span = when (b.span) { 4 -> 2; 2 -> 1; else -> 4 })
        is AlternanceBlock -> b.copy(half = !b.half)
        is RevisionsBlock -> b.copy(half = !b.half)
        is CarnetBlock -> b.copy(half = !b.half)
        is MaisonBlock -> b.copy(half = !b.half)
        else -> b
    }
}

/** Bouton ↕ d'un espace : 40 → 80 → 120 → 200 dp. */
fun LauncherConfig.cycleSpacerHeight(pageId: String, blockId: String) = updateBlock(pageId, blockId) { b ->
    if (b is SpacerBlock) {
        val heights = listOf(40, 80, 120, 200)
        b.copy(heightDp = heights[(heights.indexOf(b.heightDp) + 1).mod(heights.size)])
    } else {
        b
    }
}

/** Regroupe les blocs en lignes de 4 colonnes, dans l'ordre. */
fun groupRows(blocks: List<HomeBlock>): List<List<HomeBlock>> {
    val rows = mutableListOf<List<HomeBlock>>()
    var current = mutableListOf<HomeBlock>()
    var used = 0
    for (block in blocks) {
        if (used + block.span > ROW_COLUMNS && current.isNotEmpty()) {
            rows.add(current)
            current = mutableListOf()
            used = 0
        }
        current.add(block)
        used += block.span
    }
    if (current.isNotEmpty()) rows.add(current)
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
    get() = spaces.flatMap { it.pages }.flatMap { it.stickers }.map { it.file }.toSet() +
        chat.images.values

// ─── Alternance & carnet ────────────────────────────────────────────────────

fun LauncherConfig.updateAlternance(transform: (AlternanceSettings) -> AlternanceSettings) =
    copy(alternance = transform(alternance))

fun LauncherConfig.updateCarnetSettings(transform: (CarnetSettings) -> CarnetSettings) =
    copy(carnet = transform(carnet))

/** Modifie la journée `date` (ISO) du carnet ; on ne garde que les 90 derniers jours. */
fun LauncherConfig.updateCarnetDay(date: String, transform: (CarnetDay) -> CarnetDay): LauncherConfig {
    val day = transform(carnet.days[date] ?: CarnetDay())
    val days = (carnet.days + (date to day)).toSortedMap().entries.toList().takeLast(90).associate { it.key to it.value }
    return copy(carnet = carnet.copy(days = days))
}

/** Hauteur d'une « case » de widget, en dp (une ligne de la grille). */
const val WIDGET_CELL_HEIGHT_DP = 90

/** Change la taille d'un widget Android : largeur en colonnes, hauteur en cases. */
fun LauncherConfig.setWidgetSize(pageId: String, blockId: String, columns: Int, rows: Int) =
    updateBlock(pageId, blockId) { b ->
        if (b is AppWidgetBlock) b.copy(columns = columns.coerceIn(1, ROW_COLUMNS), heightDp = rows.coerceIn(1, 6) * WIDGET_CELL_HEIGHT_DP) else b
    }

// ─── Widgets maison v0.14 ───────────────────────────────────────────────────

fun LauncherConfig.updateCours(transform: (CoursSettings) -> CoursSettings) = copy(cours = transform(cours))

fun LauncherConfig.updateChat(transform: (ChatSettings) -> ChatSettings) = copy(chat = transform(chat))

fun LauncherConfig.updateScreenTime(transform: (ScreenTimeSettings) -> ScreenTimeSettings) =
    copy(screenTime = transform(screenTime))

/** Coche / décoche une tâche du chat pour ce jour (on garde 14 jours). */
fun LauncherConfig.toggleChore(date: String, index: Int): LauncherConfig {
    val today = chat.done[date].orEmpty()
    val updated = if (index in today) today - index else today + index
    val done = (chat.done + (date to updated)).toSortedMap().entries.toList().takeLast(14).associate { it.key to it.value }
    return copy(chat = chat.copy(done = done))
}

/** Réglages du Pomodoro (durées, Focus auto) sans toucher au minuteur en cours. */
fun LauncherConfig.updatePomodoroSettings(workMinutes: Int, breakMinutes: Int, autoFocus: Boolean) =
    copy(pomodoro = pomodoro.copy(workMinutes = workMinutes.coerceIn(1, 120), breakMinutes = breakMinutes.coerceIn(1, 60), autoFocus = autoFocus))

/** Lance une séance de travail. Avec `autoFocus`, le mode Focus s'allume (et on retient s'il l'était déjà). */
fun LauncherConfig.startPomodoro(now: Long): LauncherConfig {
    val p = pomodoro
    val wasOn = if (p.phase == PomodoroPhase.Travail) p.focusBefore else focus.enabled
    return copy(
        pomodoro = p.copy(phase = PomodoroPhase.Travail, startedAt = now, endsAt = now + p.workMinutes * 60_000L, focusBefore = wasOn),
        focus = if (p.autoFocus) focus.copy(enabled = true) else focus,
    )
}

/** Arrête tout ; si le Focus avait été allumé par le Pomodoro, on le remet comme avant. */
fun LauncherConfig.stopPomodoro(): LauncherConfig {
    val p = pomodoro
    val restoredFocus = if (p.phase == PomodoroPhase.Travail && p.autoFocus) focus.copy(enabled = p.focusBefore) else focus
    return copy(pomodoro = p.copy(phase = PomodoroPhase.Arret, startedAt = 0, endsAt = 0), focus = restoredFocus)
}

/**
 * Fait avancer le minuteur : travail fini → pause (Focus remis comme avant) ; pause finie → arrêt.
 * Appelée régulièrement, et au retour sur Fern (le téléphone a pu rester en veille longtemps).
 */
fun LauncherConfig.tickPomodoro(now: Long): LauncherConfig {
    var c = this
    if (c.pomodoro.phase == PomodoroPhase.Travail && now >= c.pomodoro.endsAt) {
        val p = c.pomodoro
        c = c.copy(
            pomodoro = p.copy(phase = PomodoroPhase.Pause, startedAt = p.endsAt, endsAt = p.endsAt + p.breakMinutes * 60_000L),
            focus = if (p.autoFocus) c.focus.copy(enabled = p.focusBefore) else c.focus,
        )
    }
    if (c.pomodoro.phase == PomodoroPhase.Pause && now >= c.pomodoro.endsAt) {
        c = c.copy(pomodoro = c.pomodoro.copy(phase = PomodoroPhase.Arret, startedAt = 0, endsAt = 0))
    }
    return c
}

/** Y a-t-il un widget de ce type quelque part (pour ne pas calculer pour rien) ? */
fun LauncherConfig.hasMaison(kind: MaisonKind): Boolean =
    spaces.flatMap { it.pages }.flatMap { it.blocks }.any { it is MaisonBlock && it.kind == kind }
