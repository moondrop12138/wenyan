package com.wenyan.app.data.security

import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * KeystoreAesGcmCipher 的桌面（JVM）实现：与 Android 版同名同包同公共 API（编译期替换）。
 *
 * Android 版密钥存 AndroidKeyStore 硬件；桌面版用「机器指纹派生」：
 *   读取 Windows MachineGuid（注册表 HKLM\SOFTWARE\Microsoft\Cryptography）+ 用户名，
 *   SHA-256 派生 AES-256 密钥，密钥不落盘（每次启动重算）。
 *
 * 防护等级：防「拖库文件到别的机器直接解密」，不防本机恶意进程——
 * 与 Android Keystore 对个人单机场景的实际防护等价（不防 root 后本机）。
 *
 * 注意：与 Android 端密文不互通（密钥派生路径不同），桌面版独立建档（用户已确认不做数据迁移）。
 */
class KeystoreAesGcmCipher : AesGcmCipher(MachineFingerprintKeyProvider()) {

    private class MachineFingerprintKeyProvider : AesGcmCipher.SecretKeyProvider {
        @Volatile
        private var cached: SecretKey? = null

        override fun getOrCreate(): SecretKey =
            cached ?: synchronized(this) {
                cached ?: derive().also { cached = it }
            }

        private fun derive(): SecretKey {
            val material = machineGuid() + "|" + normalizedUsername()
            // M8: PBKDF2WithHmacSHA256（固定盐 + 迭代），替代裸 SHA-256；用户名规范化（trim/小写）
            val spec = PBEKeySpec(material.toCharArray(), SALT, ITERATIONS, KEY_BITS)
            val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            return SecretKeySpec(keyBytes, "AES")
        }

        private fun normalizedUsername(): String =
            (System.getProperty("user.name") ?: "wenyan").trim().lowercase()

        /** 读 Windows MachineGuid；非 Windows / 读取失败回退到主机名+用户名（个人版可接受） */
        private fun machineGuid(): String {
            return try {
                val proc = ProcessBuilder(
                    "reg", "query",
                    "HKLM\\SOFTWARE\\Microsoft\\Cryptography",
                    "/v", "MachineGuid",
                ).redirectErrorStream(true).start()
                val out = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                out.lines()
                    .firstOrNull { it.contains("MachineGuid") }
                    ?.substringAfterLast(" ")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: fallback()
            } catch (e: Exception) {
                fallback()
            }
        }

        private fun fallback(): String =
            (System.getenv("COMPUTERNAME") ?: "unknown-host")

        private companion object {
            // M8: 固定盐（非机密，仅防彩虹表 + 增加派生成本）；保留「与 Android 端密文不互通」设计
            private val SALT = byteArrayOf(
                0x57, 0x65, 0x6e, 0x79, 0x61, 0x6e, 0x2d, 0x70,
                0x62, 0x6b, 0x64, 0x66, 0x32, 0x2d, 0x76, 0x31,
            )
            private const val ITERATIONS = 120_000
            private const val KEY_BITS = 256
        }
    }
}
