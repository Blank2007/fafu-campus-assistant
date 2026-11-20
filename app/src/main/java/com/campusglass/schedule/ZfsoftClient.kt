package com.campusglass.schedule

import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.nio.charset.Charset

/** 教务抓取过程中的可展示错误（message 直接给用户看，不要放技术栈） */
class ZfsoftException(message: String) : Exception(message)

/**
 * 正方教务系统（旧版 V6）原生 HTTP 客户端 —— 零第三方依赖，只用 java.net。
 *
 * 本机实测的四个关键点（改动前请重看）：
 *  1. 服务器**不下发 Cookie**，会话靠 URL 里的 (一串会话ID)/ 前缀维持：
 *     任何请求都会 302 到 http://jwgl.fafu.edu.cn/(xxxx)/xxx.aspx，后续请求必须带这段前缀。
 *     所以这里每次都从跳转地址里动态抠前缀，绝不写死。
 *  2. 全站 GB2312：请求体、URL 参数、响应解码都要 GB2312，用 UTF-8 会一直登录失败。
 *  3. 登录表单 RadioButtonList1 的值是中文「学生」，且必须先过验证码（校验顺序：验证码 → 密码）。
 *  4. 课表页 xskbcx.aspx 必须带 xh（学号）、xm（姓名）与 Referer（xs_main.aspx），否则 302 回登录页。
 */
object ZfsoftClient {

    val CHARSET: Charset = Charset.forName("GB2312")

    private const val UA =
        "Mozilla/5.0 (Linux; Android 15; Pixel) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    data class Site(val label: String, val base: String)

    /** 金山学院是用户所在学院；校本部留作备选 */
    val SITES = listOf(
        Site("金山学院", "http://jsxyjwgl.fafu.edu.cn"),
        Site("校本部", "http://jwgl.fafu.edu.cn"),
    )

    val DEFAULT_SITE: Site get() = SITES.first()

    /** 课表页固定参数（正方左菜单里的功能号） */
    private const val GNMKDM = "N121601"

    /**
     * 一次完整的抓取会话。每个会话内部有状态（前缀 / __VIEWSTATE / cookie），
     * 不能跨页面复用，也不能并发调用。
     */
    class Session(val site: Site = DEFAULT_SITE) {

        private var prefix = ""
        private var loginUrl = ""
        private var viewState = ""
        private val cookies = LinkedHashMap<String, String>()

        /**
         * 打开登录页并取回验证码图片字节（PNG/GIF，实测 72×27，显示时请放大）。
         *
         * 两台服务器部署不一样，这里是实测差异，别写死：
         *  - 校本部 jwgl：根路径 302 → /(会话ID)/default2.aspx（表单 action=default2.aspx，带前缀）
         *  - 金山学院 jsxyjwgl：根路径直接 200（表单 action=default.aspx，**没有**会话前缀）
         * 所以登录提交地址与验证码地址都从页面里的 form action / img src 现抓，
         * 没有前缀时按根路径拼，有前缀时按当前页面相对路径解析。
         */
        fun loadLoginPage(): ByteArray {
            cookies.clear()
            prefix = ""
            viewState = ""

            val first = get(site.base + "/")
            var html = first.text()
            var pageUrl = first.finalUrl.ifBlank { site.base + "/" }
            takePrefixFrom(pageUrl)

            if (findViewState(html).isBlank()) {
                // 根路径不是登录表单（跳转页 / 别的入口），再试一次传统地址
                val guess = site.base + prefix + "default2.aspx"
                val second = get(guess)
                html = second.text()
                pageUrl = second.finalUrl.ifBlank { guess }
                takePrefixFrom(pageUrl)
            }
            viewState = findViewState(html)
            if (viewState.isBlank()) {
                throw ZfsoftException("没有拿到登录页（可能教务处网站改版或手机网络异常），请稍后重试")
            }

            val action = formAction(html)
            loginUrl = if (action.isBlank()) {
                site.base + prefix + "default2.aspx"
            } else {
                absoluteUrl(action, pageUrl)
            }

            val codeSrc = captchaSrc(html)
            val codeUrl = if (codeSrc.isBlank()) {
                site.base + prefix + "CheckCode.aspx"
            } else {
                absoluteUrl(codeSrc, pageUrl)
            }
            val code = get(codeUrl, referer = loginUrl)
            if (code.code != 200 || code.bytes.size < 100) {
                throw ZfsoftException("验证码图片没下载下来（网络不稳），请重试")
            }
            return code.bytes
        }

