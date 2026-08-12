package com.personal.ircclient.core.security

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Custom Alphabet-only encoder to avoid IRC server filters that block numbers or special characters.
 * Uses a Base32-like approach with only letters.
 */
object AlphaEncoder {
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEF" // 32 letters

    fun encode(data: ByteArray): String {
        val result = StringBuilder()
        var bitBuffer = 0
        var bitCount = 0
        for (b in data) {
            bitBuffer = (bitBuffer shl 8) or (b.toInt() and 0xFF)
            bitCount += 8
            while (bitCount >= 5) {
                result.append(ALPHABET[(bitBuffer shr (bitCount - 5)) and 0x1F])
                bitCount -= 5
            }
        }
        if (bitCount > 0) {
            result.append(ALPHABET[(bitBuffer shl (5 - bitCount)) and 0x1F])
        }
        return result.toString()
    }

    fun decode(s: String): ByteArray {
        val cleanS = s.trim().filter { it in ALPHABET }
        if (cleanS.isEmpty()) return ByteArray(0)
        
        val result = mutableListOf<Byte>()
        var bitBuffer = 0
        var bitCount = 0
        for (c in cleanS) {
            val value = ALPHABET.indexOf(c)
            bitBuffer = (bitBuffer shl 5) or value
            bitCount += 5
            if (bitCount >= 8) {
                result.add(((bitBuffer shr (bitCount - 8)) and 0xFF).toByte())
                bitCount -= 8
            }
        }
        return result.toByteArray()
    }
}

/**
 * Enhanced Encryption Manager for FenixIRC.
 * Uses AES-256-CBC with random IV for each message.
 * Encodes output using only alphabetical characters to bypass server filters.
 */
object EncryptionManager {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val IV_SIZE = 16
    private const val KEY_SIZE = 32 // 256 bits

    fun generateRandomKey(): String {
        val random = SecureRandom()
        val bytes = ByteArray(16) // 128 bits of entropy
        random.nextBytes(bytes)
        return AlphaEncoder.encode(bytes)
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
            
            AlphaEncoder.encode(combined)
        } catch (e: Exception) {
            e.printStackTrace()
            text
        }
    }

    fun decrypt(encryptedText: String, key: String): String {
        return try {
            val combined = AlphaEncoder.decode(encryptedText.trim())
            if (combined.isEmpty() || combined.size < IV_SIZE) return encryptedText

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
            android.util.Log.e("EncryptionManager", "Decryption error: ${e.message}")
            encryptedText
        }
    }
}
