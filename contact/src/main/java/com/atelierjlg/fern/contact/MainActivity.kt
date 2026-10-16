package com.atelierjlg.fern.contact

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.atelierjlg.fern.contact.data.ContactsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.atelierjlg.fern.contact.ui.ContactApp
import com.atelierjlg.fern.theme.FernSharedTheme

/**
 * Fern Contact : contacts + téléphone. Ouvre aussi le clavier quand une autre appli
 * demande à composer un numéro (lien « tel: »).
 */
class MainActivity : ComponentActivity() {
    private val viewModel: ContactViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handle(intent)
        viewModel.scheduleBirthdays()
        setContent {
            FernSharedTheme { ContactApp(viewModel, onFinish = { finish() }) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }

    /** « tel:0612345678 » (depuis Fern, un site, un SMS…) : clavier pré-rempli. */
    private fun handle(intent: Intent?) {
        intent ?: return
        // Depuis une notification d'anniversaire : la fiche du contact.
        val contact = intent.getLongExtra(EXTRA_CONTACT, -1L)
        if (contact >= 0) {
            viewModel.selectTab(Tab.Contacts)
            viewModel.open(Screen.Detail(contact))
            return
        }
        // La fiche d'un contact (lien content://com.android.contacts/…).
        val data = intent.data
        if (intent.action == Intent.ACTION_VIEW && data?.authority == android.provider.ContactsContract.AUTHORITY) {
            lifecycleScope.launch {
                val id = withContext(Dispatchers.IO) {
                    runCatching {
                        contentResolver.query(data, arrayOf(android.provider.ContactsContract.Contacts._ID), null, null, null)
                            ?.use { if (it.moveToFirst()) it.getLong(0) else null }
                    }.getOrNull()
                }
                if (id != null) {
                    viewModel.selectTab(Tab.Contacts)
                    viewModel.open(Screen.Detail(id))
                }
            }
            return
        }
        // « Voir ou créer » : la fiche si le numéro est connu, sinon un nouveau contact pré-rempli.
        if (intent.action == android.provider.ContactsContract.Intents.SHOW_OR_CREATE_CONTACT && data?.scheme == "tel") {
            val num = data.schemeSpecificPart
            lifecycleScope.launch {
                val found = withContext(Dispatchers.IO) { ContactsRepo(this@MainActivity).lookup(num) }
                viewModel.selectTab(Tab.Contacts)
                viewModel.open(if (found != null) Screen.Detail(found.first) else Screen.Edit(null, num))
            }
            return
        }
        val number = intent.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart
        when {
            number != null -> {
                viewModel.dialed.value = number
                viewModel.selectTab(Tab.Clavier)
            }
            intent.action == Intent.ACTION_DIAL || intent.getBooleanExtra(EXTRA_OPEN_DIALPAD, false) -> {
                viewModel.dialed.value = ""
                viewModel.selectTab(Tab.Clavier)
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_DIALPAD = "open_dialpad"
        const val EXTRA_CONTACT = "contact_id"
    }
}
