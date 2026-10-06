package com.atelierjlg.fern.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.util.Log

/**
 * Héberge les widgets Android des autres applis (météo, agenda…) sur l'accueil de Fern.
 *
 * Fonctionnement d'Android : on « réserve » un numéro de widget, Android demande à Jules
 * l'autorisation (une seule fois par appli), puis l'appli du widget peut le configurer.
 */
class WidgetHost(context: Context) {

    private val appContext = context.applicationContext
    val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    private val host = AppWidgetHost(appContext, HOST_ID)

    fun startListening() = runCatching { host.startListening() }.onFailure { Log.w(TAG, "startListening", it) }

    fun stopListening() = runCatching { host.stopListening() }.onFailure { Log.w(TAG, "stopListening", it) }

    /** Tous les widgets disponibles, triés par nom. */
    fun providers(): List<AppWidgetProviderInfo> =
        runCatching { manager.installedProviders }.getOrDefault(emptyList())
            .sortedBy { it.loadLabel(appContext.packageManager)?.lowercase() ?: "" }

    fun label(info: AppWidgetProviderInfo): String = info.loadLabel(appContext.packageManager) ?: info.provider.packageName

    fun allocateId(): Int = host.allocateAppWidgetId()

    /** true si Android autorise déjà Fern à afficher ce widget (sinon il faut demander). */
    fun bindIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean =
        runCatching { manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }.getOrDefault(false)

    fun info(id: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    fun delete(id: Int) = runCatching { host.deleteAppWidgetId(id) }

    /** Crée la vue du widget, à la taille voulue (en dp). */
    @Suppress("DEPRECATION")
    fun createView(context: Context, id: Int, widthDp: Int, heightDp: Int): AppWidgetHostView? {
        val info = info(id) ?: return null
        return runCatching {
            host.createView(context, id, info).apply {
                updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp)
            }
        }.onFailure { Log.w(TAG, "createView $id", it) }.getOrNull()
    }

    /** Libère les numéros réservés qui ne sont plus utilisés nulle part. */
    fun cleanup(used: Set<Int>) {
        runCatching { host.appWidgetIds.filterNot { it in used }.forEach { host.deleteAppWidgetId(it) } }
    }

    companion object {
        private const val HOST_ID = 1024
        private const val TAG = "FernWidgets"
    }
}
