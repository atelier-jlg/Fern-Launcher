package com.atelierjlg.fern.search

/** Tout ce que la recherche trouve en plus des applis. */
data class SearchExtras(
    val calculation: String? = null,
    val contacts: List<ContactResult> = emptyList(),
    val events: List<EventResult> = emptyList(),
    val shortcuts: List<ShortcutResult> = emptyList(),
    /** Il manque l'autorisation de lire les contacts / l'agenda. */
    val missingContacts: Boolean = false,
    val missingCalendar: Boolean = false,
) {
    companion object {
        val Empty = SearchExtras()
    }
}
