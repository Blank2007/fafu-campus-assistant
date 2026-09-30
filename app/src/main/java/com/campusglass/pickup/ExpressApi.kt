package com.campusglass.pickup

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 快递物流查询（App 内直出结果）。
 *
 * 接口：快递100 开放查询页同款接口（免鉴权 JSON，实测可用）：
 *  1) /autonumber/autoComNum  自动识别快递公司（可能返回多个候选）
 *  2) /query?type=<公司>&postid=<单号>   查询物流轨迹
 * 兼容策略：逐个候选公司试查 + 单号前缀兜底映射，尽量多公司可用。
 *
 * v3.3 修复：
 *  - 「查无结果」不再被当成成功（该接口对查不到的单号同样返回 status=200 + state=3，
 *    真值标记是 condition=F00，旧版据此把没查到的单号显示成「已签收 ✔」）；
 *  - 区分「无数据」「网络异常」「识别不出公司」三种失败，不再把网络问题说成单号有问题；
 *  - 单号做 URL 编码；读取 HTTP 状态码；连接用完 disconnect；
 *  - 状态码表补全（0-8/14），扩展码用最新轨迹兜底推断，不再一律「状态更新中」；
 *  - 候选公司数量与单次查询总耗时设上限。
 */
object ExpressApi {

    data class Trace(val time: String, val context: String)

    /** 查询结果类型：只有 OK 才代表真的拿到了轨迹 */
    enum class Kind { OK, NO_DATA, NO_CARRIER, NETWORK }

    data class Result(
        val kind: Kind,
        val comName: String,
        val stateText: String,
        val traces: List<Trace>,
        val message: String,
        val number: String = "",
    ) {
        /** 兼容旧调用：是否为有效结果 */
        val ok: Boolean get() = kind == Kind.OK
    }

    /** 当前实测可查询的主流快递（自动识别为准） */
    val SUPPORTED_HINT =
        "支持：顺丰、圆通、中通、申通、韵达、极兔、EMS、邮政、京东、德邦等主流快递\n" +
            "（个别小快递/新单号可能查不到，识别不了时会提示）"

    private const val UA =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36"

    private const val CONNECT_TIMEOUT = 8_000
    private const val READ_TIMEOUT = 8_000

    /** 最多试几个候选公司 / 单次查询总耗时上限 */
    private const val MAX_CANDIDATES = 4
    private const val BUDGET_MS = 20_000L

    private data class Com(val code: String, val name: String)

    /** 公司代码 → 中文名（前缀兜底与标题栏显示用，避免冒出 shunfeng/yuantong 这类内部代码） */
    private val CARRIER_NAMES = mapOf(
        "shunfeng" to "顺丰速运", "yuantong" to "圆通速递", "zhongtong" to "中通快递",
        "shentong" to "申通快递", "yunda" to "韵达速递", "jtexpress" to "极兔速递",
        "jd" to "京东物流", "debang" to "德邦快递", "debangwuliu" to "德邦物流",
        "ems" to "EMS", "youzhengguonei" to "邮政快递包裹", "youshu" to "邮政标准快递",
        "youzhengglobal" to "邮政国际", "tiantian" to "天天快递", "huitongkuaidi" to "百世快递",
        "baishiwuliu" to "百世物流", "quanfengkuaidi" to "全峰快递", "zhaijisong" to "宅急送",
        "youshuwuliu" to "优速快递", "suer" to "速尔快递", "guotongkuaidi" to "国通快递",
        "annengwuliu" to "安能物流", "zhongyouwuliu" to "中邮物流", "cces" to "CCES",
    )

    /** 单号前缀 → 公司代码兜底（autonumber 失效时用） */
    private val PREFIX_CARRIERS = listOf(
        "SF" to "shunfeng", "YT" to "yuantong", "ZT" to "zhongtong",
        "ST" to "shentong", "YD" to "yunda", "JT" to "jtexpress",
        "JD" to "jd", "DBL" to "debang", "EMS" to "ems",
    )

    fun query(nu: String): Result {
        val number = nu.trim()
        if (number.isBlank()) {
            return Result(Kind.NO_CARRIER, "未知", "", emptyList(), "请先输入快递单号", number)
        }
        val encoded = runCatching { URLEncoder.encode(number, "UTF-8") }.getOrDefault(number)

        val candidates = collectCandidates(encoded, number)
        if (candidates.isEmpty()) {
            return Result(Kind.NO_CARRIER, "未知", "", emptyList(), "没识别出快递公司，请核对单号", number)
        }

        val deadline = System.currentTimeMillis() + BUDGET_MS
        var sawResponse = false
        var sawNetworkError = false

        for (com in candidates) {
            if (System.currentTimeMillis() > deadline) break

            val body = runCatching {
                get(
                    "https://www.kuaidi100.com/query?type=${com.code}&postid=$encoded" +
                        "&temp=0.${(10..99).random()}&phone="
                )
            }.onFailure { sawNetworkError = true }.getOrNull() ?: continue

            sawResponse = true

            val json = runCatching { JSONObject(body) }.getOrNull() ?: continue
            if (json.optString("status") != "200") continue

            val arr = json.optJSONArray("data") ?: continue
            if (arr.length() == 0) continue

            val traces = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Trace(o.optString("ftime", o.optString("time")), o.optString("context"))
            }
            if (traces.isEmpty()) continue

            // 「查无结果」同样是 status=200 + state=3，必须显式识别，否则会把查不到的单号显示成已签收
            if (isNoResult(json, traces)) continue

            val stateText = stateText(json.optString("state", ""), traces)
            val name = carrierName(json.optString("com"), com)
            return Result(Kind.OK, name, stateText, traces, "ok", number)
        }

