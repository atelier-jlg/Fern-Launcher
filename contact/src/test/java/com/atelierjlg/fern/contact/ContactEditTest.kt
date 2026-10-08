package com.atelierjlg.fern.contact

import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import com.atelierjlg.fern.contact.data.ContactDetail
import com.atelierjlg.fern.contact.data.EditOp
import com.atelierjlg.fern.contact.data.Labeled
import com.atelierjlg.fern.contact.data.NameRow
import com.atelierjlg.fern.contact.data.OrgRow
import com.atelierjlg.fern.contact.data.SingleRow
import com.atelierjlg.fern.contact.data.formatBirthday
import com.atelierjlg.fern.contact.data.planEdit
import com.atelierjlg.fern.contact.data.toForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactEditTest {
    private val jules = ContactDetail(
        id = 1, lookupKey = "k", displayName = "Dr Jules Martin", rawContactId = 10,
        name = NameRow(100, given = "Jules", family = "Martin", prefix = "Dr"),
        organization = OrgRow(101, company = "VINCI", title = "Alternant"),
        birthday = SingleRow(102, "--05-14"),
        note = SingleRow(103, "Aime les fougères"),
        phones = listOf(
            Labeled(104, "06 12 34 56 78", Phone.TYPE_MOBILE),
            Labeled(105, "01 23 45 67 89", Phone.TYPE_CUSTOM, "Atelier"),
        ),
    )

    @Test
    fun sansModificationRienNEstEcrit() {
        assertEquals(emptyList<EditOp>(), planEdit(jules, jules.toForm()))
    }

    @Test
    fun changerUnNumeroNeToucheQuACetteLigne() {
        val form = jules.toForm().let { f -> f.copy(phones = f.phones.map { if (it.rowId == 104L) it.copy(value = "07 00 00 00 00") else it }) }
        val ops = planEdit(jules, form)
        assertEquals(1, ops.size)
        val op = ops.single() as EditOp.Update
        assertEquals(104L, op.rowId)
        assertEquals("07 00 00 00 00", op.values["data1"])
    }

    @Test
    fun leLibellePersonnaliseEstGarde() {
        val form = jules.toForm().copy(note = "Aime les fougères et le chat")
        val ops = planEdit(jules, form)
        assertTrue(ops.none { it is EditOp.Update && it.rowId == 105L })
    }

    @Test
    fun leNomGardeLaCivilite() {
        val ops = planEdit(jules, jules.toForm().copy(given = "Jul"))
        val op = ops.single() as EditOp.Update
        assertEquals(100L, op.rowId)
        assertEquals("Dr Jul Martin", op.values[StructuredName.DISPLAY_NAME])
    }

    @Test
    fun viderLaSocieteGardeLePoste() {
        val op = planEdit(jules, jules.toForm().copy(company = "")).single() as EditOp.Update
        assertEquals(101L, op.rowId)
        assertEquals(null, op.values[Organization.COMPANY])
    }

    @Test
    fun ajoutEtSuppression() {
        val form = jules.toForm().copy(
            birthday = "",
            phones = listOf(Labeled(104, "06 12 34 56 78", Phone.TYPE_MOBILE), Labeled(null, "3631", Phone.TYPE_OTHER)),
        )
        val ops = planEdit(jules, form)
        assertTrue(EditOp.Delete(102) in ops)
        assertTrue(EditOp.Delete(105) in ops)
        assertTrue(ops.any { it is EditOp.Insert && it.mime == Phone.CONTENT_ITEM_TYPE && it.values["data1"] == "3631" })
        assertEquals(3, ops.size)
    }

    @Test
    fun nouveauContact() {
        val ops = planEdit(null, jules.toForm().copy(given = "Léa", family = "", nickname = "", company = "", birthday = "", note = "",
            phones = listOf(Labeled(null, "0612345678")), emails = emptyList()))
        assertEquals(2, ops.size)
        assertTrue(ops.all { it is EditOp.Insert })
    }

    @Test
    fun anniversaires() {
        assertEquals("14 mai", formatBirthday("--05-14"))
        assertEquals("1er janvier 1990", formatBirthday("1990-01-01"))
        assertEquals("bizarre", formatBirthday("bizarre"))
    }
}

class BirthdayInputTest {
    @Test
    fun saisie() {
        assertEquals("--05-14", com.atelierjlg.fern.contact.data.parseBirthdayInput("14/05"))
        assertEquals("1990-05-04", com.atelierjlg.fern.contact.data.parseBirthdayInput("4/5/1990"))
        assertEquals("", com.atelierjlg.fern.contact.data.parseBirthdayInput(" "))
        assertEquals(null, com.atelierjlg.fern.contact.data.parseBirthdayInput("31/13"))
        assertEquals("14/05/1990", com.atelierjlg.fern.contact.data.birthdayInput("1990-05-14"))
        assertEquals("14/05", com.atelierjlg.fern.contact.data.birthdayInput("--05-14"))
    }
}
