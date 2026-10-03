package com.campusglass.pickup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * 拼多多 / 支付宝·菜鸟 取件快捷跳转（精简版：仅保留实际使用的链路）。
 */
object AppJump {

    const val PDD_PKG = "com.xunmeng.pinduoduo"
    const val ALIPAY_PKG = "com.eg.android.AlipayGphone"

    /** 拼多多 · 个人中心（多多买菜 → 自提服务 → 我的身份码） */
    private const val PDD_PERSONAL =
        "pinduoduo://com.xunmeng.pinduoduo/index.html?index=4&pr_tab_link=personal.html"
    private const val PDD_HOME = "pinduoduo://open.homepage"

    /** 拼多多 · 个人中心 */
    fun openPddPersonal(context: Context) = jumpChain(context, listOf(PDD_PERSONAL, PDD_HOME), PDD_PKG)

    /** 拼多多 · 首页 */
    fun openPddHome(context: Context) = jumpChain(context, listOf("pinduoduo://", PDD_HOME), PDD_PKG)

    /** 保底：直接打开 App 首页（跳转失败时用） */
    fun openAppFallback(context: Context, pkg: String) {
        runCatching {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
        }
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            Toast.makeText(context, "未安装该应用，且无法打开应用市场", Toast.LENGTH_SHORT).show()
        }
    }

    private fun jumpChain(context: Context, uris: List<String>, pkg: String) {
        for (uri in uris) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                        .setPackage(pkg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }
        }
        openAppFallback(context, pkg)
    }
}
