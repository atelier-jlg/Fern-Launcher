package com.atelierjlg.fern.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.apps.AppEntry
import com.atelierjlg.fern.ui.theme.Fern

/*
 * Petits composants réutilisés partout (un peu comme des classes CSS réutilisables).
 */

/** Une icône d'appli, avec ou sans son nom dessous. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppEntry,
    iconSize: Dp,
    showLabel: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        it()
                    }
                },
            )
            .padding(vertical = 4.dp),
    ) {
        AppIconImage(app = app, size = iconSize)
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = app.label,
                style = Fern.type.nomApp,
                color = Fern.colors.creme,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Un emplacement vide en mode édition : un cercle en pointillé avec « + ». */
@Composable
fun EmptySlot(iconSize: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Fern.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(vertical = 4.dp)
            .size(iconSize)
            .border(1.5.dp, colors.moussePale, CircleShape)
            .clickable(onClick = onClick),
    ) {
        Text("+", style = Fern.type.titreWidget, color = colors.lichen)
    }
}

/** Un petit bouton en pilule (« + PACK », « TERMINÉ »…). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val colors = Fern.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(if (accent) colors.pistache else colors.lierre, RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = Fern.type.libelle,
            color = if (accent) colors.nuit else colors.creme,
            maxLines = 1,
        )
    }
}

/** Un bouton rond minuscule avec un symbole (↑ ↓ ✕ ✎), pour les barres d'outils d'édition. */
@Composable
fun GlyphButton(glyph: String, onClick: () -> Unit, enabled: Boolean = true, size: Dp = 32.dp) {
    val colors = Fern.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(2.dp)
            .size(size)
            .background(colors.lierre, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(glyph, style = Fern.type.corps, color = if (enabled) colors.creme else colors.moussePale)
    }
}

/** La barre de recherche en pilule. */
@Composable
fun FernSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onGo: () -> Unit = {},
) {
    val colors = Fern.colors
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = Fern.type.corps.copy(color = colors.creme),
        cursorBrush = SolidColor(colors.roseCarmin),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(colors.lierre, RoundedCornerShape(percent = 50))
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(text = placeholder, style = Fern.type.corps, color = colors.lichen)
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    Text(
                        text = "✕",
                        style = Fern.type.corps,
                        color = colors.lichen,
                        modifier = Modifier
                            .clickable { onQueryChange("") }
                            .padding(start = 12.dp),
                    )
                }
            }
        },
    )
}

/** Une boîte de dialogue pour saisir un texte (renommer une page, un pack, une appli…). */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Valider",
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(value.text) }),
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.text) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Une boîte de confirmation (« Supprimer cette page ? »). */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
