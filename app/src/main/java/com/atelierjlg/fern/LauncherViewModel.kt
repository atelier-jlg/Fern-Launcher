package com.atelierjlg.fern

import android.Manifest
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atelierjlg.fern.apps.AppEntry
import com.atelierjlg.fern.apps.AppIndex
import com.atelierjlg.fern.apps.AppRepository
import com.atelierjlg.fern.data.ConfigStore
import com.atelierjlg.fern.data.DefaultLayout
import com.atelierjlg.fern.data.DrawerSettings
import com.atelierjlg.fern.data.HomeBlock
import com.atelierjlg.fern.data.LauncherConfig
import com.atelierjlg.fern.data.SlotRef
import com.atelierjlg.fern.data.addBlock
import com.atelierjlg.fern.data.addPage
import com.atelierjlg.fern.data.countLaunch
import com.atelierjlg.fern.data.freeDestinations
import com.atelierjlg.fern.data.moveBlock
import com.atelierjlg.fern.data.movePage
import com.atelierjlg.fern.data.removeBlock
import com.atelierjlg.fern.data.removePage
import com.atelierjlg.fern.data.renameApp
import com.atelierjlg.fern.data.renamePack
import com.atelierjlg.fern.data.renamePage
import com.atelierjlg.fern.data.setHidden
import com.atelierjlg.fern.data.setSlot
import com.atelierjlg.fern.data.updateDrawer
import com.atelierjlg.fern.search.Calculator
import com.atelierjlg.fern.search.ContactResult
import com.atelierjlg.fern.search.EventResult
import com.atelierjlg.fern.search.SearchExtras
import com.atelierjlg.fern.search.SearchRepository
import com.atelierjlg.fern.search.ShortcutResult
import com.atelierjlg.fern.data.AppWidgetBlock
import com.atelierjlg.fern.data.ContextBlock
import com.atelierjlg.fern.data.FernJson
import com.atelierjlg.fern.data.ROW_COLUMNS
import com.atelierjlg.fern.data.WIDGET_CELL_HEIGHT_DP
import com.atelierjlg.fern.data.setWidgetSize
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import com.atelierjlg.fern.data.AlternanceSettings
import com.atelierjlg.fern.data.CarnetSettings
import com.atelierjlg.fern.data.updateAlternance
import com.atelierjlg.fern.data.updateCarnetDay
import com.atelierjlg.fern.data.updateCarnetSettings
import com.atelierjlg.fern.widgets.Anki
import com.atelierjlg.fern.data.CatPose
import com.atelierjlg.fern.data.ChatSettings
import com.atelierjlg.fern.data.CoursSettings
import com.atelierjlg.fern.data.MaisonKind
import com.atelierjlg.fern.data.PomodoroPhase
import com.atelierjlg.fern.data.ScreenTimeSettings
import com.atelierjlg.fern.data.hasMaison
import com.atelierjlg.fern.data.migrate
import com.atelierjlg.fern.data.startPomodoro
import com.atelierjlg.fern.data.stopPomodoro
import com.atelierjlg.fern.data.tickPomodoro
import com.atelierjlg.fern.data.ChatEvent
import com.atelierjlg.fern.data.applyChatEvents
import com.atelierjlg.fern.data.removeSentChatEvents
import com.atelierjlg.fern.data.toggleChoreShared
import com.atelierjlg.fern.data.ruleFor
import com.atelierjlg.fern.data.setChoreRule
import com.atelierjlg.fern.data.ChoreRule
import com.atelierjlg.fern.widgets.ChatSchedule
import com.atelierjlg.fern.widgets.ChatRelay
import com.atelierjlg.fern.data.updateChat
import com.atelierjlg.fern.data.updateCours
import com.atelierjlg.fern.data.updatePomodoroSettings
import com.atelierjlg.fern.data.updateScreenTime
import com.atelierjlg.fern.system.PomodoroAlarm
import com.atelierjlg.fern.widgets.Agenda
import com.atelierjlg.fern.widgets.CalendarInfo
import com.atelierjlg.fern.widgets.CoursState
import com.atelierjlg.fern.widgets.ScreenTime
import com.atelierjlg.fern.widgets.ScreenTimeState
import com.atelierjlg.fern.widgets.Weather
import com.atelierjlg.fern.widgets.WeatherCodes
import com.atelierjlg.fern.data.Sticker
import com.atelierjlg.fern.data.addSticker
import com.atelierjlg.fern.data.removeSticker
import com.atelierjlg.fern.data.cycleSpacerHeight
import com.atelierjlg.fern.data.cycleWidth
import com.atelierjlg.fern.data.updateSticker
import com.atelierjlg.fern.data.usedStickerFiles
import java.io.File
import com.atelierjlg.fern.data.Family
import com.atelierjlg.fern.data.IconSettings
import com.atelierjlg.fern.data.setAppFamily
import com.atelierjlg.fern.data.updateIcons
import com.atelierjlg.fern.data.PlaceSettings
import com.atelierjlg.fern.data.newId
import com.atelierjlg.fern.data.updateBlockById
import com.atelierjlg.fern.data.updatePlace
import com.atelierjlg.fern.data.usedAppWidgetIds
import com.atelierjlg.fern.widgets.MusicController
import com.atelierjlg.fern.widgets.NowPlaying
import com.atelierjlg.fern.widgets.WidgetHost
import com.atelierjlg.fern.data.FocusSettings
import com.atelierjlg.fern.data.SpaceSchedule
import com.atelierjlg.fern.data.activeRuleAt
import com.atelierjlg.fern.data.addSpace
import com.atelierjlg.fern.data.nextSpace
import com.atelierjlg.fern.data.removeSpace
import com.atelierjlg.fern.data.renameSpace
import com.atelierjlg.fern.data.setActiveSpace
import com.atelierjlg.fern.data.setSpaceTheme
import com.atelierjlg.fern.data.updateFocus
import com.atelierjlg.fern.data.updateSchedule
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import com.atelierjlg.fern.data.GestureAction
import com.atelierjlg.fern.data.NamedTheme
import com.atelierjlg.fern.data.SearchSettings
import com.atelierjlg.fern.data.ThemeFile
import com.atelierjlg.fern.data.applyTheme
import com.atelierjlg.fern.data.deleteSavedTheme
import com.atelierjlg.fern.data.saveTheme
import com.atelierjlg.fern.data.setThemeColor
import com.atelierjlg.fern.data.updateSearch
import com.atelierjlg.fern.data.GestureBinding
import com.atelierjlg.fern.data.GestureSettings
import com.atelierjlg.fern.data.updateGestures
import com.atelierjlg.fern.system.SystemActions
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ce qui est ouvert par-dessus l'accueil. */
enum class OverlayMode {
    /** Le tiroir d'applis (glisser vers le haut). */
    Drawer,

