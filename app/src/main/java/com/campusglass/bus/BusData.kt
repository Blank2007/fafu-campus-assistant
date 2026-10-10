package com.campusglass.bus

/**
 * 福建农林大学南平校区（南平市建阳区童游大街1号）附近公交数据。
 *
 * V4.8 重核（2026-10-10，多源交叉）：时刻表按【去程/返程】分开——此前双向混排，
 * 曾把返程首班（107路电商园区方向 07:15）当成去程首班使用。
 *
 * 来源：
 * - 建阳公交官方开通通告（大武夷新闻网 / 福建日报 2025-08-27，含双向站点）
 * - 无敌电动公交数据（2026-08-21~25，去程/返程页面分别核对）
 * - 南平公交时刻表 iecity（2026-10）· 海峡网 · 高德经停
 * - 定制公交：南平市政府·大武夷新闻网(2025-09 迎新) · 福建农林大学南平校区后勤科通知(2026-07-03)
 * 未公开的时刻标注“以站牌/掌上公交为准”，不硬编。
 */

/** 单方向发车计划 */
data class DirSchedule(
    val label: String,                  // "新岭公交总站 → 万达广场"
    val first: String = "",             // 首班（""=未公开）
    val last: String = "",              // 末班
    val intervalNote: String = "",
    val fixedTimes: List<String>? = null,   // 固定班次（有则直接用）
    val note: String = "",
    val timeOfficial: Boolean = true,       // true=官方/实测；false=推算
    val peakFrom: String? = null,
    val peakTo: String? = null,
    val peakIntervalMin: Int? = null,
    val offPeakIntervalMin: Int? = null,
)

data class BusRoute(
    val name: String,
    val endpoints: String,
    val fare: String,
    val destTags: List<String>,
    val upSched: DirSchedule,
    val downSched: DirSchedule? = null,     // null=返程时刻未公开
    val note: String = "",
    val upStops: List<String> = emptyList(),
    val downStops: List<String> = emptyList(),
)

object BusData {

    const val CAMPUS_STOP = "福建农林大学南平校区"
    const val SERVICE_HOTLINE = "0599-8768567"
    const val ZS_BUS_TIP = "实时到站请用「掌上公交」APP（103/105/107 等均已接入）"
    val SOURCE_NOTE =
        "数据来源：建阳公交官方开通通告(2025-08) · 无敌电动公交数据(2026-08 双向核对) · 南平公交时刻表(2026-10) · 校区后勤科通知(2026-07)\n" +
            "时刻分去程/返程两方向；标注“推算”的按发车间隔估算，未公开的以站牌为准。" + ZS_BUS_TIP + " · 热线 $SERVICE_HOTLINE"

    val DESTINATIONS = listOf("建发悦城", "万达广场", "动车站")

