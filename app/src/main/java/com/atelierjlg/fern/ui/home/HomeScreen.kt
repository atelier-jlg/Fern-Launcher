package com.atelierjlg.fern.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atelierjlg.fern.LauncherViewModel
import com.atelierjlg.fern.OverlayMode
import com.atelierjlg.fern.R
import com.atelierjlg.fern.apps.AppIndex
import com.atelierjlg.fern.data.AppRowBlock
import com.atelierjlg.fern.data.AppWidgetBlock
import com.atelierjlg.fern.data.ContextBlock
import com.atelierjlg.fern.data.MusicBlock
import com.atelierjlg.fern.data.PlaceSettings
import com.atelierjlg.fern.data.SkyBlock
import com.atelierjlg.fern.data.SpacerBlock
import com.atelierjlg.fern.data.AppBlock
import com.atelierjlg.fern.data.WIDGET_CELL_HEIGHT_DP
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.atelierjlg.fern.data.AlternanceBlock
import com.atelierjlg.fern.data.CarnetBlock
import com.atelierjlg.fern.data.CarnetDay
import com.atelierjlg.fern.data.RevisionsBlock
import com.atelierjlg.fern.ui.widgets.AlternanceWidget
import com.atelierjlg.fern.ui.widgets.CarnetWidget
import com.atelierjlg.fern.ui.widgets.RevisionsWidget
import com.atelierjlg.fern.widgets.Anki
import com.atelierjlg.fern.data.ROW_COLUMNS
import com.atelierjlg.fern.data.canChangeWidth
import com.atelierjlg.fern.data.span
import com.atelierjlg.fern.data.groupRows
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import com.atelierjlg.fern.ui.widgets.AppWidgetView
import com.atelierjlg.fern.ui.widgets.ContextWidget
import com.atelierjlg.fern.ui.widgets.MusicWidget
import com.atelierjlg.fern.ui.widgets.SkyWidget
import com.atelierjlg.fern.ui.widgets.WidgetAdder
import com.atelierjlg.fern.data.ClockBlock
import com.atelierjlg.fern.data.MaisonBlock
import com.atelierjlg.fern.data.MaisonKind
import com.atelierjlg.fern.system.PomodoroAlarm
import com.atelierjlg.fern.ui.widgets.ChatWidget
import com.atelierjlg.fern.ui.widgets.CoursWidget
import com.atelierjlg.fern.ui.widgets.MeteoWidget
import com.atelierjlg.fern.ui.widgets.PlanteWidget
import com.atelierjlg.fern.ui.widgets.PomodoroWidget
import com.atelierjlg.fern.ui.widgets.TempsEcranWidget
import com.atelierjlg.fern.widgets.Plant
import com.atelierjlg.fern.data.DOCK_SIZE
import com.atelierjlg.fern.data.HomeBlock
import com.atelierjlg.fern.data.HomePage
import com.atelierjlg.fern.data.PackBlock
import com.atelierjlg.fern.data.SlotRef
import com.atelierjlg.fern.data.newId
import com.atelierjlg.fern.ui.common.ConfirmDialog
import com.atelierjlg.fern.ui.common.GlyphButton
import com.atelierjlg.fern.ui.common.PillButton
import com.atelierjlg.fern.ui.common.TextInputDialog
import com.atelierjlg.fern.ui.theme.Fern
import kotlin.math.abs


