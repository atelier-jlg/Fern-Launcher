package com.atelierjlg.fern.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.data.isValidHex

/**
 * Choisir une couleur : trois curseurs (teinte, saturation, luminosité) ou le code « #RRGGBB ».
 */
@Composable
fun ColorDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(parse(initial), it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    var hex by remember { mutableStateOf(initial.uppercase()) }

    fun fromSliders() {
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
        hex = "#%06X".format(argb and 0xFFFFFF)
    }

    fun fromHex(text: String) {
        hex = text.uppercase()
        if (isValidHex(hex)) {
            val out = FloatArray(3)
            android.graphics.Color.colorToHSV(parse(hex), out)
            hue = out[0]
            sat = out[1]
            value = out[2]
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Couleur") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(Color(parse(hex)), RoundedCornerShape(16.dp)),
                )
                Text("Teinte")
                Slider(value = hue, onValueChange = { hue = it; fromSliders() }, valueRange = 0f..360f)
                Text("Saturation")
                Slider(value = sat, onValueChange = { sat = it; fromSliders() })
                Text("Luminosité")
                Slider(value = value, onValueChange = { value = it; fromSliders() })
                OutlinedTextField(
                    value = hex,
                    onValueChange = { fromHex(it) },
                    singleLine = true,
                    label = { Text("Code couleur") },
                    isError = !isValidHex(hex),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (isValidHex(hex)) onConfirm(hex) }, enabled = isValidHex(hex)) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

private fun parse(hex: String): Int =
    runCatching { android.graphics.Color.parseColor(hex) }.getOrDefault(android.graphics.Color.GRAY)
