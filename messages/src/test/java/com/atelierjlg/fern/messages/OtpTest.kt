package com.atelierjlg.fern.messages

import com.atelierjlg.fern.messages.data.Otp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpTest {
    @Test
    fun codesReconnus() {
        assertEquals("482913", Otp.find("Votre code de vérification est 482913. Ne le partagez pas."))
        assertEquals("123456", Otp.find("G-123456 est votre code de validation Google."))
        assertEquals("4829", Otp.find("Code PIN : 4829"))
        assertEquals("123456", Otp.find("Your verification code: 123 456"))
        assertEquals("98765432", Otp.find("Ameli : votre code de connexion est 98765432"))
    }

    @Test
    fun pasDeFauxPositifs() {
        assertNull(Otp.find("On se voit à 18h30 ?"))
        assertNull(Otp.find("Rappelle-moi au 06 12 34 56 78"))
        assertNull(Otp.find("Le code de la porte, je te le dis demain"))
        assertNull(Otp.find("Ton colis 123456 arrive demain"))
        assertNull(Otp.find("Paiement de 1250,00 € confirmé"))
    }
}