/**
 * L'écran d'accueil : les pages (glisser à gauche / à droite), le dock en bas,
 * et le mode édition (appui long n'importe où).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val editing by vm.editing.collectAsStateWithLifecycle()
    val homeTick by vm.homeTick.collectAsStateWithLifecycle()

    val radial by vm.radial.collectAsStateWithLifecycle()
    val gestures = config.gestures
    val space = config.activeSpace
    val pages = space.pages
    val pagerState = rememberPagerState { pages.size }

    // Appui sur Accueil : retour à la première page.
    LaunchedEffect(homeTick) {
        if (homeTick > 0) pagerState.animateScrollToPage(0)
    }

    val actions = SlotActions(
        editing = editing,
        onLaunch = vm::launch,
        onPick = vm::pickFor,
        // Un appui long sur une appli fait la même chose qu'un appui long sur le fond.
        onEnterEdit = { vm.perform(gestures.longPress) },
    )

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .homeGestures(
                enabled = !editing,
                onSwipeUp = { vm.perform(gestures.swipeUp) },
                onSwipeDown = { vm.perform(gestures.swipeDown) },
                onDoubleTap = { vm.perform(gestures.doubleTap) },
                onLongPress = { at -> vm.perform(gestures.longPress, at) },
            ),
    ) {
        val screenHeight = maxHeight
        WallpaperScroll(pagerState, pages.size)

        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            HorizontalPager(
                state = pagerState,
                key = { index -> pages.getOrNull(index)?.id ?: index },
                modifier = Modifier.weight(1f),
            ) { index ->
                val page = pages.getOrNull(index) ?: return@HorizontalPager
                PageView(
                    vm = vm,
                    page = page,
                    index = index,
                    pageCount = pages.size,
                    // Toute la hauteur : juste sous la barre d'état (+ la barre d'édition en mode édition).
                    topSpace = if (editing) 64.dp else 8.dp,
                    apps = apps,
                    actions = actions,
                )
            }
            // Plusieurs Spaces : leur nom s'affiche au-dessus du dock, toucher passe au suivant.
            if (config.spaces.size > 1) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PillButton(
                        text = space.name,
                        onClick = {
                            val i = config.spaces.indexOfFirst { it.id == space.id }
                            vm.setActiveSpace(config.spaces[(i + 1) % config.spaces.size].id)
                        },
                    )
                }
            }
            PageDots(count = pages.size, current = pagerState.currentPage)
            Dock(
                slots = space.dock,
                apps = apps,
                actions = actions,
                onOpenDrawer = { vm.open(OverlayMode.Drawer) },
            )
        }

        radial?.let { center ->
            RadialWheel(
                center = center,
                slots = gestures.radialApps,
                apps = apps,
                onLaunch = { app -> vm.closeRadial(); vm.launch(app) },
                onPick = vm::pickFor,
                onDismiss = vm::closeRadial,
            )
        }

        if (editing) {
            EditBar(
                onAddPage = { name -> vm.addPage(name) },
                onSettings = vm::openSettings,
                onDone = { vm.setEditing(false) },
            )
        }
    }
}

/**
 * Les gestes de l'accueil : glisser vers le haut / le bas, appui long.
 * (Glisser à gauche / à droite est géré par les pages elles-mêmes.)
 *
 * rememberUpdatedState : le détecteur, créé une seule fois, appelle toujours
 * la version la plus récente des fonctions (sinon il redémarrerait en plein geste).
 */
@Composable
private fun Modifier.homeGestures(
    enabled: Boolean,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: (Offset) -> Unit,
): Modifier {
    if (!enabled) return this
    val up by rememberUpdatedState(onSwipeUp)
    val down by rememberUpdatedState(onSwipeDown)
    val double by rememberUpdatedState(onDoubleTap)
    val long by rememberUpdatedState(onLongPress)
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    return this
        .pointerInput(threshold) {
            var total = 0f
            detectVerticalDragGestures(
                onDragStart = { total = 0f },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    total += dragAmount
                },
                onDragEnd = {
                    when {
                        total < -threshold -> up()
                        total > threshold -> down()
                    }
                },
            )
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = { double() },
                onLongPress = { long(it) },
            )
        }
}

