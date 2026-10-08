package com.atelierjlg.fern.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.atelierjlg.fern.ui.theme.FernColors
import com.atelierjlg.fern.ui.theme.FernPalettes
import com.atelierjlg.fern.ui.theme.FernTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** D'où vient le thème affiché. */
enum class ThemeSource {
    /** Lu à l'instant dans Fern Launcher. */
    Fern,
    /** Fern injoignable : dernier thème reçu, gardé en mémoire. */
    Cache,
    /** Jamais reçu : Estampe nuit. */
    Default,
}

data class ReceivedTheme(
    val name: String,
    val colors: FernColors,
    val source: ThemeSource,
    /** Pourquoi Fern n'a pas répondu (pour l'écran de diagnostic). */
    val error: String? = null,
)

/**
 * Côté applis sœurs : va chercher le thème actif de Fern Launcher.
 * Le dernier thème reçu est gardé (SharedPreferences) pour s'afficher tout de suite au démarrage,
 * sans « flash » d'une autre couleur.
 */
object FernThemeReader {
    private const val PREFS = "fern-theme"
    private const val KEY_JSON = "json"

    /** Le dernier thème reçu, ou Estampe nuit. Rapide : à appeler au démarrage. */
    fun cached(context: Context): ReceivedTheme {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_JSON, null)
        val theme = json?.let(::parseSharedTheme)
            ?: return ReceivedTheme("Estampe nuit", FernPalettes.EstampeNuit, ThemeSource.Default)
        return ReceivedTheme(theme.name, theme.colors.toFernColors(), ThemeSource.Cache)
    }

    /** Interroge Fern (à faire hors du fil principal). En cas d'échec : le dernier thème reçu. */
    fun read(context: Context): ReceivedTheme {
        val error = try {
            context.contentResolver.query(FernThemeContract.URI, null, null, null, null).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val json = cursor.getString(cursor.getColumnIndexOrThrow(FernThemeContract.COLUMN_JSON))
                    val theme = parseSharedTheme(json)
                    if (theme != null) {
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_JSON, json).apply()
                        return ReceivedTheme(theme.name, theme.colors.toFernColors(), ThemeSource.Fern)
                    }
                    "Thème illisible"
                } else {
                    "Fern Launcher n'est pas installé"
                }
            }
        } catch (e: SecurityException) {
            // Fern est là, mais n'est pas signé avec la même clé que cette appli.
            "Accès refusé (clé de signature différente)"
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
        return cached(context).copy(error = error)
    }
}

/**
 * Le thème de Fern, tenu à jour : relu à chaque retour dans l'appli et dès que Fern
 * annonce un changement (Paramètres → Thème, changement de Space…).
 */
@Composable
fun rememberFernTheme(): ReceivedTheme {
    val context = LocalContext.current.applicationContext
    var theme by remember { mutableStateOf(FernThemeReader.cached(context)) }
    var refresh by remember { mutableIntStateOf(0) }

    // Retour dans l'appli : on relit.
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    // Fern prévient quand son thème change (pendant que l'appli est ouverte).
    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refresh++
            }
        }
        val registered = runCatching {
            context.contentResolver.registerContentObserver(FernThemeContract.URI, false, observer)
        }.isSuccess
        onDispose { if (registered) context.contentResolver.unregisterContentObserver(observer) }
    }
    LaunchedEffect(refresh) {
        theme = withContext(Dispatchers.IO) { FernThemeReader.read(context) }
    }
    return theme
}

/** Le thème de Fern Launcher appliqué à toute une appli sœur. */
@Composable
fun FernSharedTheme(content: @Composable (ReceivedTheme) -> Unit) {
    val received = rememberFernTheme()
    FernTheme(colors = received.colors) { content(received) }
}
