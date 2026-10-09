package com.atelierjlg.fern.messages

import com.atelierjlg.fern.messages.mms.MmsPart
import com.atelierjlg.fern.messages.mms.Pdu
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PduTest {
    private val photo = ByteArray(5000) { (it * 7).toByte() }

    @Test
    fun annonceDeMms() {
        val bytes = Pdu.notification("T123", "http://mms.free.fr/abc", "+33612345678", 48_000, 7 * 24 * 3600)
        val n = Pdu.parseNotification(bytes)
        assertNotNull(n)
        assertEquals("T123", n!!.transactionId)
        assertEquals("http://mms.free.fr/abc", n.contentLocation)
        assertEquals("+33612345678", n.from)
        assertEquals(48_000L, n.size)
        assertEquals(7L * 24 * 3600, n.expiryDelta)
        assertNull(n.expiry)
    }

    @Test
    fun envoiPuisRelecture() {
        val text = "Coucou 🌿 c'est l'été à Nantes"
        val parts = listOf(
            MmsPart("application/smil", Pdu.smil("image0.jpg", "text0.txt").toByteArray(), contentId = "smil", contentLocation = "smil.xml"),
            MmsPart("image/jpeg", photo, name = "image0.jpg", contentId = "image0", contentLocation = "image0.jpg"),
            MmsPart("text/plain", text.toByteArray(), contentId = "text0", contentLocation = "text0.txt", charset = 106),
        )
        val bytes = Pdu.sendRequest("tr-42", listOf("06 12 34 56 78", "+33700000000"), parts, subject = "Été")
        val m = Pdu.parseMessage(bytes)!!
        assertEquals(Pdu.SEND_REQ, m.type)
        assertEquals("tr-42", m.transactionId)
        assertEquals(listOf("0612345678", "+33700000000"), m.to)
        assertEquals("Été", m.subject)
        assertEquals(Pdu.MULTIPART_RELATED, m.contentType)
        assertEquals(3, m.parts.size)
        val img = m.parts[1]
        assertEquals("image/jpeg", img.contentType)
        assertEquals("image0", img.contentId)
        assertEquals("image0.jpg", img.contentLocation)
        assertEquals("image0.jpg", img.name)
        assertArrayEquals(photo, img.data)
        assertEquals(text, m.parts[2].text())
        assertEquals("application/smil", m.parts[0].contentType)
    }

    @Test
    fun grossePiece() {
        // Au-delà de 127 octets d'en-têtes / de 16 Ko de données, les longueurs prennent plusieurs octets.
        val big = ByteArray(300_000) { it.toByte() }
        val parts = listOf(MmsPart("image/png", big, name = "x".repeat(200) + ".png", contentId = "img"))
        val m = Pdu.parseMessage(Pdu.sendRequest("t", listOf("3631"), parts))!!
        assertArrayEquals(big, m.parts.single().data)
        assertEquals("x".repeat(200) + ".png", m.parts.single().name)
    }

    @Test
    fun reponses() {
        assertEquals(Pdu.NOTIFYRESP_IND, Pdu.parseMessage(Pdu.notifyResponse("abc"))!!.type)
        assertEquals("abc", Pdu.parseMessage(Pdu.acknowledge("abc"))!!.transactionId)
    }

    @Test
    fun nimporteQuoi() {
        assertNull(Pdu.parseNotification(byteArrayOf(1, 2, 3)))
        assertNull(Pdu.parseNotification(ByteArray(0)))
    }
}
