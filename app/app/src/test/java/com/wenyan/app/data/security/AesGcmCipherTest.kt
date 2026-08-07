package com.wenyan.app.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * AES-GCM 加解密测试（AC-11）
 * 用 JCE 内存密钥提供者（非 Android Keystore，JVM 可跑）验证核心加解密逻辑；
 * Android Keystore 包装类在 instrumentation 覆盖。
 */
class AesGcmCipherTest {

    private class JceKeyProvider : AesGcmCipher.SecretKeyProvider {
        val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        override fun getOrCreate(): SecretKey = key
    }

    @Test
    fun `encrypt then decrypt roundtrip`() {
        val cipher = AesGcmCipher(JceKeyProvider())
        val plaintext = "sk-1234567890abcdef"
        val encrypted = cipher.encrypt(plaintext)
        assertNotEquals(plaintext, encrypted)
        assertEquals(plaintext, cipher.decrypt(encrypted))
    }

    @Test
    fun `same plaintext produces different ciphertext (random IV)`() {
        val provider = JceKeyProvider()
        val cipher = AesGcmCipher(provider)
        val c1 = cipher.encrypt("secret")
        val c2 = cipher.encrypt("secret")
        assertNotEquals(c1, c2)
        assertEquals("secret", cipher.decrypt(c1))
        assertEquals("secret", cipher.decrypt(c2))
    }

    @Test
    fun `decrypt garbage throws`() {
        val cipher = AesGcmCipher(JceKeyProvider())
        assertThrows(Exception::class.java) { cipher.decrypt("not-valid-base64!!") }
    }

    @Test
    fun `decrypt empty throws`() {
        val cipher = AesGcmCipher(JceKeyProvider())
        assertThrows(Exception::class.java) { cipher.decrypt("") }
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val provider = JceKeyProvider()
        val cipher = AesGcmCipher(provider)
        val encrypted = cipher.encrypt("precious")
        // 翻转最后一位字节 → GCM tag 校验失败
        val bytes = java.util.Base64.getDecoder().decode(encrypted)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        val tampered = java.util.Base64.getEncoder().encodeToString(bytes)
        assertThrows(Exception::class.java) { cipher.decrypt(tampered) }
    }

    @Test
    fun `different key cannot decrypt`() {
        val cipherA = AesGcmCipher(JceKeyProvider())
        val cipherB = AesGcmCipher(JceKeyProvider())
        val encrypted = cipherA.encrypt("secret")
        assertThrows(Exception::class.java) { cipherB.decrypt(encrypted) }
    }

    @Test
    fun `unicode content roundtrip`() {
        val cipher = AesGcmCipher(JceKeyProvider())
        val plaintext = "sk-中文密钥测试-123"
        assertEquals(plaintext, cipher.decrypt(cipher.encrypt(plaintext)))
    }
}
