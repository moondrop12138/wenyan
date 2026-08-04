package com.wenyan.app.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Keystore AES-GCM 加密器（AC-11：API Key 密文存储）
 *
 * 密钥存于 Android Keystore，不出设备（minSdk 26 支持 KeyGenParameterSpec）。
 * 加解密核心逻辑在 AesGcmCipher（纯 JCE，可单测）。
 */
class KeystoreAesGcmCipher : AesGcmCipher(KeystoreKeyProvider()) {

    private class KeystoreKeyProvider : AesGcmCipher.SecretKeyProvider {
        private val keyStore: KeyStore =
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        override fun getOrCreate(): SecretKey {
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            return generator.generateKey()
        }

        private companion object {
            const val ANDROID_KEYSTORE = "AndroidKeyStore"
            const val KEY_ALIAS = "wenyan_api_key"
        }
    }
}
