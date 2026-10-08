package com.atelierjlg.fern.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.ui.theme.Fern

/**
 * Écran provisoire des applis sœurs : montre le thème reçu de Fern Launcher.
 * Sert à vérifier sur le téléphone que le partage du thème marche (étape 0 de la feuille de route).
 */
@Composable
fun ThemeCheckScreen(appName: String, version: String, received: ReceivedTheme) {
    val c = Fern.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.nuit)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(appName, style = Fern.type.titreWidget, color = c.creme)
        Text("EN CONSTRUCTION · V$version", style = Fern.type.libelle, color = c.lichen)

        Column(
            Modifier
                .fillMaxWidth()
                .background(c.mousse, RoundedCornerShape(28.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("THÈME", style = Fern.type.libelle, color = c.lichen)
            Text(received.name, style = Fern.type.corps, color = c.creme)
            val (dot, status) = when (received.source) {
                ThemeSource.Fern -> c.pistache to "Reçu de Fern Launcher"
                ThemeSource.Cache -> c.roseCarmin to "Fern injoignable · dernier thème reçu"
                ThemeSource.Default -> c.roseCarmin to "Jamais reçu · thème par défaut"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(dot, CircleShape))
                Text("  $status", style = Fern.type.nomApp, color = c.lichen)
            }
            received.error?.let { Text(it, style = Fern.type.nomApp, color = c.moussePale) }
        }

        // Le nuancier : chaque couleur du thème, avec son nom universel.
        val swatches = listOf(
            "Fond" to c.nuit, "Cartes" to c.mousse, "Boutons" to c.lierre, "Séparateurs" to c.sousBois,
            "Texte" to c.creme, "Texte secondaire" to c.lichen, "Texte discret" to c.moussePale,
            "Accent" to c.roseCarmin, "Accent foncé" to c.carmin, "Accent clair" to c.pistache,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.mousse, RoundedCornerShape(28.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("NUANCIER", style = Fern.type.libelle, color = c.lichen)
            swatches.forEach { (label, color) -> Swatch(label, color, c.sousBois) }
        }
        Text(
            "Change de thème dans Fern (Paramètres → Thème) : cette page suit toute seule.",
            style = Fern.type.nomApp,
            color = c.moussePale,
        )
    }
}

@Composable
private fun Swatch(label: String, color: Color, border: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(28.dp)
                .background(color, CircleShape)
                .border(1.dp, border, CircleShape),
        )
        Text("  $label", style = Fern.type.corps, color = Fern.colors.creme)
    }
}
