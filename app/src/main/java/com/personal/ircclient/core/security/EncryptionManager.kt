package com.personal.ircclient.core.security

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Enhanced Encryption Manager for FenixIRC.
 * Uses AES-256-CBC with random IV for each message.
 */
object EncryptionManager {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val IV_SIZE = 16
    private const val KEY_SIZE = 32 // 256 bits

    fun generateRandomKey(): String {
        val random = SecureRandom()
        val bytes = ByteArray(16) // 128 bits of entropy for the shared secret
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun deriveKey(password: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = password.toByteArray(Charsets.UTF_8)
        val keyBytes = digest.digest(bytes)
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(text: String, key: String): String {
        return try {
            val secretKey = deriveKey(key)
            val cipher = Cipher.getInstance(ALGORITHM)
            
            val random = SecureRandom()
            val ivBytes = ByteArray(IV_SIZE)
            random.nextBytes(ivBytes)
            val iv = IvParameterSpec(ivBytes)
            
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv)
            
            val encrypted = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
            
            // Format: IV + ENCRYPTED_DATA
            val combined = ByteArray(ivBytes.size + encrypted.size)
            System.arraycopy(ivBytes, 0, combined, 0, ivBytes.size)
            System.arraycopy(encrypted, 0, combined, ivBytes.size, encrypted.size)
            
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            text
        }
    }

    fun decrypt(encryptedText: String, key: String): String {
        return try {
            val combined = Base64.decode(encryptedText, Base64.DEFAULT)
            if (combined.size < IV_SIZE) return encryptedText

            val ivBytes = ByteArray(IV_SIZE)
            System.arraycopy(combined, 0, ivBytes, 0, IV_SIZE)
            val iv = IvParameterSpec(ivBytes)
            
            val encryptedBytes = ByteArray(combined.size - IV_SIZE)
            System.arraycopy(combined, IV_SIZE, encryptedBytes, 0, encryptedBytes.size)
            
            val secretKey = deriveKey(key)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)
            
            val decrypted = cipher.doFinal(encryptedBytes)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            // If decryption fails, it might be an unencrypted legacy message
            encryptedText
        }
    }
}
