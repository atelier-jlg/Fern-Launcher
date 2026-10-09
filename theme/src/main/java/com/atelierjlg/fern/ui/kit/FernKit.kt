package com.atelierjlg.fern.ui.kit

/*
 * Petits composants communs aux applis Fern (Contact, Messages) : avatar, boutons ronds,
 * pilules, champs de saisie, barre du haut. Tous prennent leurs couleurs dans le thème.
 */

import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atelierjlg.fern.common.SearchText
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.ui.theme.FernIcon
import com.atelierjlg.fern.ui.theme.FernIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Charge une photo de contact (hors du fil principal), réduite à la taille utile. */
@Composable
fun rememberPhoto(uri: String?, sizePx: Int = 256): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val scale = sizePx.toFloat() / maxOf(info.size.width, info.size.height)
                    if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }.asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}

/** Rond avec la photo du contact, ou ses initiales sur une couleur du thème. */
@Composable
fun Avatar(name: String, photoUri: String?, size: Dp, modifier: Modifier = Modifier) {
    val c = Fern.colors
    val photo = rememberPhoto(photoUri)
    val palette = listOf(c.lierre, c.carmin, c.sousBois, c.moussePale)
    val bg = palette[(SearchText.fold(name).hashCode() and 0x7fffffff) % palette.size]
    Box(modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        if (photo != null) {
            Image(photo, null, Modifier.size(size), contentScale = ContentScale.Crop)
        } else {
            val initials = name.split(' ', '-').filter { it.isNotBlank() && it.first().isLetter() }
                .take(2).joinToString("") { it.first().uppercase() }
            if (initials.isNotEmpty()) {
                Text(initials, style = Fern.type.corps.copy(fontSize = (size.value * 0.36f).sp), color = c.creme)
            } else {
                FernIcon(FernIcons.User, c.creme, size = size * 0.5f)
            }
        }
    }
}

/**
 * Effet « bouton qui s'enfonce » : l'élément se tasse un peu sous le doigt et revient avec un léger rebond.
 * À combiner avec un clickable qui partage le même [interaction].
 */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.92f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "appui",
    )
    graphicsLayer(scaleX = scale, scaleY = scale)
}

/** Gros bouton rond avec un picto et un libellé dessous (écran d'appel, fiche contact). */
@Composable
fun RoundAction(
    icon: ImageVector,
    label: String,
    background: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    labelColor: Color = Fern.colors.lichen,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (enabled) background else background.copy(alpha = 0.35f), label = "fond")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .pressScale(interaction, 0.88f)
                .size(size)
                .clip(CircleShape)
                .background(bg)
                .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            FernIcon(icon, if (enabled) tint else tint.copy(alpha = 0.5f), size = size * 0.4f)
        }
        if (label.isNotEmpty()) {
            Text(
                label.uppercase(),
                style = Fern.type.libelle.copy(fontSize = 10.sp, letterSpacing = 1.sp),
                color = labelColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Pilule cliquable (filtres, choix). */
@Composable
fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Fern.colors
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) c.pistache else c.mousse, label = "pilule")
    val fg by animateColorAsState(if (selected) c.nuit else c.creme, label = "texte")
    Box(
        modifier
            .pressScale(interaction, 0.94f)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .defaultMinSize(minHeight = 40.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = Fern.type.libelle, color = fg)
    }
}

/** Une ligne « picto + texte » cliquable (actions d'une fiche). */
@Composable
fun ActionRow(icon: ImageVector, text: String, modifier: Modifier = Modifier, tint: Color = Fern.colors.creme, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FernIcon(icon, tint)
        Text(text, style = Fern.type.corps, color = tint)
    }
}

/** Champ de saisie aux couleurs du thème. */
@Composable
fun FernField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    singleLine: Boolean = true,
    leading: ImageVector? = null,
    error: Boolean = false,
) {
    val c = Fern.colors
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = Fern.type.corps.copy(color = c.creme),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.pistache),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = if (keyboardType == androidx.compose.ui.text.input.KeyboardType.Text) {
                androidx.compose.ui.text.input.KeyboardCapitalization.Sentences
            } else {
                androidx.compose.ui.text.input.KeyboardCapitalization.None
            },
        ),
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.mousse)
                    .then(if (error) Modifier.background(c.carmin.copy(alpha = 0.25f)) else Modifier)
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    FernIcon(leading, c.lichen, size = 20.dp)
                    androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = Fern.type.corps, color = c.moussePale)
                    inner()
                }
            }
        },
    )
}

/** Titre de section en capitales (« NUMÉROS », « A »…). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Fern.type.libelle, color = Fern.colors.lichen, modifier = modifier.padding(top = 16.dp, bottom = 6.dp))
}

/** Barre du haut : retour à gauche, actions à droite. */
@Composable
fun TopBar(onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButtonRound(FernIcons.Back, Fern.colors.creme, onClick = onBack)
        Spacer(Modifier.weight(1f))
        actions()
    }
}

@Composable
fun IconButtonRound(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        FernIcon(icon, tint)
    }
}
