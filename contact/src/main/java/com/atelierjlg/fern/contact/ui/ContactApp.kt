package com.atelierjlg.fern.contact.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atelierjlg.fern.contact.ContactViewModel
import com.atelierjlg.fern.contact.Screen
import com.atelierjlg.fern.contact.SetupState
import com.atelierjlg.fern.contact.Tab
import com.atelierjlg.fern.ui.kit.*
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.ui.theme.FernIcon
import com.atelierjlg.fern.ui.theme.FernIcons

/** La racine de l'appli : onglets en bas, ou la fiche / le formulaire par-dessus. */
@Composable
fun ContactApp(vm: ContactViewModel, onFinish: () -> Unit) {
    val stack by vm.screen.collectAsState()
    val tab by vm.tab.collectAsState()
    BackHandler(enabled = stack.size > 1) { vm.back() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Fern.colors.nuit)
            .systemBarsPadding()
            .imePadding(),
    ) {
        when (val top = stack.last()) {
            Screen.Tabs -> TabsScreen(vm, tab)
            is Screen.Detail -> DetailScreen(vm, top.id)
            is Screen.Edit -> EditScreen(vm, top.id, top.number)
        }
    }
}

@Composable
private fun TabsScreen(vm: ContactViewModel, tab: Tab) {
    val setup by vm.setup.collectAsState()
    Column(Modifier.fillMaxSize()) {
        if (!setup.complete) SetupCard(vm, setup)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.Favoris -> FavoritesScreen(vm)
                Tab.Recents -> RecentsScreen(vm)
                Tab.Contacts -> ContactsScreen(vm)
                Tab.Clavier -> DialpadScreen(vm)
            }
        }
        BottomTabs(tab) { vm.selectTab(it) }
    }
}

@Composable
private fun BottomTabs(current: Tab, onSelect: (Tab) -> Unit) {
    val c = Fern.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(c.mousse)
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tab.entries.forEach { t ->
            val selected = t == current
            val icon = when (t) {
                Tab.Favoris -> FernIcons.Star
                Tab.Recents -> FernIcons.Clock
                Tab.Contacts -> FernIcons.User
                Tab.Clavier -> FernIcons.Dialpad
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (selected) c.lierre else c.mousse)
                    .clickable { onSelect(t) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FernIcon(icon, if (selected) c.pistache else c.lichen, size = 20.dp)
                Text(
                    t.label.uppercase(),
                    style = Fern.type.libelle.copy(fontSize = 9.sp),
                    color = if (selected) c.creme else c.lichen,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Ce qui manque pour que l'appli marche à fond (appli par défaut, autorisations…). */
@Composable
private fun SetupCard(vm: ContactViewModel, setup: SetupState) {
    val c = Fern.colors
    var roleRefused by remember { mutableStateOf(false) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        roleRefused = result.resultCode != Activity.RESULT_OK
        vm.reload()
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.reload() }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.reload() }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(c.mousse)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("POUR BIEN DÉMARRER", style = Fern.type.libelle, color = c.lichen)
        if (!setup.isDefaultDialer) {
            Text("Fern Contact n'est pas encore l'appli Téléphone : les appels s'affichent encore dans l'ancienne.",
                style = Fern.type.nomApp, color = c.creme)
            Pill("Choisir Fern Contact", selected = true) {
                val intent = vm.dialerRoleIntent()
                if (intent != null) roleLauncher.launch(intent) else settingsLauncher.launch(vm.appSettingsIntent())
            }
            if (roleRefused) {
                Text(
                    "Refusé ? Sur /e/OS, une appli installée à la main doit d'abord être débloquée : " +
                        "Réglages → Applis → Fern Contact → ⋮ → « Autoriser les paramètres restreints », puis réessaie.",
                    style = Fern.type.nomApp, color = c.roseCarmin,
                )
                Pill("Ouvrir les réglages de l'appli", selected = false) { settingsLauncher.launch(vm.appSettingsIntent()) }
            }
        }
        if (setup.missingPermissions.isNotEmpty()) {
            Text("Accès à donner : contacts, journal d'appels, téléphone et notifications.", style = Fern.type.nomApp, color = c.creme)
            Pill("Autoriser", selected = setup.isDefaultDialer) { permLauncher.launch(setup.missingPermissions.toTypedArray()) }
        }
        if (!setup.canFullScreen) {
            Text("Pour voir les appels téléphone verrouillé : autorise l'affichage en plein écran.", style = Fern.type.nomApp, color = c.creme)
            Pill("Autoriser le plein écran", selected = false) { vm.fullScreenSettingsIntent()?.let(settingsLauncher::launch) }
        }
    }
    androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
}
