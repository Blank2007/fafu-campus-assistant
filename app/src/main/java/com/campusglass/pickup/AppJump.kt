package com.campusglass.pickup

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
 *
 * v3.3 修复：
 *  - 未安装目标 App 时不再“点了没反应”：明确提示，并说明下一步怎么做；
 *  - 拼多多 scheme 被屏蔽时真正走 Activity 探测兜底（旧版兜底代码从未被调用）；
 *  - isInstalled 在 Android 12/12L 上不再因 PackageInfoFlags(API 33) 崩溃。
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
        val pm = context.packageManager
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0)
        }
        true
    } catch (_: Exception) {
        false
    }

    /** 拼多多 · 扫一扫直达身份码流程（驿站现场扫墙上二维码最短路径） */
    fun openPddScan(context: Context) = openPdd(
        context,
        listOf(PDD_SCAN, PDD_PERSONAL, PDD_HOME),
        "已打开拼多多，请点「扫一扫」扫驿站二维码",
    )

    /** 拼多多 · 个人中心（进“多多买菜→自提服务→我的身份码”） */
    fun openPddPersonal(context: Context) = openPdd(
        context,
        listOf(PDD_PERSONAL, PDD_HOME),
        "已打开拼多多首页，请按提示进入：个人中心 → 多多买菜 → 自提服务 → 我的身份码",
    )

    /** 拼多多 · 首页 */
    fun openPddHome(context: Context) = openPdd(context, listOf("pinduoduo://", PDD_HOME), "已打开拼多多")

    const val WECHAT_PKG = "com.tencent.mm"

    /**
     * 拼多多驿站在微信里的身份码/包裹页入口（用户提供，含其驿站点 A082507556）。
     * 微信 OAuth 链接需在微信内打开：复制链接 + 自动拉起微信，用户粘贴到
     * 「文件传输助手」点击即可直达身份码/包裹页。
     */
    const val PDD_WECHAT_PACKAGE_URL =
        "https://open.weixin.qq.com/connect/oauth2/authorize?appid=wx913449bbda3b9f9a" +
            "&redirect_uri=https://mdkd.pinduoduo.com/weixin/login?redirect_url=/weixin/package" +
            "&station_code=A082507556&response_type=code&scope=snsapi_base" +
            "&state=false&connect_redirect=1#wechat_redirect"

    fun openPddWeChatPackage(context: Context) {
        // 复制链接到剪贴板
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("拼多多身份码链接", PDD_WECHAT_PACKAGE_URL))
        }
        // 拉起微信
        val launched = runCatching {
            val launch = context.packageManager.getLaunchIntentForPackage(WECHAT_PKG)
            if (launch != null) {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } else false
        }.getOrDefault(false)
        if (launched) {
            Toast.makeText(
                context,
                "链接已复制：在微信里发给「文件传输助手」并点击，直达身份码",
                Toast.LENGTH_LONG,
            ).show()
        } else if (!openInBrowser(context, PDD_WECHAT_PACKAGE_URL)) {
            Toast.makeText(context, "未安装微信，且无法打开浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    /** 浏览器直接试开（部分环境不走微信也能进） */
    fun openPddWeChatInBrowser(context: Context) =
        openPdd(context, listOf(PDD_WECHAT_PACKAGE_URL), "已用浏览器打开身份码链接")

    /** 直接跳指定 URI（嗅探出的真实路由用） */
    fun openUri(context: Context, uri: String, pkg: String) =
        jumpChain(context, listOf(uri), pkg, "已尝试打开目标页面")

    /** 支付宝 · 菜鸟取件码（失败回退：支付宝首页 → 应用市场） */
    fun openAlipayCainiao(context: Context) {
        if (!isInstalled(context, ALIPAY_PKG)) {
            Toast.makeText(
                context,
                "未安装支付宝，无法直达菜鸟取件码（可在支付宝里搜索「菜鸟」小程序）",
                Toast.LENGTH_LONG,
            ).show()
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$ALIPAY_PKG"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }
        jumpChain(
            context,
            listOf(ALIPAY_CAINIAO, "alipays://platformapi/startapp?appId=20000067"),
            ALIPAY_PKG,
            "已打开支付宝，请进入「菜鸟」小程序看取件码",
        )
    }

    /** 拼多多统一入口：未安装 → 提示；scheme 拉起 → 探测取件页 → 首页兜底 */
    private fun openPdd(context: Context, uris: List<String>, homeHint: String) {
        if (!isInstalled(context, PDD_PKG)) {
            Toast.makeText(
                context,
                "未安装拼多多，无法直达身份码页（可先安装拼多多，或用微信入口）",
                Toast.LENGTH_LONG,
            ).show()
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PDD_PKG"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }
        // 1) scheme 直达（新版拼多多可能已屏蔽，失败会自动往下走）
        if (jumpChain(context, uris, PDD_PKG, homeHint, silent = true)) return
        // 2) Activity 层探测取件/扫码页
        if (PddLauncher.tryDeepLink(context, PDD_PKG)) {
            Toast.makeText(context, homeHint, Toast.LENGTH_LONG).show()
            return
        }
        // 3) 首页兜底 + 引导
        if (PddLauncher.openApp(context, PDD_PKG)) {
            Toast.makeText(context, homeHint, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "未能打开拼多多，请手动打开后再进入身份码页面", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 依次尝试 scheme 跳转。
     * @param silent true 时不弹提示（由调用方决定最终提示），返回是否成功发起跳转
     */
    private fun jumpChain(
        context: Context,
        uris: List<String>,
        pkg: String,
        hint: String,
        silent: Boolean = false,
    ): Boolean {
        for (uri in uris) {
            // 1) 指定包名的 ACTION_VIEW（最精准）
            if (runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                            .setPackage(pkg)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.isSuccess
            ) {
                if (!silent) Toast.makeText(context, hint, Toast.LENGTH_LONG).show()
                return true
            }
            // 2) 不限包名（部分 ROM 对 scheme 解析更宽松）
            if (runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.isSuccess
            ) {
                if (!silent) Toast.makeText(context, hint, Toast.LENGTH_LONG).show()
                return true
            }
        }
        // 3) 按包名拉起首页
        context.packageManager.getLaunchIntentForPackage(pkg)?.let { launch ->
            if (runCatching {
                    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
            ) {
                if (!silent) Toast.makeText(context, hint, Toast.LENGTH_LONG).show()
                return true
            }
        }
        // 4) 应用市场兜底
        if (runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
        ) {
            if (!silent) {
                Toast.makeText(context, "未安装该应用，已跳转应用市场", Toast.LENGTH_SHORT).show()
            }
            return true
        }
        if (!silent) {
            Toast.makeText(context, "未安装该应用，且无法打开应用市场", Toast.LENGTH_SHORT).show()
        }
        return false
    }

    private fun openInBrowser(context: Context, url: String): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
}
