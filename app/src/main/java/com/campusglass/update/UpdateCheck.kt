package com.campusglass.update

import com.campusglass.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 检查更新结果 */
data class UpdateCheck(
    val message: String,
    val newTag: String?,
    val downloadUrl: String? = null,
    val fileName: String? = null,
    val sha256: String? = null,
    val sigUrl: String? = null,      // B-2：Ed25519 签名资产地址
)

/**
 * 检查更新：读 GitHub Releases 最新版（带 User-Agent + 重试；GitHub API 强制要求 UA）。
 *
 * 从 SettingsScreen 抽出（v4.18），供「设置页手动检查」与「启动时自动检查」共用，
 * 避免两处各写一份解析逻辑（旧版曾因 assets 取值顺序出过误取问题）。
 */
fun checkUpdate(): UpdateCheck {
    val url = "https://api.github.com/repos/Blank2007/fafu-campus-assistant/releases/latest"
    var lastErr = ""
    repeat(2) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 12_000
            conn.readTimeout = 12_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "FAFU-Campus-Assistant/" + BuildConfig.VERSION_NAME)   // SET-4
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val code = conn.responseCode
            if (code == 404) {
                conn.disconnect()
                lastErr = "未找到发布信息"
                return@runCatching
            }
            if (code == 403) {
                // T7：限流专属文案 + 尊重 Retry-After
                val wait = conn.getHeaderField("Retry-After")?.toLongOrNull()?.coerceIn(1, 5) ?: 2
                conn.disconnect()
                lastErr = "GitHub 接口限流，请 ${wait}s 后再试"
                Thread.sleep(wait * 1000)
                return@runCatching
            }
            if (code != 200) {
                conn.disconnect()
                lastErr = "HTTP $code"
                return@runCatching
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            conn.disconnect()
            val latest = json.optString("tag_name", "").removePrefix("v").removePrefix("V")
            val body = json.optString("body", "").trim()      // 更新日志
            // U7：下载地址/文件名/sha256 来自 API 资产（不硬编码）
            // B-2：按名称匹配 .apk 与配套 .apk.sig（不盲取 assets[0]，防资产顺序变化误取）
            val assetsArr = json.optJSONArray("assets")
            var apkObj: JSONObject? = null
            var sigObj: JSONObject? = null
            if (assetsArr != null) {
                for (i in 0 until assetsArr.length()) {
                    val o = assetsArr.optJSONObject(i) ?: continue
                    val n = o.optString("name")
                    if (n.endsWith(".apk") && apkObj == null) apkObj = o
                    if (n.endsWith(".apk.sig")) sigObj = o
                }
            }
            val a0 = apkObj
            val assetUrl = a0?.optString("browser_download_url")?.ifBlank { null }
            val assetName = a0?.optString("name")?.ifBlank { null }
                ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val assetSha = a0?.optString("digest")?.removePrefix("sha256:")?.ifBlank { null }
            val sigUrl = sigObj?.optString("browser_download_url")?.ifBlank { null }
            val current = BuildConfig.VERSION_NAME
            // SET-4：数字段比大小（兼容 3.2 / v3.2.0 / 3.2.zilyf 等写法），旧版不再误报
            val a = verParts(latest); val b = verParts(current)
            val cmp = (0 until maxOf(a.size, b.size)).fold(0) { acc, i ->
                if (acc != 0) acc else (a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 })
            }
            return when {
                latest.isBlank() -> UpdateCheck("没获取到版本信息，请稍后再试", null)
                cmp <= 0 -> UpdateCheck("已是最新版本（v$current）✅", null)
                else -> UpdateCheck(
                    "发现新版本 v$latest（当前 v$current）！选一种方式更新" +
                        if (body.isNotBlank()) "\n\n📋 更新日志：\n" + body.take(800) else "",
                    latest,
                    assetUrl,
                    assetName,
                    assetSha,
                    sigUrl,
                )
            }
        }.onFailure { lastErr = it.message ?: "网络异常" }
    }
    return UpdateCheck("检查失败：$lastErr（可能是网络不畅，可直接点「浏览器下载」查看）", null)
}

/** SET-4：把版本串拆成数字段，用于比较（兼容 3.2 / v3.2.0 / 3.2.zilyf） */
internal fun verParts(s: String): List<Int> = s.split(Regex("[^0-9]+")).mapNotNull { it.toIntOrNull() }

/** 版本比较：a > b 返回正数 */
fun compareVersion(a: List<Int>, b: List<Int>): Int =
    (0 until maxOf(a.size, b.size)).fold(0) { acc, i ->
        if (acc != 0) acc else (a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 })
    }
