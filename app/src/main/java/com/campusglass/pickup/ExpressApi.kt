package com.campusglass.pickup

import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 快递100 网页同款接口（只读）· v5（审查 E1-E12 全量修复）。
 * - suspend + 可取消：协程取消立即生效（E2），整轮查询 20s 总时限
 * - 正向证据判定签收（E4）；状态码表补全（E3）；限流单独识别（E6）
 * - data 元素类型防护（E7）；缓存键大小写归一 + 失败短缓存 + 强制刷新（E12）
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

    private const val TOTAL_BUDGET_MS = 20_000L      // E2：整轮总时限

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

    /** 代码 → 中文名兜底（E12：不暴露英文内部代码） */
    private val CODE_NAMES = mapOf(
        "shunfeng" to "顺丰速运", "yuantong" to "圆通速递", "zhongtong" to "中通快递",
        "shentong" to "申通快递", "yunda" to "韵达速递", "jtexpress" to "极兔速递",
        "jd" to "京东物流", "debang" to "德邦快递", "ems" to "EMS",
        "youzhengguonei" to "邮政包裹", "youzhengzhengyou" to "邮政快递",
        "annengwuliu" to "安能物流", "quanfengkuaidi" to "全峰快递",
        "youshuwuliu" to "优速物流", "zhaijisong" to "宅急送",
    )

    private data class Com(val code: String, val name: String)

    const val SUPPORTED_HINT =
        "（个别小快递/新单号可能查不到，识别不了时会提示）"

    private fun prettyName(code: String, raw: String): String =
        raw.ifBlank { CODE_NAMES[code] ?: code }

    // ---- 缓存：成功 3 分钟 / 失败 30 秒（E12） ----
    private val cache = object : LinkedHashMap<String, Pair<Long, Result>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, Result>>?) = size > 20
    }

    /** 单号归一化：去空白、全角数字/字母转半角（E12） */
    fun normalize(raw: String): String = buildString {
        for (ch in raw.trim()) {
            when {
                ch in '０'..'９' -> append('0' + (ch - '０'))
                ch in 'Ａ'..'Ｚ' -> append('A' + (ch - 'Ａ'))
                ch in 'ａ'..'ｚ' -> append('a' + (ch - 'ａ'))
                ch.isWhitespace() -> {}
                else -> append(ch)
            }
        }
    }

    suspend fun query(rawNu: String, force: Boolean = false): Result {
        val nu = normalize(rawNu)
        if (nu.isBlank()) return Result(false, "未知", "", emptyList(), "请先输入快递单号")

        val cacheKey = nu.uppercase()                       // E12：大小写归一
        if (!force) {
            val hit = synchronized(cache) { cache[cacheKey] }
            if (hit != null) {
                val ttl = if (hit.second.ok) 180_000L else 30_000L
                if (System.currentTimeMillis() - hit.first < ttl) return hit.second
            }
        }

        val deadline = System.currentTimeMillis() + TOTAL_BUDGET_MS
        val encoded = URLEncoder.encode(nu, "UTF-8")

        // ---- 1) 确定性候选公司 ----
        val candidates = mutableListOf<Com>()
        runCatching {
            val auto = JSONObject(
                get("https://www.kuaidi100.com/autonumber/autoComNum?resultv=2&text=$encoded",
                    (deadline - System.currentTimeMillis()).coerceIn(1_000, TOTAL_BUDGET_MS))
            )
            val arr = auto.optJSONArray("auto")
            if (arr != null) {
                for (i in 0 until arr.length().coerceAtMost(2)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val code = o.optString("comCode")
                    if (code.isNotBlank()) candidates += Com(code, prettyName(code, o.optString("name")))
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
        var limited = 0                                  // E6：限流计数
        var serverErr = 0                                // V4-25：服务端异常（不是限流）
        for ((idx, com) in candidates.withIndex()) {
            if (System.currentTimeMillis() > deadline) break        // E2：总时限
            if (idx > 0) delay(400)                                  // 可取消（E2）

            val remain = (deadline - System.currentTimeMillis()).coerceIn(1_000, TOTAL_BUDGET_MS)   // V4-8
            val q = try {
                get("https://www.kuaidi100.com/query?type=${com.code}&postid=$encoded&phone=", remain)
            } catch (e: IOException) {
                if (e.message?.contains("403") == true) limited++ else networkFailures++
                continue
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e                                             // E2：取消要冒泡
            } catch (_: Exception) {
                networkFailures++
                continue
            }

            val json = runCatching { JSONObject(q) }.getOrNull()
            if (json == null) { serverErr++; continue }             // V4-25：响应非 JSON（接口异常，非限流）
            if (json.optString("status") != "200") {
                serverErr++                                         // V4-25：服务端错误（如公司代码无效）
                continue
            }
            // E4：condition 以 F 开头即无结果（不只认 F00）
            if (json.optString("condition").startsWith("F", ignoreCase = true)) continue

            val respNu = json.optString("nu").replace(" ", "")
            if (respNu.isNotBlank() && !respNu.equals(nu, ignoreCase = true)) continue
            val respCom = json.optString("com")
            if (respCom.isNotBlank() && respCom != com.code) continue

            val arr = json.optJSONArray("data") ?: continue
            if (arr.length() == 0) continue

            val traces = mutableListOf<Trace>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue           // E7：类型防护
                traces += Trace(o.optString("ftime", o.optString("time")), o.optString("context"))
            }
            if (traces.isEmpty()) continue
            if (traces.any { it.context.contains("查无结果") || it.context.contains("暂无结果") }) continue

            val ischeck = json.optString("ischeck")                  // E4：签收正向证据
            val state = json.optString("state", "")
            val stateText = stateTextOf(state, traces, ischeck)
            val res = Result(true, prettyName(com.code, com.name), stateText, traces, "ok")
            synchronized(cache) { cache[cacheKey] = System.currentTimeMillis() to res }
            return res
        }

        // ---- 3) 失败文案（E6：区分限流 / 网络 / 查不到） ----
        val fail = when {
            limited > 0 && networkFailures == 0 && serverErr == 0 ->
                "查询过于频繁，被限流了，请稍等几秒再试"
            networkFailures > 0 && limited == 0 && serverErr == 0 ->
                "网络异常，请检查网络后重试"
            serverErr > 0 && networkFailures == 0 && limited == 0 ->
                "快递接口暂时不可用（或该单号不支持查询），请稍后再试"
            limited + networkFailures + serverErr >= candidates.size ->
                "网络异常或查询接口繁忙，请稍等几秒再试"
            else ->
                "没查到该单号的轨迹（尝试了：${candidates.joinToString("、") { it.name }}）。" +
                    "可能是单号有误、刚揽收未同步或小快递公司，稍后再试"
        }
        val result = Result(false, candidates.first().name, "", emptyList(), fail)
        synchronized(cache) { cache[cacheKey] = System.currentTimeMillis() to result }   // E12：失败短缓存
        return result
    }

    /** E3：状态码表补全；E4：签收需正向证据；兜底用最新一条轨迹（data 倒序） */
    private fun stateTextOf(code: String, traces: List<Trace>, ischeck: String): String {
        val text = when (code) {
            "0" -> "运输中"
            "1" -> "已揽收"
            "2" -> "疑难件"
            "3" -> "已签收 ✔"
            "4" -> "已退签"
            "5" -> "派件中 🚚"
            "6" -> "退回中"
            "7" -> "转投中"
            "8" -> "清关中"
            "10" -> "待清关"
            "11" -> "清关中"
            "12" -> "已清关"
            "14" -> "拒签"
            else -> null
        }
        if (code == "3") {
            // E4：签收要有正向证据（ischeck=1 或轨迹含签收/妥投），否则按轨迹判
            val positive = ischeck == "1" ||
                traces.any { it.context.contains("签收") || it.context.contains("妥投") }
            if (!positive) return keywordState(traces)
        }
        return text ?: keywordState(traces)
    }

    /** 兜底：按最新一条轨迹关键字（data 为倒序，第一条最新） */
    private fun keywordState(traces: List<Trace>): String {
        val t = traces.firstOrNull()?.context ?: ""
        return when {
            t.contains("拒签") -> "拒签"
            t.contains("签收") || t.contains("妥投") -> "已签收 ✔"
            t.contains("派送") || t.contains("派件") -> "派件中 🚚"
            t.contains("运输") || t.contains("到达") || t.contains("中转") -> "运输中"
            t.contains("揽收") || t.contains("收件") -> "已揽收"
            t.contains("清关") -> "清关中"
            t.contains("下单") -> "已下单"
            t.contains("退") -> "退回中"
            else -> "状态更新中"
        }
    }

    /** 非 2xx 报错；403 直接标记限流（E6）；仅一次 500ms 有界退避（E2）。
     *  budgetMs 传入后单次请求超时不超过剩余预算（V4-8：总时限硬约束） */
    private suspend fun get(url: String, budgetMs: Long = 8_000): String {
        suspend fun once(): String {
            val conn = URL(url).openConnection() as HttpURLConnection
            val t = budgetMs.coerceIn(1_000, 8_000).toInt()
            conn.connectTimeout = t
            conn.readTimeout = t
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
        return try {
            once()
        } catch (e: IOException) {
            if (e.message?.contains("403") == true) throw e     // 限流不重试（E6）
            delay(500)                                          // 可取消（E2）
            once()
        }
    }
}