    /** Le tiroir avec le clavier déjà ouvert (glisser vers le bas). */
    Search,
}

data class OverlayState(
    val visible: Boolean = false,
    /** On garde le dernier mode même fermé, pour l'animation de fermeture. */
    val mode: OverlayMode = OverlayMode.Drawer,
)

/**
 * Le « cerveau » de Fern : il détient l'état et reçoit les actions.
 * Les écrans (Compose) ne font qu'afficher cet état et appeler ces fonctions.
 *
 * Un ViewModel survit aux rotations et aux recréations de l'écran.
 */
/** Les variantes de Firefox, dans l'ordre de préférence. */
private val FIREFOX_PACKAGES = listOf(
    "org.mozilla.firefox",
    "org.mozilla.fennec_fdroid",
    "org.mozilla.firefox_beta",
    "org.mozilla.fenix",
    "org.mozilla.focus",
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application).also { it.start() }
    private val store = ConfigStore(application)

    val config: StateFlow<LauncherConfig> = store.config

    /** Les applis telles qu'affichées (renommées, masquées à part…). */
    val apps: StateFlow<AppIndex> = combine(repository.apps, store.config) { apps, config ->
        AppIndex.build(apps, config)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppIndex.Empty)

    private val _overlay = MutableStateFlow(OverlayState())
    val overlay: StateFlow<OverlayState> = _overlay.asStateFlow()

    /** Mode édition de l'accueil (appui long). */
    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    /** Augmente à chaque appui sur Accueil (l'accueil revient alors à la première page). */
    private val _homeTick = MutableStateFlow(0)
    val homeTick: StateFlow<Int> = _homeTick.asStateFlow()

    /** Emplacement en cours de remplissage : le sélecteur d'applis est ouvert. */
    private val _picking = MutableStateFlow<SlotRef?>(null)
    val picking: StateFlow<SlotRef?> = _picking.asStateFlow()

    private val searchRepository = SearchRepository(application)

    /** Augmente quand les autorisations changent : la recherche se relance. */
    private val _searchVersion = MutableStateFlow(0)
    val searchVersion: StateFlow<Int> = _searchVersion.asStateFlow()

    // ─── Widgets ─────────────────────────────────────────────────────────────

    val widgetHost = WidgetHost(application)
    private val music = MusicController(application)
    val nowPlaying: StateFlow<NowPlaying?> = music.nowPlaying
    val musicAccess: StateFlow<Boolean> = music.hasAccess

    private val _nextEvent = MutableStateFlow<EventResult?>(null)
    val nextEvent: StateFlow<EventResult?> = _nextEvent.asStateFlow()

    init {
        // Widgets Android : on libère les numéros réservés qui ne servent plus.
        widgetHost.cleanup(store.config.value.usedAppWidgetIds)
        // Prochain événement (widget Contexte) : toutes les 5 minutes.
        viewModelScope.launch {
            while (true) {
                _nextEvent.value = withContext(Dispatchers.IO) { searchRepository.nextEvent() }
                refreshCours()
                refreshScreenTime()
                delay(5 * 60_000L)
            }
        }
    }

    // ─── Météo, cours, chat, plante, Pomodoro, temps d'écran (v0.14) ────────

    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather.asStateFlow()
    private val weatherFile: File get() = File(getApplication<Application>().filesDir, "weather.json")

    /** Recharge la météo si elle a plus de 30 min (ou tout de suite avec `force`). */
    fun refreshWeather(force: Boolean = false) {
        val c = config.value
        if (!c.place.weatherOnClock && !c.hasMaison(MaisonKind.Meteo)) return
        val last = _weather.value
        if (!force && last != null && System.currentTimeMillis() - last.fetchedAt < 30 * 60_000L) return
        viewModelScope.launch {
            val fresh = withContext(Dispatchers.IO) { WeatherCodes.fetch(c.place.latitude, c.place.longitude) }
            if (fresh != null) {
                _weather.value = fresh
                withContext(Dispatchers.IO) { runCatching { weatherFile.writeText(FernJson.encodeToString(Weather.serializer(), fresh)) } }
            } else if (force) {
                toast("Météo indisponible (pas de réseau ?)")
            }
        }
    }

    private val _cours = MutableStateFlow(CoursState())
    val cours: StateFlow<CoursState> = _cours.asStateFlow()

    fun refreshCours() {
        val c = config.value
        if (!c.hasMaison(MaisonKind.Cours)) return
        viewModelScope.launch {
            _cours.value = withContext(Dispatchers.IO) {
                Agenda.coursState(getApplication(), c.cours.calendarIds, c.cours.examKeywords)
            }
        }
    }

    /** Les agendas Android (pour choisir ceux du widget Cours). */
    suspend fun calendars(): List<CalendarInfo> = withContext(Dispatchers.IO) { Agenda.calendars(getApplication()) }

    fun updateCours(transform: (CoursSettings) -> CoursSettings) {
        store.update { it.updateCours(transform) }
        refreshCours()
    }

    fun openEventId(eventId: Long) = startSafely(
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)),
    )

    fun updateChat(transform: (ChatSettings) -> ChatSettings) = store.update { it.updateChat(transform) }

    /** Coche une tâche ; si le partage est actif, l'autre téléphone est prévenu tout de suite. */
    fun toggleChore(index: Int) {
        // La « période » dépend du rythme de la tâche (gamelle : matin / soir ; litière : semaine).
        val key = ChatSchedule.periodKey(config.value.chat.ruleFor(index), java.time.LocalDateTime.now())
        store.update { it.toggleChoreShared(key, index, deviceId) }
        syncChat()
    }

    /**
     * Identifiant de CE téléphone, tiré au hasard une fois. Gardé hors de la config :
     * ainsi, même si ta compagne importe ta sauvegarde, vos deux Fern restent distincts.
     */
    private val deviceId: String by lazy {
        val prefs = getApplication<Application>().getSharedPreferences("fern-device", android.content.Context.MODE_PRIVATE)
        prefs.getString("id", null) ?: ChatRelay.newDeviceId().also { prefs.edit().putString("id", it).apply() }
    }

    fun setChoreRule(index: Int, rule: ChoreRule) = store.update { it.setChoreRule(index, rule) }

    /** Fern est-il à l'écran ? (On ne lit le relais que dans ce cas : économie de batterie.) */
    private var inForeground = false
    private var syncing = false
    private var syncAgain = false

    /**
     * Partage du chat : envoie les changements en attente, puis lit ceux de l'autre téléphone.
     * Appelé au retour sur l'accueil, toutes les 30 s tant qu'il est affiché, et après chaque case cochée.
     */
    fun syncChat() {
        val sync = config.value.chat.sync
        if (!sync.enabled || sync.topic.isBlank()) return
        if (syncing) {
            // Une relève est déjà en cours : on en refera une juste après (pour la case qu'on vient de cocher).
            syncAgain = true
            return
        }
        syncing = true
        viewModelScope.launch {
            try {
                val sent = withContext(Dispatchers.IO) {
                    val ok = mutableListOf<ChatEvent>()
                    for (event in sync.pending) {
                        if (ChatRelay.send(sync.server, sync.topic, event)) ok += event else break
                    }
                    ok
                }
                if (sent.isNotEmpty()) store.update { it.removeSentChatEvents(sent) }
                val received = withContext(Dispatchers.IO) { ChatRelay.poll(sync.server, sync.topic, config.value.chat.sync.lastId) }
                if (received != null) store.update { it.applyChatEvents(received.first, received.second, deviceId) }
                _chatSyncError.value = ChatRelay.lastError
            } finally {
                syncing = false
                if (syncAgain) {
                    syncAgain = false
                    syncChat()
                }
            }
        }
    }

    /** Le dernier problème de partage (null = tout va bien), affiché dans Paramètres → Le chat. */
    private val _chatSyncError = MutableStateFlow<String?>(null)
    val chatSyncError: StateFlow<String?> = _chatSyncError.asStateFlow()

    /** Crée un partage (nouveau code secret) ou en rejoint un (code reçu). */
    fun startChatSync(code: String?) {
        val topic = code?.let { ChatRelay.parseCode(it) } ?: if (code == null) ChatRelay.newTopic() else null
        if (topic == null) {
            toast("Code invalide")
            return
        }
        store.update { c ->
            c.updateChat {
                it.copy(sync = it.sync.copy(enabled = true, topic = topic, lastId = "", pending = emptyList()))
            }
        }
        syncChat()
    }

    fun stopChatSync() = store.update { c -> c.updateChat { it.copy(sync = it.sync.copy(enabled = false, pending = emptyList())) } }

    /** Ouvre le menu « Partager » d'Android avec ce texte (Signal, SMS…). */
    fun shareText(text: String) = startSafely(
        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null),
    )

    /** Copie une image (ou animation GIF / WebP) choisie pour le chat dans le dossier des stickers. */
    fun importChatImage(pose: CatPose, uri: Uri) {
        viewModelScope.launch {
            val name = "${newId()}.img"
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        stickerFile(name).outputStream().use { input.copyTo(it) }
                    } ?: error("illisible")
                }.isSuccess
            }
            if (ok) {
                store.update { c -> c.updateChat { it.copy(images = it.images + (pose to name)) } }
            } else {
                toast("Impossible de lire cette image")
            }
        }
    }

    fun startPomodoro() {
        val now = System.currentTimeMillis()
        store.update { it.startPomodoro(now) }
        val p = config.value.pomodoro
        PomodoroAlarm.schedule(getApplication(), p.endsAt, p.endsAt + p.breakMinutes * 60_000L)
    }

    fun stopPomodoro() {
        store.update { it.stopPomodoro() }
        PomodoroAlarm.cancel(getApplication())
    }

    private fun tickPomodoro() = store.update { it.tickPomodoro(System.currentTimeMillis()) }

    fun updatePomodoroSettings(workMinutes: Int, breakMinutes: Int, autoFocus: Boolean) =
        store.update { it.updatePomodoroSettings(workMinutes, breakMinutes, autoFocus) }

    private val _screenTime = MutableStateFlow(ScreenTimeState())
    val screenTime: StateFlow<ScreenTimeState> = _screenTime.asStateFlow()

    fun refreshScreenTime() {
        val c = config.value
        if (!c.hasMaison(MaisonKind.TempsEcran)) return
        viewModelScope.launch {
            val context = getApplication<Application>()
            _screenTime.value = withContext(Dispatchers.IO) {
                if (!ScreenTime.hasAccess(context)) return@withContext ScreenTimeState(hasAccess = false)
                val times = runCatching { ScreenTime.today(context) }.getOrDefault(emptyMap())
                // Les applis du mode Focus (clé « paquet/activité@profil ») ; sinon toutes.
                val focusPackages = c.focus.blockedApps.map { it.substringBefore('/') }.toSet()
                val selected = if (focusPackages.isEmpty()) times else times.filterKeys { it in focusPackages }
                val labels = apps.value.all.associate { it.packageName to it.label }
                ScreenTimeState(
                    hasAccess = true,
                    totalMs = selected.values.sum(),
                    top = selected.entries.sortedByDescending { it.value }.take(3).map { (labels[it.key] ?: it.key) to it.value },
                    focusOnly = focusPackages.isNotEmpty(),
                )
            }
        }
    }

    fun updateScreenTime(transform: (ScreenTimeSettings) -> ScreenTimeSettings) = store.update { it.updateScreenTime(transform) }

    /** Ouvre le choix du fond d'écran d'Android. */
    fun openWallpaperPicker() = startSafely(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Fond d'écran"))

    /** Fern est-il le lanceur par défaut (rôle « Accueil » d'Android) ? */
    fun isDefaultHome(): Boolean {
        val roles = getApplication<Application>().getSystemService(android.app.role.RoleManager::class.java)
        return roles?.isRoleHeld(android.app.role.RoleManager.ROLE_HOME) ?: false
    }

    /** La demande système « Utiliser Fern comme appli d'accueil ? » (null si indisponible). */
    fun homeRoleRequest(): Intent? {
        val roles = getApplication<Application>().getSystemService(android.app.role.RoleManager::class.java) ?: return null
        if (!roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) return null
        return roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)
    }

    /** Les réglages « Applications par défaut » d'Android (plan B, ex. sur Xiaomi). */
    fun openDefaultAppsSettings() {
        val ok = runCatching {
            getApplication<Application>().startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        if (!ok) startSafely(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
    }

    fun openUsageAccess() = startSafely(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))

    // Placé après les propriétés ci-dessus : en Kotlin, un bloc init ne voit que ce qui est déclaré avant lui.
    init {
        // Partage du chat : on relève la boîte aux lettres toutes les 30 s, si Fern est à l'écran.
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                if (inForeground) syncChat()
            }
        }
        // Pomodoro : on regarde toutes les 10 secondes si une phase est finie.
        viewModelScope.launch {
            while (true) {
                if (config.value.pomodoro.phase != PomodoroPhase.Arret) tickPomodoro()
                delay(10_000L)
            }
        }
        // Météo : on recharge la dernière connue (fichier), en attendant Internet.
        viewModelScope.launch {
            _weather.value = withContext(Dispatchers.IO) {
                runCatching { FernJson.decodeFromString(Weather.serializer(), weatherFile.readText()) }.getOrNull()
            }
        }
    }

    // ─── Révisions, alternance, carnet ──────────────────────────────────────

    private val _anki = MutableStateFlow<Anki.State>(Anki.State.NotInstalled)
    val anki: StateFlow<Anki.State> = _anki.asStateFlow()

    fun refreshAnki() {
        viewModelScope.launch { _anki.value = withContext(Dispatchers.IO) { Anki.state(getApplication()) } }
    }

    fun openAnki() = openPackage(Anki.PACKAGE)

    fun updateAlternance(transform: (AlternanceSettings) -> AlternanceSettings) = store.update { it.updateAlternance(transform) }

    fun updateCarnetSettings(transform: (CarnetSettings) -> CarnetSettings) = store.update { it.updateCarnetSettings(transform) }

    private fun todayIso() = java.time.LocalDate.now().toString()

    fun setMood(mood: Int) = store.update { c -> c.updateCarnetDay(todayIso()) { it.copy(mood = mood) } }

    fun toggleHabit(index: Int) = store.update { c ->
        c.updateCarnetDay(todayIso()) { d -> d.copy(habits = if (index in d.habits) d.habits - index else d.habits + index) }
    }

    fun setCarnetNote(note: String) = store.update { c -> c.updateCarnetDay(todayIso()) { it.copy(note = note.trim()) } }

    /**
     * Envoie la note dans Obsidian : elle est ajoutée à la fin de la note « Carnet/2026-10-06 »
     * (créée si besoin), grâce aux liens `obsidian://` de l'appli.
     */
    fun sendNoteToObsidian(note: String) {
        val settings = config.value.carnet
        val day = config.value.carnet.days[todayIso()]
        val text = buildString {
            day?.mood?.let { append("Humeur : ").append(listOf("graine", "pousse", "bourgeon", "fleur", "éclose")[it]).append("\n") }
            val done = day?.habits.orEmpty().mapNotNull { settings.habits.getOrNull(it) }
            if (done.isNotEmpty()) append("Habitudes : ").append(done.joinToString(", ")).append("\n")
            append(note)
        }
        val path = listOf(settings.obsidianFolder.trim('/'), todayIso()).filter { it.isNotBlank() }.joinToString("/")
        val uri = Uri.Builder().scheme("obsidian").authority("new").apply {
            if (settings.obsidianVault.isNotBlank()) appendQueryParameter("vault", settings.obsidianVault)
            appendQueryParameter("file", path)
            appendQueryParameter("content", "\n" + text)
            appendQueryParameter("append", "true")
        }.build()
        startSafely(Intent(Intent.ACTION_VIEW, uri))
    }

    /** Fern revient au premier plan. */
    fun onForeground() {
        refreshAnki()
        refreshWeather()
        refreshCours()
        refreshScreenTime()
        if (config.value.pomodoro.phase != PomodoroPhase.Arret) tickPomodoro()
        inForeground = true
        syncChat()
        widgetHost.startListening()
        music.start()
        checkSchedule()
    }

    /** Fern passe en arrière-plan. */
    fun onBackground() {
        inForeground = false
        widgetHost.stopListening()
        flush()
    }

    fun musicPlayPause() = music.playPause()
    fun musicNext() = music.next()
    fun musicPrevious() = music.previous()

    fun openPackage(packageName: String) {
        val context = getApplication<Application>()
        context.packageManager.getLaunchIntentForPackage(packageName)?.let { startSafely(it) }
    }

    fun setContextNote(pageId: String, blockId: String, note: String) = store.update {
        it.updateBlockById(pageId, blockId) { b -> if (b is ContextBlock) b.copy(note = note) else b }
    }

    fun cycleWidgetHeight(pageId: String, blockId: String) = store.update {
        it.updateBlockById(pageId, blockId) { b ->
            if (b is AppWidgetBlock) {
                val sizes = listOf(120, 180, 240, 320)
                b.copy(heightDp = sizes[(sizes.indexOf(b.heightDp) + 1).mod(sizes.size)])
            } else {
                b
            }
        }
    }

    /**
     * Les tailles possibles pour un widget Android, en (colonnes × cases) :
     * on respecte la taille minimale déclarée par l'appli, et si elle se laisse redimensionner
     * (horizontalement / verticalement).
     */
    fun widgetSizeOptions(appWidgetId: Int): List<Pair<Int, Int>> {
        val info = widgetHost.info(appWidgetId) ?: return listOf(4 to 2)
        val metrics = getApplication<Application>().resources.displayMetrics
        fun dp(px: Int) = px / metrics.density
        val screenDp = metrics.widthPixels / metrics.density
        val cellW = (screenDp - 40f - 3 * 10f) / ROW_COLUMNS
        val cellH = WIDGET_CELL_HEIGHT_DP.toFloat()
        val horizontal = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
        val vertical = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
        val minW = dp(if (horizontal && info.minResizeWidth > 0) info.minResizeWidth else info.minWidth)
        val minH = dp(if (vertical && info.minResizeHeight > 0) info.minResizeHeight else info.minHeight)
        val maxW = if (Build.VERSION.SDK_INT >= 31 && info.maxResizeWidth > 0) dp(info.maxResizeWidth) else Float.MAX_VALUE
        val maxH = if (Build.VERSION.SDK_INT >= 31 && info.maxResizeHeight > 0) dp(info.maxResizeHeight) else Float.MAX_VALUE
        val cols = (1..ROW_COLUMNS).filter { c ->
            val w = c * cellW + (c - 1) * 10f
            w + 8f >= minW && (horizontal || c == ROW_COLUMNS || w <= maxW) && w <= maxW + cellW
        }.ifEmpty { listOf(ROW_COLUMNS) }
        val rows = (1..5).filter { r ->
            val h = r * cellH
            h + 8f >= minH && h <= maxH + cellH
        }.ifEmpty { listOf(2) }
        // Non redimensionnable : on ne propose que la plus petite taille qui convient (+ pleine largeur).
        val allowedCols = if (horizontal) cols else listOf(cols.first()).plus(ROW_COLUMNS).distinct()
        val allowedRows = if (vertical) rows else listOf(rows.first())
        return allowedRows.flatMap { r -> allowedCols.map { c -> c to r } }
    }

    fun setWidgetSize(pageId: String, blockId: String, columns: Int, rows: Int) =
        store.update { it.setWidgetSize(pageId, blockId, columns, rows) }

    fun addAppWidget(pageId: String, appWidgetId: Int, label: String, heightDp: Int) =
        addBlock(pageId, AppWidgetBlock(id = newId(), appWidgetId = appWidgetId, provider = label, heightDp = heightDp))

    fun updatePlace(transform: (PlaceSettings) -> PlaceSettings) = store.update { it.updatePlace(transform) }

    init {
        // Planning des Spaces : une vérification par minute.
        viewModelScope.launch {
            while (true) {
                checkSchedule()
                delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
        // Stickers orphelins (supprimés, ou d'une ancienne sauvegarde) : on fait le ménage.
        viewModelScope.launch(Dispatchers.IO) { cleanupStickers() }
        // Style d'icônes : on recharge les icônes quand il change.
        viewModelScope.launch {
            store.config.map { it.icons }.distinctUntilChanged().collect { repository.setIconSettings(it) }
        }
        // Premier lancement : on pose la disposition de départ dès que la liste des applis est connue.
        viewModelScope.launch {
            val installed = repository.apps.first { it.isNotEmpty() }
            if (!store.config.value.seeded) {
                store.update { DefaultLayout.seed(it, installed, application) }
            }
        }
    }

    // ─── Tiroir / recherche ─────────────────────────────────────────────────

    fun open(mode: OverlayMode) {
        _overlay.value = OverlayState(visible = true, mode = mode)
    }

    fun closeOverlay() {
        _overlay.update { it.copy(visible = false) }
    }

    /** Appui sur le bouton Accueil alors qu'on est déjà sur Fern. */
    fun onHomePressed() {
        if (!_overlay.value.visible && !_editing.value && !_settingsOpen.value) _homeTick.update { it + 1 }
        _radial.value = null
        _settingsOpen.value = false
        closeOverlay()
        _picking.value = null
        _editing.value = false
    }

    /** Bouton Retour : on ferme la couche la plus haute. */
    fun onBack() {
        when {
            _picking.value != null -> _picking.value = null
            _settingsOpen.value -> _settingsOpen.value = false
            _radial.value != null -> _radial.value = null
            _overlay.value.visible -> closeOverlay()
            _editing.value -> _editing.value = false
        }
    }

    // ─── Applis ─────────────────────────────────────────────────────────────

    fun launch(app: AppEntry) {
        repository.launch(app)
        store.update { it.countLaunch(app.key) }
        closeOverlay()
    }

    fun openAppInfo(app: AppEntry) = repository.openAppInfo(app)

    fun uninstall(app: AppEntry) = repository.uninstall(app)

    fun setHidden(app: AppEntry, hidden: Boolean) = store.update { it.setHidden(app.key, hidden) }

    fun updateDrawer(transform: (DrawerSettings) -> DrawerSettings) = store.update { it.updateDrawer(transform) }

    fun setAppFamily(app: AppEntry, family: Family?) = store.update { it.setAppFamily(app.key, family) }

    fun updateIcons(transform: (IconSettings) -> IconSettings) = store.update { it.updateIcons(transform) }

    fun installedIconPacks() = repository.iconPacks.installedPacks()

    fun renameApp(app: AppEntry, name: String) = store.update { it.renameApp(app.key, name) }

    // ─── Recherche globale ──────────────────────────────────────────────────

    /**
     * Contacts, agenda, raccourcis, calcul : seulement si la « recherche étendue » est activée.
     * Par défaut, la recherche se limite aux applis puis au web.
     */
    suspend fun searchExtras(query: String): SearchExtras = withContext(Dispatchers.IO) {
        if (!config.value.search.extended) return@withContext SearchExtras.Empty
        val appLabels = apps.value.all.associate { it.packageName to it.label }
        SearchExtras(
            calculation = Calculator.evaluate(query)?.let { Calculator.format(it) },
            contacts = searchRepository.contacts(query),
            events = searchRepository.events(query),
            shortcuts = searchRepository.shortcuts(query, appLabels),
            missingContacts = !searchRepository.hasPermission(Manifest.permission.READ_CONTACTS),
            missingCalendar = !searchRepository.hasPermission(Manifest.permission.READ_CALENDAR),
        )
    }

    fun onPermissionsChanged() = _searchVersion.update { it + 1 }

    private fun startSafely(intent: Intent) {
        val context = getApplication<Application>()
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            closeOverlay()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Aucune appli pour ouvrir ça", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyToClipboard(text: String) {
        val context = getApplication<Application>()
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Fern", text))
        Toast.makeText(context, "Copié : $text", Toast.LENGTH_SHORT).show()
    }

    fun openContact(contact: ContactResult) = startSafely(Intent(Intent.ACTION_VIEW, contact.uri))

    fun callContact(contact: ContactResult) {
        contact.phone?.let { startSafely(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", it, null))) }
    }

    fun messageContact(contact: ContactResult) {
        contact.phone?.let { startSafely(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", it, null))) }
    }

    fun openEvent(event: EventResult) = startSafely(
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin),
    )

    fun startShortcut(shortcut: ShortcutResult) {
        searchRepository.startShortcut(shortcut)
        closeOverlay()
    }

    /** Le navigateur pour la recherche web : celui choisi, sinon Firefox s'il est installé. */
    private fun browserPackage(): String? {
        val pm = getApplication<Application>().packageManager
        val chosen = config.value.search.browserPackage
        if (chosen != null && pm.getLaunchIntentForPackage(chosen) != null) return chosen
        return FIREFOX_PACKAGES.firstOrNull { pm.getLaunchIntentForPackage(it) != null }
    }

    /** « avec Firefox » ou « sur le web », pour le libellé du bouton. */
    fun webLabel(): String {
        val pkg = browserPackage() ?: return "sur le web"
        val name = apps.value.all.firstOrNull { it.packageName == pkg }?.label ?: "Firefox"
        return "avec $name"
    }

    /**
     * Recherche web : on ouvre l'adresse comme un lien ordinaire dans Firefox,
     * pour qu'il applique ses réglages (onglet privé pour les liens externes).
     */
    fun webSearch(query: String) {
        val url = config.value.search.webSearchUrl.replace("%s", Uri.encode(query))
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        val pkg = browserPackage()
        if (pkg != null) {
            try {
                getApplication<Application>().startActivity(
                    Intent(intent).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                closeOverlay()
                return
            } catch (e: ActivityNotFoundException) {
                // On retombe sur le navigateur par défaut.
            }
        }
        startSafely(intent)
    }

    // ─── Gestes ──────────────────────────────────────────────────────────────

    /** Roue d'applis ouverte (position du doigt), ou null. */
    private val _radial = MutableStateFlow<Offset?>(null)
    val radial: StateFlow<Offset?> = _radial.asStateFlow()

    fun closeRadial() {
        _radial.value = null
    }

    /** Exécute l'action associée à un geste. `at` = position du doigt (pour la roue). */
    fun perform(binding: GestureBinding, at: Offset? = null) {
        val context = getApplication<Application>()
        when (binding.action) {
            GestureAction.Rien -> Unit
            GestureAction.Tiroir -> open(OverlayMode.Drawer)
            GestureAction.Recherche -> open(OverlayMode.Search)
            GestureAction.Notifications -> SystemActions.expandNotifications(context)
            GestureAction.ReglagesRapides -> SystemActions.expandQuickSettings(context)
            GestureAction.Verrouiller -> SystemActions.lockScreen(context)
            GestureAction.Edition -> setEditing(true)
            GestureAction.RoueRadiale -> _radial.value = at ?: Offset.Unspecified
            GestureAction.Appli -> apps.value.find(binding.appKey)?.let { launch(it) }
                ?: Toast.makeText(context, "Choisis l'appli de ce geste dans les Paramètres", Toast.LENGTH_SHORT).show()
            GestureAction.SpaceSuivant -> {
                store.update { it.nextSpace() }
                Toast.makeText(context, "Space : ${config.value.activeSpace.name}", Toast.LENGTH_SHORT).show()
            }
            GestureAction.Focus -> toggleFocus()
        }
    }

    fun updateGestures(transform: (GestureSettings) -> GestureSettings) = store.update { it.updateGestures(transform) }

    // ─── Spaces & Focus ──────────────────────────────────────────────────────

    fun setActiveSpace(spaceId: String) {
        store.update { it.setActiveSpace(spaceId) }
        _homeTick.update { it + 1 }
    }

    fun addSpace(name: String, copyCurrent: Boolean) = store.update { it.addSpace(name, copyCurrent) }
    fun renameSpace(spaceId: String, name: String) = store.update { it.renameSpace(spaceId, name) }
    fun removeSpace(spaceId: String) = store.update { it.removeSpace(spaceId) }
    fun setSpaceTheme(spaceId: String, theme: NamedTheme?) = store.update { it.setSpaceTheme(spaceId, theme) }
    fun updateSchedule(transform: (SpaceSchedule) -> SpaceSchedule) = store.update { it.updateSchedule(transform) }
    fun updateFocus(transform: (FocusSettings) -> FocusSettings) = store.update { it.updateFocus(transform) }

    fun toggleFocus() {
        store.update { c -> c.updateFocus { it.copy(enabled = !it.enabled) } }
        val on = config.value.focus.enabled
        Toast.makeText(
            getApplication<Application>(),
            if (on) "Mode Focus activé" else "Mode Focus coupé",
            Toast.LENGTH_SHORT,
        ).show()
    }

    /** La règle de planning appliquée en dernier : on ne rebascule que quand elle change. */
    private var lastRuleId: String? = null

    /** Vérifie le planning (appelé chaque minute et au retour sur l'accueil). */
    fun checkSchedule() {
        val schedule = config.value.spaceSchedule
        if (!schedule.enabled) {
            lastRuleId = null
            return
        }
        val now = LocalDateTime.now()
        val rule = activeRuleAt(schedule.rules, now.dayOfWeek.value, now.hour * 60 + now.minute)
        if (rule?.id != lastRuleId) {
            lastRuleId = rule?.id
            if (rule != null && rule.spaceId != config.value.activeSpace.id) {
                store.update { it.setActiveSpace(rule.spaceId) }
            }
        }
    }

    // ─── Paramètres ──────────────────────────────────────────────────────────

    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

    fun openSettings() {
        closeOverlay()
        _editing.value = false
        _settingsOpen.value = true
    }

    fun closeSettings() {
        _settingsOpen.value = false
    }

    fun updateSearch(transform: (SearchSettings) -> SearchSettings) = store.update { it.updateSearch(transform) }
    fun setThemeColor(key: String, hex: String) = store.update { it.setThemeColor(key, hex) }
    fun applyTheme(theme: NamedTheme) = store.update { it.applyTheme(theme) }
    fun saveTheme(name: String) = store.update { it.saveTheme(name) }
    fun deleteSavedTheme(name: String) = store.update { it.deleteSavedTheme(name) }

    private fun toast(message: String) {
        Toast.makeText(getApplication<Application>(), message, Toast.LENGTH_LONG).show()
    }

    /** Écrit un texte dans un fichier choisi par Jules (sélecteur de fichiers Android). */
    private fun writeText(uri: Uri, text: String, success: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(text.encodeToByteArray())
                    } ?: error("fichier inaccessible")
                }.isSuccess
            }
            toast(if (ok) success else "Impossible d'écrire le fichier")
        }
    }

    private suspend fun readText(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        }.getOrNull()
    }

    fun exportTheme(uri: Uri) = writeText(
        uri,
        FernJson.encodeToString(ThemeFile.serializer(), ThemeFile(theme = config.value.theme)),
        "Thème exporté",
    )

    fun importTheme(uri: Uri) {
        viewModelScope.launch {
            val theme = readText(uri)?.let { text ->
                runCatching { FernJson.decodeFromString(ThemeFile.serializer(), text).theme }.getOrNull()
            }
            if (theme == null) {
                toast("Ce fichier n'est pas un thème Fern")
            } else {
                store.update { it.applyTheme(theme).saveTheme(theme.name) }
                toast("Thème « ${theme.name} » importé")
            }
        }
    }

    fun exportBackup(uri: Uri) = writeText(
        uri,
        FernJson.encodeToString(LauncherConfig.serializer(), config.value),
        "Sauvegarde exportée",
    )

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val restored = readText(uri)?.let { text ->
                runCatching { FernJson.decodeFromString(LauncherConfig.serializer(), text).migrate() }.getOrNull()
            }
            if (restored == null) {
                toast("Ce fichier n'est pas une sauvegarde Fern")
            } else {
                store.replace(restored.copy(seeded = true))
                toast("Sauvegarde restaurée")
            }
        }
    }

    // ─── Mode édition ───────────────────────────────────────────────────────

    fun setEditing(on: Boolean) {
        _editing.value = on
        if (on) closeOverlay()
    }

    fun pickFor(ref: SlotRef?) {
        _picking.value = ref
    }

    fun setSlot(ref: SlotRef, app: AppEntry?) {
        store.update { it.setSlot(ref, app?.key) }
        _picking.value = null
    }

    /** Range une appli à un endroit précis (« Ajouter à… » depuis le tiroir). */
    fun addTo(ref: SlotRef, app: AppEntry) = store.update { it.setSlot(ref, app.key) }

    fun freeDestinations() = config.value.freeDestinations()

    fun cycleWidth(pageId: String, blockId: String) = store.update { it.cycleWidth(pageId, blockId) }

    fun cycleSpacerHeight(pageId: String, blockId: String) = store.update { it.cycleSpacerHeight(pageId, blockId) }

    // ─── Stickers ────────────────────────────────────────────────────────────

    private val stickerDir: File get() = File(getApplication<Application>().filesDir, "stickers").apply { mkdirs() }

    fun stickerFile(name: String): File = File(stickerDir, name)

    /** Copie l'image choisie dans le dossier privé de Fern et la pose au centre de la page. */
    fun importSticker(pageId: String, uri: Uri) {
        viewModelScope.launch {
            val name = "${newId()}.img"
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        stickerFile(name).outputStream().use { input.copyTo(it) }
                    } ?: error("illisible")
                }.isSuccess
            }
            if (ok) {
                store.update { it.addSticker(pageId, Sticker(id = newId(), file = name)) }
                toast("Sticker ajouté : déplace-le au doigt, pince pour agrandir ou tourner")
            } else {
                toast("Impossible de lire cette image")
            }
        }
    }

    fun updateSticker(pageId: String, sticker: Sticker) = store.update { it.updateSticker(pageId, sticker) }

    fun removeSticker(pageId: String, stickerId: String) = store.update { it.removeSticker(pageId, stickerId) }

    /** Supprime les fichiers de stickers qui ne sont plus utilisés nulle part. */
    private fun cleanupStickers() {
        val used = config.value.usedStickerFiles
        stickerDir.listFiles()?.filter { it.name !in used }?.forEach { it.delete() }
    }

    fun addBlock(pageId: String, block: HomeBlock) = store.update { it.addBlock(pageId, block) }
    fun removeBlock(pageId: String, blockId: String) {
        // Un widget Android supprimé libère aussi son numéro.
        val block = config.value.activeSpace.pages.firstOrNull { it.id == pageId }
            ?.blocks?.firstOrNull { it.id == blockId }
        if (block is AppWidgetBlock) widgetHost.delete(block.appWidgetId)
        store.update { it.removeBlock(pageId, blockId) }
    }
    fun moveBlock(pageId: String, blockId: String, delta: Int) = store.update { it.moveBlock(pageId, blockId, delta) }
    fun renamePack(pageId: String, blockId: String, title: String) = store.update { it.renamePack(pageId, blockId, title) }

    fun addPage(title: String) = store.update { it.addPage(title) }
    fun removePage(pageId: String) = store.update { it.removePage(pageId) }
    fun renamePage(pageId: String, title: String) = store.update { it.renamePage(pageId, title) }
    fun movePage(pageId: String, delta: Int) = store.update { it.movePage(pageId, delta) }

    /** L'appli passe en arrière-plan : on écrit la config tout de suite. */
    fun flush() = store.flush()

    override fun onCleared() {
        repository.stop()
        store.flush()
    }
}