    val routes: List<BusRoute> = listOf(
        BusRoute(
            name = "定制公交（校区 ⇄ 高铁南平市站）⭐直达",
            endpoints = "福建农林大学南平校区 ⇄ 高铁南平市站 · 点对点直达约 15 分钟",
            fare = "以车上/通知为准",
            destTags = listOf("动车站"),
            upSched = DirSchedule(
                label = "校区 → 高铁南平市站",
                intervalNote = "固定时刻表未公开，按校区通知发车",
                timeOfficial = true,
                note = "武夷交发定制大巴（6 车点对点）；学期内运行，寒暑假停运（2026 暑假运行至 7 月 9 日，恢复另行通知）",
            ),
            downSched = DirSchedule(
                label = "高铁南平市站 → 校区",
                intervalNote = "固定时刻表未公开，到站口右手上车",
                timeOfficial = true,
                note = "出站口右手边上车（迎新/开放日常态化运营）",
            ),
            note = "校区往返高铁站最省心的选择，车程约 15 分钟；具体班次以校区通知/「掌上公交」APP 为准。寒暑假期间改乘 K2 路",
        ),
        BusRoute(
            name = "103路（青春快线）",
            endpoints = "新岭公交总站 ⇄ 万达广场（经校区正门）",
            fare = "全程一票制 1 元",
            destTags = listOf("万达广场"),
            upSched = DirSchedule(
                label = "新岭公交总站 → 万达广场",
                first = "07:30", last = "21:00",
                intervalNote = "早高峰约 10 分钟一班、其余约 15 分钟（推算）",
                note = "官方通告首班 07:30；无敌电动(2026-08)记 07:45，两源不一以站牌为准。单程 7.5 公里约 15 分钟",
                peakFrom = "07:30", peakTo = "08:00", peakIntervalMin = 10,
                offPeakIntervalMin = 15,
            ),
            downSched = DirSchedule(
                label = "万达广场 → 新岭公交总站",
                first = "07:30", last = "21:00",
                intervalNote = "与去程对称（推算）",
                timeOfficial = false,
                note = "官方未单列返程时刻，按对称推定；返程经停万达广场南、郑氏骨科医院",
            ),
            note = "校区往返商圈主力线；早 8 点上课 7:40 从校区上车即可",
            upStops = listOf(
                "新岭公交总站", CAMPUS_STOP, "海源新材料", "创业园三期", "兴华啤酒", "万辉路",
                "高一路", "童子山加油站", "滨江壹号", "万达广场",
            ),
            downStops = listOf(
                "万达广场", "万达广场南", "滨江壹号", "童子山加油站", "高一路", "万辉路",
                "兴华啤酒", "创业园三期", "海源新材料", CAMPUS_STOP, "郑氏骨科医院", "新岭公交总站",
            ),
        ),
        BusRoute(
            name = "105路（园区直通车）",
            endpoints = "新岭公交总站 ⇄ 赤岸小区/新区管委会（经校区正门）",
            fare = "全程一票制 1 元",
            destTags = emptyList(),
            upSched = DirSchedule(
                label = "新岭公交总站 → 赤岸小区（新区管委会）",
                first = "06:50", last = "18:30",
                intervalNote = "早高峰 7:30-8:00 每 10 分钟一班（官方）；平峰约 20 分钟（推算）",
                note = "25 分钟跑完 14 公里；2026-08 走向微调（童游大街北/巨电新能源/碧全月亮湾/海林大厦/南平移动公司/芦上/宝鼎路）",
                peakFrom = "07:30", peakTo = "08:00", peakIntervalMin = 10,
                offPeakIntervalMin = 20,
            ),
            downSched = DirSchedule(
                label = "赤岸小区（新区管委会） → 新岭公交总站",
                first = "06:50", last = "18:30",
                intervalNote = "与去程对称（推算）",
                timeOfficial = false,
                note = "官方未单列返程时刻，按对称推定，以站牌为准",
            ),
            note = "通勤向线路，途经武夷山水城、南平大剧院等行政中心片区",
            upStops = listOf(
                "新岭公交总站", CAMPUS_STOP, "海源新材料", "童游大街北", "巨电新能源", "碧全月亮湾",
                "海林大厦", "南平移动公司", "芦上", "宝鼎路", "武夷山水城", "赤岸小区（新区管委会）",
            ),
            downStops = listOf(
                "赤岸小区（新区管委会）", "武夷山水城", "宝鼎路", "芦上", "南平移动公司", "海林大厦",
                "碧全月亮湾", "巨电新能源", "童游大街北", "海源新材料", CAMPUS_STOP, "新岭公交总站",
            ),
        ),
        BusRoute(
            name = "107路（烟火漫游线）",
            endpoints = "新岭公交总站 ⇄ 电商园区（经宋慈广场，校区正门）",
            fare = "全程一票制 1 元",
            destTags = listOf("建发悦城"),
            upSched = DirSchedule(
                label = "新岭公交总站 → 电商园区",
                first = "07:30", last = "21:00",
                intervalNote = "约 20 分钟一班（推算）",
                note = "终点已延伸：宋慈广场 → 农校 → 电商园区；去建发悦城坐到「童游农贸（烟草公司）」换乘建阳1路",
            ),
            downSched = DirSchedule(
                label = "电商园区 → 郑氏骨科医院（校区旁）",
                first = "07:15", last = "21:00",
                intervalNote = "约 20 分钟一班（推算）",
                note = "⚠️ 返程首班 07:15 早于去程（07:30）——勿混用；返程终点郑氏骨科医院，经兴业路(林后村)",
            ),
            note = "市井烟火线：童游农贸市场、市立医院、宋慈广场；双向首班不同（去程 07:30 / 返程 07:15）",
            upStops = listOf(
                "新岭公交总站", CAMPUS_STOP, "海源新材料", "童游大街北", "兴业路(林后村)", "闽铝轻量化",
                "童子山加油站", "嘉禾北路", "广信街（新区管委会）", "实业集团", "崇阳北路", "滨江壹号",
                "赤岸", "周家", "万晟皇庭", "万晟星城", "火车站（建阳）", "检察院", "和顺景园",
                "曼头山路口", "社区卫生中心", "安居楼", "童游农贸（烟草公司）", "金茂广场", "市立医院",
                "宋慈广场（水吉车站）", "农校", "电商园区",
            ),
            downStops = listOf(
                "电商园区", "农校", "宋慈广场（水吉车站）", "市立医院", "金茂广场", "童游农贸（烟草公司）",
                "安居楼", "社区卫生中心", "曼头山路口", "和顺景园", "检察院", "火车站（建阳）",
                "崇阳新都", "万晟皇庭", "周家", "赤岸", "滨江壹号", "崇阳北路", "实业集团",
                "广信街（新区管委会）", "嘉禾北路", "童子山加油站", "闽铝轻量化", "兴业路(林后村)",
                "童游大街北", "海源新材料", CAMPUS_STOP, "郑氏骨科医院",
            ),
        ),
        BusRoute(
            name = "武夷新区工业园区专线",
            endpoints = "福建农林大学南平校区 ⇄ 社会福利中心（百龄帮）",
            fare = "以站牌为准",
            destTags = emptyList(),
            upSched = DirSchedule(
                label = "校区 → 社会福利中心（百龄帮）",
                first = "07:30", last = "17:30",
                intervalNote = "班次较少，按固定班次运行（具体时刻未公开）",
                note = "自校区发车（2026-08 核对）；途经建阳火车站、宋慈广场、农校、电商园区、殡葬服务中心",
            ),
            downSched = DirSchedule(
                label = "社会福利中心（百龄帮） → 校区",
                intervalNote = "返程时刻未公开，以站牌为准",
                timeOfficial = true,
                note = "在「社会福利中心(百龄帮)」站牌候车",
            ),
            note = "班次少但站点串起老城区；适合卡点出行，其余时段用 107 路更稳",
            upStops = listOf(
                CAMPUS_STOP, "海源新材料", "兴华啤酒", "闽铝轻量化", "赤岸", "周家", "万晟皇庭",
                "崇阳新都", "火车站（建阳）", "区检察院", "和顺景园", "曼头山路口", "社区卫生中心",
                "安居楼", "童游农贸（烟草公司）", "金茂广场", "市立医院", "宋慈广场（水吉车站）",
                "农校", "电商园区", "殡葬服务中心", "社会福利中心（百龄帮）",
            ),
        ),
        BusRoute(
            name = "建阳K2路（西区公交站 ⇄ 高铁南平市站）",
            endpoints = "经停校区站，直达动车站（高铁南平市站），约 15 分钟",
            fare = "8 元（全程一票制，以站牌为准）",
            destTags = listOf("动车站"),
            upSched = DirSchedule(
                label = "西区公交站 → 高铁南平市站",
                first = "07:00", last = "21:00",
                intervalNote = "高峰约 20 分钟/趟、平峰约 30 分钟/趟（官方区间 23-90 分钟波动）",
                note = "校区→高铁站为尾段（校区→新安路→贵口→芹口→高铁南平市站）约 15 分钟",
            ),
            downSched = DirSchedule(
                label = "高铁南平市站 → 西区公交站",
                first = "08:10", last = "23:00",
                intervalNote = "约 90 分钟/趟",
                note = "⚠️ 末班车发车时间视高铁到站时间调整（可能延后）；高铁站→校区经芹口→贵口→郑氏骨科医院→校区",
            ),
            note = "去动车站的常规公交（校区上/下车站即「福建农林大学南平校区」站）；最省心是上方定制公交",
            upStops = listOf(
                "西区公交站", "大潭映象", "二院", "宝山路口（水南农贸）", "汽车站", "总工会",
                "市立医院（建阳）", "鹏宇佳苑", "安居楼", "社区卫生中心", "和顺景园", "供电局",
                "火车站（建阳）", "万晟星城", "万晟皇庭", "周家", "赤岸", "兴华啤酒", "创业园三期",
                "海源新材料", CAMPUS_STOP, "新安路", "贵口", "芹口(将口收费站)", "高铁南平市站",
            ),
            downStops = listOf(
                "高铁南平市站", "芹口(将口收费站)", "贵口", "郑氏骨科医院", CAMPUS_STOP, "海源新材料",
                "创业园三期", "兴华啤酒", "赤岸", "周家", "万晟皇庭", "火车站（建阳）", "区检察院",
                "和顺景园", "社区卫生中心", "安居楼", "鹏宇佳苑", "市立医院（建阳）", "总工会",
                "汽车站", "二院", "教师进修学院", "西区公交站",
            ),
        ),
        BusRoute(
            name = "建阳K3路（参考·不经校区）",
            endpoints = "赤岸小区（新区管委会）⇄ 高铁南平市站",
            fare = "以站牌为准",
            destTags = listOf("动车站"),
            upSched = DirSchedule(
                label = "赤岸小区（新区管委会） → 高铁南平市站",
                first = "06:40", last = "21:40",
                intervalNote = "多源首末班不一（06:40-21:40 / 07:40-23:00），以站牌为准",
                timeOfficial = false,
                note = "⚠️ 不经校区！可在「万达广场南/嘉禾北路」换乘 103 路；时间紧建议直接定制公交/K2",
            ),
            downSched = DirSchedule(
                label = "高铁南平市站 → 赤岸小区（新区管委会）",
                first = "07:45", last = "22:15",
                intervalNote = "多源不一，以站牌为准",
                timeOfficial = false,
                note = "返程经将口电信、市委党校、武夷学校、碧全江誉、南林公园、市民广场等",
            ),
            note = "行政中心直达高铁线（市民广场/行政服务中心/云谷校区一带），对校区学生为换乘参考",
            upStops = listOf(
                "赤岸小区（新区管委会）", "嘉禾北路", "万达广场南", "行政服务中心", "崇善里",
                "云谷校区", "和谐苑", "福康苑（体育公园）", "市民广场（南平市行政中心）", "南林公园",
                "碧全江誉", "武夷学校", "市委党校", "将口电信", "高铁南平市站",
            ),
            downStops = listOf(
                "高铁南平市站", "将口电信", "市委党校", "武夷学校", "碧全江誉", "南林公园",
                "市民广场（南平市行政中心）", "福康苑（体育公园）", "和谐苑", "云谷校区", "崇善里",
                "行政服务中心", "万达广场南", "嘉禾北路", "赤岸小区（新区管委会）",
            ),
        ),
        BusRoute(
            name = "建阳1路（去建发悦城的接驳线）",
            endpoints = "云谷南 ⇄ 建盏文创园（经童游农贸、滨江壹号、万达广场）",
            fare = "一票制 1 元",
            destTags = listOf("建发悦城", "万达广场"),
            upSched = DirSchedule(
                label = "云谷南 → 建盏文创园",
                first = "06:25", last = "18:30",
                intervalNote = "约 23-60 分钟/趟",
                note = "换乘点：童游农贸（烟草公司）/ 滨江壹号（107 路/103 路可达）",
                timeOfficial = false,
            ),
            downSched = DirSchedule(
                label = "建盏文创园 → 云谷南",
                first = "06:25", last = "18:30",
                intervalNote = "约 23-60 分钟/趟",
                timeOfficial = false,
                note = "以站牌为准",
            ),
            note = "建发悦城在「建发悦城」站下；从校区坐 107 路到童游农贸换乘",
        ),
        BusRoute(
            name = "K1路（高铁/武夷山 接驳参考）",
            endpoints = "武夷山景区 ⇄ 高铁南平市站",
            fare = "10 元",
            destTags = listOf("动车站"),
            upSched = DirSchedule(
                label = "武夷山 → 高铁南平市站（快速通道线）",
                first = "07:00", last = "20:00",
                intervalNote = "快速通道线；303 省道线 07:20-19:30",
                timeOfficial = true,
                note = "不在校区设站，武夷山方向接驳参考",
            ),
            downSched = DirSchedule(
                label = "高铁南平市站 → 武夷山",
                first = "08:20", last = "22:45",
                intervalNote = "快速通道线；303 省道线 08:45-22:00",
                timeOfficial = true,
            ),
            note = "去武夷山玩的参考线；从校区去高铁站先乘定制公交或 K2",
        ),
    )

    /** 发车时刻点列表（固定班次直接用；否则按首末班+间隔生成；未公开返回空） */
    fun departureTimes(sched: DirSchedule): List<String> {
        sched.fixedTimes?.let { return it }
        val step = sched.offPeakIntervalMin ?: return emptyList()
        val first = toMin(sched.first)
        val last = toMin(sched.last)
        if (first < 0 || last <= first) return emptyList()
        val peakStart = sched.peakFrom?.let { toMin(it) }
        val peakEnd = sched.peakTo?.let { toMin(it) }
        val peakStep = sched.peakIntervalMin
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

    /** B1：表尾是否为“硬补的标称末班”（与间隔错位） */
    fun lastIsStamped(sched: DirSchedule, times: List<String>): Boolean {
        if (times.size < 2) return false
        val step = sched.offPeakIntervalMin ?: return false
        val gap = toMin(times.last()) - toMin(times[times.size - 2])
        return gap < step
    }
}
