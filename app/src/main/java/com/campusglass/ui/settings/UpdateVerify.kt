package com.campusglass.ui.settings

import android.util.Base64
import java.io.File

/**
 * B-2（审查 v4.10）：更新包 Ed25519 验签——把信任根锚定在 APK 内部。
 * 发布流程：离线私钥（不在仓库内）对 APK 签名，生成同名 .sig 资产随 Release 发布；
 * 应用下载 APK 与 .sig 后验签，不通过则拒绝安装。
 *
 * 注：私钥由发布方离线保管；泄露私钥 = 可伪造更新，请妥善保管。
 */
object UpdateVerify {

    /** Ed25519 公钥（SPKI DER 的 Base64） */
    private const val PUBKEY_B64 = "MCowBQYDK2VwAyEAf9u2UmXTsjq9rbkkNFQ+Vbjt0kGMJHZwujtWM4f0l/w="

    /** 校验 APK 的 Ed25519 签名（sig 为 .sig 文件原始字节） */
    fun verifyEd25519(apk: File, sig: ByteArray): Boolean = runCatching {
        val pub = java.security.KeyFactory.getInstance("Ed25519")
            .generatePublic(
                java.security.spec.X509EncodedKeySpec(Base64.decode(PUBKEY_B64, Base64.DEFAULT))
            )
        val v = java.security.Signature.getInstance("Ed25519")
        v.initVerify(pub)
        apk.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                v.update(buf, 0, n)          // 分块 update，避免大文件整读
            }
        }
        v.verify(sig)
    }.getOrDefault(false)
}
