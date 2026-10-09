package com.atelierjlg.fern.contact.data

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName

/*
 * Les contacts, tels que Fern Contact les manipule.
 *
 * Android range un contact en « lignes » (une ligne par numéro, par e-mail, pour le nom…),
 * un peu comme une table SQL. L'ancienne appli réécrivait tout le contact à chaque modification
 * et perdait ce qu'elle ne connaissait pas (poste, service, autres dates…). Ici, on ne touche
 * qu'aux lignes réellement modifiées : tout le reste est laissé intact.
 */

/** Une valeur avec un type : un numéro « Mobile », un e-mail « Travail »… */
data class Labeled(
    /** Ligne existante dans Android, ou null pour une nouvelle. */
    val rowId: Long? = null,
    val value: String = "",
    val type: Int = Phone.TYPE_MOBILE,
    /** Libellé personnalisé (type « personnalisé » = 0). */
    val label: String? = null,
)

/** Une ligne à une seule valeur utile (surnom, note, anniversaire…). */
data class SingleRow(val rowId: Long, val value: String)

data class NameRow(
    val rowId: Long,
    val given: String = "",
    val family: String = "",
    val prefix: String = "",
    val middle: String = "",
    val suffix: String = "",
)

/** La société ; le poste (title) est gardé tel quel, Fern Contact ne l'affiche pas encore. */
data class OrgRow(val rowId: Long, val company: String, val title: String = "")

/** Un contact dans la liste (léger). */
data class ContactSummary(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val starred: Boolean = false,
    val photoUri: String? = null,
    val phones: List<String> = emptyList(),
    /** Surnom, société… pour la recherche élargie. */
    val extra: String = "",
    /** Le surnom (« Maman », « Pipoune »), affiché dans les favoris. */
    val nickname: String? = null,
)

/** Un contact complet (la fiche). */
data class ContactDetail(
    val id: Long,
    val lookupKey: String,
    val displayName: String,
    val starred: Boolean = false,
    val photoUri: String? = null,
    val customRingtone: String? = null,
    /** Le « contact brut » où ajouter les nouvelles lignes (celui du nom). */
    val rawContactId: Long,
    val name: NameRow? = null,
    val nickname: SingleRow? = null,
    val organization: OrgRow? = null,
    val birthday: SingleRow? = null,
    val note: SingleRow? = null,
    val phones: List<Labeled> = emptyList(),
    val emails: List<Labeled> = emptyList(),
)

/** Le formulaire « Modifier » / « Nouveau contact ». */
data class ContactForm(
    val given: String = "",
    val family: String = "",
    val nickname: String = "",
    val company: String = "",
    /** « AAAA-MM-JJ » ou « --MM-JJ » (sans l'année), comme Android l'enregistre. */
    val birthday: String = "",
    val note: String = "",
    val phones: List<Labeled> = listOf(Labeled()),
    val emails: List<Labeled> = emptyList(),
) {
    val isEmpty: Boolean
        get() = given.isBlank() && family.isBlank() && company.isBlank() &&
            phones.all { it.value.isBlank() } && emails.all { it.value.isBlank() }
}

fun ContactDetail.toForm(): ContactForm = ContactForm(
    given = name?.given ?: displayName,
    family = name?.family.orEmpty(),
    nickname = nickname?.value.orEmpty(),
    company = organization?.company.orEmpty(),
    birthday = birthday?.value.orEmpty(),
    note = note?.value.orEmpty(),
    phones = phones.ifEmpty { listOf(Labeled()) },
    emails = emails,
)

/** Une modification à faire dans la base des contacts d'Android. */
sealed interface EditOp {
    data class Update(val rowId: Long, val values: Map<String, Any?>) : EditOp
    data class Insert(val mime: String, val values: Map<String, Any?>) : EditOp
    data class Delete(val rowId: Long) : EditOp
}

/**
 * Compare le contact d'origine et le formulaire, et renvoie la liste minimale des modifications.
 * [original] = null : nouveau contact (tout est à insérer).
 */
