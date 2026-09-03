package com.example

import com.example.crypto.CryptoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testCryptoEngineEncryptionDecryptionRoundtrip() {
        val originalSecretMessage = "Pesan Sangat Rahasia 99+99: Temui saya di brankas terenkripsi!"
        val encrypted = CryptoEngine.encrypt(originalSecretMessage)

        assertNotNull(encrypted.ciphertext)
        assertNotNull(encrypted.iv)
        assertNotNull(encrypted.salt)
        assertNotNull(encrypted.checksum)
        assertNotEquals(originalSecretMessage, encrypted.ciphertext)

        val decrypted = CryptoEngine.decrypt(encrypted.ciphertext, encrypted.iv, encrypted.salt)
        assertEquals(originalSecretMessage, decrypted)
    }

    @Test
    fun testCryptoEngineFingerprint() {
        val fingerprint1 = CryptoEngine.generateFingerprint("alice_e2ee")
        val fingerprint2 = CryptoEngine.generateFingerprint("alice_e2ee")
        assertEquals(fingerprint1, fingerprint2)
        assertTrue(fingerprint1.contains(" "))
    }
}
