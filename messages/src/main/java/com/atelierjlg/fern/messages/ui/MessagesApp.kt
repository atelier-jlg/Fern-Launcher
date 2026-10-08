package com.atelierjlg.fern.messages.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.messages.MessagesViewModel
import com.atelierjlg.fern.messages.Screen
import com.atelierjlg.fern.messages.SetupState
import com.atelierjlg.fern.ui.kit.Pill
import com.atelierjlg.fern.ui.theme.Fern

/** La racine : la liste, ou l'écran ouvert par-dessus (conversation, nouveau message…). */
@Composable
fun MessagesApp(vm: MessagesViewModel) {
    val stack by vm.screen.collectAsState()
    BackHandler(enabled = stack.size > 1) { vm.back() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Fern.colors.nuit)
            .systemBarsPadding()
            .imePadding(),
    ) {
        when (val top = stack.last()) {
            Screen.Inbox -> InboxScreen(vm, archived = false)
            Screen.Archived -> InboxScreen(vm, archived = true)
            Screen.Search -> SearchScreen(vm)
            Screen.Scheduled -> ScheduledScreen(vm)
            is Screen.Thread -> ThreadScreen(vm, top.threadId)
            is Screen.Compose -> ComposeScreen(vm, top.number, top.body, top.image)
        }
    }
}

/** Ce qui manque pour que l'appli marche (appli SMS par défaut, autorisations). */
@Composable
fun SetupCard(vm: MessagesViewModel, setup: SetupState) {
    val c = Fern.colors
    var refused by remember { mutableStateOf(false) }
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refused = it.resultCode != Activity.RESULT_OK
        vm.reload()
    }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.reload() }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.reload() }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(c.mousse)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("POUR BIEN DÉMARRER", style = Fern.type.libelle, color = c.lichen)
        if (!setup.isDefaultSms) {
            Text(
                "Fern Messages n'est pas l'appli SMS : il peut lire, mais pas recevoir ni envoyer.",
                style = Fern.type.nomApp, color = c.creme,
            )
            Pill("Choisir Fern Messages", selected = true) {
                val intent = vm.smsRoleIntent()
                if (intent != null) role.launch(intent) else settings.launch(vm.appSettingsIntent())
            }
            if (refused) {
                Text(
                    "Refusé ? Sur /e/OS, une appli installée à la main doit d'abord être débloquée : " +
                        "Réglages → Applis → Fern Messages → ⋮ → « Autoriser les paramètres restreints », puis réessaie.",
                    style = Fern.type.nomApp, color = c.roseCarmin,
                )
                Pill("Ouvrir les réglages de l'appli", selected = false) { settings.launch(vm.appSettingsIntent()) }
            }
        }
        if (setup.missingPermissions.isNotEmpty()) {
            Text("Accès à donner : SMS, contacts et notifications.", style = Fern.type.nomApp, color = c.creme)
            Pill("Autoriser", selected = setup.isDefaultSms) { perms.launch(setup.missingPermissions.toTypedArray()) }
        }
    }
}
