package com.atelierjlg.fern.contact.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atelierjlg.fern.common.PhoneNumbers
import com.atelierjlg.fern.common.SearchText
import com.atelierjlg.fern.contact.ContactViewModel
import com.atelierjlg.fern.contact.Screen
import com.atelierjlg.fern.ui.kit.*
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.ui.theme.FernIcon
import com.atelierjlg.fern.ui.theme.FernIcons

private val KEYS = listOf(
    "1" to "", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to "", "0" to "+", "#" to "",
)

/**
 * Le clavier : chiffres + suggestions (un morceau du numéro, ou le nom tapé « à l'ancienne » :
 * 5-8-5 pour « Jules »). Appui long sur 1 = messagerie, sur 0 = « + ».
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialpadScreen(vm: ContactViewModel) {
    val c = Fern.colors
    val typed by vm.dialed.collectAsState()
    val contacts by vm.contacts.collectAsState()
    val suggestions = remember(typed, contacts) {
        if (typed.length < 2) emptyList() else contacts.mapNotNull { ct ->
            val byNumber = ct.phones.firstOrNull { PhoneNumbers.containsDigits(it, typed) }
            when {
                byNumber != null -> ct to byNumber
                SearchText.t9Matches(ct.name, typed) && ct.phones.isNotEmpty() -> ct to ct.phones.first()
                else -> null
            }
        }.take(3)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Suggestions
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Bottom) {
            suggestions.forEach { (contact, number) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                        .combinedClickable(onClick = { vm.call(number) }, onLongClick = { vm.open(Screen.Detail(contact.id)) })
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(contact.name, contact.photoUri, 36.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(contact.name, style = Fern.type.corps, color = c.creme, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(PhoneNumbers.format(number), style = Fern.type.nomApp, color = c.lichen)
                    }
                    FernIcon(FernIcons.Phone, c.pistache, size = 18.dp)
                }
            }
        }
        // Numéro tapé
        Text(
            if (typed.isEmpty()) " " else PhoneNumbers.format(typed).takeIf { it.filter(Char::isDigit) == typed.filter(Char::isDigit) } ?: typed,
            style = Fern.type.titreWidget.copy(fontSize = if (typed.length > 13) 26.sp else 34.sp),
            color = c.creme,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
        // Touches
        KEYS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (digit, letters) ->
                    Key(
                        digit, letters,
                        onClick = { vm.dialed.value = typed + digit },
                        onLongClick = when (digit) {
                            "0" -> { { vm.dialed.value = "$typed+" } }
                            "1" -> { { vm.callVoicemail() } }
                            else -> null
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Créer un contact · Appeler · Effacer
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                if (typed.isNotEmpty()) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).combinedClickable(onClick = { vm.open(Screen.Edit(null, typed)) }),
                        contentAlignment = Alignment.Center,
                    ) { FernIcon(FernIcons.UserPlus, c.lichen) }
                }
            }
            RoundAction(FernIcons.Phone, "", c.pistache, c.nuit, size = 68.dp, enabled = typed.isNotEmpty()) { vm.call(typed) }
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                if (typed.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .combinedClickable(onClick = { vm.dialed.value = typed.dropLast(1) }, onLongClick = { vm.dialed.value = "" }),
                        contentAlignment = Alignment.Center,
                    ) { FernIcon(FernIcons.Backspace, c.lichen) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Key(digit: String, letters: String, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val c = Fern.colors
    Box(
        Modifier
            .padding(vertical = 5.dp)
            .size(72.dp)
            .clip(CircleShape)
            .background(c.mousse)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(digit, style = Fern.type.titreWidget.copy(fontSize = 28.sp, lineHeight = 30.sp), color = c.creme)
            if (letters.isNotEmpty()) Text(letters, style = Fern.type.libelle.copy(fontSize = 9.sp), color = c.lichen)
        }
    }
}
