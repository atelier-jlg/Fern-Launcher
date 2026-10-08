package com.atelierjlg.fern.contact.data

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import com.atelierjlg.fern.common.PhoneNumbers
import com.atelierjlg.fern.common.SearchText

/**
 * Lecture et écriture des contacts d'Android (ContactsContract).
 * Toutes les fonctions sont lentes (base de données) : à appeler hors du fil principal.
 */
class ContactsRepo(private val context: Context) {
    private val resolver get() = context.contentResolver

    /** Tous les contacts, triés A–Z (sans accents : « Élodie » avec les E). */
    fun all(): List<ContactSummary> {
        data class Base(val id: Long, val key: String, val name: String, val starred: Boolean, val photo: String?)
        val bases = mutableListOf<Base>()
        resolver.query(
            Contacts.CONTENT_URI,
            arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.STARRED, Contacts.PHOTO_THUMBNAIL_URI),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(2) ?: continue
                bases += Base(c.getLong(0), c.getString(1) ?: "", name, c.getInt(3) == 1, c.getString(4))
            }
        }
        val phones = HashMap<Long, MutableList<String>>()
        val extras = HashMap<Long, StringBuilder>()
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.CONTACT_ID, Data.MIMETYPE, Data.DATA1),
            "${Data.MIMETYPE} IN (?, ?, ?, ?)",
            arrayOf(Phone.CONTENT_ITEM_TYPE, Nickname.CONTENT_ITEM_TYPE, Organization.CONTENT_ITEM_TYPE, Note.CONTENT_ITEM_TYPE),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val value = c.getString(2) ?: continue
                if (c.getString(1) == Phone.CONTENT_ITEM_TYPE) {
                    val list = phones.getOrPut(id) { mutableListOf() }
                    if (list.none { PhoneNumbers.same(it, value) }) list += value
                } else {
                    extras.getOrPut(id) { StringBuilder() }.append(' ').append(value)
                }
            }
        }
        return bases.map {
            ContactSummary(it.id, it.key, it.name, it.starred, it.photo, phones[it.id].orEmpty(), extras[it.id]?.toString().orEmpty())
        }.sortedBy { SearchText.fold(it.name) }
    }

    /** La fiche complète d'un contact. */
    fun detail(id: Long): ContactDetail? {
        var lookupKey = ""
        var displayName = ""
        var starred = false
        var photo: String? = null
        var ringtone: String? = null
        resolver.query(
            ContentUris.withAppendedId(Contacts.CONTENT_URI, id),
            arrayOf(Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.STARRED, Contacts.PHOTO_URI, Contacts.CUSTOM_RINGTONE),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return null
            lookupKey = c.getString(0) ?: ""
            displayName = c.getString(1) ?: ""
            starred = c.getInt(2) == 1
            photo = c.getString(3)
            ringtone = c.getString(4)
        } ?: return null

        var raw = -1L
        var name: NameRow? = null
        var nickname: SingleRow? = null
        var org: OrgRow? = null
        var birthday: SingleRow? = null
        var note: SingleRow? = null
        val phones = mutableListOf<Labeled>()
        val emails = mutableListOf<Labeled>()
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data._ID, Data.RAW_CONTACT_ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5, Data.DATA6),
            "${Data.CONTACT_ID} = ?", arrayOf(id.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val rowId = c.getLong(0)
                if (raw < 0) raw = c.getLong(1)
                fun s(i: Int) = c.getString(i).orEmpty()
                when (c.getString(2)) {
                    StructuredName.CONTENT_ITEM_TYPE -> if (name == null) {
                        name = NameRow(rowId, given = s(4), family = s(5), prefix = s(6), middle = s(7), suffix = s(8))
                        raw = c.getLong(1)
                    }
                    Nickname.CONTENT_ITEM_TYPE -> if (nickname == null && s(3).isNotBlank()) nickname = SingleRow(rowId, s(3))
                    Organization.CONTENT_ITEM_TYPE -> if (org == null) org = OrgRow(rowId, s(3), title = s(6))
                    Note.CONTENT_ITEM_TYPE -> if (note == null && s(3).isNotBlank()) note = SingleRow(rowId, s(3))
                    Event.CONTENT_ITEM_TYPE -> if (birthday == null && c.getInt(4) == Event.TYPE_BIRTHDAY) birthday = SingleRow(rowId, s(3))
                    Phone.CONTENT_ITEM_TYPE -> phones += Labeled(rowId, s(3), c.getInt(4), c.getString(5))
                    Email.CONTENT_ITEM_TYPE -> emails += Labeled(rowId, s(3), c.getInt(4), c.getString(5))
                }
            }
        }
        if (raw < 0) return null
        return ContactDetail(
            id = id, lookupKey = lookupKey, displayName = displayName, starred = starred, photoUri = photo,
            customRingtone = ringtone, rawContactId = raw, name = name, nickname = nickname, organization = org,
            birthday = birthday, note = note, phones = phones, emails = emails,
        )
    }

    /**
     * Enregistre le formulaire. [original] = null : nouveau contact (rangé dans le téléphone).
     * Renvoie l'identifiant du contact.
     */
    fun save(original: ContactDetail?, form: ContactForm): Long? {
        val ops = ArrayList<ContentProviderOperation>()
        if (original == null) {
            ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, null)
                .withValue(RawContacts.ACCOUNT_NAME, null)
                .build()
        }
        planEdit(original, form).forEach { op ->
            ops += when (op) {
                is EditOp.Delete -> ContentProviderOperation.newDelete(ContentUris.withAppendedId(Data.CONTENT_URI, op.rowId)).build()
                is EditOp.Update -> ContentProviderOperation.newUpdate(ContentUris.withAppendedId(Data.CONTENT_URI, op.rowId))
                    .withValues(op.values.toContentValues()).build()
                is EditOp.Insert -> ContentProviderOperation.newInsert(Data.CONTENT_URI).apply {
                    if (original == null) withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    else withValue(Data.RAW_CONTACT_ID, original.rawContactId)
                    withValue(Data.MIMETYPE, op.mime)
                    withValues(op.values.toContentValues())
                }.build()
            }
        }
        if (ops.isEmpty()) return original?.id
        val results = resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        if (original != null) return original.id
        val rawId = results.firstOrNull()?.uri?.let { ContentUris.parseId(it) } ?: return null
        return contactIdOfRaw(rawId)
    }

    private fun contactIdOfRaw(rawId: Long): Long? =
        resolver.query(ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId), arrayOf(RawContacts.CONTACT_ID), null, null, null)
            ?.use { if (it.moveToFirst()) it.getLong(0) else null }

    /** Photo de profil (JPEG déjà réduit). */
    fun setPhoto(rawContactId: Long, jpeg: ByteArray?) {
        val existing = resolver.query(
            Data.CONTENT_URI, arrayOf(Data._ID),
            "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?", arrayOf(rawContactId.toString(), Photo.CONTENT_ITEM_TYPE), null,
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }
        when {
            jpeg == null && existing != null -> resolver.delete(ContentUris.withAppendedId(Data.CONTENT_URI, existing), null, null)
            jpeg != null && existing != null -> resolver.update(
                ContentUris.withAppendedId(Data.CONTENT_URI, existing), ContentValues().apply { put(Photo.PHOTO, jpeg) }, null, null,
            )
            jpeg != null -> resolver.insert(
                Data.CONTENT_URI,
                ContentValues().apply {
                    put(Data.RAW_CONTACT_ID, rawContactId)
                    put(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                    put(Photo.PHOTO, jpeg)
                },
            )
        }
    }

    /** Favori : on ne touche qu'à l'étoile. */
    fun setStarred(id: Long, starred: Boolean) {
        resolver.update(
            ContentUris.withAppendedId(Contacts.CONTENT_URI, id),
            ContentValues().apply { put(Contacts.STARRED, if (starred) 1 else 0) }, null, null,
        )
    }

    fun delete(detail: ContactDetail) {
        resolver.delete(Contacts.getLookupUri(detail.id, detail.lookupKey) ?: return, null, null)
    }

    /** Qui appelle ? Nom et photo à partir du numéro (null si inconnu). */
    fun lookup(number: String): Triple<Long, String, String?>? {
        if (number.isBlank()) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return runCatching {
            resolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_URI),
                null, null, null,
            )?.use { c -> if (c.moveToFirst()) Triple(c.getLong(0), c.getString(1) ?: number, c.getString(2)) else null }
        }.getOrNull()
    }
}

private fun Map<String, Any?>.toContentValues() = ContentValues().also { cv ->
    forEach { (k, v) ->
        when (v) {
            null -> cv.putNull(k)
            is String -> cv.put(k, v)
            is Int -> cv.put(k, v)
            is Long -> cv.put(k, v)
            is ByteArray -> cv.put(k, v)
            else -> cv.put(k, v.toString())
        }
    }
}
