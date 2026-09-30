package com.campusglass.pickup

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 拼多多取件页定位：scheme 被新版拼多多屏蔽，改用 Activity 层探测
 * （AppPageFinder）+ 保底打开 App + 页内路径引导。
 *
 * v3.3 修复：探测链路真正接上（旧版 findCandidates/tryAny 没有任何调用者，
 * 且 AppPageFinder 读不到 Activity 列表），openApp 返回结果供调用方提示用户。
 */
object PddLauncher {

    data class Candidate(val label: String, val className: String)

    fun findCandidates(context: Context, pkg: String): List<Candidate> =
        AppPageFinder.find(context, pkg, AppPageFinder.PICKUP_KEYWORDS)
            .map { Candidate(it.label, it.className) }

    fun launch(context: Context, pkg: String, className: String): Boolean =
        AppPageFinder.launch(context, pkg, className)

    /** 依次尝试探测到的取件/扫码相关 Activity，成功拉起返回 true */
    fun tryDeepLink(context: Context, pkg: String): Boolean =
        findCandidates(context, pkg).any { launch(context, pkg, it.className) }

    /**
     * 保底：直接打开 App 首页；没有首页入口时尝试应用市场。
     * @return 是否成功发起跳转（false 时调用方应提示“未安装该应用”）
     */
    fun openApp(context: Context, pkg: String): Boolean {
        context.packageManager.getLaunchIntentForPackage(pkg)?.let { launch ->
            if (runCatching {
                    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
            ) {
                return true
            }
        }
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
    }
}