        val networkProblem = !sawResponse && sawNetworkError
        return Result(
            if (networkProblem) Kind.NETWORK else Kind.NO_DATA,
            candidates.first().name,
            "",
            emptyList(),
            if (networkProblem) {
                "网络异常，未能连接快递100（也可能被临时限流）。请检查网络后稍后重试，" +
                    "或直接到快递100 网页查询。"
            } else {
                "未查询到该单号的物流信息（尝试了：${candidates.joinToString("、") { it.name }}）。" +
                    "可能刚揽收未同步、单号有误或已过期，核对后可再试。"
            },
            number,
        )
    }

    /** 候选公司：接口自动识别 → 单号前缀兜底 → 纯数字单号补邮政 */
    private fun collectCandidates(encoded: String, raw: String): List<Com> {
        val out = mutableListOf<Com>()

        runCatching {
            val auto = JSONObject(get("https://www.kuaidi100.com/autonumber/autoComNum?resultv=2&text=$encoded"))
            val arr = auto.optJSONArray("auto")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val code = o.optString("comCode")
                    if (code.isNotBlank() && out.none { it.code == code }) {
                        out += Com(code, CARRIER_NAMES[code] ?: o.optString("name").ifBlank { code })
                    }
                }
            }
        }

        val upper = raw.uppercase()
        PREFIX_CARRIERS.forEach { (prefix, code) ->
            if (upper.startsWith(prefix) && out.none { it.code == code }) {
                out += Com(code, CARRIER_NAMES[code] ?: code)
            }
        }
        if (raw.length in 10..13 && raw.all { it.isDigit() } && out.none { it.code == "youzhengguonei" }) {
            out += Com("youzhengguonei", CARRIER_NAMES.getValue("youzhengguonei"))
        }

        return out.take(MAX_CANDIDATES)
    }

    /** 是否「查无结果」（真值标记 condition=F00 或轨迹文案「查无结果」） */
    private fun isNoResult(json: JSONObject, traces: List<Trace>): Boolean {
        if (json.optString("condition").equals("F00", ignoreCase = true)) return true
        if (json.optString("message").contains("查无结果")) return true
        return traces.all { it.context.isBlank() || it.context.contains("查无结果") }
    }

    private fun carrierName(code: String, fallback: Com): String =
        CARRIER_NAMES[code] ?: CARRIER_NAMES[fallback.code] ?: fallback.name.ifBlank { code }

    private fun stateText(code: String, traces: List<Trace>): String = when (code) {
        "0" -> "运输中"
        "1" -> "已揽收"
        "2" -> "疑难件"
        "3" -> "已签收 ✔"
        "4" -> "已退签"
        "5" -> "派件中 🚚"
        "6" -> "退回中"
        "7" -> "转投中"
        "8" -> "清关中"
        "14" -> "拒签"
        else -> inferStateFromTraces(traces)
    }

    /** 接口会返回 501/1002 等扩展状态码；表里没有时按轨迹推断，避免只显示「状态更新中」 */
    private fun inferStateFromTraces(traces: List<Trace>): String {
        val rules = listOf(
            "已签收" to "已签收 ✔", "签收" to "已签收 ✔",
            "派送中" to "派件中 🚚", "派件" to "派件中 🚚", "派送" to "派件中 🚚",
            "待取件" to "待取件 📦", "已到站" to "待取件 📦", "自提" to "待取件 📦",
            "揽收" to "已揽收", "已收件" to "已揽收",
            "退回" to "退回中", "拒收" to "已退签", "退签" to "已退签",
            "清关" to "清关中", "转投" to "转投中",
            "在途" to "运输中", "运输" to "运输中", "发出" to "运输中",
        )
        fun match(text: String): String? = rules.firstOrNull { text.contains(it.first) }?.second
        // 先看最新一条轨迹，再退化为整段轨迹
        return match(traces.first().context)
            ?: match(traces.joinToString(" ") { it.context })
            ?: "状态更新中（以最新轨迹为准）"
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Referer", "https://www.kuaidi100.com/")
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            runCatching { conn.disconnect() }
        }
    }
}
