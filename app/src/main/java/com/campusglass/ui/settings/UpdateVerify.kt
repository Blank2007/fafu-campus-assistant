package com.campusglass.ui.settings

import android.util.Base64
import java.io.File

/**
 * B-2（审查 v4.10）：更新包 Ed25519 验签——把信任根锚定在 APK 内部。
 * 发布流程：离线私钥（不在仓库内）对 APK 签名，生成同名 .sig 资产随 Release 发布；
 * 应用下载 APK 与 .sig 后验签，不通过则拒绝安装。
 *
 * 注：私钥由发布方离线保管；泄露私钥 = 可伪造更新，请妥善保管。
 *
 * ---------------------------------------------------------------------------
 * 密钥轮换（务必按此顺序，否则老用户会失去「直接下载安装」能力）
 * ---------------------------------------------------------------------------
 * 老用户设备上内置的是**他们那一版发布时的公钥**，因此：
 *   1. 换密钥时，过渡版本**必须仍用旧私钥签名**发布——否则老用户验签不过，
 *      只能退化成「浏览器下载」（我们面向的多是不懂这些的同学，尽量别让他们走这一步）。
 *   2. 该过渡版本同时把**新公钥**追加进下面的 KEYS_B64 列表，于是它新旧都能验。
 *   3. 从再下一版起，就可以改用新私钥签名了（此时用户已装上"双密钥"版本）。
 *
 * 列表**只增不减**，且**首项永远是当前发布私钥对应的公钥**；
 * 删除任何一项，都会让仍内置该公钥的历史版本用户失去直接安装能力。
 * ---------------------------------------------------------------------------
 */
object UpdateVerify {

    /**
     * 可接受的 Ed25519 公钥列表（SPKI DER 的 Base64）。
     * 首项 = 当前发布私钥对应公钥（与 .handover-secrets/update_signing.pub 一致）。
     */
    private val KEYS_B64: List<String> = listOf(
        // 主密钥（v4.11 起启用，当前发布用）
        "MCowBQYDK2VwAyEAf9u2UmXTsjq9rbkkNFQ+Vbjt0kGMJHZwujtWM4f0l/w=",
        // 轮换预留位：如需换密钥，把新公钥追加到这里（详见上方轮换说明）
    )

    /** 任一内置公钥验签通过即视为合法（支持密钥轮换的过渡期） */
    fun verifyEd25519(apk: File, sig: ByteArray): Boolean {
        for (b64 in KEYS_B64) {
            if (verifyWith(b64, apk, sig)) return true
        }
        return false
    }

    private fun verifyWith(pubKeyB64: String, apk: File, sig: ByteArray): Boolean = runCatching {
        val pub = java.security.KeyFactory.getInstance("Ed25519")
            .generatePublic(
                java.security.spec.X509EncodedKeySpec(Base64.decode(pubKeyB64, Base64.DEFAULT))
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
