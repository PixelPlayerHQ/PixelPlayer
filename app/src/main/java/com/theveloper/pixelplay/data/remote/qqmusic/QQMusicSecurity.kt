package com.theveloper.pixelplay.data.remote.qqmusic

import android.util.Base64
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * QQ Music `musics.fcg` Crypto Utility.
 * Implements AES-128-GCM encryption (ae) and cyclic XOR decryption (se).
 */
object QQMusicSecurity {

    private const val AES_KEY_HEX = "bd035870d5afa4133454af0836f5e1cf"
    private val AES_KEY_BYTES = AES_KEY_HEX.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    // 21-byte XOR Key for se()
    private val XOR_KEY = byteArrayOf(
        0x8a.toByte(), 0x45.toByte(), 0x9f.toByte(), 0x15.toByte(), 0x58.toByte(), 
        0x33.toByte(), 0x22.toByte(), 0x0f.toByte(), 0x1f.toByte(), 0x4f.toByte(), 
        0x81.toByte(), 0x85.toByte(), 0x97.toByte(), 0x33.toByte(), 0x59.toByte(), 
        0x90.toByte(), 0x00.toByte(), 0x2f.toByte(), 0x3f.toByte(), 0x4b.toByte(), 
        0x88.toByte()
    )

    /**
     * Encrypts the JSON payload using AES-128-GCM (ae).
     * Format: Base64(Nonce[12] + Ciphertext + Tag[16])
     */
    fun encryptRequest(plaintext: String): String {
        val random = SecureRandom()
        val nonce = ByteArray(12)
        random.nextBytes(nonce)

        val secretKey = SecretKeySpec(AES_KEY_BYTES, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val ciphertextWithTag = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val buffer = ByteBuffer.allocate(nonce.size + ciphertextWithTag.size)
        buffer.put(nonce)
        buffer.put(ciphertextWithTag)

        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }

    /**
     * Decrypts the binary response using cyclic XOR (se).
     */
    fun decryptResponse(encryptedData: ByteArray): String {
        val decrypted = ByteArray(encryptedData.size)
        for (i in encryptedData.indices) {
            decrypted[i] = (encryptedData[i].toInt() xor XOR_KEY[i % 21].toInt()).toByte()
        }
        return String(decrypted, Charsets.UTF_8)
    }
}
