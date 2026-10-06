package com.atelierjlg.fern.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.atelierjlg.fern.apps.AppEntry
import com.atelierjlg.fern.data.IconMode
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.ui.theme.LocalIconMode

/**
 * L'icône d'une appli, selon le style choisi dans les Paramètres :
 * - origine / pack : l'image telle quelle ;
 * - plaques Fern : une plaque ronde de la couleur de la famille, avec le picto à 70 %
 *   recoloré dans la couleur du trait (sans contour ni ombre, comme le guide Renkin).
 */
@Composable
fun AppIconImage(app: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    if (LocalIconMode.current != IconMode.Plaques) {
        Image(bitmap = app.icon, contentDescription = app.label, modifier = modifier.size(size))
        return
    }
    val colors = Fern.colors
    val trait = colors.glyph(app.family)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(colors.plate(app.family), CircleShape),
    ) {
        val glyph = app.glyph
        if (glyph != null) {
            Image(
                bitmap = glyph,
                contentDescription = app.label,
                colorFilter = ColorFilter.tint(trait, BlendMode.SrcIn),
                modifier = Modifier.size(size * 0.7f),
            )
        } else {
            // Pas de picto disponible : l'initiale, dans la même couleur que les pictos.
            val fontSize = with(LocalDensity.current) { (size * 0.42f).toSp() }
            Text(
                text = app.label.take(1).uppercase(),
                style = Fern.type.titreWidget.copy(fontSize = fontSize, lineHeight = fontSize, fontWeight = FontWeight(800)),
                color = trait,
            )
        }
    }
}
