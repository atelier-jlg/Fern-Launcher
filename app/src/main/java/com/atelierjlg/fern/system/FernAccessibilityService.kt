package com.atelierjlg.fern.system

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Service d'accessibilité minimal : il ne lit RIEN à l'écran.
 * Il sert uniquement à verrouiller l'écran (double appui) et, en secours,
 * à ouvrir les notifications / réglages rapides.
 *
 * Android exige que Jules l'active une fois à la main :
 * Paramètres → Accessibilité → Fern Launcher.
 */
class FernAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        /** Le service actif, ou null s'il n'est pas activé. */
        @Volatile
        var instance: FernAccessibilityService? = null
            private set
    }
}
