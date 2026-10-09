package com.atelierjlg.fern.messages

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.atelierjlg.fern.messages.sms.MessageNotifier
import com.atelierjlg.fern.messages.ui.MessagesApp
import com.atelierjlg.fern.theme.FernSharedTheme

/**
 * Fern Messages. S'ouvre aussi depuis une notification (la bonne conversation), un lien « sms: »
 * ou un partage de texte depuis une autre appli.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: MessagesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handle(intent)
        setContent { FernSharedTheme { MessagesApp(viewModel) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.forgetContacts()
        viewModel.reload()
        viewModel.retryMms()
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        val thread = intent.getLongExtra(MessageNotifier.EXTRA_THREAD, -1L)
        if (thread >= 0) {
            viewModel.openThreadFromOutside(thread)
            return
        }
        when (intent.action) {
            // « sms:0612345678?body=Salut » ou « smsto:… » (depuis Fern Contact, un site, Fern…)
            Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                if (data.scheme !in listOf("sms", "smsto", "mms", "mmsto")) return
                val ssp = data.schemeSpecificPart.orEmpty()
                val number = ssp.substringBefore('?')
                val body = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: ssp.substringAfter("body=", "").takeIf { ssp.contains("body=") }.orEmpty()
                viewModel.open(Screen.Compose(number, body))
            }
            // Texte, photo ou carte de contact partagés depuis une autre appli.
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                @Suppress("DEPRECATION")
                val stream = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                val type = intent.type.orEmpty().lowercase()
                val image = stream?.takeIf { type.startsWith("image/") }
                val vcard = stream?.takeIf { type.contains("vcard") }
                if (text.isBlank() && image == null && vcard == null) return
                // « address » : le destinataire, quand l'appli qui partage le connaît (Fern Contact).
                val number = intent.getStringExtra("address").orEmpty()
                viewModel.open(Screen.Compose(number, text, image?.toString(), vcard?.toString()))
            }
        }
    }
}
