package com.atelierjlg.fern.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Charge et sauvegarde la configuration dans `fern-config.json` (stockage privé de l'appli).
 *
 * - La lecture se fait une fois au démarrage.
 * - Chaque modification passe par [update] ; l'écriture sur disque est regroupée
 *   (300 ms après la dernière modification) pour ne pas écrire 10 fois de suite.
 * - AtomicFile garantit qu'un fichier à moitié écrit (batterie vide…) ne casse rien.
 */
class ConfigStore(context: Context) {

    // Portée propre au stockage : une écriture en cours n'est jamais annulée par la fermeture d'un écran.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val file = AtomicFile(File(context.filesDir, FILE_NAME))
    private val _config = MutableStateFlow(load())
    val config: StateFlow<LauncherConfig> = _config.asStateFlow()

    private var saveJob: Job? = null

    fun update(transform: (LauncherConfig) -> LauncherConfig) {
        _config.update(transform)
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(300)
            write(_config.value)
        }
    }

    /** Remplace toute la configuration (restauration d'une sauvegarde). */
    fun replace(config: LauncherConfig) = update { config }

    /** Écrit tout de suite (quand l'appli passe en arrière-plan). */
    fun flush() {
        saveJob?.cancel()
        val snapshot = _config.value
        scope.launch { write(snapshot) }
    }

    private fun load(): LauncherConfig {
        return try {
            if (!file.baseFile.exists()) return LauncherConfig()
            val text = file.readFully().decodeToString()
            FernJson.decodeFromString(LauncherConfig.serializer(), text)
        } catch (e: Exception) {
            // Fichier illisible : on le met de côté pour ne pas le perdre, et on repart à zéro.
            Log.e(TAG, "Configuration illisible, sauvegardée en .bak", e)
            runCatching { file.baseFile.copyTo(File(file.baseFile.path + ".bak"), overwrite = true) }
            LauncherConfig()
        }
    }

    @Synchronized
    private fun write(config: LauncherConfig) {
        val stream = file.startWrite()
        try {
            stream.write(FernJson.encodeToString(LauncherConfig.serializer(), config).encodeToByteArray())
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            Log.e(TAG, "Échec d'écriture de la configuration", e)
        }
    }

    private companion object {
        const val FILE_NAME = "fern-config.json"
        const val TAG = "FernConfig"
    }
}
