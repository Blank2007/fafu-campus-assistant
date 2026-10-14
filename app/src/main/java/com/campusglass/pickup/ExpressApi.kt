package com.campusglass.pickup

import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 快递100 网页同款接口（只读）· v6（v4.17）。
 *
 * v4.17 关键修正（实测 2026-10-15，测试单号极兔）：
 * - **不再用 `www.kuaidi100.com`，改用 `m.kuaidi100.com`**：同一单号同一参数，
 *   www 连续 12 次全部返回"查无结果"，m 站第 2 次/第 5 次就拿到了真实轨迹。
 * - **接口是"概率性"的**：同一单号单次请求只有一部分会返回轨迹，其余返回
 *   `condition=F00` + `data:[{"context":"查无结果"}]`（多节点缓存不一致）。
 *   所以**同一个公司必须重试若干次**——原来只试一次就换下一个候选，
 *   这才是"什么单号都查不到"的真正原因。
 * - **不再只看 `condition` 判空**：实测存在 `condition=F00` 但 `data` 里有 12 条真实轨迹的响应，
 *   一律以 `data` 内容为准。
 * - 网页端没有可爬的服务端 HTML：`m.kuaidi100.com/result.jsp` 是 Vue 单页应用，
 *   它自己也是调这个 `/query` 接口（见 `result.js` 的 `request("/query", …)`），
 *   所以"爬网页"和"调接口"拿到的是同一份数据。
 *
 * 其它（沿用 v5）：suspend + 可取消（E2）；正向证据判定签收（E4）；状态码表补全（E3）；
 * 限流单独识别（E6）；data 元素类型防护（E7）；缓存键大小写归一 + 失败短缓存 + 强制刷新（E12）。
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

    /** v4.17：m 站命中率明显好于 www（www 实测连续 12 次全空） */
    private const val HOST = "m.kuaidi100.com"

    private const val TOTAL_BUDGET_MS = 25_000L      // E2：整轮总时限（v4.17 由 20s 放宽到 25s，留给重试）

    /**
     * v4.17：同一家公司最多试几次。
     * 实测（2026-10-15，连发 15 次）：该免费接口单次命中率约 13%~25%，
     * 其余返回 `condition=F00`「查无结果」。取 5 次 → 单公司约 50%~76%，
     * 两个候选公司合计约 75%~94%。失败响应通常很快（~0.15s），所以多试几次成本不高。
     */
    private const val TRY_PER_COM = 5

    /** v4.17：同一家公司两次尝试之间的间隔（"查无结果"通常是极快的缓存未命中，不用等太久） */
    private const val RETRY_GAP_MS = 250L

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

    /** 代码 → 中文名兜底（E12：不暴露英文内部代码） */    private val CODE_NAMES = mapOf(
        "shunfeng" to "顺丰速运", "yuantong" to "圆通速递", "zhongtong" to "中通快递",
        "shentong" to "申通快递", "yunda" to "韵达速递", "jtexpress" to "极兔速递",
        "jd" to "京东物流", "debang" to "德邦快递", "ems" to "EMS",
        "youzhengguonei" to "邮政包裹", "youzhengzhengyou" to "邮政快递",
        "annengwuliu" to "安能物流", "quanfengkuaidi" to "全峰快递",
        "youshuwuliu" to "优速物流", "zhaijisong" to "宅急送",
    )

    private data class Com(val code: String, val name: String)

    /**
     * UI 展示用的「能自动识别的常见快递」清单（v4.16）。
     *
     * ⚠️ 这不是白名单 —— [query] 还会用快递100 的 autoComNum 识别更多公司；
     * 这里列的是离线前缀表 + 常见代码表里认识的名字，给用户一个"大概覆盖哪些"的概念，
     * 免得查不到时以为是 App 坏了。
     */
    val SUPPORTED_COURIERS: List<String> = CODE_NAMES.values.distinct()

    const val SUPPORTED_HINT =
        "免费数据源偶尔查不到，会自动重试几次；仍无结果时可点结果卡里的「去快递100 网页查」"

    /**
     * v4.17：这条"轨迹"是不是占位条目（不是真实物流）。
     *
     * 免费接口查不到时会返回 `data:[{"context":"查无结果"}]`，把它当成轨迹显示出去
     * 就变成了"查到一条查无结果"，所以这里统一剔掉。抽成独立函数是为了能单测。
     */
    internal fun isPlaceholderTrace(context: String): Boolean =
        context.isBlank() || context.contains("查无结果") || context.contains("暂无结果")

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

    /**
     * 离线猜候选快递公司（**不发任何网络请求**），用于输入框下方的实时提示。
     * 与 [query] 内部的候选生成同源（都走 [prefixCandidates]），避免两处规则跑偏。
     */
    fun guessCompanies(rawNu: String): List<String> =
        prefixCandidates(normalize(rawNu)).map { it.name }

    /**
     * 按单号前缀与位数规则猜候选公司（纯本地规则，确定性）。
     * - 前缀表：[PREFIX_MAP]
     * - 纯数字且 10~13 位：补一个"邮政包裹"（邮政单号没有字母前缀，只能按位数兜）
     */
    private fun prefixCandidates(nu: String): List<Com> {
        if (nu.length < 4) return emptyList()          // 太短必然误报，直接不给
        val upper = nu.uppercase()
        val out = mutableListOf<Com>()
        PREFIX_MAP.forEach { (p, pair) ->
            val (code, name) = pair
            if (upper.startsWith(p) && out.none { it.code == code }) out += Com(code, name)
        }
        if (nu.length in 10..13 && nu.all { it.isDigit() } && out.none { it.code == "youzhengguonei" }) {
            out += Com("youzhengguonei", "邮政包裹")
        }
        return out
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
        prefixCandidates(nu).forEach { c -> if (candidates.none { it.code == c.code }) candidates += c }
        if (candidates.isEmpty()) {
            return Result(false, "未知", "", emptyList(), "没识别出快递公司，请核对单号")
        }

        // ---- 2) 依序试查（每家公司重试若干次）+ 交叉校验 ----
        var networkFailures = 0
        var limited = 0                                  // E6：限流计数
        var serverErr = 0                                // V4-25：服务端异常（不是限流）
        var emptyHits = 0                                // v4.17：接口正常返回、但这次没给轨迹
        for ((idx, com) in candidates.withIndex()) {
            if (System.currentTimeMillis() > deadline) break        // E2：总时限
            if (idx > 0) delay(400)                                  // 可取消（E2）

            // v4.17：这家公司最多试 TRY_PER_COM 次。
            // 实测该免费接口是"概率性"的：同一单号、同一参数，一部分请求返回真实轨迹，
            // 其余返回 condition=F00「查无结果」（多节点缓存不一致）。
            // 原来只试一次就换下一个候选 —— 这正是"什么单号都查不到"的真正原因。
            for (attempt in 0 until TRY_PER_COM) {
                if (System.currentTimeMillis() > deadline) break
                if (attempt > 0) delay(RETRY_GAP_MS)                 // 可取消（E2）

                val remain = (deadline - System.currentTimeMillis()).coerceIn(1_000, TOTAL_BUDGET_MS)   // V4-8
                // 参数与快递100 网页端（m.kuaidi100.com/result.jsp）对齐：id/valicode/temp/phone。
                // temp 用随机数是为了绕开 CDN 缓存，尽量拿到"新鲜"的那一份。
                val q = try {
                    get(
                        "https://$HOST/query?type=${com.code}&postid=$encoded" +
                            "&id=1&valicode=&temp=${Math.random()}&phone=",
                        remain,
                    )
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

                val respNu = json.optString("nu").replace(" ", "")
                if (respNu.isNotBlank() && !respNu.equals(nu, ignoreCase = true)) continue
                val respCom = json.optString("com")
                if (respCom.isNotBlank() && respCom != com.code) continue

                val arr = json.optJSONArray("data")
                val traces = mutableListOf<Trace>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue        // E7：类型防护
                        val ctx = o.optString("context")
                        // v4.17：把"查无结果 / 暂无结果"这类占位条目剔掉，只看真实轨迹
                        if (isPlaceholderTrace(ctx)) continue
                        traces += Trace(o.optString("ftime", o.optString("time")), ctx)
                    }
                }
                // v4.17：**不再**用 condition 判空。实测存在 condition=F00 却带 12 条真实轨迹的响应，
                // 所以一律以 data 里有没有真实轨迹为准；没有就算这次没拿到，进入下一次重试。
                if (traces.isEmpty()) { emptyHits++; continue }

                val ischeck = json.optString("ischeck")                  // E4：签收正向证据
                val state = json.optString("state", "")
                val stateText = stateTextOf(state, traces, ischeck)
                val res = Result(true, prettyName(com.code, com.name), stateText, traces, "ok")
                synchronized(cache) { cache[cacheKey] = System.currentTimeMillis() to res }
                return res
            }
        }

        // ---- 3) 失败文案（E6：区分限流 / 网络 / 查不到） ----
        val fail = when {
            limited > 0 && networkFailures == 0 && serverErr == 0 ->
                "查询过于频繁，被限流了，请稍等几秒再试"
            networkFailures > 0 && limited == 0 && serverErr == 0 && emptyHits == 0 ->
                "网络异常，请检查网络后重试"
            serverErr > 0 && networkFailures == 0 && limited == 0 && emptyHits == 0 ->
                "快递接口暂时不可用（或该单号不支持查询），请稍后再试"
            emptyHits > 0 ->
                // v4.17：接口正常、也已经重试过，但数据源确实没给数据。
                // 和网络/接口故障分开讲，否则用户会以为 App 坏了。
                "快递100 免费数据源暂时没有这条单号的记录" +
                    "（已重试 ${emptyHits} 次，试过：${candidates.joinToString("、") { it.name }}）。\n" +
                    "常见原因：① 刚揽收，物流还没同步　② 该快递公司未被免费源收录　③ 单号有误。\n" +
                    "可以稍后再试，或点下方「去快递100 网页查」看官方结果"
            else ->
                "网络异常或查询接口繁忙，请稍等几秒再试"
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
            t.contains("签收") || t.contains("妥投") || t.contains("已投递") -> "已签收 ✔"   // v4.17：补"已投递"
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