        /** 登录；成功返回教务里的姓名（可能为空串），失败抛 [ZfsoftException] */
        fun login(studentId: String, password: String, code: String): String {
            if (studentId.isBlank()) throw ZfsoftException("请先填学号")
            if (password.isBlank()) throw ZfsoftException("请先填密码")
            if (viewState.isBlank()) throw ZfsoftException("验证码已过期，请点图片刷新后再试")

            val body = form(
                "__VIEWSTATE" to viewState,
                "txtUserName" to studentId.trim(),
                // 金山学院部署里可见的用户名框是 Textbox1（脚本再同步到 txtUserName），两个都带最稳
                "Textbox1" to studentId.trim(),
                "TextBox2" to password,
                "txtSecretCode" to code.trim(),
                "RadioButtonList1" to "学生",
                "Button1" to "",
                "lbLanguage" to "",
                "hidPdrs" to "",
                "hidsc" to "",
            )
            val resp = post(loginUrl, body, referer = loginUrl)
            if (resp.code in 300..399) {
                val loc = resp.location.orEmpty()
                if (loc.isBlank()) throw ZfsoftException("登录响应异常（跳转地址为空）")
                val next = absoluteUrl(loc, cur = loginUrl)
                takePrefixFrom(next)
                return runCatching { fetchName(studentId.trim()) }.getOrDefault("")
            }
            val msg = alertText(resp.text())
            throw ZfsoftException(msg.ifBlank { "登录失败：请核对学号、密码与验证码" })
        }

        /** 抓课表页 HTML（GB2312 解码后返回） */
        fun fetchScheduleHtml(studentId: String, name: String): String {
            val sid = percentEncode(studentId.trim())
            val url = site.base + prefix + "xskbcx.aspx?xh=" + sid +
                "&xm=" + percentEncode(name) + "&gnmkdm=" + GNMKDM
            val referer = site.base + prefix + "xs_main.aspx?xh=" + sid
            val resp = get(url, referer = referer)
            if (resp.code in 300..399) throw ZfsoftException("登录状态已失效，请重新登录后再抓取")
            val html = resp.text()
            if (html.contains("txtSecretCode") && html.contains("验证码")) {
                throw ZfsoftException("登录状态已失效，请重新登录后再抓取")
            }
            if (html.isBlank()) throw ZfsoftException("课表页返回了空内容，请重试")
            return html
        }

        // ---------------- 内部实现 ----------------

        private class Resp(val code: Int, val bytes: ByteArray, val finalUrl: String, val location: String?) {
            fun text(): String = String(bytes, CHARSET)
        }

        private fun fetchName(studentId: String): String {
            val url = site.base + prefix + "xs_main.aspx?xh=" + percentEncode(studentId)
            val html = get(url, referer = site.base + prefix + "xs_main.aspx").text()
            val raw = Regex("""id="xhxm"[^>]*>\s*([^<]+?)\s*<""").find(html)?.groupValues?.get(1).orEmpty()
            return raw.replace("同学", "").trim()
        }