/**
 * Le fond d'écran du téléphone glisse doucement avec les pages (effet de profondeur),
 * comme dans les autres lanceurs. Android s'en occupe : on lui dit juste où on en est.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WallpaperScroll(pagerState: PagerState, pageCount: Int) {
    val view = androidx.compose.ui.platform.LocalView.current
    val wallpaper = remember { android.app.WallpaperManager.getInstance(view.context) }
    LaunchedEffect(pageCount) {
        snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction }.collect { position ->
            val steps = (pageCount - 1).coerceAtLeast(1)
            val x = (position / steps).coerceIn(0f, 1f)
            runCatching {
                wallpaper.setWallpaperOffsetSteps(1f / steps, 1f)
                view.windowToken?.let { wallpaper.setWallpaperOffsets(it, x, 0.5f) }
            }
        }
    }
}

/** Le contenu d'une page : titre, puis les blocs (deux packs côte à côte). */
@Composable
private fun PageView(
    vm: LauncherViewModel,
    page: HomePage,
    index: Int,
    pageCount: Int,
    topSpace: Dp,
    apps: AppIndex,
    actions: SlotActions,
) {
    val colors = Fern.colors
    var renamingPage by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renamingPack by remember { mutableStateOf<PackBlock?>(null) }
    var newPack by remember { mutableStateOf(false) }
    var addingWidget by remember { mutableStateOf(false) }
    var resizingWidget by remember { mutableStateOf<AppWidgetBlock?>(null) }
    val place = vm.config.collectAsStateWithLifecycle().value.place
    // Choisir une image dans la galerie / les fichiers : elle devient un sticker de cette page.
    val stickerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.importSticker(page.id, it) }
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .then(if (actions.editing) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(topSpace))

        if (actions.editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = page.title.uppercase(),
                    style = Fern.type.titreWidget,
                    color = colors.creme,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { renamingPage = true },
                )
                GlyphButton("✎", onClick = { renamingPage = true })
                GlyphButton("←", onClick = { vm.movePage(page.id, -1) }, enabled = index > 0)
                GlyphButton("→", onClick = { vm.movePage(page.id, +1) }, enabled = index < pageCount - 1)
                GlyphButton("✕", onClick = { confirmDelete = true }, enabled = pageCount > 1)
            }
            Spacer(Modifier.height(12.dp))
        }
        // Hors mode édition, le titre de la page n'est pas affiché (choix de Jules).

        for (row in groupRows(page.blocks)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                for (block in row) {
                    // Chaque élément prend sa largeur en colonnes (sur 4).
                    Column(Modifier.weight(block.span.toFloat())) {
                        if (actions.editing) {
                            BlockToolbar(
                                label = blockLabel(block),
                                onUp = { vm.moveBlock(page.id, block.id, -1) },
                                onDown = { vm.moveBlock(page.id, block.id, +1) },
                                onDelete = { vm.removeBlock(page.id, block.id) },
                                onRename = if (block is PackBlock) ({ renamingPack = block }) else null,
                                onResize = when (block) {
                                    is AppWidgetBlock -> ({ resizingWidget = block })
                                    is SpacerBlock -> ({ vm.cycleSpacerHeight(page.id, block.id) })
                                    else -> null
                                },
                                onToggleHalf = if (block.canChangeWidth && block !is AppWidgetBlock) ({ vm.cycleWidth(page.id, block.id) }) else null,
                                compact = block.span == 1,
                            )
                        }
                        BlockView(vm, page, block, apps, actions, place)
                    }
                }
                // Les colonnes restantes de la ligne restent vides (l'élément garde sa largeur).
                val rest = ROW_COLUMNS - row.sumOf { it.span }
                if (rest > 0) Spacer(Modifier.weight(rest.toFloat()))
            }
            Spacer(Modifier.height(10.dp))
        }

        if (actions.editing) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("+ Appli", onClick = {
                    // On crée l'emplacement puis on ouvre tout de suite le choix de l'appli.
                    val block = AppBlock(id = newId())
                    vm.addBlock(page.id, block)
                    vm.pickFor(SlotRef.Block(page.id, block.id, 0))
                })
                PillButton("+ Pack", onClick = { newPack = true })
                PillButton("+ Rangée", onClick = { vm.addBlock(page.id, AppRowBlock(id = newId())) })
                PillButton("+ Horloge", onClick = { vm.addBlock(page.id, ClockBlock(id = newId())) })
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("+ Widget", onClick = { addingWidget = true })
                PillButton("+ Sticker", onClick = { stickerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                PillButton("+ Espace", onClick = { vm.addBlock(page.id, SpacerBlock(id = newId(), span = 4)) })
            }
            Spacer(Modifier.height(120.dp))
        }
    }
    // Les stickers, posés librement par-dessus (déplaçables en mode édition).
    StickerLayer(
        stickers = page.stickers,
        editing = actions.editing,
        stickerFile = vm::stickerFile,
        onChange = { vm.updateSticker(page.id, it) },
        onDelete = { vm.removeSticker(page.id, it.id) },
    )
    }

    if (renamingPage) {
        TextInputDialog(
            title = "Nom de la page",
            initial = page.title,
            onConfirm = { vm.renamePage(page.id, it); renamingPage = false },
            onDismiss = { renamingPage = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Supprimer « ${page.title} » ?",
            message = "La page et ses packs disparaissent. Les applis restent installées.",
            confirmLabel = "Supprimer",
            onConfirm = { vm.removePage(page.id); confirmDelete = false },
            onDismiss = { confirmDelete = false },
        )
    }
    renamingPack?.let { pack ->
        TextInputDialog(
            title = "Nom du pack",
            initial = pack.title,
            onConfirm = { vm.renamePack(page.id, pack.id, it); renamingPack = null },
            onDismiss = { renamingPack = null },
        )
    }
    resizingWidget?.let { widget ->
        val options = remember(widget.appWidgetId) { vm.widgetSizeOptions(widget.appWidgetId) }
        val currentRows = (widget.heightDp + WIDGET_CELL_HEIGHT_DP / 2) / WIDGET_CELL_HEIGHT_DP
        AlertDialog(
            onDismissRequest = { resizingWidget = null },
            title = { Text("Taille de « ${widget.provider} »") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Largeur × hauteur, en cases. Seules les tailles acceptées par le widget sont proposées.")
                    for (rowOptions in options.chunked(4)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for ((c, r) in rowOptions) {
                                PillButton(
                                    text = "$c×$r",
                                    accent = c == widget.span && r == currentRows,
                                    onClick = { vm.setWidgetSize(page.id, widget.id, c, r); resizingWidget = null },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { resizingWidget = null }) { Text("Fermer") } },
        )
    }
    if (addingWidget) {
        WidgetAdder(vm = vm, pageId = page.id, onDone = { addingWidget = false })
    }
    if (newPack) {
        TextInputDialog(
            title = "Nouveau pack",
            initial = "",
            confirmLabel = "Créer",
            onConfirm = { name ->
                vm.addBlock(page.id, PackBlock(id = newId(), title = name.ifBlank { "Pack" }))
                newPack = false
            },
            onDismiss = { newPack = false },
        )
    }
}

@Composable
private fun BlockView(
    vm: LauncherViewModel,
    page: HomePage,
    block: HomeBlock,
    apps: AppIndex,
    actions: SlotActions,
    place: PlaceSettings,
) {
    when (block) {
        is ClockBlock -> {
            val weather by vm.weather.collectAsStateWithLifecycle()
            val onClock = vm.config.collectAsStateWithLifecycle().value.place.weatherOnClock
            ClockView(weatherLine = weather?.takeIf { onClock }?.let { "${it.temperature}° ${it.kind.label.uppercase()}" })
        }
        is AppRowBlock -> AppRowView(
            slots = block.apps,
            refFor = { SlotRef.Block(page.id, block.id, it) },
            apps = apps,
            actions = actions,
        )
        is PackBlock -> PackCard(
            title = block.title,
            slots = block.apps,
            refFor = { SlotRef.Block(page.id, block.id, it) },
            apps = apps,
            actions = actions,
        )
        is SkyBlock -> SkyWidget(place, compact = block.half)
        is MusicBlock -> {
            val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
            val access by vm.musicAccess.collectAsStateWithLifecycle()
            MusicWidget(
                compact = block.half,
                nowPlaying = nowPlaying,
                hasAccess = access,
                onPlayPause = vm::musicPlayPause,
                onNext = { vm.musicNext() },
                onPrevious = { vm.musicPrevious() },
                onOpen = vm::openPackage,
            )
        }
        is ContextBlock -> {
            val nextEvent by vm.nextEvent.collectAsStateWithLifecycle()
            ContextWidget(
                note = block.note,
                nextEvent = nextEvent,
                place = place,
                onNoteChange = { vm.setContextNote(page.id, block.id, it) },
            )
        }
        is AppWidgetBlock -> AppWidgetView(vm.widgetHost, block.appWidgetId, block.heightDp)
        is SpacerBlock -> Box(
            Modifier
                .fillMaxWidth()
                .height(block.heightDp.dp)
                .then(
                    // Invisible hors mode édition ; en édition, un cadre pour le repérer.
                    if (actions.editing) Modifier.border(1.dp, Fern.colors.moussePale, RoundedCornerShape(20.dp)) else Modifier,
                ),
        )
        is AlternanceBlock -> {
            val alternance = vm.config.collectAsStateWithLifecycle().value.alternance
            AlternanceWidget(alternance, compact = block.half)
        }
        is RevisionsBlock -> {
            val anki by vm.anki.collectAsStateWithLifecycle()
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshAnki() }
            RevisionsWidget(
                state = anki,
                onOpenAnki = vm::openAnki,
                onRequestPermission = { permission.launch(Anki.PERMISSION) },
            )
        }
        is CarnetBlock -> {
            val carnet = vm.config.collectAsStateWithLifecycle().value.carnet
            CarnetWidget(
                day = carnet.days[java.time.LocalDate.now().toString()] ?: CarnetDay(),
                habits = carnet.habits,
                compact = block.half,
                onMood = vm::setMood,
                onToggleHabit = vm::toggleHabit,
                onNote = vm::setCarnetNote,
                onSendToObsidian = vm::sendNoteToObsidian,
            )
        }
        is MaisonBlock -> MaisonView(vm, block)
        is AppBlock -> Slot(
            app = apps.find(block.app),
            ref = SlotRef.Block(page.id, block.id, 0),
            iconSize = 54.dp,
            showLabel = true,
            actions = actions,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun blockLabel(block: HomeBlock) = when (block) {
    is ClockBlock -> "Horloge"
    is AppRowBlock -> "Rangée"
    is PackBlock -> block.title
    is SkyBlock -> "Ciel"
    is MusicBlock -> "Musique"
    is ContextBlock -> "Contexte"
    is AppWidgetBlock -> block.provider
    is SpacerBlock -> "Espace"
    is AppBlock -> "Appli"
    is AlternanceBlock -> "Alternance"
    is RevisionsBlock -> "Révisions"
    is CarnetBlock -> "Carnet"
    is MaisonBlock -> block.kind.label
}

/** Les widgets maison de la v0.14, chacun branché sur le ViewModel. */
@Composable
private fun MaisonView(vm: LauncherViewModel, block: MaisonBlock) {
    val config by vm.config.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    when (block.kind) {
        MaisonKind.Meteo -> {
            val weather by vm.weather.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { vm.refreshWeather() }
            MeteoWidget(weather, config.place.name, compact = block.half, onRefresh = { vm.refreshWeather(force = true) })
        }
        MaisonKind.Cours -> {
            val cours by vm.cours.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { vm.refreshCours() }
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshCours() }
            CoursWidget(
                state = cours,
                alternance = config.alternance,
                compact = block.half,
                onAllow = { permission.launch(android.Manifest.permission.READ_CALENDAR) },
                onOpenEvent = vm::openEventId,
            )
        }
        MaisonKind.Chat -> ChatWidget(
            settings = config.chat,
            compact = block.half,
            imageFile = vm::stickerFile,
            onToggleChore = vm::toggleChore,
        )
        MaisonKind.Plante -> {
            val state = remember(config.carnet) {
                Plant.state(config.carnet.days, java.time.LocalDate.now(), config.carnet.habits.size)
            }
            PlanteWidget(state, compact = block.half)
        }
        MaisonKind.Pomodoro -> {
            // Android 13+ : il faut demander le droit d'afficher la notification de fin.
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.startPomodoro() }
            PomodoroWidget(
                settings = config.pomodoro,
                compact = block.half,
                onStart = {
                    if (PomodoroAlarm.canNotify(context)) vm.startPomodoro() else permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                },
                onStop = vm::stopPomodoro,
            )
        }
        MaisonKind.TempsEcran -> {
            val screenTime by vm.screenTime.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { vm.refreshScreenTime() }
            TempsEcranWidget(screenTime, config.screenTime.goalMinutes, compact = block.half, onAllow = vm::openUsageAccess)
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    if (count <= 1) return
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (i == current) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (i == current) Fern.colors.creme else Fern.colors.moussePale),
            )
        }
    }
}

