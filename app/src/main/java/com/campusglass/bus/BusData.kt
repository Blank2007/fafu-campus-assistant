package com.campusglass.bus

/**
 * 福建农林大学南平校区（南平市建阳区童游大街1号）附近公交数据。
 *
 * 来源：建阳公交官方通告（大武夷新闻网 2025-08-27）· 海峡网（103路早高峰10分钟一班）·
 * 高德/无敌电动线路数据 · 南平市人民政府公交乘车指南（建阳1路）· 百度百科（建阳1路走向）。
 * 标注“推算”的时刻按间隔估算，实际以站牌/「掌上公交」APP 为准。
 */
data class BusRoute(
    val name: String,
    val endpoints: String,
    val firstDeparture: String,     // 首班 "7:30"
    val lastDeparture: String,      // 末班 "21:00"
    val fare: String,
    val intervalNote: String,
    val timeOfficial: Boolean,      // true=官方时刻；false=按间隔推算
    val destTags: List<String>,     // 目的地筛选：建发悦城 / 万达广场 / 动车站
    val mapKeyword: String,         // 高德搜索公交线路用
    val note: String = "",
    val peakFrom: String? = null,   // 高峰时段起点（如 "7:30"）
    val peakTo: String? = null,
    val peakIntervalMin: Int? = null,
    val offPeakIntervalMin: Int? = null,
    val upStops: List<String> = emptyList(),
    val downStops: List<String> = emptyList(),
)

object BusData {

    const val CAMPUS_STOP = "福建农林大学南平校区"
    const val SERVICE_HOTLINE = "0599-8768567"
    const val SOURCE_NOTE =
        "数据来源：建阳公交官方通告 · 海峡网 · 高德/线路数据 · 南平市政府公交指南\n" +
            "标注“推算”的时刻按发车间隔估算，实际以站牌为准；实时到站用「掌上公交」APP · 热线 $SERVICE_HOTLINE"

    /** 目的地筛选项 */
    val DESTINATIONS = listOf("建发悦城", "万达广场", "动车站")

