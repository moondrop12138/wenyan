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

        /** L24: 首次密钥生成的进程内互斥锁 */
        private val keyGenLock = Any()
        private val keyStore: KeyStore =
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        override fun getExisting(): SecretKey? = try {
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            // M7: UnrecoverableKeyException / KeyStoreException 等归一为 null → 解密路径报「密钥不可用」
            null
        }

                override fun getOrCreate(): SecretKey {
            // L24 修复：检查-生成两步无锁——并发生成同 alias 相互覆盖，
            // 先落库的密文永久不可解（ProviderRepository 保存后即坏）。
            // synchronized + 双检：等锁期间他人可能已完成生成。
            synchronized(keyGenLock) {
                getExisting()?.let { return it }
                try {
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
                } catch (e: Exception) {
                    throw AesGcmCipher.KeyUnavailableException(e.message ?: "密钥不可用，请重新输入 API Key")
                }
            }
        }

private companion object {
            const val ANDROID_KEYSTORE = "AndroidKeyStore"
            const val KEY_ALIAS = "wenyan_api_key"
        }
    }
}
