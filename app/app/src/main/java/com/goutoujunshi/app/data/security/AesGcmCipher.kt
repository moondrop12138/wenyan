package com.goutoujunshi.app.data.security

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM 加解密核心（纯 JCE，无 Android Keystore 依赖，可 JVM 单测）
 *
 * 密文格式：Base64(IV(12B) || ciphertext)，密钥由外部 SecretKeyProvider 提供。
 * Base64 用 java.util（minSdk 26 起平台内置，与 JVM 单测一致）。
 */
open class AesGcmCipher(private val keyProvider: SecretKeyProvider) {

    interface SecretKeyProvider {
        fun getOrCreate(): SecretKey
    }

    /**
     * 加密明文，返回 Base64(IV || ciphertext)
     */
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getOrCreate())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encrypted.size)
        iv.copyInto(combined, 0)
        encrypted.copyInto(combined, iv.size)
        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * 解密 Base64(IV || ciphertext) 原文
     * @throws Exception 密文损坏、IV 不足或密钥不匹配
     */
    fun decrypt(encryptedBase64: String): String {
        val combined = Base64.getDecoder().decode(encryptedBase64)
        require(combined.size > IV_SIZE_BYTES) { "ciphertext too short" }
        val iv = combined.copyOfRange(0, IV_SIZE_BYTES)
        val ciphertext = combined.copyOfRange(IV_SIZE_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider.getOrCreate(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE_BYTES = 12
        private const val TAG_BITS = 128
    }
}
