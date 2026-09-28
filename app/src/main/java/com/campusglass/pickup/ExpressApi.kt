package com.campusglass.pickup

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 快递物流查询（App 内直出结果）。
 *
 * 接口：快递100 开放查询页同款接口（免鉴权 JSON，实测可用）：
 *  1) /autonumber/autoComNum  自动识别快递公司（可能返回多个候选）
 *  2) /query?type=<公司>&postid=<单号>   查询物流轨迹
 * 兼容策略：逐个候选公司试查 + 单号前缀兜底映射，尽量多公司可用。
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

    /** 当前实测可查询的主流快递（自动识别为准） */
    val SUPPORTED_HINT =
        "支持：顺丰、圆通、中通、申通、韵达、极兔、EMS、邮政、京东、德邦等主流快递\n" +
            "（个别小快递/新单号可能查不到，识别不了时会提示）"

    private const val UA =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36"

    /** 单号前缀 → 快递公司代码兜底（autonumber 失效时用） */
    private val PREFIX_MAP = mapOf(
        "SF" to "shunfeng", "YT" to "yuantong", "ZT" to "zhongtong",
        "ST" to "shentong", "YD" to "yunda", "JT" to "jtexpress",
        "JD" to "jd", "DBL" to "debang", "EMS" to "ems",
    )

    fun query(nu: String): Result = runCatching {
        // ---- 1) 候选公司列表 ----
        data class Com(val code: String, val name: String)
        val candidates = mutableListOf<Com>()

        runCatching {
            val auto = JSONObject(get("https://www.kuaidi100.com/autonumber/autoComNum?resultv=2&text=$nu"))
            val arr = auto.optJSONArray("auto")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val code = o.optString("comCode")
                    if (code.isNotBlank()) candidates += Com(code, o.optString("name").ifBlank { code })
                }
            }
        }

        // 前缀兜底
        val upper = nu.uppercase()
        PREFIX_MAP.forEach { (p, code) ->
            if (upper.startsWith(p) && candidates.none { it.code == code }) {
                candidates += Com(code, code)
            }
        }
        if (nu.length in 10..13 && nu.all { it.isDigit() } && candidates.none { it.code == "youzhengguonei" }) {
            candidates += Com("youzhengguonei", "邮政")
        }

        if (candidates.isEmpty()) {
            return Result(false, "未知", "", emptyList(), "没识别出快递公司，请核对单号")
        }

        // ---- 2) 逐个候选试查 ----
        for (com in candidates) {
            val q = runCatching {
                get(
                    "https://www.kuaidi100.com/query?type=${com.code}&postid=$nu" +
                        "&temp=0.${(10..99).random()}&phone="
                )
            }.getOrNull() ?: continue

            val json = runCatching { JSONObject(q) }.getOrNull() ?: continue
            if (json.optString("status") != "200") continue

            val arr = json.optJSONArray("data") ?: continue
            if (arr.length() == 0) continue

            val stateCode = json.optString("state", "")
            val stateText = when (stateCode) {
                "0" -> "运输中"
                "1" -> "已揽收"
                "2" -> "疑难件"
                "3" -> "已签收 ✔"
                "4" -> "已退签"
                "5" -> "派件中 🚚"
                "6" -> "退回中"
                else -> "状态更新中"
            }
            val traces = mutableListOf<Trace>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                traces += Trace(o.optString("ftime", o.optString("time")), o.optString("context"))
            }
            return Result(true, com.name.ifBlank { com.code }, stateText, traces, "ok")
        }

        Result(
            false,
            candidates.first().name,
            "",
            emptyList(),
            "该快递公司暂查不到轨迹（尝试了：${candidates.joinToString("、") { it.name }}）。" +
                "可能是小快递公司/刚揽收未同步，稍后再试或到快递100网页查询"
        )
    }.getOrElse {
        Result(false, "未知", "", emptyList(), "网络异常：" + (it.message ?: "请稍后重试"))
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Referer", "https://www.kuaidi100.com/")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}
