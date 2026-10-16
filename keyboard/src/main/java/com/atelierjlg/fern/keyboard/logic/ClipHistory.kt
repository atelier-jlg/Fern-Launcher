package com.atelierjlg.fern.keyboard.logic

/**
 * L'historique du presse-papiers : les N derniers textes copiés, plus ceux qu'on a épinglés
 * (gardés tant qu'on ne les retire pas). Rangé dans le téléphone uniquement.
 * Les copies « sensibles » (mots de passe de Proton Pass…) n'y entrent jamais.
 */
class ClipHistory(private var keep: Int = 5) {
    data class Entry(val text: String, val time: Long, val pinned: Boolean = false)

    private val items = ArrayList<Entry>()

    /** Les épinglés d'abord, puis les plus récents. */
    val entries: List<Entry> get() = items.filter { it.pinned } + items.filter { !it.pinned }

    fun setKeep(n: Int) {
        keep = n.coerceIn(1, 20)
        trim()
    }

    /** Un nouveau texte copié. Un texte déjà présent remonte en tête (sans perdre son épingle). */
    fun add(text: String, time: Long): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.length > MAX_LENGTH) return false
        val old = items.indexOfFirst { it.text == t }
        val pinned = old >= 0 && items[old].pinned
        if (old == 0) return false
        if (old > 0) items.removeAt(old)
        items.add(0, Entry(t, time, pinned))
        trim()
        return true
    }

    fun togglePin(text: String) {
        val i = items.indexOfFirst { it.text == text }
        if (i >= 0) items[i] = items[i].copy(pinned = !items[i].pinned)
        trim()
    }

    fun remove(text: String) {
        items.removeAll { it.text == text }
    }

    /** Efface tout sauf les épinglés. */
    fun clearUnpinned() {
        items.removeAll { !it.pinned }
    }

    private fun trim() {
        var unpinned = 0
        val it = items.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (!e.pinned && ++unpinned > keep) it.remove()
        }
        while (items.count { it.pinned } > MAX_PINNED) {
            items.removeAt(items.indexOfLast { it.pinned })
        }
    }

    /** Une ligne par entrée : « temps<TAB>épinglé<TAB>texte » (tabulations et retours à la ligne échappés). */
    fun serialize(): String = buildString {
        for (e in items) append(e.time).append('\t').append(if (e.pinned) 1 else 0).append('\t').append(escape(e.text)).append('\n')
    }

    companion object {
        private const val MAX_LENGTH = 5_000
        private const val MAX_PINNED = 20

        fun parse(text: String, keep: Int): ClipHistory {
            val h = ClipHistory(keep)
            text.lineSequence().forEach { line ->
                val p = line.split('\t', limit = 3)
                if (p.size == 3) {
                    val time = p[0].toLongOrNull() ?: return@forEach
                    h.items.add(Entry(unescape(p[2]), time, p[1] == "1"))
                }
            }
            h.trim()
            return h
        }

        private fun escape(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

        private fun unescape(s: String): String = buildString {
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) {
                        't' -> append('\t')
                        'n' -> append('\n')
                        else -> append(s[i + 1])
                    }
                    i += 2
                } else {
                    append(c)
                    i++
                }
            }
        }
    }
}
