package com.campusglass.pickup

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 快递100 网页同款接口（只读）· v4 重写。
 *
 * 核心修复（同一单号查出多个不同结果）：
 * - 确定性候选顺序：仅官方识别（autonumber）+ 单号前缀兜底，不再随机
 * - 交叉校验：响应里的单号 nu 必须与查询一致、公司 com 必须与请求一致，
 *   否则丢弃该结果（防止查出"别的包裹/别的公司"的轨迹）
 * - 「查无结果」（condition=F00 / 轨迹含"查无结果"）绝不当成功
 * - 3 分钟结果缓存：重复查询稳定一致，且不触发限流
 */
object ExpressApi {

    data class Trace(val time: String, val context: String)

    data class Result(
        val ok: Boolean,
        val comName: String,
        val stateText: String,
        val traces: List<Trace>,
        val message: String,
    )

    private const val UA =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36"

    /** 单号前缀 → (代码, 中文名) */
    private val PREFIX_MAP = listOf(
        "SF" to ("shunfeng" to "顺丰速运"),
        "YT" to ("yuantong" to "圆通速递"),
        "ZT" to ("zhongtong" to "中通快递"),
        "ST" to ("shentong" to "申通快递"),
        "YD" to ("yunda" to "韵达速递"),
        "JT" to ("jtexpress" to "极兔速递"),
        "JD" to ("jd" to "京东物流"),
        "DBL" to ("debang" to "德邦快递"),
        "EMS" to ("ems" to "EMS"),
    )

    private data class Com(val code: String, val name: String)

    const val SUPPORTED_HINT =
        "（个别小快递/新单号可能查不到，识别不了时会提示）"

    // ---- 3 分钟结果缓存（重复查询结果稳定 + 防限流） ----
    private val cache = object : LinkedHashMap<String, Pair<Long, Result>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, Result>>?) = size > 20
    }
    private const val CACHE_TTL_MS = 180_000L

    /** 单号归一化：去空白、全角数字转半角 */
    fun normalize(raw: String): String = buildString {
        for (ch in raw.trim()) {
            when {
                ch in '０'..'９' -> append('0' + (ch - '０'))
                ch.isWhitespace() -> {}
                else -> append(ch)
            }
        }
    }

    fun query(rawNu: String): Result {
        val nu = normalize(rawNu)
        if (nu.isBlank()) return Result(false, "未知", "", emptyList(), "请先输入快递单号")

        synchronized(cache) {
            val hit = cache[nu]
            if (hit != null && System.currentTimeMillis() - hit.first < CACHE_TTL_MS) return hit.second
        }

        return runCatching {
            val encoded = URLEncoder.encode(nu, "UTF-8")

            // ---- 1) 确定性候选公司 ----
            val candidates = mutableListOf<Com>()
            runCatching {
                val auto = JSONObject(
                    get("https://www.kuaidi100.com/autonumber/autoComNum?resultv=2&text=$encoded")
                )
                val arr = auto.optJSONArray("auto")
                if (arr != null) {
                    for (i in 0 until arr.length().coerceAtMost(2)) {     // 最多 2 个官方识别
                        val o = arr.getJSONObject(i)
                        val code = o.optString("comCode")
                        if (code.isNotBlank()) candidates += Com(code, o.optString("name").ifBlank { code })
                    }
                }
            }
            val upper = nu.uppercase()
            PREFIX_MAP.forEach { (p, pair) ->
                val (code, name) = pair
                if (upper.startsWith(p) && candidates.none { it.code == code }) {
                    candidates += Com(code, name)
                }
            }
            if (nu.length in 10..13 && nu.all { it.isDigit() } && candidates.none { it.code == "youzhengguonei" }) {
                candidates += Com("youzhengguonei", "邮政包裹")
            }
            if (candidates.isEmpty()) {
                return Result(false, "未知", "", emptyList(), "没识别出快递公司，请核对单号")
            }

            // ---- 2) 依序试查 + 交叉校验 ----
            var networkFailures = 0
            for ((idx, com) in candidates.withIndex()) {
                if (idx > 0) runCatching { Thread.sleep(400) }

                val q = runCatching {
                    get(
                        "https://www.kuaidi100.com/query?type=${com.code}&postid=$encoded&phone="
                    )
                }.getOrElse {
                    networkFailures++
                    continue
                }

                val json = runCatching { JSONObject(q) }.getOrNull() ?: continue
                if (json.optString("status") != "200") continue
                if (json.optString("condition") == "F00") continue        // 查无结果

                // 交叉校验：响应单号必须与查询一致（防查出别的包裹）
                val respNu = json.optString("nu").replace(" ", "")
                if (respNu.isNotBlank() && !respNu.equals(nu, ignoreCase = true)) continue
                // 交叉校验：响应公司必须与请求一致（防串公司轨迹）
                val respCom = json.optString("com")
                if (respCom.isNotBlank() && respCom != com.code) continue

                val arr = json.optJSONArray("data") ?: continue
                if (arr.length() == 0) continue

                val traces = mutableListOf<Trace>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    traces += Trace(o.optString("ftime", o.optString("time")), o.optString("context"))
                }
                if (traces.any { it.context.contains("查无结果") }) continue

                val res = Result(true, com.name, stateTextOf(json.optString("state", ""), traces), traces, "ok")
                synchronized(cache) { cache[nu] = System.currentTimeMillis() to res }
                return res
            }

            if (networkFailures > 0 && networkFailures == candidates.size) {
                return Result(
                    false, candidates.first().name, "", emptyList(),
                    "网络异常或查询接口繁忙（断网/被限流），请稍后重试",
                )
            }
            Result(
                false, candidates.first().name, "", emptyList(),
                "没查到该单号的轨迹（尝试了：${candidates.joinToString("、") { it.name }}）。" +
                    "可能是单号有误、刚揽收未同步或小快递公司，稍后再试",
            )
        }.getOrElse {
            Result(false, "未知", "", emptyList(), "网络异常：" + (it.message ?: "请稍后重试"))
        }
    }

    /** 状态码表 + 按最新轨迹关键字兜底 */
    private fun stateTextOf(code: String, traces: List<Trace>): String {
        when (code) {
            "0" -> return "运输中"
            "1" -> return "已揽收"
            "2" -> return "疑难件"
            "3" -> return "已签收 ✔"
            "4" -> return "已退签"
            "5" -> return "派件中 🚚"
            "6" -> return "退回中"
            "7" -> return "转投中"
        }
        val t = traces.lastOrNull()?.context ?: ""
        return when {
            t.contains("签收") -> "已签收 ✔"
            t.contains("派送") || t.contains("派件") -> "派件中 🚚"
            t.contains("运输") || t.contains("到达") || t.contains("中转") -> "运输中"
            t.contains("揽收") || t.contains("收件") -> "已揽收"
            t.contains("下单") -> "已下单"
            t.contains("退") -> "退回中"
            else -> "状态更新中"
        }
    }

    /** 非 2xx 报错；失败 1.2s 退避后重试一次 */
    private fun get(url: String): String {
        fun once(): String {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Referer", "https://www.kuaidi100.com/")
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                throw IOException(if (code == 403) "HTTP 403（查询过于频繁，稍等几秒再试）" else "HTTP $code")
            }
            return try {
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
        return try {
            once()
        } catch (e: IOException) {
            Thread.sleep(1_200)
            once()
        }
    }
}
