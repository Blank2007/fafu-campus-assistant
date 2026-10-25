package com.campusglass.bus

import java.time.LocalTime

/**
 * 建阳公交数据（福建农林大学南平校区 / 南平市建阳区童游大街 1 号 附近）。
 *
 * ══════════════════════════════════════════════════════════════════════
 * 数据核对：2026-10-25（v6.0 全面重核，逐条对照官方来源）
 * ══════════════════════════════════════════════════════════════════════
 *
 * 权威来源（按可信度排序）：
 * 1. 南平市建阳区公共交通有限公司官网公告（www.np.gov.cn）
 *    - 《建阳区公交线路首末班发车时间实行夏季运行时间的通告》2026-05-28
 *      → 103/105/107/K2/K3/工业园区专线/1路/K1 的**首末班时间**全部以此为准
 *    - 《关于优化107路公交线路的通告》2026-07-29（2026-08-01 起执行）
 *      → 107 改走「兴业路（林后村）」，取消滨江壹号/崇阳北路/实业集团/广信街/嘉禾北路
 *    - 《关于优化105路公交线路的通告》2026-05-15（2026-05-19 起执行）
 *      → 105 取消「南林」，新增南平移动/宝鼎路/芦上/海林大厦/碧全月亮湾
 *    - 《关于优化K3路公交线路的通告》2025-11-11（2025-11-17 起执行）
 *      → K3 新增林后大街路口/法治公园，取消碧全江誉/大红袍路
 * 2. 大武夷新闻网（武夷发展集团 / 建阳公交供稿）2025-08-27
 *    → 103/105/107 开线时的完整双向站点
 * 3. 掌上公交 APP（实时到站，本 App 无法接入）
 *
 * ⚠️ 首末班均为**起点站发车时间**。校区站是中途站，实际到站约晚 3~8 分钟。
 *
 * ⚠️ 未重新核实的部分（保留旧数据并显式标注，不假装准确）：
 * - K2 的站点序列（官方未发布完整站点公告，沿用高德经停/无敌电动核对结果）
 * - 各线路的发车间隔（官方只公布首末班，间隔来自公交爱好者实测与掌上公交）
 * - 工业园区专线的完整站点（官方仅公布首末班与部分站点）
 */

/** 单方向运营信息 */
data class Direction(
    /** 方向说明，如「新岭公交总站 → 万达广场」 */
    val label: String,
    /** 首班（起点站发车），"" = 官方未公布 */
    val first: String = "",
    /** 末班（起点站发车） */
    val last: String = "",
    /** 平峰发车间隔（分钟），null = 未知 */
    val intervalMin: Int? = null,
    /** 高峰发车间隔（分钟） */
    val peakIntervalMin: Int? = null,
    /** 高峰起（"07:30"） */
    val peakFrom: String? = null,
    /** 高峰止（"08:00"） */
    val peakTo: String? = null,
    /** 官方固定班次（有则直接列出，不推算） */
    val fixedTimes: List<String>? = null,
    /** true = 官方公布；false = 官方未单列、按对向推定 */
    val official: Boolean = true,
    /** 该方向是否「学生从校区上车」的方向 */
    val fromCampus: Boolean = false,
    /** 途经站点（起点 → 终点，按顺序） */
    val stops: List<String> = emptyList(),
    val note: String = "",
)

/** 一条公交线路 */
data class BusRoute(
    val name: String,
    /** 线路短名（卡片上显示，如「103 路」） */
    val shortName: String,
    /** 一句话走向 */
    val endpoints: String,
    val fare: String,
    /** 途经的目的地标签（对应 [BusData.SCENES] 的 id） */
    val sceneIds: List<String>,
    /** 从校区出去的方向 */
    val out: Direction,
    /** 回校区的方向 */
    val back: Direction,
    /** 是否经停校区（false = 参考线路，主列表标灰） */
    val servesCampus: Boolean = true,
    val note: String = "",
)

/** 目的地场景：主列表按这个分组 */
data class Scene(
    val id: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    /** 该场景的「目标站名」，用于算「从校区还有几站」 */
    val targetStop: String = "",
    /** 该场景需要换乘时的提示（目标站不在直达线路上时显示） */
    val transferNote: String = "",
)

