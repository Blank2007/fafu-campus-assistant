package com.campusglass

import com.campusglass.bus.BusData
import com.campusglass.bus.Direction
import com.campusglass.bus.estimateNext
import com.campusglass.bus.formatHhMm
import com.campusglass.bus.intervalAt
import com.campusglass.bus.minutesUntilLast
import com.campusglass.bus.parseHhMm
import com.campusglass.bus.stopsFromCampus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * 公交模块逻辑与数据单测（v6.0 重写时补上，此前该模块零测试）。
 *
 * 分三类：
 * 1. **发车估算**：纯函数边界（首班前 / 高峰 / 平峰 / 过末班 / 固定班次）
 * 2. **站数计算**：`stopsFromCampus` 的正常与异常路径
 * 3. **数据回归**：把 2026-10-25 联网核对的结果锁死，防止以后又被改回旧数据
 */
class BusLogicTest {

    // ══════════════════ 1. 发车时刻估算 ══════════════════

    private fun dir(
        first: String = "07:00",
        last: String = "21:00",
        interval: Int? = 20,
        peak: Int? = null,
        peakFrom: String? = null,
        peakTo: String? = null,
        fixed: List<String>? = null,
    ) = Direction(
        label = "测试方向",
        first = first, last = last,
        intervalMin = interval,
        peakIntervalMin = peak, peakFrom = peakFrom, peakTo = peakTo,
        fixedTimes = fixed,
    )

    @Test
    fun beforeFirstBus_returnsFirstBusAsNext() {
        // 06:00 去等 07:00 首班 → 下一班就是首班，不能返回空
        val next = estimateNext(dir(first = "07:00"), LocalTime.of(6, 0), 3)
        assertEquals("07:00", next.first())
        assertEquals(3, next.size)
    }

    @Test
    fun atFirstBusTime_includesFirstBus() {
        val next = estimateNext(dir(first = "07:00"), LocalTime.of(7, 0), 2)
        assertEquals(listOf("07:00", "07:20"), next)
    }

    @Test
    fun afterLastBus_returnsEmpty() {
        // 22:00 已过 21:00 末班 → 必须返回空，让 UI 提示「今日末班已过」
        val next = estimateNext(dir(last = "21:00"), LocalTime.of(22, 0), 3)
        assertTrue("过末班应返回空", next.isEmpty())
    }

    @Test
    fun lastBusIsInclusive() {
        // 正好在末班时刻，末班本身应该算「赶得上」
        val next = estimateNext(dir(first = "07:00", last = "21:00", interval = 60), LocalTime.of(21, 0), 3)
        assertEquals(listOf("21:00"), next)
    }

    @Test
    fun estimatesNeverExceedLastBus() {
        // 所有估算出来的班次都必须 <= 末班，不能凭空多出一班
        val d = dir(first = "07:00", last = "21:00", interval = 25)
        val next = estimateNext(d, LocalTime.of(20, 50), 5)
        next.forEach {
            val t = parseHhMm(it)!!
            assertTrue("$it 不应晚于末班 21:00", !t.isAfter(LocalTime.of(21, 0)))
        }
    }

    @Test
    fun peakIntervalUsedInsidePeakWindow() {
        val d = dir(interval = 20, peak = 10, peakFrom = "07:30", peakTo = "08:30")
        assertEquals("高峰内应用 10 分钟", 10, intervalAt(d, LocalTime.of(8, 0)))
        assertEquals("高峰外应用 20 分钟", 20, intervalAt(d, LocalTime.of(10, 0)))
        assertEquals("高峰起点应算高峰", 10, intervalAt(d, LocalTime.of(7, 30)))
        assertEquals("高峰终点应算高峰", 10, intervalAt(d, LocalTime.of(8, 30)))
        assertEquals("高峰前 1 分钟不算高峰", 20, intervalAt(d, LocalTime.of(7, 29)))
    }

    @Test
    fun peakWindowShiftsSubsequentEstimates() {
        // 07:30 之后间隔变密，08:00 前后的班次应该更密
        val d = dir(first = "07:00", last = "21:00", interval = 30, peak = 10, peakFrom = "07:30", peakTo = "08:30")
        val next = estimateNext(d, LocalTime.of(7, 0), 6)
        // 07:00 → 07:30(30min) → 07:40 → 07:50 → 08:00 → 08:10 ...
        assertEquals(listOf("07:00", "07:30", "07:40", "07:50", "08:00", "08:10"), next)
    }

