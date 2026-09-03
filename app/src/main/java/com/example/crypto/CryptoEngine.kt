package com.example.crypto

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedResult(
    val ciphertext: String,
    val iv: String,
    val salt: String,
    val checksum: String
)

object CryptoEngine {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATION_COUNT = 65536
    private const val KEY_LENGTH = 256
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12 // Standard 12 bytes for GCM
    private const val SALT_LENGTH = 16

    // Default master seed for peer-to-peer session keys
    private const val DEFAULT_SECRET_SEED = "Secr3t_99+99_VaultKey_E2EE"

    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun generateIv(): ByteArray {
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)
        return iv
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plainText: String, secretKeySeed: String = DEFAULT_SECRET_SEED): EncryptedResult {
        val salt = generateSalt()
        val iv = generateIv()
        val key = deriveKey(secretKeySeed, salt)

        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val ciphertext = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        val ivStr = Base64.encodeToString(iv, Base64.NO_WRAP)
        val saltStr = Base64.encodeToString(salt, Base64.NO_WRAP)
        val checksum = calculateChecksum(plainText)

        return EncryptedResult(
            ciphertext = ciphertext,
            iv = ivStr,
            salt = saltStr,
            checksum = checksum
        )
    }

    fun decrypt(ciphertext: String, ivStr: String, saltStr: String, secretKeySeed: String = DEFAULT_SECRET_SEED): String {
        return try {
            val salt = Base64.decode(saltStr, Base64.NO_WRAP)
            val iv = Base64.decode(ivStr, Base64.NO_WRAP)
            val cipherBytes = Base64.decode(ciphertext, Base64.NO_WRAP)
            val key = deriveKey(secretKeySeed, salt)

            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            "[Enkripsi Terproteksi / Gagal Dekripsi: ${e.localizedMessage ?: "Invalid Key"}]"
        }
    }

    fun encryptBytes(data: ByteArray, secretKeySeed: String = DEFAULT_SECRET_SEED): EncryptedResult {
        val salt = generateSalt()
        val iv = generateIv()
        val key = deriveKey(secretKeySeed, salt)

        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

        val cipherBytes = cipher.doFinal(data)
        val ciphertext = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        val ivStr = Base64.encodeToString(iv, Base64.NO_WRAP)
        val saltStr = Base64.encodeToString(salt, Base64.NO_WRAP)
        val checksum = calculateSha256(data)

        return EncryptedResult(
            ciphertext = ciphertext,
            iv = ivStr,
            salt = saltStr,
            checksum = checksum
        )
    }

    fun decryptBytes(ciphertext: String, ivStr: String, saltStr: String, secretKeySeed: String = DEFAULT_SECRET_SEED): ByteArray? {
        return try {
            val salt = Base64.decode(saltStr, Base64.NO_WRAP)
            val iv = Base64.decode(ivStr, Base64.NO_WRAP)
            val cipherBytes = Base64.decode(ciphertext, Base64.NO_WRAP)
            val key = deriveKey(secretKeySeed, salt)

            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

            cipher.doFinal(cipherBytes)
        } catch (e: Exception) {
            null
        }
    }

    fun calculateChecksum(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun calculateSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }.take(16)
    }

    fun generateFingerprint(id: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(id.toByteArray(Charsets.UTF_8))
        val hex = hash.joinToString("") { "%02X".format(it) }
        return hex.chunked(4).take(8).joinToString(":")
    }
}