    val routes = listOf(
        BusRoute(
            name = "103路（青春快线）",
            endpoints = "福建农林大学南平校区 ⇄ 万达广场",
            firstDeparture = "7:30",
            lastDeparture = "21:00",
            fare = "全程一票制 1 元",
            intervalNote = "早高峰 7:30-8:00 每 10 分钟一班（官方）；平峰约 15 分钟、晚间约 20 分钟（推算）",
            timeOfficial = true,
            destTags = listOf("万达广场"),
            mapKeyword = "建阳103路公交",
            note = "单程 7.5 公里约 15 分钟，直达商圈",
            peakFrom = "7:30", peakTo = "8:00", peakIntervalMin = 10,
            offPeakIntervalMin = 15,
            upStops = listOf(
                "万达广场", "万达广场南", "滨江壹号", "童子山加油站", "高一路", "万辉路",
                "兴华啤酒", "创业园三期", "海源新材料", CAMPUS_STOP, "郑氏骨科医院", "新岭公交总站",
            ),
            downStops = listOf(
                "新岭公交总站", CAMPUS_STOP, "海源新材料", "创业园三期", "兴华啤酒", "万辉路",
                "高一路", "童子山加油站", "滨江壹号", "万达广场",
            ),
        ),
        BusRoute(
            name = "K2路（西区公交站 ⇄ 高铁南平市站）",
            endpoints = "经停校区站，直达动车站（高铁南平市站），车程约 15 分钟",
            firstDeparture = "7:00",
            lastDeparture = "21:00",
            fare = "以站牌为准",
            intervalNote = "发车间隔约 23-90 分钟/趟（官方区间），波动较大",
            timeOfficial = false,
            destTags = listOf("动车站"),
            mapKeyword = "建阳K2路公交",
            note = "校区站（21站）→ 新安路 → 贵口 → 芹口(将口收费站) → 高铁南平市站（25站）",
            offPeakIntervalMin = null,
        ),
        BusRoute(
            name = "107路",
            endpoints = "福建农林大学南平校区 ⇄ 宋慈广场",
            firstDeparture = "6:50",
            lastDeparture = "21:00",
            fare = "全程一票制 1 元",
            intervalNote = "约 20 分钟一班（推算）",
            timeOfficial = false,
            destTags = listOf("建发悦城"),
            mapKeyword = "建阳107路公交",
            note = "去建发悦城：坐到「童游农贸（烟草公司）」换乘建阳1路（1路经建发悦城站）",
            offPeakIntervalMin = 20,
            upStops = listOf(
                "宋慈广场（水吉车站）", "市立医院", "金茂广场", "童游农贸（烟草公司）", "安居楼",
                "社区卫生中心", "曼头山路口", "和顺景园", "供电局", "检察院", "火车站",
                "万晟星城", "万晟皇庭", "周家", "赤岸", "滨江壹号", "崇阳北路", "实业集团",
                "广信街（新区管委会）", "嘉禾北路", "童子山加油站", "闽铝轻量化", "新五路",
                "海源新材料", CAMPUS_STOP, "郑氏骨科医院", "新岭公交总站",
            ),
            downStops = listOf(
                "新岭公交总站", CAMPUS_STOP, "海源新材料", "新五路", "闽铝轻量化", "童子山加油站",
                "嘉禾北路", "广信街（新区管委会）", "实业集团", "崇阳北路", "滨江壹号", "赤岸",
                "周家", "万晟皇庭", "崇阳新都", "火车站", "检察院", "和顺景园", "曼头山路口",
                "社区卫生中心", "安居楼", "童游农贸（烟草公司）", "金茂广场", "市立医院",
                "宋慈广场（水吉车站）",
            ),
        ),
        BusRoute(
            name = "建阳1路（去建发悦城的接驳线）",
            endpoints = "考亭书院 ⇄ 闽北卫校（经建发悦城、万达广场）",
            firstDeparture = "6:25",
            lastDeparture = "18:45",
            fare = "一票制 1 元",
            intervalNote = "约 15 分钟一班（推算）",
            timeOfficial = false,
            destTags = listOf("建发悦城", "万达广场"),
            mapKeyword = "建阳1路公交",
            note = "换乘点：童游农贸（烟草公司）/ 滨江壹号。途经：建发悦城—人民公园西门—童游农贸—…—滨江壹号—万达广场—…—闽北卫校",
            offPeakIntervalMin = 15,
        ),
        BusRoute(
            name = "105路",
            endpoints = "福建农林大学南平校区 ⇄ 新区管委会",
            firstDeparture = "6:50",
            lastDeparture = "18:30",
            fare = "全程一票制 1 元",
            intervalNote = "约 20 分钟一班（推算）",
            timeOfficial = false,
            destTags = emptyList(),
            mapKeyword = "建阳105路公交",
            note = "途经武夷山水城、南平大剧院、图书馆、云谷片区",
            offPeakIntervalMin = 20,
            upStops = listOf(
                "新区管委会", "武夷山水城（市农发行）", "南平大剧院", "图书馆", "一中初中部",
                "云谷校区", "瑞玺苑", "云谷实验学校", "建发和鸣", "法治公园", "碧全江誉",
                "南林小学", "崇阳街道", "南林", "巨电新能源", "海源新材料", CAMPUS_STOP,
                "郑氏骨科医院", "新岭公交总站",
            ),
            downStops = listOf(
                "新岭公交枢纽总站", CAMPUS_STOP, "海源新材料", "巨电新能源", "南林", "崇阳街道",
                "南林小学", "碧全江誉", "法治公园", "建发和鸣", "云谷实验学校", "瑞云苑",
                "和谐苑", "云谷校区", "一中初中部", "水之厅", "南平大剧院", "武夷山水城（市农发行）",
                "新区管委会",
            ),
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
            offPeakIntervalMin = null,
        ),
    )

    /** 生成发车时刻点列表（空 = 间隔不稳定不生成） */
    fun departureTimes(route: BusRoute): List<String> {
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
            out += String.format("%02d:%02d", t / 60, t % 60)
            val inPeak = peakStep != null && peakStart != null && peakEnd != null && t >= peakStart && t < peakEnd
            t += if (inPeak) peakStep!! else step
        }
        return out
    }

    private fun toMin(s: String): Int {
        val parts = s.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: return -1) * 60 +
            (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }
}