    @Test
    fun fixedTimes_areUsedDirectly_notEstimated() {
        // 有官方固定班次时必须照抄，绝不能按间隔推算
        val d = dir(first = "", last = "", interval = 30, fixed = listOf("06:50", "07:55", "14:30", "18:05"))
        val next = estimateNext(d, LocalTime.of(8, 0), 3)
        assertEquals(listOf("14:30", "18:05"), next)
    }

    @Test
    fun noTimetable_returnsEmpty() {
        // 定制公交这类「按校区通知发车」的，没有首末班 → 不能瞎算
        val d = dir(first = "", last = "")
        assertTrue(estimateNext(d, LocalTime.of(9, 0), 3).isEmpty())
    }

    @Test
    fun missingLast_returnsEmpty() {
        val d = dir(first = "07:00", last = "")
        assertTrue("没有末班信息无法估算", estimateNext(d, LocalTime.of(9, 0), 3).isEmpty())
    }

    @Test
    fun minutesUntilLast_handlesBothSides() {
        val d = dir(last = "21:00")
        assertEquals(60L, minutesUntilLast(d, LocalTime.of(20, 0)))
        assertTrue("过末班应为负数", (minutesUntilLast(d, LocalTime.of(21, 30)) ?: 0) < 0)
        assertNull(minutesUntilLast(dir(last = ""), LocalTime.of(20, 0)))
    }

    @Test
    fun parseHhMm_rejectsGarbage() {
        assertNotNull(parseHhMm("07:30"))
        assertNull(parseHhMm(""))
        assertNull(parseHhMm("0730"))
        assertNull(parseHhMm("ab:cd"))
        assertEquals("07:05", formatHhMm(LocalTime.of(7, 5)))
    }

    // ══════════════════ 2. 站数计算 ══════════════════

    private fun dirWithStops(vararg stops: String) =
        Direction(label = "测试", stops = stops.toList())

    @Test
    fun stopsFromCampus_countsForward() {
        val d = dirWithStops("总站", BusData.CAMPUS_STOP, "A", "B", "目的地")
        assertEquals(3, stopsFromCampus(d, "目的地"))
    }

    @Test
    fun stopsFromCampus_returnsNullWhenTargetBeforeCampus() {
        // 目标站在校区之前（反方向）→ 不能返回负数站数
        val d = dirWithStops("目的地", BusData.CAMPUS_STOP, "A")
        assertNull(stopsFromCampus(d, "目的地"))
    }

    @Test
    fun stopsFromCampus_returnsNullWhenTargetMissing() {
        val d = dirWithStops("总站", BusData.CAMPUS_STOP, "A")
        assertNull(stopsFromCampus(d, "不存在的站"))
        assertNull(stopsFromCampus(d, ""))
    }

    @Test
    fun stopsFromCampus_returnsNullWhenCampusMissing() {
        // 不经校区的线路没有校区站 → null
        val d = dirWithStops("A", "B", "C")
        assertNull(stopsFromCampus(d, "C"))
    }

    // ══════════════════ 3. 数据回归（锁住 2026-10-25 核对结果）══════════════════

    private fun route(shortName: String) =
        BusData.routes.first { it.shortName == shortName }

    @Test
    fun sceneIds_areAllValid() {
        val valid = BusData.SCENES.map { it.id }.toSet()
        BusData.routes.forEach { r ->
            r.sceneIds.forEach { id ->
                assertTrue("线路「${r.name}」引用了不存在的场景 id: $id", id in valid)
            }
            assertTrue("线路「${r.name}」至少要有一个场景", r.sceneIds.isNotEmpty())
        }
    }

    @Test
    fun everyScene_hasAtLeastOneRoute() {
        BusData.SCENES.forEach { s ->
            assertTrue("场景「${s.title}」没有任何线路，筛选后会是空白", BusData.routesForScene(s.id).isNotEmpty())
        }
    }

    @Test
    fun routesServingCampus_mustContainCampusStop() {
        BusData.routes.filter { it.servesCampus }.forEach { r ->
            assertTrue(
                "线路「${r.name}」标称经校区，但去程站点里没有校区站",
                r.out.stops.contains(BusData.CAMPUS_STOP) || r.out.stops.isEmpty(),
            )
        }
    }

    @Test
    fun nonCampusRoutes_areMarkedCorrectly() {
        // K3/K1/建阳1路 明确不经校区，标错会导致学生按它去等车
        assertTrue("K3 不应标为经校区", !route("K3 路").servesCampus)
        assertTrue("K1 不应标为经校区", !route("K1 路").servesCampus)
        assertTrue("建阳1路 不应标为经校区", !route("建阳1路").servesCampus)
        assertTrue("103 必须经校区", route("103 路").servesCampus)
        assertTrue("107 必须经校区", route("107 路").servesCampus)
    }

