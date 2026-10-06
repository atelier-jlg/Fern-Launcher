package com.atelierjlg.fern.ui.drawer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.apps.AppEntry
import com.atelierjlg.fern.search.ContactResult
import com.atelierjlg.fern.search.EventResult
import com.atelierjlg.fern.search.SearchExtras
import com.atelierjlg.fern.search.ShortcutResult
import com.atelierjlg.fern.ui.common.PillButton
import com.atelierjlg.fern.ui.theme.Fern

/** Les actions possibles sur les résultats de recherche (hors applis). */
class SearchActions(
    val onCopy: (String) -> Unit,
    val onOpenContact: (ContactResult) -> Unit,
    val onCall: (ContactResult) -> Unit,
    val onMessage: (ContactResult) -> Unit,
    val onOpenEvent: (EventResult) -> Unit,
    val onShortcut: (ShortcutResult) -> Unit,
    val onWebSearch: (String) -> Unit,
    val onRequestPermissions: () -> Unit,
)

/** Une section de résultats : un titre et des lignes. */
private class Section(val key: String, val title: String?, val rows: List<Pair<String, @Composable () -> Unit>>)

/**
 * Les résultats de recherche : d'abord les applis, puis (si la recherche étendue est activée)
 * calcul, contacts, raccourcis, agenda, et enfin la recherche web.
 *
 * `fromBottom` : le meilleur résultat est en bas, près du pouce (barre de recherche en bas du tiroir).
 * Sinon il est en haut (panneau de recherche qui descend du haut).
 */
@Composable
fun SearchResults(
    query: String,
    apps: List<AppEntry>,
    extras: SearchExtras,
    webLabel: String,
    fromBottom: Boolean,
    appCell: @Composable (AppEntry) -> Unit,
    actions: SearchActions,
    modifier: Modifier = Modifier,
) {
    val sections = buildList {
        val appRows = apps.take(8).chunked(4)
        if (appRows.isNotEmpty()) {
            add(
                Section(
                    key = "apps",
                    title = "Applis",
                    rows = appRows.mapIndexed { i, row ->
                        "apps-$i" to @Composable {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { app -> Box(Modifier.weight(1f)) { appCell(app) } }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    },
                ),
            )
        }
        extras.calculation?.let { result ->
            add(
                Section("calc", "Calcul", listOf("calc" to @Composable {
                    ResultCard(onClick = { actions.onCopy(result) }) {
                        Text("= $result", style = Fern.type.titreWidget, color = Fern.colors.pistache, modifier = Modifier.weight(1f))
                        Text("COPIER", style = Fern.type.libelle, color = Fern.colors.lichen)
                    }
                })),
            )
        }
        if (extras.contacts.isNotEmpty()) {
            add(Section("contacts", "Contacts", extras.contacts.map { c -> "contact-${c.id}" to @Composable { ContactRow(c, actions) } }))
        }
        if (extras.shortcuts.isNotEmpty()) {
            add(
                Section("shortcuts", "Raccourcis", extras.shortcuts.mapIndexed { i, sc ->
                    "shortcut-$i" to @Composable { ShortcutRow(sc, actions) }
                }),
            )
        }
        if (extras.events.isNotEmpty()) {
            add(
                Section("events", "Agenda", extras.events.map { e ->
                    "event-${e.eventId}-${e.begin}" to @Composable { EventRow(e, actions) }
                }),
            )
        }
        add(
            Section("web", null, listOf("web" to @Composable {
                ResultCard(onClick = { actions.onWebSearch(query) }) {
                    Text(
                        "Chercher « $query » $webLabel",
                        style = Fern.type.corps,
                        color = Fern.colors.creme,
                        modifier = Modifier.weight(1f),
                    )
                    Text("↗", style = Fern.type.corps, color = Fern.colors.lichen)
                }
            })),
        )
        if (extras.missingContacts || extras.missingCalendar) {
            add(
                Section("permissions", null, listOf("permissions" to @Composable {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Text(
                            "Fern peut aussi chercher dans tes contacts et ton agenda.",
                            style = Fern.type.corps,
                            color = Fern.colors.lichen,
                        )
                        Spacer(Modifier.height(8.dp))
                        PillButton("Autoriser", onClick = actions.onRequestPermissions, accent = true)
                    }
                })),
            )
        }
    }

    LazyColumn(
        reverseLayout = fromBottom,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        // En partant du bas (reverseLayout), le premier élément est affiché tout en bas :
        // on inverse donc l'ordre dans chaque section pour garder le titre au-dessus.
        for (section in sections) {
            if (fromBottom) {
                sectionRows(section, reversed = true)
                sectionTitle(section)
            } else {
                sectionTitle(section)
                sectionRows(section, reversed = false)
            }
        }
    }
}

private fun LazyListScope.sectionTitle(section: Section) {
    val title = section.title ?: return
    item(key = "title-${section.key}") {
        Text(
            text = title.uppercase(),
            style = Fern.type.libelle,
            color = Fern.colors.roseCarmin,
            modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
        )
    }
}

private fun LazyListScope.sectionRows(section: Section, reversed: Boolean) {
    val rows = if (reversed) section.rows.reversed() else section.rows
    items(rows, key = { it.first }) { (_, content) -> content() }
}

@Composable
private fun ContactRow(contact: ContactResult, actions: SearchActions) {
    ResultCard(onClick = { actions.onOpenContact(contact) }) {
        Initial(contact.name)
        Spacer(Modifier.width(12.dp))
        Text(
            contact.name,
            style = Fern.type.corps,
            color = Fern.colors.creme,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (contact.phone != null) {
            PillButton("Appeler", onClick = { actions.onCall(contact) })
            Spacer(Modifier.width(6.dp))
            PillButton("SMS", onClick = { actions.onMessage(contact) })
        }
    }
}

@Composable
private fun ShortcutRow(shortcut: ShortcutResult, actions: SearchActions) {
    ResultCard(onClick = { actions.onShortcut(shortcut) }) {
        shortcut.icon?.let {
            Image(bitmap = it, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(shortcut.label, style = Fern.type.corps, color = Fern.colors.creme, maxLines = 1)
            Text(shortcut.appLabel.uppercase(), style = Fern.type.libelle, color = Fern.colors.moussePale)
        }
    }
}

@Composable
private fun EventRow(event: EventResult, actions: SearchActions) {
    ResultCard(onClick = { actions.onOpenEvent(event) }) {
        Column(Modifier.weight(1f)) {
            Text(event.title, style = Fern.type.corps, color = Fern.colors.creme, maxLines = 1)
            Text(event.whenLabel.uppercase(), style = Fern.type.libelle, color = Fern.colors.lichen)
        }
    }
}

@Composable
private fun ResultCard(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Fern.colors.mousse, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        content = content,
    )
}

/** Une pastille ronde avec l'initiale du contact. */
@Composable
private fun Initial(name: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .background(Fern.colors.carmin, CircleShape),
    ) {
        Text(name.take(1).uppercase(), style = Fern.type.corps, color = Fern.colors.creme)
    }
}