object BusData {

    const val CAMPUS_STOP = "福建农林大学南平校区"
    const val SERVICE_HOTLINE = "0599-8768567"
    const val ZS_BUS_TIP = "实时到站请用「掌上公交」APP"

    /** 目的地场景（主列表分组顺序即此处顺序） */
    val SCENES = listOf(
        Scene("wanda", "🛍", "万达广场", "购物 · 吃饭 · 电影", "万达广场"),
        Scene("city", "🏙", "市区（宋慈广场 / 市立医院）", "市井烟火 · 看病 · 办事", "市立医院"),
        Scene("rail", "🚄", "高铁南平市站", "回福州 / 去武夷山方向", "高铁南平市站"),
        Scene("jianfa", "🏬", "建发悦城", "需在童游农贸换乘", "建发悦城",
            "在「童游农贸（烟草公司）」下车，换乘建阳1路到建发悦城"),
        Scene("station", "🚉", "建阳火车站", "普速列车", "火车站"),
        Scene("newarea", "🏛", "新区（行政服务中心 / 云谷）", "市民广场 · 云谷校区", "行政服务中心"),
    )

    val routes: List<BusRoute> = listOf(

        // ────────────────────────── 103 路 ──────────────────────────
        BusRoute(
            name = "103路（青春快线）",
            shortName = "103 路",
            endpoints = "新岭公交总站 ⇄ 万达广场（经校区正门）",
            fare = "一票制 1 元",
            sceneIds = listOf("wanda"),
            servesCampus = true,
            out = Direction(
                label = "新岭公交总站 → 万达广场",
                first = "07:45", last = "21:00",
                intervalMin = 15, peakIntervalMin = 10,
                peakFrom = "07:45", peakTo = "08:30",
                fromCampus = true,
                official = true,
                stops = listOf(
                    "新岭公交总站", CAMPUS_STOP, "海源新材料", "创业园三期", "兴华啤酒",
                    "万辉路", "高一路", "童子山加油站", "滨江壹号", "万达广场",
                ),
                note = "官方夏季表：新岭总站 07:45 首班 / 21:00 末班；万达广场 07:30 首班 / 20:45 末班",
            ),
            back = Direction(
                label = "万达广场 → 新岭公交总站",
                first = "07:30", last = "20:45",
                intervalMin = 15, peakIntervalMin = 10,
                peakFrom = "07:30", peakTo = "08:30",
                official = true,
                stops = listOf(
                    "万达广场", "万达广场南", "滨江壹号", "童子山加油站", "高一路", "万辉路",
                    "兴华啤酒", "创业园三期", "海源新材料", CAMPUS_STOP, "郑氏骨科医院", "新岭公交总站",
                ),
                note = "回程末班 20:45（比去程早 15 分钟），别记错",
            ),
            note = "单程 7.5 公里约 15 分钟，是去万达的主力线。⚠️ 去程末班 21:00、回程末班 20:45，方向不同",
        ),

        // ────────────────────────── 107 路 ──────────────────────────
        BusRoute(
            name = "107路（烟火漫游线）",
            shortName = "107 路",
            endpoints = "新岭公交总站 ⇄ 电商园区（经校区、宋慈广场）",
            fare = "一票制 1 元",
            sceneIds = listOf("city", "station", "jianfa"),
            servesCampus = true,
            out = Direction(
                label = "新岭公交总站 → 电商园区",
                first = "07:30", last = "21:00",
                intervalMin = 20,
                fromCampus = true,
                official = true,
                stops = listOf(
                    "新岭公交总站", CAMPUS_STOP, "海源新材料", "童游大街北", "兴业路（林后村）",
                    "闽铝轻量化", "童子山加油站", "赤岸", "周家", "万晟皇庭", "崇阳新都",
                    "火车站", "检察院", "和顺景园", "曼头山路口", "社区卫生中心", "安居楼",
                    "童游农贸（烟草公司）", "金茂广场", "市立医院", "宋慈广场（水吉车站）",
                    "农校", "电商园区",
                ),
                note = "2026-08-01 起改走兴业路（林后村），不再经滨江壹号/崇阳北路/实业集团/广信街/嘉禾北路",
            ),
            back = Direction(
                label = "电商园区 → 郑氏骨科医院（校区旁）",
                first = "07:10", last = "21:00",
                intervalMin = 20,
                official = true,
                stops = listOf(
                    "电商园区", "农校", "宋慈广场（水吉车站）", "市立医院", "金茂广场",
                    "童游农贸（烟草公司）", "安居楼", "社区卫生中心", "曼头山路口", "和顺景园",
                    "供电局", "检察院", "火车站", "万晟星城", "万晟皇庭", "周家", "赤岸",
                    "童子山加油站", "闽铝轻量化", "兴业路（林后村）", "童游大街北",
                    "海源新材料", CAMPUS_STOP, "郑氏骨科医院",
                ),
                note = "回程首班 07:10 早于去程 07:30；终点是郑氏骨科医院（校区旁），不是新岭总站",
            ),
            note = "去市立医院、宋慈广场、童游农贸都坐这趟；去建发悦城在「童游农贸」换建阳1路",
        ),

        // ────────────────────────── 105 路 ──────────────────────────
        BusRoute(
            name = "105路（园区直通车）",
            shortName = "105 路",
            endpoints = "新岭公交总站 ⇄ 赤岸小区/新区管委会（经校区正门）",
            fare = "一票制 1 元",
            sceneIds = listOf("newarea"),
            servesCampus = true,
            out = Direction(
                label = "新岭公交总站 → 赤岸小区（新区管委会）",
                first = "06:50", last = "18:30",
                intervalMin = 20, peakIntervalMin = 10,
                peakFrom = "07:30", peakTo = "08:00",
                fromCampus = true,
                official = true,
                stops = listOf(
                    "新岭公交总站", CAMPUS_STOP, "海源新材料", "童游大街北", "巨电新能源",
                    "碧全月亮湾", "海林大厦", "南平移动", "芦上", "宝鼎路", "崇阳街道",
                    "南林小学", "碧全江誉", "法治公园", "建发和鸣", "云谷实验学校", "府前站",
                    "瑞云苑", "和谐苑", "云谷校区", "一中初中部", "水之厅", "南平大剧院",
                    "武夷山水城", "赤岸小区（新区管委会）",
                ),
                note = "2026-05-19 起取消「南林」，新增南平移动/宝鼎路/芦上/海林大厦/碧全月亮湾",
            ),
            back = Direction(
                label = "赤岸小区（新区管委会） → 郑氏骨科医院（校区旁）",
                first = "06:50", last = "18:30",
                intervalMin = 20, peakIntervalMin = 10,
                peakFrom = "07:30", peakTo = "08:00",
                official = true,
                stops = listOf(
                    "赤岸小区（新区管委会）", "武夷山水城", "南平大剧院", "水之厅", "一中初中部",
                    "云谷校区", "瑞玺苑", "府前站", "云谷实验学校", "建发和鸣", "法治公园",
                    "碧全江誉", "南林小学", "崇阳街道", "宝鼎路", "芦上", "南平移动",
                    "海林大厦", "碧全月亮湾", "巨电新能源", "童游大街北", "海源新材料",
                    CAMPUS_STOP, "郑氏骨科医院",
                ),
                note = "回程终点是郑氏骨科医院（校区旁），不进新岭总站",
            ),
            note = "去武夷山水城、南平大剧院、云谷校区一带坐这趟；末班 18:30 偏早，晚上回校区别指望它",
        ),

        // ─────────────────── 定制公交（校区 ⇄ 高铁站）───────────────────
        BusRoute(
            name = "定制公交（校区 ⇄ 高铁南平市站）",
            shortName = "定制公交",
            endpoints = "福建农林大学南平校区 ⇄ 高铁南平市站 · 点对点直达约 15 分钟",
            fare = "以车上/校区通知为准",
            sceneIds = listOf("rail"),
            servesCampus = true,
            out = Direction(
                label = "校区 → 高铁南平市站",
                official = false,
                fromCampus = true,
                note = "固定时刻表未公开，按校区通知发车；武夷交发定制大巴，6 车点对点",
            ),
            back = Direction(
                label = "高铁南平市站 → 校区",
                official = false,
                note = "出站口右手边上车",
            ),
            note = "校区往返高铁站最省心的选择，车程约 15 分钟。学期内运行，寒暑假停运（此时改乘 K2）",
        ),

        // ────────────────────────── K2 路 ──────────────────────────
        BusRoute(
            name = "建阳K2路（西区公交站 ⇄ 高铁南平市站）",
            shortName = "K2 路",
            endpoints = "经停校区站，直达高铁南平市站，约 15 分钟",
            fare = "8 元（以站牌为准）",
            sceneIds = listOf("rail"),
            servesCampus = true,
            out = Direction(
                label = "西区公交站 → 高铁南平市站",
                first = "07:00", last = "21:00",
                intervalMin = 30, peakIntervalMin = 20,
                fromCampus = true,
                official = true,
                stops = listOf(
                    "西区公交站", "大潭映象", "二院", "宝山路口（水南农贸）", "汽车站", "总工会",
                    "市立医院", "鹏宇佳苑", "安居楼", "社区卫生中心", "和顺景园", "供电局",
                    "火车站", "万晟星城", "万晟皇庭", "周家", "赤岸", "兴华啤酒", "创业园三期",
                    "海源新材料", CAMPUS_STOP, "新安路", "贵口", "芹口（将口收费站）", "高铁南平市站",
                ),
                note = "站点序列沿用 2026-08 核对结果，官方未发布完整站点公告",
            ),
            back = Direction(
                label = "高铁南平市站 → 西区公交站",
                first = "08:10", last = "23:50",
                intervalMin = 30,
                official = true,
                stops = listOf(
                    "高铁南平市站", "芹口（将口收费站）", "贵口", "郑氏骨科医院", CAMPUS_STOP,
                    "海源新材料", "创业园三期", "兴华啤酒", "赤岸", "周家", "万晟皇庭",
                    "火车站", "检察院", "和顺景园", "社区卫生中心", "安居楼", "鹏宇佳苑",
                    "市立医院", "总工会", "汽车站", "二院", "教师进修学院", "西区公交站",
                ),
                note = "末班 23:50 对接高铁到站，具体以高铁实际到站为准",
            ),
            note = "去高铁站的常规公交（票价 8 元）。最省心还是上面的定制公交，但寒暑假只有 K2 能坐",
        ),

        // ────────────────────────── 工业园区专线 ──────────────────────────
        BusRoute(
            name = "武夷新区工业园区专线",
            shortName = "园区专线",
            endpoints = "社会福利中心（百龄帮）⇄ 武夷智谷（经校区）",
            fare = "2 元（以站牌为准）",
            sceneIds = listOf("station"),
            servesCampus = true,
            out = Direction(
                label = "校区 → 社会福利中心（百龄帮）",
                first = "07:10", last = "17:40",
                intervalMin = 120,
                fromCampus = true,
                official = true,
                stops = listOf(
                    CAMPUS_STOP, "海源新材料", "兴华啤酒", "闽铝轻量化", "赤岸", "周家",
                    "万晟皇庭", "崇阳新都", "火车站", "检察院", "和顺景园", "曼头山路口",
                    "社区卫生中心", "安居楼", "童游农贸（烟草公司）", "金茂广场", "市立医院",
                    "宋慈广场（水吉车站）", "农校", "电商园区", "殡葬服务中心", "社会福利中心（百龄帮）",
                ),
                note = "站点为部分核对（官方仅公布首末班与主要站点），以站牌为准",
            ),
            back = Direction(
                label = "社会福利中心（百龄帮） → 武夷智谷",
                first = "06:50", last = "18:30",
                intervalMin = 120,
                official = true,
                note = "官方固定班次未逐条公布；从社会福利中心发车后约 30-40 分钟行经校区",
            ),
            note = "班次很少（约 2 小时一班），卡点出行；其余时段用 107 路更稳",
        ),

        // ─────────────────── 建阳1路（换乘参考，不经校区）───────────────────
        BusRoute(
            name = "建阳1路（去建发悦城的接驳线）",
            shortName = "建阳1路",
            endpoints = "上考亭 ⇄ 云谷南（经童游农贸、万达广场）",
            fare = "一票制 1 元",
            sceneIds = listOf("jianfa", "wanda"),
            servesCampus = false,
            out = Direction(
                label = "（换乘）童游农贸 → 建发悦城方向",
                first = "06:30", last = "18:30",
                intervalMin = 40,
                fromCampus = true,
                official = true,
                note = "⚠️ 不经校区。从校区坐 107 路到「童游农贸（烟草公司）」下车换乘",
            ),
            back = Direction(
                label = "（换乘）建发悦城 → 童游农贸方向",
                first = "06:30", last = "18:30",
                intervalMin = 40,
                official = true,
                note = "末班 18:30，晚上回校区请预留换乘时间",
            ),
            note = "去建发悦城的唯一公交方案：107 路到童游农贸换 1 路（2 元总共）",
        ),

        // ─────────────────── K3 路（不经校区，参考）───────────────────
        BusRoute(
            name = "建阳K3路（赤岸小区 ⇄ 高铁南平市站）",
            shortName = "K3 路",
            endpoints = "赤岸小区（新区管委会）⇄ 高铁南平市站",
            fare = "以站牌为准",
            sceneIds = listOf("rail", "newarea"),
            servesCampus = false,
            out = Direction(
                label = "赤岸小区（新区管委会） → 高铁南平市站",
                first = "06:40", last = "21:40",
                intervalMin = 25,
                fromCampus = true,
                official = true,
                stops = listOf(
                    "赤岸小区（新区管委会）", "嘉禾北路", "万达广场西", "行政服务中心", "崇和里",
                    "云谷校区", "瑞云苑西", "福康苑（体育公园）", "市民广场（南平市行政中心）",
                    "建阳路北路口", "法治公园", "林后大街路口", "市委党校", "山尾路",
                    "将口电信", "高铁南平市站",
                ),
                note = "⚠️ 不经校区。需先在「万达广场西/嘉禾北路」换乘 103 路",
            ),
            back = Direction(
                label = "高铁南平市站 → 赤岸小区（新区管委会）",
                first = "07:40", last = "23:50",
                intervalMin = 25,
                official = true,
                stops = listOf(
                    "高铁南平市站", "将口电信", "山尾路", "市委党校", "林后大街路口", "法治公园",
                    "南林公园", "市民广场（南平市行政中心）", "福康苑（体育公园）", "和谐苑",
                    "云谷校区", "崇善里", "行政服务中心", "万达广场南", "嘉禾北路",
                    "赤岸小区（新区管委会）",
                ),
                note = "末班 23:50 对接高铁到站",
            ),
            note = "行政中心直达高铁线，对校区学生只是换乘参考；直达请用定制公交或 K2",
        ),

        // ─────────────────── K1 路（不经校区，参考）───────────────────
        BusRoute(
            name = "K1路（武夷山 ⇄ 高铁南平市站）",
            shortName = "K1 路",
            endpoints = "武夷广场 ⇄ 高铁南平市站（分快线 / 省道线两套时刻）",
            fare = "以站牌为准",
            sceneIds = listOf("rail"),
            servesCampus = false,
            out = Direction(
                label = "武夷广场 → 高铁南平市站",
                first = "07:00", last = "20:35",
                intervalMin = 60,
                fromCampus = true,
                official = true,
                note = "省道线 07:00-20:35；另有 K1 快线 20:05-21:00（夜间高铁接驳）",
            ),
            back = Direction(
                label = "高铁南平市站 → 武夷广场",
                first = "08:20", last = "21:50",
                intervalMin = 60,
                official = true,
                note = "省道线 08:20-21:50；另有 K1 快线 20:10-23:00",
            ),
            note = "⚠️ 不在校区设站。去武夷山玩的话，先从校区坐定制公交/K2 到高铁站再换 K1",
        ),
    )

