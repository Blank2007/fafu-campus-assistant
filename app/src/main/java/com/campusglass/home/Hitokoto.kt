package com.campusglass.home

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

data class Quote(val text: String, val from: String)

/**
 * 一言（hitokoto.cn）每日诗句。
 * 每日缓存一句；小部件/首页共用。网络调用必须在后台线程。
 */
object Hitokoto {

    private fun prefs(c: Context) = c.getSharedPreferences("hitokoto", Context.MODE_PRIVATE)

    private fun save(c: Context, q: Quote) {
        prefs(c).edit()
            .putString("date", LocalDate.now().toString())
            .putString("text", q.text)
            .putString("from", q.from)
            .apply()
    }

    /** 显示兜底：上次保存的一句（不论哪天；断网/获取失败时显示这一条） */
    fun cached(c: Context): Quote? {
        val t = prefs(c).getString("text", "") ?: return null
        return if (t.isBlank()) null else Quote(t, prefs(c).getString("from", "") ?: "")
    }

    /** 是否需要拉取（当天没拉过才拉，A6：不强刷） */
    fun needsFetch(c: Context): Boolean =
        prefs(c).getString("date", "") != LocalDate.now().toString()

    /** 拉取今日一句（当天已拉过直接返回；拉不到用之前的一条）；后台线程调用 */
    fun fetchToday(c: Context): Quote? {
        val p = prefs(c)
        if (p.getString("date", "") == LocalDate.now().toString()) return cached(c)
        val q = fetch() ?: return cached(c)      // 失败兜底：之前一条
        save(c, q)
        return q
    }

    /** 强制换一句（小部件点击/首页每次打开）；后台线程调用。连拉最多 3 次确保不是同一句 */
    fun fetchNew(c: Context): Quote? {
        val q = fetch() ?: return cached(c)     // W-3：单次拉取；拉不到用之前一条
        save(c, q)
        return q
    }

    /** 每次打开 App 拉一句新的（首页）；后台线程调用 */
    fun fetchFresh(c: Context): Quote? = fetchNew(c)

    /** 诗词 + 文学分类；时间戳破接口缓存（同一请求会返回同一句） */
    private fun fetch(): Quote? = runCatching {
        val conn = URL(
            "https://v1.hitokoto.cn/?c=i&c=d&encode=json&_=${System.currentTimeMillis()}"
        )
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 3_000
        conn.readTimeout = 4_000
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            return null
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val o = JSONObject(body)
        val text = o.optString("hitokoto").trim()
        if (text.isBlank()) null else Quote(text, o.optString("from").trim())
    }.getOrNull()
}