/** Le dock : 4 applis + le bouton du tiroir, sur une pilule. */
@Composable
private fun Dock(
    slots: List<String?>,
    apps: AppIndex,
    actions: SlotActions,
    onOpenDrawer: () -> Unit,
) {
    val colors = Fern.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .height(76.dp)
            .background(colors.mousse.copy(alpha = 0.85f), RoundedCornerShape(38.dp))
            .padding(horizontal = 8.dp),
    ) {
        for (index in 0 until DOCK_SIZE) {
            Slot(
                app = apps.find(slots.getOrNull(index)),
                ref = SlotRef.Dock(index),
                iconSize = 52.dp,
                showLabel = false,
                actions = actions,
                modifier = Modifier.weight(1f),
            )
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            DrawerButton(onClick = onOpenDrawer)
        }
    }
}

/** Le bouton du tiroir : une plaque ronde avec une grille de 9 points. */
@Composable
private fun DrawerButton(onClick: () -> Unit) {
    val colors = Fern.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(colors.lierre)
            .clickable(onClick = onClick),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) {
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(colors.pistache),
                        )
                    }
                }
            }
        }
    }
}

/** La barre du haut en mode édition. */
@Composable
private fun EditBar(onAddPage: (String) -> Unit, onSettings: () -> Unit, onDone: () -> Unit) {
    val colors = Fern.colors
    val haptics = LocalHapticFeedback.current
    var newPage by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .background(colors.nuit.copy(alpha = 0.9f), RoundedCornerShape(percent = 50))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text("Édition".uppercase(), style = Fern.type.libelle, color = colors.roseCarmin, modifier = Modifier.weight(1f))
        PillButton("+ Page", onClick = { newPage = true })
        Spacer(Modifier.width(6.dp))
        PillButton("Réglages", onClick = onSettings)
        Spacer(Modifier.width(6.dp))
        PillButton("Terminé", onClick = onDone, accent = true)
    }

    if (newPage) {
        TextInputDialog(
            title = "Nouvelle page",
            initial = "",
            confirmLabel = "Créer",
            onConfirm = { onAddPage(it.ifBlank { "Page" }); newPage = false },
            onDismiss = { newPage = false },
        )
    }
}
