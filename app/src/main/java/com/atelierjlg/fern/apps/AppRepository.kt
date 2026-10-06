package com.atelierjlg.fern.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Tient à jour la liste des applis installées et sait les lancer.
 *
 * On passe par `LauncherApps`, le service Android prévu pour les lanceurs :
 * il gère les profils (perso / travail) et nous prévient à chaque installation,
 * désinstallation ou mise à jour.
 *
 * `apps` est un StateFlow : une valeur observable. L'interface se redessine
 * toute seule quand la liste change (un peu comme une variable réactive en JS).
 */
class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val iconSizePx = (56 * context.resources.displayMetrics.density).roundToInt()
    private val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.PRIMARY }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refreshJob: Job? = null

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    /** Android nous appelle ici quand une appli bouge. */
    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
    }

    fun start() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun stop() {
        launcherApps.unregisterCallback(callback)
        scope.cancel()
    }

    /**
     * Recharge la liste en arrière-plan. Si plusieurs événements arrivent d'un coup
     * (mise à jour de 10 applis), on attend un peu pour ne recharger qu'une fois.
     */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(150)
            _apps.value = loadApps()
        }
    }

    private fun loadApps(): List<AppEntry> {
        val self = context.packageName
        return userManager.userProfiles
            .flatMap { user ->
                // Un profil verrouillé (espace privé, profil travail en pause) peut refuser.
                runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
            }
            .filter { it.componentName.packageName != self }
            .mapNotNull { info -> runCatching { info.toEntry() }.getOrNull() }
            .sortedWith(compareBy(collator) { it.label })
    }

    private fun LauncherActivityInfo.toEntry() = AppEntry(
        label = label.toString(),
        packageName = componentName.packageName,
        component = componentName,
        user = user,
        // getBadgedIcon ajoute la petite mallette sur les applis du profil travail.
        icon = getBadgedIcon(0).toBitmap(iconSizePx, iconSizePx).asImageBitmap(),
    )

    // ─── Actions ────────────────────────────────────────────────────────────

    fun launch(app: AppEntry, sourceBounds: Rect? = null) {
        try {
            launcherApps.startMainActivity(app.component, app.user, sourceBounds, null)
        } catch (e: Exception) {
            // L'appli vient d'être désinstallée, ou elle est désactivée.
            Log.w(TAG, "Impossible de lancer ${app.component}", e)
            Toast.makeText(context, "Impossible d'ouvrir ${app.label}", Toast.LENGTH_SHORT).show()
            refresh()
        }
    }

    fun openAppInfo(app: AppEntry) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
            .onFailure { Log.w(TAG, "Infos indisponibles pour ${app.component}", it) }
    }

    fun uninstall(app: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
            .putExtra(Intent.EXTRA_USER, app.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Désinstallation impossible", Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val TAG = "FernApps"
    }
}
