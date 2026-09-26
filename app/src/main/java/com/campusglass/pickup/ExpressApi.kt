package com.campusglass.pickup

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/**
 * 快递物流查询（App 内直出结果，不走浏览器）。
 *
 * 接口：快递100 开放查询页同款接口（免鉴权 JSON，实测可用）：
 *  1) https://www.kuaidi100.com/autonumber/autoComNum?resultv=2&text=<单号>  自动识别快递公司
 *  2) https://www.kuaidi100.com/query?type=<公司>&postid=<单号>&temp=<随机>   查询物流轨迹
 * 参考站点：kuaidi100.com（平台服务，仅查询展示用途）。
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

    /** 阻塞查询（协程 IO 线程调用） */
    fun query(nu: String): Result = runCatching {
        // ---- 1) 识别快递公司 ----
        val autoJson = get("https://www.kuaidi100.com/autonumber/autoComNum?resultv=2&text=$nu")
        val auto = JSONObject(autoJson)
        val first = auto.optJSONArray("auto")?.optJSONObject(0)
        val comCode = first?.optString("comCode").orEmpty()
        val comName = first?.optString("name").orEmpty().ifBlank { "未知快递公司" }
        if (comCode.isBlank()) {
            return Result(false, comName, "", emptyList(), "没识别出快递公司，请核对单号")
        }

        // ---- 2) 查询轨迹 ----
        val q = get(
            "https://www.kuaidi100.com/query?type=$comCode&postid=$nu" +
                "&temp=0.${Random.nextInt(10, 99)}&phone="
        )
        val json = JSONObject(q)
        if (json.optString("status") != "200") {
            return Result(false, comName, "", emptyList(), json.optString("message", "查询失败"))
        }
        val stateCode = json.optString("state", "")
        val traces = mutableListOf<Trace>()
        val arr = json.optJSONArray("data")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                traces += Trace(o.optString("ftime", o.optString("time")), o.optString("context"))
            }
        }
        // state: 0在途 1揽收 2疑难 3签收 4退签 5派件 6退回 7转投
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
        if (traces.isEmpty()) {
            Result(false, comName, stateText, emptyList(), "暂无轨迹，稍后再试")
        } else {
            Result(true, comName, stateText, traces, "ok")
        }
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
