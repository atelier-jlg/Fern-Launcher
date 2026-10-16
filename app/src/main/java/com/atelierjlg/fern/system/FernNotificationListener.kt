package com.atelierjlg.fern.system

import android.service.notification.NotificationListenerService

/**
 * Service vide : Android exige qu'un lanceur soit « autorisé à lire les notifications »
 * pour voir quelle musique est en cours (widget Musique). Fern ne lit pas les notifications.
 */
class FernNotificationListener : NotificationListenerService()
