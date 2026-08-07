package com.personal.ircclient.core.security

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.security.SecureRandom

object EncryptionManager {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"

    fun generateRandomKey(): String {
        val random = SecureRandom()
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun generateKey(password: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = password.toByteArray(Charsets.UTF_8)
        val keyBytes = digest.digest(bytes)
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(text: String, key: String): String {
        return try {
            val secretKey = generateKey(key)
            val cipher = Cipher.getInstance(ALGORITHM)
            val iv = IvParameterSpec(ByteArray(16)) // In a production app, use a random IV and prepend it
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv)
            
            val encrypted = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encrypted, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            text
        }
    }

    fun decrypt(encryptedText: String, key: String): String {
        return try {
            val secretKey = generateKey(key)
            val cipher = Cipher.getInstance(ALGORITHM)
            val iv = IvParameterSpec(ByteArray(16))
            cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)
            
            val decoded = Base64.decode(encryptedText, Base64.DEFAULT)
            val decrypted = cipher.doFinal(decoded)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            // If decryption fails, return the original text (it might not be encrypted)
            encryptedText
        }
    }
}