    /** 按场景取线路（保持 [routes] 的原始顺序） */
    fun routesForScene(sceneId: String): List<BusRoute> =
        routes.filter { sceneId in it.sceneIds }

    /** 全部线路里出现过的场景 id（用于过滤空分组） */
    fun activeSceneIds(): List<String> = SCENES.map { it.id }.filter { routesForScene(it).isNotEmpty() }

    val SOURCE_NOTE =
        "数据核对 2026-10-25：建阳公交官方公告（首末班夏季表 2026-05-28 / 107 优化 2026-07-29 / " +
            "105 优化 2026-05-15 / K3 优化 2025-11-11）+ 大武夷新闻网 2025-08-27 开线公告。\n" +
            "首末班为**起点站发车时间**，校区是中途站，实际到站约晚 3~8 分钟；间隔来自实测与掌上公交，非官方。" +
            "$ZS_BUS_TIP · 服务热线 $SERVICE_HOTLINE"
}

// ══════════════════════════════════════════════════════════════════════
// 发车时刻估算（纯函数，可单测）
// ══════════════════════════════════════════════════════════════════════

/** "07:30" → LocalTime；解析失败返回 null */
fun parseHhMm(s: String): LocalTime? = runCatching {
    val parts = s.split(":")
    if (parts.size != 2) return null
    LocalTime.of(parts[0].toInt(), parts[1].toInt())
}.getOrNull()

