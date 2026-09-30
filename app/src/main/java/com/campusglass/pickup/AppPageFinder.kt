package com.campusglass.pickup

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * 通用页面探测器：枚举目标 App 注册的 Activity，按关键词打分，
 * 用于直达“订单/个人中心/取件”等页面。逐个尝试、拉不起来自动跳过，
 * 不依赖容易失效的私有 scheme（无效链接自然被过滤）。
 *
 * v3.3 修复：
 *  - 查询 Activity 列表必须带 GET_ACTIVITIES，旧版传 0 导致 activities 恒为 null，
 *    整个探测功能实际从不工作；
 *  - PackageInfoFlags 是 API 33 才有的类型，minSdk 31 上会 NoClassDefFoundError，
 *    这里按版本分流（33+ 用新 API，31/32 用旧 API）。
 */
object AppPageFinder {

    data class Candidate(val label: String, val className: String)

    /** 订单/个人中心类关键词 */
    val ORDER_KEYWORDS = listOf(
        "order" to 10, "dingdan" to 10, "profile" to 9, "personal" to 9,
        "my" to 6, "user" to 6, "account" to 6, "center" to 5, "member" to 5,
        "main" to 1, "home" to 1,
        "goods" to -3, "search" to -3, "login" to -4, "web" to -2,
    )

    /** 取件/扫码类关键词 */
    val PICKUP_KEYWORDS = listOf(
        "scan" to 10, "sao" to 10, "qr" to 9, "identity" to 9, "idcode" to 9,
        "pickup" to 8, "pick" to 7, "station" to 6, "express" to 6,
        "code" to 4, "auth" to 4, "capture" to 5, "camera" to 5,
        "order" to -1, "goods" to -3, "search" to -3,
    )

    fun find(context: Context, pkg: String, weights: List<Pair<String, Int>>): List<Candidate> =
        activitiesOf(context, pkg)
            .distinct()
            .sortedByDescending { score(it, weights) }
            .filter { score(it, weights) > 0 }
            .take(8)
            .map { Candidate(it.substringAfterLast('.'), it) }

    /** 读取目标包已注册的 Activity 名（必须带 GET_ACTIVITIES，否则 PackageInfo.activities 为 null） */
    private fun activitiesOf(context: Context, pkg: String): List<String> = runCatching {
        val pm = context.packageManager
        val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
        }
        info.activities?.map { it.name } ?: emptyList()
    }.getOrDefault(emptyList())

    private fun score(className: String, weights: List<Pair<String, Int>>): Int {
        val s = className.lowercase()
        return weights.sumOf { (k, v) -> if (s.contains(k)) v else 0 }
    }

    /** 尝试拉起 Activity；非导出/不存在返回 false，由调用方跳过或回退 */
    fun launch(context: Context, pkg: String, className: String): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setClassName(pkg, className)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (_: Exception) {
        false
    }

    /** 依次尝试候选页面，成功返回 true */
    fun tryAny(context: Context, pkg: String, weights: List<Pair<String, Int>>): Boolean =
        find(context, pkg, weights).any { launch(context, pkg, it.className) }
}
