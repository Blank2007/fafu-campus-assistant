package com.campusglass.schedule

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 教务账号的本地保存（Android Keystore AES/GCM，密钥不可导出）。
 *
 * 为什么不用 androidx.security:security-crypto：那套库已不推荐（EncryptedSharedPreferences 弃用），
 * 而且多一个依赖；这里直接调 Keystore，minSdk 31 完全可用。
 *
 * 安全边界（写在明面上）：
 *  - 密码只在用户主动勾选「记住」时保存；不勾选则连账号都不落盘。
 *  - 密钥在系统 Keystore 里，root 后的设备仍可能被读；本 App 不上传任何账号到服务器，
 *    教务请求只发给学校域名（明文 http，见 network_security_config.xml 的放行说明）。
 */
object ZfCredentials {

    private const val PREFS = "zf_soft_account"
    private const val KEY_USER = "user_enc"
    private const val KEY_PASS = "pass_enc"
    private const val KEY_SITE = "site"
    private const val KEY_ALIAS = "campusglass_zf_account_v1"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    data class Account(val studentId: String, val password: String, val siteIndex: Int)

    fun save(context: Context, studentId: String, password: String, siteIndex: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val e1 = encrypt(studentId)
        val e2 = encrypt(password)
        if (e1 == null || e2 == null) {
            clear(context)   // Keystore 不可用（极少数机型）：宁可不存，也不要明文落盘
            return
        }
        prefs.edit()
            .putString(KEY_USER, e1)
            .putString(KEY_PASS, e2)
            .putInt(KEY_SITE, siteIndex)
            .apply()
    }

    fun load(context: Context): Account? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val u = prefs.getString(KEY_USER, null) ?: return null
        val p = prefs.getString(KEY_PASS, null) ?: return null
        val user = decrypt(u) ?: return null
        val pass = decrypt(p) ?: return null
        if (user.isBlank() || pass.isBlank()) return null
        return Account(user, pass, prefs.getInt(KEY_SITE, 0))
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    // ---------------- Keystore ----------------

    private fun secretKey(): SecretKey? = runCatching {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build(),
                )
                generateKey()
            }
    }.getOrNull()

    /** iv(12B) + 密文 → Base64 */
    private fun encrypt(plain: String): String? = runCatching {
        val key = secretKey() ?: return null
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }.getOrNull()

    private fun decrypt(encoded: String): String? = runCatching {
        val key = secretKey() ?: return null
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        if (raw.size <= IV_LEN) return null
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, raw, 0, IV_LEN))
        String(cipher.doFinal(raw, IV_LEN, raw.size - IV_LEN), Charsets.UTF_8)
    }.getOrNull()
}