/** LocalTime → "07:30" */
fun formatHhMm(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)

/** 某时刻所属时段的发车间隔（高峰优先，其次平峰，兜底 20 分钟） */
fun intervalAt(d: Direction, t: LocalTime): Int {
    val pf = d.peakFrom?.let { parseHhMm(it) }
    val pt = d.peakTo?.let { parseHhMm(it) }
    val peak = d.peakIntervalMin
    if (pf != null && pt != null && peak != null && !t.isBefore(pf) && !t.isAfter(pt)) return peak
    return d.intervalMin ?: peak ?: 20
}

/**
 * 估算 [now] 之后的下几班发车时间。
 *
 * ⚠️ **这是估算，不是实时数据**：用「首班 + 间隔」线性推算，官方并未公布逐班时刻。
 * 调用方必须在 UI 上标注「约」，不能让学生以为是精确时刻。
 *
 * 规则：
 * - 有 [Direction.fixedTimes] → 直接用官方固定班次，**不推算**
 * - 首末班缺失 → 返回空（无法估算）
 * - [now] 已过末班 → 返回空（调用方应提示「今日末班已过」）
 *
 * @return 最多 [count] 个 "HH:mm"
 */
fun estimateNext(d: Direction, now: LocalTime, count: Int = 3): List<String> {
    d.fixedTimes?.let { fixed ->
        return fixed.mapNotNull { parseHhMm(it) }
            .filter { !it.isBefore(now) }
            .take(count)
            .map { formatHhMm(it) }
    }
    val first = parseHhMm(d.first) ?: return emptyList()
    val last = parseHhMm(d.last) ?: return emptyList()
    if (now.isAfter(last)) return emptyList()

    val out = mutableListOf<String>()
    var t = first
    var guard = 0
    while (!t.isAfter(last) && out.size < count && guard++ < 500) {
        if (!t.isBefore(now)) out.add(formatHhMm(t))
        t = t.plusMinutes(intervalAt(d, t).toLong())
    }
    return out
}

/** 距离末班还剩多少分钟；已过末班返回负数，无末班信息返回 null */
fun minutesUntilLast(d: Direction, now: LocalTime): Long? {
    val last = parseHhMm(d.last) ?: return null
    return java.time.Duration.between(now, last).toMinutes()
}

/**
 * 从校区站到 [target] 还有几站。
 *
 * @return null = 该方向不途经（或站点数据缺失），调用方不要显示站数
 */
fun stopsFromCampus(d: Direction, target: String): Int? {
    if (target.isBlank()) return null
    val i = d.stops.indexOfFirst { it == BusData.CAMPUS_STOP }
    val j = d.stops.indexOfFirst { it == target }
    if (i < 0 || j < 0 || j <= i) return null
    return j - i
}