fun planEdit(original: ContactDetail?, form: ContactForm): List<EditOp> {
    val ops = mutableListOf<EditOp>()

    // Nom : on met à jour prénom et nom, et le nom affiché (en gardant civilité, 2ᵉ prénom…).
    val given = form.given.trim()
    val family = form.family.trim()
    val nameRow = original?.name
    if (nameRow != null) {
        if (given != nameRow.given || family != nameRow.family) {
            val display = listOf(nameRow.prefix, given, nameRow.middle, family, nameRow.suffix)
                .filter { it.isNotBlank() }.joinToString(" ")
            ops += EditOp.Update(
                nameRow.rowId,
                mapOf(StructuredName.GIVEN_NAME to given, StructuredName.FAMILY_NAME to family, StructuredName.DISPLAY_NAME to display),
            )
        }
    } else if (given.isNotEmpty() || family.isNotEmpty()) {
        ops += EditOp.Insert(
            StructuredName.CONTENT_ITEM_TYPE,
            mapOf(
                StructuredName.GIVEN_NAME to given,
                StructuredName.FAMILY_NAME to family,
                StructuredName.DISPLAY_NAME to listOf(given, family).filter { it.isNotEmpty() }.joinToString(" "),
            ),
        )
    }

    ops += single(original?.nickname, form.nickname, Nickname.CONTENT_ITEM_TYPE, Nickname.NAME)
    ops += single(original?.note, form.note, Note.CONTENT_ITEM_TYPE, Note.NOTE)
    ops += single(
        original?.birthday, form.birthday, Event.CONTENT_ITEM_TYPE, Event.START_DATE,
        insertExtras = mapOf(Event.TYPE to Event.TYPE_BIRTHDAY),
    )

    // Société : si un poste est enregistré, on garde la ligne (on vide seulement la société).
    val org = original?.organization
    val company = form.company.trim()
    when {
        org == null && company.isNotEmpty() ->
            ops += EditOp.Insert(Organization.CONTENT_ITEM_TYPE, mapOf(Organization.COMPANY to company))
        org != null && company != org.company ->
            ops += if (company.isEmpty() && org.title.isBlank()) EditOp.Delete(org.rowId)
            else EditOp.Update(org.rowId, mapOf(Organization.COMPANY to company.ifEmpty { null }))
    }

    ops += list(original?.phones.orEmpty(), form.phones, Phone.CONTENT_ITEM_TYPE)
    ops += list(original?.emails.orEmpty(), form.emails, Email.CONTENT_ITEM_TYPE)
    return ops
}

private fun single(
    row: SingleRow?,
    newValue: String,
    mime: String,
    column: String,
    insertExtras: Map<String, Any?> = emptyMap(),
): List<EditOp> {
    val v = newValue.trim()
    return when {
        row == null && v.isNotEmpty() -> listOf(EditOp.Insert(mime, mapOf(column to v) + insertExtras))
        row != null && v.isEmpty() -> listOf(EditOp.Delete(row.rowId))
        row != null && v != row.value -> listOf(EditOp.Update(row.rowId, mapOf(column to v)))
        else -> emptyList()
    }
}

private fun list(original: List<Labeled>, edited: List<Labeled>, mime: String): List<EditOp> {
    val ops = mutableListOf<EditOp>()
    // « data1 / data2 / data3 » = valeur, type, libellé, pour les numéros comme pour les e-mails.
    fun values(l: Labeled) = mapOf("data1" to l.value.trim(), "data2" to l.type, "data3" to l.label)
    original.forEach { old ->
        val new = edited.firstOrNull { it.rowId == old.rowId }
        when {
            new == null || new.value.isBlank() -> ops += EditOp.Delete(old.rowId ?: return@forEach)
            new.value.trim() != old.value || new.type != old.type || new.label != old.label ->
                ops += EditOp.Update(old.rowId ?: return@forEach, values(new))
        }
    }
    edited.filter { it.rowId == null && it.value.isNotBlank() }.forEach { ops += EditOp.Insert(mime, values(it)) }
    return ops
}

/** Les types proposés dans le formulaire (le type « personnalisé » d'un contact existant est gardé). */
val PhoneTypes = listOf(Phone.TYPE_MOBILE to "Mobile", Phone.TYPE_HOME to "Domicile", Phone.TYPE_WORK to "Travail", Phone.TYPE_OTHER to "Autre")
val EmailTypes = listOf(Email.TYPE_HOME to "Perso", Email.TYPE_WORK to "Travail", Email.TYPE_OTHER to "Autre")

/** Le libellé affiché d'un numéro. */
fun phoneLabel(l: Labeled): String = when {
    l.type == Phone.TYPE_CUSTOM && !l.label.isNullOrBlank() -> l.label
    else -> PhoneTypes.firstOrNull { it.first == l.type }?.second ?: when (l.type) {
        Phone.TYPE_FAX_WORK, Phone.TYPE_FAX_HOME -> "Fax"
        Phone.TYPE_MAIN -> "Principal"
        Phone.TYPE_WORK_MOBILE -> "Mobile pro"
        Phone.TYPE_PAGER -> "Bip"
        else -> "Autre"
    }
}

fun emailLabel(l: Labeled): String = when {
    l.type == Email.TYPE_CUSTOM && !l.label.isNullOrBlank() -> l.label
    else -> EmailTypes.firstOrNull { it.first == l.type }?.second ?: "Autre"
}

/** « 1990-05-14 » → « 14 mai 1990 », « --05-14 » → « 14 mai ». */
fun formatBirthday(raw: String): String {
    val months = listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")
    val m = Regex("^(\\d{4}|-)-?(\\d{2})-(\\d{2})").find(raw.trim()) ?: return raw
    val (y, mo, d) = m.destructured
    val month = months.getOrNull(mo.toInt() - 1) ?: return raw
    val day = d.toInt().let { if (it == 1) "1er" else it.toString() }
    return if (y.length == 4) "$day $month $y" else "$day $month"
}

/** L'inverse, pour pré-remplir le champ : « 1990-05-14 » → « 14/05/1990 ». */
fun birthdayInput(raw: String): String {
    val m = Regex("^(\\d{4}|-)-?(\\d{2})-(\\d{2})").find(raw.trim()) ?: return raw
    val (y, mo, d) = m.destructured
    return if (y.length == 4) "$d/$mo/$y" else "$d/$mo"
}
