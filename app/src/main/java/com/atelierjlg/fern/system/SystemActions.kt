package com.atelierjlg.fern.system

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast

/** Les actions « système » déclenchées par les gestes. */
object SystemActions {

    private const val TAG = "FernSystem"

    fun lockScreen(context: Context) {
        val service = FernAccessibilityService.instance
        if (service != null) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            askForAccessibility(context, "Pour verrouiller l'écran d'un double appui, active « Fern Launcher » dans Accessibilité.")
        }
    }

    fun expandNotifications(context: Context) {
        if (!expandStatusBar(context, "expandNotificationsPanel")) {
            FernAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        }
    }

    fun expandQuickSettings(context: Context) {
        if (!expandStatusBar(context, "expandSettingsPanel")) {
            FernAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        }
    }

    /**
     * Ouvre le volet du haut via une méthode interne d'Android (pas d'API publique pour ça).
     * Marche sur la plupart des téléphones ; sinon on passe par l'accessibilité.
     */
    @SuppressLint("WrongConstant")
    private fun expandStatusBar(context: Context, method: String): Boolean = try {
        val service = context.getSystemService("statusbar")
        val clazz = Class.forName("android.app.StatusBarManager")
        clazz.getMethod(method).invoke(service)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Volet indisponible ($method)", e)
        false
    }

    private fun askForAccessibility(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
