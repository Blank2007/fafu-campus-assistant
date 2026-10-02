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

    /** 今日已缓存的句子（无网络时兜底显示） */
    fun cached(c: Context): Quote? {
        val p = prefs(c)
        if (p.getString("date", "") != LocalDate.now().toString()) return null
        val t = p.getString("text", "") ?: return null
        return if (t.isBlank()) null else Quote(t, p.getString("from", "") ?: "")
    }

    /** 拉取今日一句（已有缓存直接返回）；后台线程调用 */
    fun fetchToday(c: Context): Quote? {
        cached(c)?.let { return it }
        val q = fetch() ?: return null
        save(c, q)
        return q
    }

    /** 强制换一句（小部件点击）；后台线程调用。避免换到同一句 */
    fun fetchNew(c: Context): Quote? {
        val old = prefs(c).getString("text", "")
        var q = fetch()
        if (q != null && q.text == old) q = fetch() ?: q
        if (q != null) save(c, q)
        return q
    }

    /** 每次打开 App 拉一句新的（首页）；后台线程调用 */
    fun fetchFresh(c: Context): Quote? = fetchNew(c)

    /** 诗词 + 文学分类 */
    private fun fetch(): Quote? = runCatching {
        val conn = URL("https://v1.hitokoto.cn/?c=i&c=d&encode=json")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
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
