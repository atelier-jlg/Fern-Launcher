package com.atelierjlg.fern.messages

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.atelierjlg.fern.theme.FernSharedTheme
import com.atelierjlg.fern.theme.ThemeCheckScreen

/** Fern Messages. Pour l'instant : vérifie que le thème de Fern Launcher arrive bien. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            // Toute l'appli prend le thème actif de Fern Launcher.
            FernSharedTheme { received ->
                ThemeCheckScreen("Fern Messages", BuildConfig.VERSION_NAME, received)
            }
        }
    }
}
