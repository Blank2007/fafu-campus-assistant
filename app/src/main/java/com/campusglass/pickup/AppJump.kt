package com.campusglass.pickup

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.content.Intent
import android.widget.Toast

/**
 * 拼多多 / 支付宝·菜鸟 取件码快捷跳转。
 *
 * 设计目标：不是手输取件码，而是一键拉起“能看取件码”的页面。
 *
 * 来源（公开逆向资料 / 社区实测，详见“关于”页）：
 *  - 拼多多取件看【身份码】（多多驿站扫码出库用，K 开头取件码配合身份码亮码），
 *    入口：个人中心→多多买菜→自提服务→我的身份码；驿站现场可扫墙上二维码直达身份码页。
 *    scheme：pinduoduo://com.xunmeng.pinduoduo/scan.html（扫一扫）、index.html?...（个人中心）
 *  - 支付宝·菜鸟小程序：alipays://platformapi/startapp?appId=2021001141626787（打开即取件码，
 *    V2EX 讨论实测可用；按需求不拉起菜鸟 App，走支付宝）
 */
object AppJump {

    const val PDD_PKG = "com.xunmeng.pinduoduo"
    const val ALIPAY_PKG = "com.eg.android.AlipayGphone"

    /** 拼多多 · 扫一扫（扫驿站二维码 → 直达身份码/亮码流程，php.cn 实测路径） */
    const val PDD_SCAN = "pinduoduo://com.xunmeng.pinduoduo/scan.html"
    /** 拼多多 · 个人中心（多多买菜 → 自提服务 → 我的身份码） */
    const val PDD_PERSONAL = "pinduoduo://com.xunmeng.pinduoduo/index.html?index=4&pr_tab_link=personal.html"
    const val PDD_HOME = "pinduoduo://open.homepage"

    /** 支付宝 · 菜鸟小程序（打开即取件码） */
    const val ALIPAY_CAINIAO = "alipays://platformapi/startapp?appId=2021001141626787"

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        true
    } catch (_: Exception) {
        false
    }

    /** 拼多多 · 扫一扫直达身份码流程（驿站现场扫墙上二维码最短路径） */
    fun openPddScan(context: Context) = jumpChain(
        context,
        listOf(PDD_SCAN, PDD_PERSONAL, PDD_HOME),
        PDD_PKG,
    )

    /** 拼多多 · 个人中心（进“多多买菜→自提服务→我的身份码”） */
    fun openPddPersonal(context: Context) = jumpChain(
        context,
        listOf(PDD_PERSONAL, PDD_HOME),
        PDD_PKG,
    )

    /** 拼多多 · 首页 */
    fun openPddHome(context: Context) = jumpChain(context, listOf("pinduoduo://", PDD_HOME), PDD_PKG)

    /** 直接跳指定 URI（嗅探出的真实路由用） */
    fun openUri(context: Context, uri: String, pkg: String) = jumpChain(context, listOf(uri), pkg)

    /** 支付宝 · 菜鸟取件码（失败回退：支付宝首页 → 应用市场） */
    fun openAlipayCainiao(context: Context) = jumpChain(
        context,
        listOf(ALIPAY_CAINIAO, "alipays://platformapi/startapp?appId=20000067"),
        ALIPAY_PKG,
    )

    private fun jumpChain(context: Context, uris: List<String>, pkg: String) {
        for (uri in uris) {
            // 1) 指定包名的 ACTION_VIEW（最精准）
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                        .setPackage(pkg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }
            // 2) 不限包名（部分 ROM 对 scheme 解析更宽松）
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }
        }
        // 3) 按包名拉起首页
        runCatching {
            context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
        }
        // 4) 应用市场兜底
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            Toast.makeText(context, "未安装该应用，且无法打开应用市场", Toast.LENGTH_SHORT).show()
        }
    }
}
