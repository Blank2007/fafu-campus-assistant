package com.campusglass.pickup

/**
 * 快递查询历史的持久化格式（纯逻辑，无 Android 依赖，便于单测）。
 *
 * v4.15：历史从「一串光秃秃的单号」升级为结构化条目 —— 带上快递公司、最后状态与时间。
 * 原来历史列表只显示单号，用户根本认不出哪个是哪个，等于没用。
 *
 * **存储格式**：每行一条，字段以 `\t` 分隔（单号 / 公司 / 状态 / 时间毫秒）。
 * 用 `\t` 而不是 `|`：单号与状态里不会出现制表符，而 `|` 在从网页复制文本时有可能混进来。
 * 旧版是"每行一个纯单号"，[parse] 会自动识别并兼容（公司/状态留空、时间为 0）。
 */
object ExpressHistory {

    /** 最多保留多少条历史（与旧版一致） */
    const val MAX = 10

    private const val SEP = '\t'

    data class Entry(
        val nu: String,
        val com: String = "",
        val state: String = "",
        val time: Long = 0L,
    ) {
        /** 是否已签收 —— 供「只看未签收」筛选使用 */
        val signed: Boolean
            get() = state.contains("签收") || state.contains("妥投")
    }

    /** 编码为一行一条的文本；公司/状态里若混入制表符则替换成空格（防串列） */
    fun encode(list: List<Entry>): String = list.joinToString("\n") { e ->
        listOf(e.nu, e.com.replace(SEP, ' '), e.state.replace(SEP, ' '), e.time.toString())
            .joinToString(SEP.toString())
    }

    /**
     * 解析历史文本，**同时兼容两种格式**：
     * - 新版：`单号 \t 公司 \t 状态 \t 时间毫秒`
     * - 旧版：`单号`（公司/状态留空、时间为 0，下次查询成功后会自动回填）
     *
     * 非法行直接丢弃；单号做大小写去重并保持原有顺序。
     */
    fun parse(raw: String?): List<Entry> {
        if (raw.isNullOrBlank()) return emptyList()
        val seen = LinkedHashSet<String>()
        val out = mutableListOf<Entry>()
        for (line in raw.split("\n")) {
            if (line.isBlank()) continue
            val f = line.split(SEP)
            val nu = f.getOrNull(0)?.trim().orEmpty()
            if (nu.isBlank()) continue
            if (!seen.add(nu.uppercase())) continue
            out += Entry(
                nu = nu,
                com = f.getOrNull(1)?.trim().orEmpty(),
                state = f.getOrNull(2)?.trim().orEmpty(),
                time = f.getOrNull(3)?.trim()?.toLongOrNull() ?: 0L,
            )
            if (out.size >= MAX) break
        }
        return out
    }

    /**
     * 插入或更新一条：置顶 + 按单号（不分大小写）去重 + 截断到 [MAX]。
     *
     * 传入的 [entry] 中 **公司/状态为空时会保留原值** —— 这样"开始查询"时可以用
     * `Entry(nu, time = now)` 把条目提到最前而不冲掉上次查到的公司与状态。
     * 单号写法也沿用已有记录的那一份，免得同一个号在列表里一会儿大写一会儿小写。
     */
    fun upsert(list: List<Entry>, entry: Entry): List<Entry> {
        val old = list.firstOrNull { it.nu.equals(entry.nu, ignoreCase = true) }
        val merged = Entry(
            nu = old?.nu ?: entry.nu,
            com = entry.com.ifBlank { old?.com.orEmpty() },
            state = entry.state.ifBlank { old?.state.orEmpty() },
            time = entry.time,
        )
        return (listOf(merged) + list.filterNot { it.nu.equals(entry.nu, ignoreCase = true) }).take(MAX)
    }

    /**
     * 相对时间文案（历史列表用）。时间为 0 或异常时返回空串。
     * 超过一周直接给日期，免得"23 天前"这种反而不好读。
     */
    fun relativeTime(nowMs: Long, tMs: Long): String {
        if (tMs <= 0L) return ""
        val diff = nowMs - tMs
        return when {
            diff < 60_000L -> "刚刚"                                  // 含时钟回拨导致的负数
            diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
            diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
            diff < 7 * 86_400_000L -> "${diff / 86_400_000L} 天前"
            else -> java.time.Instant.ofEpochMilli(tMs)
                .atZone(java.time.ZoneId.systemDefault())
                .let { "${it.monthValue} 月 ${it.dayOfMonth} 日" }
        }
    }

    /** 历史条目的副标题：公司 · 状态 · 时间（各项为空则自动省略） */
    fun subtitle(e: Entry, nowMs: Long): String =
        listOf(e.com, e.state, relativeTime(nowMs, e.time))
            .filter { it.isNotBlank() }
            .joinToString(" · ")
}
