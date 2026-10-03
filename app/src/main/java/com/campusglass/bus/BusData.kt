package com.campusglass.bus

/**
 * 福建农林大学南平校区（南平市建阳区童游大街1号）附近公交数据。
 *
 * 来源（2026-09-27 核实）：建阳公交官方通告（大武夷新闻网 2025-08-27）· 海峡网/今日头条（早高峰密集班次）·
 * 无敌电动公交数据 2026-08/09（发车时刻与走向调整核实）· 高德经停信息。
 * 标注“推算”的时刻按间隔估算，实际以站牌/「掌上公交」APP 为准。
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
    val note: String = "",
    val peakFrom: String? = null,
    val peakTo: String? = null,
    val peakIntervalMin: Int? = null,
    val offPeakIntervalMin: Int? = null,
    val fixedTimes: List<String>? = null,   // 固定班次时刻（有则直接用）
    val upStops: List<String> = emptyList(),
    val downStops: List<String> = emptyList(),
)

object BusData {

    const val CAMPUS_STOP = "福建农林大学南平校区"
    const val SERVICE_HOTLINE = "0599-8768567"
    const val SOURCE_NOTE =
        "数据来源：建阳公交官方通告 · 无敌电动公交数据(2026-08/09 核实) · 海峡网 · 高德经停\n" +
            "标注“推算”的时刻按发车间隔估算，实际以站牌为准；实时到站用「掌上公交」APP · 热线 $SERVICE_HOTLINE"

    val DESTINATIONS = listOf("建发悦城", "万达广场", "动车站")

    val routes = listOf(
        BusRoute(
            name = "103路（青春快线）",
            endpoints = "福建农林大学南平校区 ⇄ 万达广场",
            firstDeparture = "7:45",
            lastDeparture = "21:00",
            fare = "全程一票制 1 元",
            intervalNote = "早高峰 7:45-8:00 每 10 分钟一班（官方）；其余约 15 分钟（推算）",
            timeOfficial = true,
            destTags = listOf("万达广场"),
            note = "单程 7.5 公里约 15 分钟，直达商圈（新岭总站发出首班 7:45，2026-08 核实）",
            peakFrom = "7:45", peakTo = "8:00", peakIntervalMin = 10,
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
            name = "105路（园区直通车）",
            endpoints = "福建农林大学南平校区 ⇄ 赤岸小区（新区管委会）",
            firstDeparture = "6:50",
            lastDeparture = "18:30",
            fare = "全程一票制 1 元",
            intervalNote = "早高峰 7:30-8:00 每 10 分钟一班（官方）；平峰约 20 分钟（推算）",
            timeOfficial = true,
            destTags = emptyList(),
            note = "25 分钟跑完 14 公里；2026-08 走向经 童游大街北/巨电新能源/碧全月亮湾/海林大厦/南平移动公司/芦上/宝鼎路",
            peakFrom = "7:30", peakTo = "8:00", peakIntervalMin = 10,
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
            name = "107路（烟火漫游线）",
            endpoints = "福建农林大学南平校区 ⇄ 宋慈广场",
            firstDeparture = "7:15",
            lastDeparture = "21:00",
            fare = "全程一票制 1 元",
            intervalNote = "约 20 分钟一班（推算）",
            timeOfficial = true,
            destTags = listOf("建发悦城"),
            note = "首班 7:15（2026-09 核实，非 6:50）；去建发悦城坐到「童游农贸（烟草公司）」换乘建阳1路",
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
            intervalNote = "发车间隔约 23-90 分钟/趟（官方区间），波动较大",
            timeOfficial = false,
            destTags = listOf("动车站"),
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
            note = "不在校区设站，武夷山方向接驳参考；去动车站首选 K2 路",
        ),
    )

    /** 发车时刻点列表（固定班次直接用；否则按首末班+间隔生成） */
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
            out += String.format(java.util.Locale.US, "%02d:%02d", t / 60, t % 60)   // BUS-8：Locale 固定
            val inPeak = peakStep != null && peakStart != null && peakEnd != null && t >= peakStart && t < peakEnd
            t += if (inPeak) peakStep!! else step
        }
        // BUS-1：末班车必进时刻表（间隔推算可能永远落不到末班点）
        if (out.isEmpty() || toMin(out.last()) < last) {
            out += String.format(java.util.Locale.US, "%02d:%02d", last / 60, last % 60)
        }
        return out
    }

    private fun toMin(s: String): Int {
        val parts = s.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: return -1) * 60 +
            (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }
}
