package com.campusglass.pickup

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 快递100 网页同款接口（只读）。
 * v3.3 修复：
 * - 「查无结果」不再误判为「已签收」（P0-1：condition=F00 / 轨迹含"查无结果"）
 * - 状态码表补全 + 按轨迹关键字兜底（P0-5）
 * - 区分「网络故障」和「真的查不到」（EX-3）
 * - 单号归一化 + URL 编码（EX-4：全角数字/空格/换行/特殊字符）
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

    const val SUPPORTED_HINT =
        "（个别小快递/新单号可能查不到，识别不了时会提示）"

    private const val UA =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36"

    /** 单号前缀 → (代码, 中文名) 兜底 */
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

        return runCatching {
            // ---- 1) 候选公司列表 ----
            data class Com(val code: String, val name: String)
            val candidates = mutableListOf<Com>()
            val encoded = URLEncoder.encode(nu, "UTF-8")

            runCatching {
                val auto = JSONObject(get("https://www.kuaidi100.com/autonumber/autoComNum?resultv=2&text=$encoded"))
                val arr = auto.optJSONArray("auto")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
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
                    candidates += Com(code, name)      // 中文名兜底（EX-5）
                }
            }
            if (nu.length in 10..13 && nu.all { it.isDigit() } && candidates.none { it.code == "youzhengguonei" }) {
                candidates += Com("youzhengguonei", "邮政包裹")
            }

            if (candidates.isEmpty()) {
                return Result(false, "未知", "", emptyList(), "没识别出快递公司，请核对单号")
            }

            // ---- 2) 逐个候选试查 ----
            var networkFailures = 0
            for (com in candidates) {
                val q = runCatching {
                    get(
                        "https://www.kuaidi100.com/query?type=${com.code}&postid=$encoded" +
                            "&temp=0.${(10..99).random()}&phone="
                    )
                }.getOrElse {
                    networkFailures++
                    continue
                }

                val json = runCatching { JSONObject(q) }.getOrNull() ?: continue
                if (json.optString("status") != "200") continue
                // P0-1：查无结果（condition=F00）绝不能当成功
                if (json.optString("condition") == "F00") continue

                val arr = json.optJSONArray("data") ?: continue
                if (arr.length() == 0) continue

                val traces = mutableListOf<Trace>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    traces += Trace(o.optString("ftime", o.optString("time")), o.optString("context"))
                }
                // 轨迹里写"查无结果"同样是空结果
                if (traces.any { it.context.contains("查无结果") }) continue

                val stateCode = json.optString("state", "")
                val stateText = stateTextOf(stateCode, traces)
                val comName = json.optString("com").ifBlank { com.name }.ifBlank { com.code }
                return Result(true, comName, stateText, traces, "ok")
            }

            if (networkFailures > 0 && networkFailures == candidates.size) {
                return Result(
                    false, candidates.first().name, "", emptyList(),
                    "网络异常或查询接口繁忙（断网/被限流），请稍后重试",
                )
            }

            Result(
                false,
                candidates.first().name,
                "",
                emptyList(),
                "没查到该单号的轨迹（尝试了：${candidates.joinToString("、") { it.name }}）。" +
                    "可能是单号有误、刚揽收未同步或小快递公司，稍后再试"
            )
        }.getOrElse {
            Result(false, "未知", "", emptyList(), "网络异常：" + (it.message ?: "请稍后重试"))
        }
    }

    /** P0-5：状态码表补全 + 按最新轨迹关键字兜底 */
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

    /** EX-3：非 2xx 一律报错（而不是吞掉说"查不到"） */
    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Referer", "https://www.kuaidi100.com/")
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw IOException("HTTP $code")
        }
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
