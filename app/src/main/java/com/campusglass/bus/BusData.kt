package com.campusglass.bus

import java.util.Locale

/**
 * 福建农林大学南平校区（南平市建阳区童游大街1号）附近公交数据。
 *
 * 来源（2026-09-27 核实）：建阳公交官方通告（大武夷新闻网 2025-08-27）· 海峡网/今日头条（早高峰密集班次）·
 * 无敌电动公交数据 2026-08/09（发车时刻与走向调整核实）· 高德经停信息。
 * 标注“推算”的时刻按间隔估算，实际以站牌/「掌上公交」APP 为准。
 *
 * v3.3 修复：
 *  - departureTimes 会补上标称末班（旧版循环在超过末班时停止，最后一班常常早于末班：103 路 20:50 vs 21:00 等）；
 *  - 时刻字符串固定用 Locale.US 格式化，避免某些语言环境下数字被本地化导致「上午/下午/晚间」分组错乱；
 *  - 回程站点改为「去程站点互逆」自动生成，不再让同一线路的上下行出现两个不同终点站/不同站名；
 *  - 103 路间隔文案与数据对齐（数据只有一个平峰间隔，晚间并没有单独的 20 分钟间隔）；
 *  - 105 路走向说明不再罗列站点表中并不存在的站名。
 */

/**
 * 站点表说明（v3.3）：
 * 旧版为 105/107 路单独维护了 downStops，但两个方向互相矛盾：
 *  - 105 路：去程终点「新岭公交总站」/回程起点「新岭公交枢纽总站」；去程「瑞玺苑」/回程「瑞云苑」；
 *    去程「图书馆」/回程「水之厅」；回程多出「和谐苑」且缺少「郑氏骨科医院」。
 *  - 107 路：回程多出「崇阳新都」，缺少「万晟星城」「郑氏骨科医院」。
 * 无法核实哪一侧为准，故统一按「去程站点集合互逆」生成回程，站点名称一律以去程为准；
 * 未能核实的单向站名（新岭公交枢纽总站 / 瑞云苑 / 水之厅 / 和谐苑 / 崇阳新都）暂不展示，
 * 实际情况请以站牌为准（弹窗内已提示）。
 */
data class BusRoute(
    val name: String,
    val endpoints: String,
    val firstDeparture: String,     // 首班 "7:30"
    val lastDeparture: String,      // 末班 "21:00"
    val fare: String,
    val intervalNote: String,
    val timeOfficial: Boolean,      // true=官方/实测时刻；false=按间隔推算
    val destTags: List<String>,     // 目的地筛选
    val mapKeyword: String = "",
    val note: String = "",
    val peakFrom: String? = null,
    val peakTo: String? = null,
    val peakIntervalMin: Int? = null,
    val offPeakIntervalMin: Int? = null,
    val fixedTimes: List<String>? = null,   // 固定班次时刻（有则直接用）
    val upStops: List<String> = emptyList(),
    val downStops: List<String> = emptyList(),
) {
    /** 回程站点：未单独给出时按去程互逆，保证两个方向不会再互相矛盾 */
    val down: List<String> get() = if (downStops.isNotEmpty()) downStops else upStops.reversed()
}

object BusData {

    const val CAMPUS_STOP = "福建农林大学南平校区"
    const val SERVICE_HOTLINE = "0599-8768567"
    const val SOURCE_NOTE =
        "数据来源：建阳公交官方通告 · 无敌电动公交数据(2026-08/09 核实) · 海峡网 · 高德经停\n" +
            "标注“推算”的时刻按发车间隔估算，实际以站牌为准；实时到站用「掌上公交」APP · 热线 $SERVICE_HOTLINE"

    const val STOPS_NOTE = "上下行站点按同一站点集合互逆排列；单向站点/临时调整以站牌为准"

    val DESTINATIONS = listOf("建发悦城", "万达广场", "动车站")

