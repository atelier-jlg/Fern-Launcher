package com.atelierjlg.fern.contact.data

import android.content.Context
import android.provider.BlockedNumberContract
import com.atelierjlg.fern.common.PhoneNumbers

/**
 * Démarchage téléphonique : en France, l'ARCEP réserve ces préfixes aux appels commerciaux
 * automatisés (depuis 2023). Un appel venant de là est presque toujours du démarchage.
 */
object Telemarketing {
    val PREFIXES = listOf("0162", "0163", "0270", "0271", "0377", "0378", "0424", "0425", "0568", "0569", "0948", "0949")

    /** « +33 1 62 … », « 0162… », « 0033162… » → démarchage. */
    fun matches(number: String): Boolean {
        val d = PhoneNumbers.digits(number)
        val national = when {
            d.startsWith("+33") -> "0" + d.drop(3)
            d.startsWith("0033") -> "0" + d.drop(4)
            else -> d
        }
        return national.length == 10 && PREFIXES.any { national.startsWith(it) }
    }
}

/** La liste des numéros bloqués d'Android (partagée avec Fern Messages). */
class BlockedRepo(private val context: Context) {
    data class Blocked(val id: Long, val number: String)

    fun all(): List<Blocked> {
        val out = mutableListOf<Blocked>()
        runCatching {
            context.contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ID, BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
                null, null, null,
            )?.use { c -> while (c.moveToNext()) out += Blocked(c.getLong(0), c.getString(1).orEmpty()) }
        }
        return out
    }

    fun unblock(number: String) = runCatching { BlockedNumberContract.unblock(context, number) }
}
