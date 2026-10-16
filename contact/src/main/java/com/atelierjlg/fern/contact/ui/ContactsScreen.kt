package com.atelierjlg.fern.contact.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.common.PhoneNumbers
import com.atelierjlg.fern.common.SearchText
import com.atelierjlg.fern.contact.ContactViewModel
import com.atelierjlg.fern.contact.Screen
import com.atelierjlg.fern.contact.data.ContactSummary
import com.atelierjlg.fern.ui.kit.*
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.ui.theme.FernIcon
import com.atelierjlg.fern.ui.theme.FernIcons

/** Recherche élargie : nom, surnom, société, note, et n'importe quel numéro (« 0612 », « 06 12 »…). */
fun ContactSummary.matches(query: String): Boolean {
    if (query.isBlank()) return true
    if (SearchText.matches("$name $extra", query)) return true
    val digits = query.filter { it.isDigit() }
    return digits.length >= 2 && digits.length == query.count { !it.isWhitespace() } &&
        phones.any { PhoneNumbers.containsDigits(it, digits) }
}

@Composable
fun ContactsScreen(vm: ContactViewModel) {
    val c = Fern.colors
    val contacts by vm.contacts.collectAsState()
    val me by vm.me.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(contacts, query) { contacts.filter { it.matches(query) } }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            FernField(query, { query = it }, "Rechercher", Modifier.weight(1f), leading = FernIcons.Search)
            IconButtonRound(FernIcons.Settings, c.lichen) { vm.open(Screen.Settings) }
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(c.pistache)
                    .clickable { vm.open(Screen.Edit(null)) },
                contentAlignment = Alignment.Center,
            ) { FernIcon(FernIcons.UserPlus, c.nuit) }
        }
        if (contacts.isEmpty()) {
            Text("Aucun contact (ou accès pas encore donné).", style = Fern.type.corps, color = c.lichen, modifier = Modifier.padding(top = 24.dp))
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            // Ma fiche, tout en haut.
            if (query.isBlank()) {
                item(key = "moi") {
                    val mine = me
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(c.mousse)
                            .clickable { vm.open(if (mine != null) Screen.Detail(mine.id) else Screen.Edit(null, me = true)) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(mine?.displayName ?: "", mine?.photoUri, 44.dp)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(mine?.displayName?.ifBlank { null } ?: "Mes infos", style = Fern.type.corps, color = c.creme)
                            Text(
                                mine?.phones?.firstOrNull()?.let { "MOI · " + PhoneNumbers.format(it.value) } ?: "TOUCHE POUR REMPLIR TA FICHE",
                                style = Fern.type.libelle, color = c.pistache,
                            )
                        }
                        if (mine != null) IconButtonRound(FernIcons.Send, c.lichen) { vm.shareMyCard() }
                    }
                }
            }
            var lastLetter = ""
            shown.forEach { contact ->
                val letter = SearchText.initial(contact.name)
                if (query.isBlank() && letter != lastLetter) {
                    lastLetter = letter
                    item(key = "lettre-$letter") { SectionLabel(letter) }
                }
                item(key = contact.id) { ContactRow(contact, Modifier.animateItem()) { vm.open(Screen.Detail(contact.id)) } }
            }
        }
    }
}

@Composable
fun ContactRow(contact: ContactSummary, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Fern.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(contact.name, contact.photoUri, 44.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(contact.name, style = Fern.type.corps, color = c.creme, maxLines = 1, overflow = TextOverflow.Ellipsis)
            contact.phones.firstOrNull()?.let {
                Text(PhoneNumbers.format(it), style = Fern.type.nomApp, color = c.lichen, maxLines = 1)
            }
        }
        if (contact.starred) FernIcon(FernIcons.Star, c.pistache, size = 16.dp)
    }
}

/** Favoris : des cartes (photo, surnom, bouton d'appel). Toucher la carte = la fiche, le bouton = appeler. */
@Composable
fun FavoritesScreen(vm: ContactViewModel) {
    val c = Fern.colors
    val contacts by vm.contacts.collectAsState()
    val favorites = remember(contacts) { contacts.filter { it.starred } }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, top = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Favoris", style = Fern.type.titreWidget, color = c.creme, modifier = Modifier.weight(1f))
            IconButtonRound(FernIcons.Search, c.lichen) { vm.selectTab(com.atelierjlg.fern.contact.Tab.Contacts) }
        }
        if (favorites.isEmpty()) {
            Text(
                "Pas encore de favori. Ouvre la fiche d'un contact et touche l'étoile.",
                style = Fern.type.corps, color = c.lichen, modifier = Modifier.padding(horizontal = 8.dp),
            )
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(favorites, key = { it.id }) { contact ->
                FavoriteCard(
                    contact,
                    Modifier.animateItem(),
                    onOpen = { vm.open(Screen.Detail(contact.id)) },
                    onCall = contact.phones.firstOrNull()?.let { number -> { vm.call(number) } },
                )
            }
        }
    }
}

@Composable
private fun FavoriteCard(contact: ContactSummary, modifier: Modifier, onOpen: () -> Unit, onCall: (() -> Unit)?) {
    val c = Fern.colors
    val shape = RoundedCornerShape(24.dp)
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        modifier
            .pressScale(interaction, 0.95f)
            .clip(shape)
            .background(c.mousse)
            .border(1.dp, c.sousBois, shape)
            .clickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current, onClick = onOpen)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(contact.name, contact.photoUri, 64.dp)
        Text(
            contact.nickname ?: contact.name.substringBefore(' '),
            style = Fern.type.corps,
            color = c.creme,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
        )
        Box(
            Modifier
                .size(width = 52.dp, height = 32.dp)
                .clip(RoundedCornerShape(50))
                .background(if (onCall != null) c.lierre else c.nuit)
                .clickable(enabled = onCall != null) { onCall?.invoke() },
            contentAlignment = Alignment.Center,
        ) { FernIcon(FernIcons.Phone, if (onCall != null) c.pistache else c.moussePale, size = 18.dp) }
    }
}