    val routes = listOf(
        BusRoute(
            name = "103路（青春快线）",
            endpoints = "福建农林大学南平校区 ⇄ 万达广场",
            firstDeparture = "7:45",
            lastDeparture = "21:00",
            fare = "全程一票制 1 元",
            // 修复：原文案写「早高峰 7:30-8:00」+「晚间约 20 分钟」，但数据最早的班次是 7:45，
            // 且结构里只有一个平峰间隔 15 分钟 —— 文案必须与能生成的时刻一致。
            intervalNote = "早高峰 7:45-8:00 每 10 分钟一班（官方，自新岭总站发班）；其余时段约 15 分钟一班（推算）",
            timeOfficial = true,
            destTags = listOf("万达广场"),
            mapKeyword = "建阳103路公交",
            note = "单程 7.5 公里约 15 分钟，直达商圈；首末班为「新岭公交总站」发车时刻，" +
                "在校区站等车请提前（2026-08 核实）",
            peakFrom = "7:45", peakTo = "8:00", peakIntervalMin = 10,
            offPeakIntervalMin = 15,
            upStops = listOf(
                "万达广场", "万达广场南", "滨江壹号", "童子山加油站", "高一路", "万辉路",
                "兴华啤酒", "创业园三期", "海源新材料", CAMPUS_STOP, "郑氏骨科医院", "新岭公交总站",
            ),
        ),
        BusRoute(
            name = "105路（园区直通车）",
            endpoints = "福建农林大学南平校区 ⇄ 赤岸小区（新区管委会）",
            firstDeparture = "6:50",
            lastDeparture = "18:30",
            fare = "全程一票制 1 元",
            intervalNote = "早高峰 7:30-8:00 每 10 分钟一班（官方）；平峰约 20 分钟（推算）",
            timeOfficial = true,
            destTags = emptyList(),
            mapKeyword = "建阳105路公交",
            note = "25 分钟跑完 14 公里；2026-08 走向有调整，站点以站牌为准",
            peakFrom = "7:30", peakTo = "8:00", peakIntervalMin = 10,
            offPeakIntervalMin = 20,
            upStops = listOf(
                "新区管委会", "武夷山水城（市农发行）", "南平大剧院", "图书馆", "一中初中部",
                "云谷校区", "瑞玺苑", "云谷实验学校", "建发和鸣", "法治公园", "碧全江誉",
                "南林小学", "崇阳街道", "南林", "巨电新能源", "海源新材料", CAMPUS_STOP,
                "郑氏骨科医院", "新岭公交总站",
            ),
        ),
        BusRoute(
            name = "107路（烟火漫游线）",
            endpoints = "福建农林大学南平校区 ⇄ 宋慈广场",
            firstDeparture = "7:15",
            lastDeparture = "21:00",
            fare = "全程一票制 1 元",
            intervalNote = "约 20 分钟一班（推算）",
            timeOfficial = false,
            destTags = listOf("建发悦城"),
            mapKeyword = "建阳107路公交",
            note = "首班 7:15（2026-09 核实，非 6:50）；去建发悦城坐到「童游农贸（烟草公司）」换乘建阳1路",
            offPeakIntervalMin = 20,
            upStops = listOf(
                "宋慈广场（水吉车站）", "市立医院", "金茂广场", "童游农贸（烟草公司）", "安居楼",
                "社区卫生中心", "曼头山路口", "和顺景园", "供电局", "检察院", "火车站",
                "万晟星城", "万晟皇庭", "周家", "赤岸", "滨江壹号", "崇阳北路", "实业集团",
                "广信街（新区管委会）", "嘉禾北路", "童子山加油站", "闽铝轻量化", "新五路",
                "海源新材料", CAMPUS_STOP, "郑氏骨科医院", "新岭公交总站",
            ),
        ),
        BusRoute(
            name = "武夷新区工业园区专线",
            endpoints = "社会福利中心（百龄帮）⇄ 武夷智谷（经停校区）",
            firstDeparture = "06:50",
            lastDeparture = "18:05",
            fare = "以站牌为准",
            intervalNote = "固定班次：06:50 / 07:55 / 14:30 / 18:05（核实时刻）",
            timeOfficial = true,
            destTags = emptyList(),
            note = "班次少但时刻固定，适合卡点出行；经停「福建农林大学南平校区」站",
            fixedTimes = listOf("06:50", "07:55", "14:30", "18:05"),
        ),
        BusRoute(
            name = "建阳K2路（西区公交站 ⇄ 高铁南平市站）",
            endpoints = "经停校区站，直达动车站（高铁南平市站），车程约 15 分钟",
            firstDeparture = "7:00",
            lastDeparture = "21:00",
            fare = "以站牌为准",
            intervalNote = "发车间隔约 23-90 分钟/趟（官方区间）",
            // 间隔区间来自官方，但下面生成的钟点时刻是估算的 → 仍需标注“推算”
            timeOfficial = false,
            destTags = listOf("动车站"),
            mapKeyword = "建阳K2路公交",
            note = "全程 25 站：西区公交站→教师进修学院→二院→宝山路口→汽车站→总工会→市立医院→鹏宇佳苑→安居楼→社区卫生中心→和顺景园→供电局→火车站→万晟星城→万晟皇庭→周家→赤岸→兴华啤酒→创业园三期→海源新材料→福建农林大学南平校区→新安路→贵口→芹口(将口收费站)→高铁南平市站",
        ),
        BusRoute(
            name = "建阳1路（去建发悦城的接驳线）",
            endpoints = "考亭书院 ⇄ 闽北卫校（经建发悦城、万达广场）",
            firstDeparture = "6:25",
            lastDeparture = "18:45",
            fare = "一票制 1 元",
            intervalNote = "约 15-19 分钟一班（推算）",
            timeOfficial = false,
            destTags = listOf("建发悦城", "万达广场"),
            mapKeyword = "建阳1路公交",
            note = "换乘点：童游农贸（烟草公司）/ 滨江壹号。途经：建发悦城—人民公园西门—童游农贸—…—滨江壹号—万达广场—…—闽北卫校",
            offPeakIntervalMin = 15,
        ),
        BusRoute(
            name = "K1路（高铁/武夷山 接驳参考）",
            endpoints = "武夷山景区 ⇄ 高铁南平市站",
            firstDeparture = "7:00",
            lastDeparture = "22:45",
            fare = "10 元",
            intervalNote = "快速通道线 7:00-20:00 / 8:20-22:45；303省道线 7:20-19:30 / 8:45-22:00",
            timeOfficial = true,
            destTags = listOf("动车站"),
            mapKeyword = "南平K1路公交",
            note = "不在校区设站，武夷山方向接驳参考；去动车站首选 K2 路",
        ),
    )

