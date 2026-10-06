package com.atelierjlg.fern

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.atelierjlg.fern.ui.LauncherRoot
import com.atelierjlg.fern.ui.theme.FernTheme
import com.atelierjlg.fern.ui.theme.toFernColors
import com.atelierjlg.fern.ui.theme.LocalIconMode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Point d'entrée : l'unique écran de Fern. Android l'ouvre quand on appuie sur Accueil.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // On dessine sous la barre d'état et la barre de navigation (icônes claires).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            // Le thème vient de la configuration : le changer dans les Paramètres recolore tout.
            val config by viewModel.config.collectAsStateWithLifecycle()
            val colors = remember(config.effectiveTheme.colors) { config.effectiveTheme.colors.toFernColors() }
            FernTheme(colors = colors) {
                CompositionLocalProvider(LocalIconMode provides config.icons.mode) {
                    LauncherRoot(viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onBackground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Fern est déjà ouvert et on rappuie sur Accueil : on revient à la page d'accueil.
        viewModel.onHomePressed()
    }
}