    @Test
    fun route103_usesOfficialSummerTimetable() {
        // 官方夏季表：万达广场 7:30-20:45 / 新岭公交总站 7:45-21:00
        // 旧数据把「万达 7:30」错当成「新岭 7:30」，这条测试锁死正确值
        val r = route("103 路")
        assertEquals("去程（新岭总站发车）首班应为 07:45", "07:45", r.out.first)
        assertEquals("去程末班应为 21:00", "21:00", r.out.last)
        assertEquals("回程（万达发车）首班应为 07:30", "07:30", r.back.first)
        assertEquals("回程末班应为 20:45", "20:45", r.back.last)
    }

    @Test
    fun route107_dropsStopsCancelledIn2026August() {
        // 2026-08-01 起改走兴业路（林后村），取消滨江壹号/崇阳北路/实业集团/广信街/嘉禾北路
        val stops = route("107 路").out.stops
        listOf("滨江壹号", "崇阳北路", "实业集团", "广信街（新区管委会）", "嘉禾北路").forEach {
            assertTrue("107 去程不应再经「$it」（2026-08-01 已取消）", it !in stops)
        }
        assertTrue("107 去程应经「兴业路（林后村）」", "兴业路（林后村）" in stops)
        assertEquals("107 去程首班应为 07:30", "07:30", route("107 路").out.first)
        assertEquals("107 回程首班应为 07:10", "07:10", route("107 路").back.first)
    }

    @Test
    fun route105_includesStopsAddedIn2026May() {
        // 2026-05-19 起新增：南平移动 / 宝鼎路 / 芦上 / 海林大厦 / 碧全月亮湾
        val stops = route("105 路").out.stops
        listOf("南平移动", "宝鼎路", "芦上", "海林大厦", "碧全月亮湾").forEach {
            assertTrue("105 去程应含「$it」（2026-05-19 新增）", it in stops)
        }
        assertTrue("105 去程不应再经「南林」（已取消）", "南林" !in stops)
        assertTrue("105 站点应为完整 25 站左右，不能只有十几站", stops.size >= 24)
    }

    @Test
    fun routeK3_usesCorrectedStopName() {
        // 官方 2025-11 公告下行是「崇和里」，旧数据误录为「明德里」
        val stops = route("K3 路").out.stops
        assertTrue("K3 应含「崇和里」", "崇和里" in stops)
        assertTrue("K3 不应含旧名「明德里」", "明德里" !in stops)
        assertEquals("K3 末班应对接高铁到站 23:50", "23:50", route("K3 路").back.last)
    }

    @Test
    fun routeK2_lastBusMatchesOfficial() {
        val r = route("K2 路")
        assertEquals("K2 西区首班 07:00", "07:00", r.out.first)
        assertEquals("K2 西区末班 21:00", "21:00", r.out.last)
        assertEquals("K2 高铁首班 08:10", "08:10", r.back.first)
        assertEquals("K2 高铁末班应对接高铁到站 23:50", "23:50", r.back.last)
    }

    @Test
    fun industrialLine_usesOfficialCampusTimes() {
        // 官方夏季表：社会福利中心 6:50-18:30 / 福建农林大学南平校区 7:10-17:40
        val r = route("园区专线")
        assertEquals("校区首班 07:10", "07:10", r.out.first)
        assertEquals("校区末班 17:40", "17:40", r.out.last)
        assertEquals("社会福利中心首班 06:50", "06:50", r.back.first)
        assertEquals("社会福利中心末班 18:30", "18:30", r.back.last)
    }

    @Test
    fun noRouteHasDuplicateStops() {
        // 同一方向里出现重复站名，说明数据录串了
        BusData.routes.forEach { r ->
            listOf(r.out to "去程", r.back to "回程").forEach { (d, tag) ->
                val dup = d.stops.groupingBy { it }.eachCount().filter { it.value > 1 }
                assertTrue("「${r.name}」$tag 有重复站点: ${dup.keys}", dup.isEmpty())
            }
        }
    }

    @Test
    fun campusStopName_isConsistent() {
        // 校区站名必须全库统一，否则「单向站」判断和站数计算都会失准
        assertEquals("福建农林大学南平校区", BusData.CAMPUS_STOP)
        BusData.routes.forEach { r ->
            listOf(r.out, r.back).forEach { d ->
                d.stops.filter { it.contains("农林大学") }.forEach {
                    assertEquals("线路「${r.name}」里的校区站名写法不一致: $it", BusData.CAMPUS_STOP, it)
                }
            }
        }
    }
}
