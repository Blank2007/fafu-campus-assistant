package com.campusglass.pickup

import android.content.Context
import android.content.Intent

/**
 * 拼多多取件页定位：scheme 被新版拼多多屏蔽，改用 Activity 层探测
 * （AppPageFinder）+ 保底打开 App + 页内路径引导。
 */
object PddLauncher {

    data class Candidate(val label: String, val className: String)

    fun findCandidates(context: Context, pkg: String): List<Candidate> =
        AppPageFinder.find(context, pkg, AppPageFinder.PICKUP_KEYWORDS)
            .map { Candidate(it.label, it.className) }

    fun launch(context: Context, pkg: String, className: String): Boolean =
        AppPageFinder.launch(context, pkg, className)

    /** 保底：直接打开 App */
    fun openApp(context: Context, pkg: String) {
        runCatching {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
        }
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