    /** 末班时刻是否可信：固定班次直接用；否则按首末班 + 间隔生成，并保证末班一定出现 */
    fun departureTimes(route: BusRoute): List<String> {
        route.fixedTimes?.let { return it }
        val step = route.offPeakIntervalMin ?: return emptyList()
        val first = toMin(route.firstDeparture)
        val last = toMin(route.lastDeparture)
        if (first < 0 || last <= first) return emptyList()
        val peakStart = route.peakFrom?.let { toMin(it) }
        val peakEnd = route.peakTo?.let { toMin(it) }
        val peakStep = route.peakIntervalMin
        val out = mutableListOf<String>()
        var t = first
        while (t <= last) {
            out += fmt(t)
            val inPeak = peakStep != null && peakStart != null && peakEnd != null && t >= peakStart && t < peakEnd
            t += if (inPeak) peakStep else step
        }
        // v3.3：循环在第一个超过末班的刻度处停止，标称末班往往不是间隔的整数倍，
        // 于是最后一班总是早于「末班」文案（103 路 20:50 vs 21:00、107 路 20:55 vs 21:00 …）。这里补齐末班。
        if (out.isEmpty() || toMin(out.last()) < last) out += fmt(last)
        return out
    }

    /** 时刻格式化：固定 Locale.US，避免部分语言环境把数字本地化后破坏「上午/下午/晚间」分组 */
    private fun fmt(minutes: Int): String =
        String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

    private fun toMin(s: String): Int {
        val parts = s.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: return -1) * 60 +
            (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }
}