        private fun request(url: String, method: String, body: ByteArray?, referer: String?): Resp {
            val conn = (URL(url).openConnection(Proxy.NO_PROXY) as HttpURLConnection)
            try {
                conn.requestMethod = method
                conn.connectTimeout = 10_000
                conn.readTimeout = 20_000
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                conn.setRequestProperty("User-Agent", UA)
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,image/*,*/*;q=0.8")
                conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
                if (referer != null) conn.setRequestProperty("Referer", referer)
                if (cookies.isNotEmpty()) {
                    conn.setRequestProperty(
                        "Cookie",
                        cookies.entries.joinToString("; ") { it.key + "=" + it.value },
                    )
                }
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    conn.setFixedLengthStreamingMode(body.size)
                    conn.outputStream.use { it.write(body) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
                collectCookies(conn)
                val location = conn.getHeaderField("Location")
                return Resp(code, bytes, url, location)
            } finally {
                conn.disconnect()
            }
        }

        private fun collectCookies(conn: HttpURLConnection) {
            val fields = conn.headerFields ?: return
            fields.entries
                .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
                .flatMap { it.value ?: emptyList() }
                .forEach { line ->
                    val pair = line.substringBefore(';')
                    val name = pair.substringBefore('=', "").trim()
                    val value = pair.substringAfter('=', "").trim()
                    if (name.isNotEmpty()) cookies[name] = value
                }
        }

        private fun get(url: String, referer: String? = null, maxHops: Int = 6): Resp {
            var cur = url
            var ref = referer
            var last = request(cur, "GET", null, ref)
            for (hop in 0 until maxHops) {
                val loc = last.location
                if (last.code !in 300..399 || loc.isNullOrBlank()) return last
                cur = absoluteUrl(loc, cur)
                ref = cur
                takePrefixFrom(cur)
                last = request(cur, "GET", null, ref)
            }
            return last
        }

        private fun post(url: String, body: ByteArray, referer: String?): Resp =
            request(url, "POST", body, referer)

        /** 从 URL 里抠出 /(会话ID)/ 前缀（含前后斜杠，拼地址时直接接在站点域名后面） */
        private fun takePrefixFrom(url: String) {
            val m = Regex("""https?://[^/]+(/\([^)]*\)/)""").find(url) ?: return
            prefix = m.groupValues[1]
        }

        private fun findViewState(html: String): String {
            Regex("""<input[^>]*name="__VIEWSTATE"[^>]*value="([^"]*)"""")
                .find(html)?.groupValues?.get(1)?.let { return it }
            return Regex("""<input[^>]*value="([^"]*)"[^>]*name="__VIEWSTATE"""")
                .find(html)?.groupValues?.get(1)
                ?: ""
        }

        /** 页面里登录表单的 action（相对地址原样返回） */
        private fun formAction(html: String): String =
            Regex("""<form[^>]*\baction\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)?.trim().orEmpty()

        /** 页面里验证码图片的 src（相对地址原样返回） */
        private fun captchaSrc(html: String): String {
            val img = Regex("""<img[^>]*\bid\s*=\s*["']icode["'][^>]*>""", RegexOption.IGNORE_CASE)
                .find(html)?.value
            if (img != null) {
                Regex("""\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    .find(img)?.groupValues?.get(1)?.trim()?.let { if (it.isNotBlank()) return it }
            }
            return Regex("""\bsrc\s*=\s*["']([^"']*CheckCode[^"']*)["']""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)?.trim().orEmpty()
        }

        private fun alertText(html: String): String =
            Regex("""alert\(\s*['"]([^'"]*)['"]\s*\)""").find(html)?.groupValues?.get(1).orEmpty()
    }

    /** 相对/绝对地址 → 绝对地址（按标准 URL 规则以 cur 为基准，保留 (会话ID)/ 目录前缀） */
    fun absoluteUrl(loc: String, cur: String): String {
        if (loc.startsWith("http://") || loc.startsWith("https://")) return loc
        return runCatching { URL(URL(cur), loc).toString() }.getOrDefault(loc)
    }

    /** GB2312 百分号编码（表单与 URL 参数通用） */
    fun percentEncode(s: String): String {
        val sb = StringBuilder()
        for (b in s.toByteArray(CHARSET)) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                sb.append(ch)
            } else {
                sb.append('%')
                sb.append("0123456789ABCDEF"[c shr 4])
                sb.append("0123456789ABCDEF"[c and 0x0F])
            }
        }
        return sb.toString()
    }

    private fun form(vararg pairs: Pair<String, String>): ByteArray {
        val sb = StringBuilder()
        pairs.forEachIndexed { i, pair ->
            if (i > 0) sb.append('&')
            sb.append(percentEncode(pair.first)).append('=').append(percentEncode(pair.second))
        }
        return sb.toString().toByteArray(CHARSET)
    }
}
