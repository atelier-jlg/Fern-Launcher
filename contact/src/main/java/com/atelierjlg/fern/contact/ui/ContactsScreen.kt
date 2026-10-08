package com.atelierjlg.fern.contact.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(contacts, query) { contacts.filter { it.matches(query) } }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            FernField(query, { query = it }, "Rechercher", Modifier.weight(1f), leading = FernIcons.Search)
            Box(
                Modifier
                    .padding(start = 10.dp)
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
            var lastLetter = ""
            shown.forEach { contact ->
                val letter = SearchText.initial(contact.name)
                if (query.isBlank() && letter != lastLetter) {
                    lastLetter = letter
                    item(key = "lettre-$letter") { SectionLabel(letter) }
                }
                item(key = contact.id) { ContactRow(contact) { vm.open(Screen.Detail(contact.id)) } }
            }
        }
    }
}

@Composable
fun ContactRow(contact: ContactSummary, onClick: () -> Unit) {
    val c = Fern.colors
    Row(
        Modifier
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

/** Favoris : toucher = appeler, appui long = la fiche. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(vm: ContactViewModel) {
    val c = Fern.colors
    val contacts by vm.contacts.collectAsState()
    val favorites = remember(contacts) { contacts.filter { it.starred } }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        SectionLabel("Favoris", Modifier.padding(top = 8.dp))
        if (favorites.isEmpty()) {
            Text(
                "Pas encore de favori. Ouvre la fiche d'un contact et touche l'étoile.",
                style = Fern.type.corps, color = c.lichen,
            )
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            items(favorites, key = { it.id }) { contact ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .combinedClickable(
                            onClick = {
                                val number = contact.phones.firstOrNull()
                                if (number != null) vm.call(number) else vm.open(Screen.Detail(contact.id))
                            },
                            onLongClick = { vm.open(Screen.Detail(contact.id)) },
                        )
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(contact.name, contact.photoUri, 76.dp)
                    Text(
                        contact.name.substringBefore(' '),
                        style = Fern.type.nomApp,
                        color = c.creme,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        Text("Toucher : appeler · appui long : la fiche", style = Fern.type.nomApp, color = c.moussePale, modifier = Modifier.padding(bottom = 8.dp))
    }
}
