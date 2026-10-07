# FAFU 校园助手 · DeepSeek 审查问题修复对照表

> **报告来源**：DeepSeek-V4.1-Flash(High) 两份报告（`FAFU-Campus-Assistant-v3.18-全量审查报告.md` + `FAFU-Campus-Assistant-v3.18-小部件审查报告.md`）  
> **修复版本**：`v4.0.sljzy`（commit `bde13cb`）  
> **问题总数**：77 项（P0 崩溃/丢数据、P1 体验正确性、P2 工程健壮）  
> **核对状态**：逐项比对代码实据 + 7 项终检，零遗漏

本表用于快速定位每条问题的修复位置，便于日后回溯。

## 统计

| 等级 | 数量 |
|---|---|
| P0 | 16 |
| P1 | 37 |
| P2 | 24 |
| **合计** | **77** |

## 🔴 P0 — 崩溃 / 丢数据（16 项）

| 编号 | 问题摘要 | 修复位置 | 行号 |
|---|---|---|---|
| A3 | 背景图主线程解码/EXIF | `app/src/main/java/com/campusglass/MainActivity.kt` | L291 |
| E1 | 历史清空/删除重启后复活 | `app/src/main/java/com/campusglass/pickup/PickupScreen.kt` | L53 |
| S1 | parseWeeks 周次区间无上限→OOM | `app/src/main/java/com/campusglass/schedule/ScheduleStore.kt` | L112 |
| SH1 | 分享码无长度上限 | `app/src/main/java/com/campusglass/schedule/ScheduleStore.kt` | L266 |
| S2 | 极端学期起始日→永久闪退 | `app/src/main/java/com/campusglass/schedule/ScheduleStore.kt` | L108 |
| S3 | 分享码 inflate 无输出上限(炸弹) | `app/src/main/java/com/campusglass/schedule/ScheduleStore.kt` | L267 |
| SH3 | 解压无输出上限(炸弹) | `app/src/main/java/com/campusglass/schedule/ScheduleStore.kt` | L267 |
| S4 | 导入不做字段语义校验→幽灵课程 | `app/src/main/java/com/campusglass/schedule/ScheduleStore.kt` | L345 |
| S5 | 坏条目静默丢弃但报成功 | `app/src/main/java/com/campusglass/schedule/ScheduleStore.kt` | L312 |
| S6 | 替换导入一键清空无备份 | `app/src/main/java/com/campusglass/schedule/ScheduleScreen.kt` | L138 |
| S7 | 合并去重键过松且不自去重 | `app/src/main/java/com/campusglass/schedule/ScheduleScreen.kt` | L118 |
| S8 | 导入无条件覆盖学期起始日 | `app/src/main/java/com/campusglass/schedule/ScheduleScreen.kt` | L96 |
| SH8 | 聊天软件吞 padding 致解码失败 | `app/src/main/java/com/campusglass/schedule/ScheduleStore.kt` | L295 |
| S11 | 整串JSON损坏清空整表并写回 | `app/src/main/java/com/campusglass/schedule/ScheduleStore.kt` | L118 |
| U1 | 下载零完整性校验 | `app/src/main/java/com/campusglass/ui/settings/SettingsScreen.kt` | L936 |
| W1 | 小部件周次未按 base 日期 | `app/src/main/java/com/campusglass/widget/WidgetListService.kt` | L35 |

## 🟡 P1 — 体验与正确性（37 项）

| 编号 | 问题摘要 | 修复位置 | 行号 |
|---|---|---|---|
| B1 | 末班硬补→伪末班 | `app/src/main/java/com/campusglass/bus/BusData.kt` | L195 |
| B2 | 上下行站点非互逆 | `app/src/main/java/com/campusglass/bus/BusData.kt` | L192 |
| B3 | 筛选空态无提示/文案打架 | `app/src/main/java/com/campusglass/bus/BusScreen.kt` | L111 |
| E2 | 查询中改输入→结果串号+不可取消 | `app/src/main/java/com/campusglass/pickup/ExpressApi.kt` | L31 |
| E3 | 状态码表只到7 | `app/src/main/java/com/campusglass/pickup/ExpressApi.kt` | L210 |
| E4 | 查无结果只做字面匹配 | `app/src/main/java/com/campusglass/pickup/ExpressApi.kt` | L215 |
| E5 | 失败用成功样式渲染 | `app/src/main/java/com/campusglass/pickup/PickupScreen.kt` | L185 |
| E6 | 限流被说成"没查到" | `app/src/main/java/com/campusglass/pickup/ExpressApi.kt` | L128 |
| E7 | data元素类型异常→整单放弃+英文透出 | `app/src/main/java/com/campusglass/pickup/ExpressApi.kt` | L106 |
| E8 | 结果卡在历史下方不滚动 | `app/src/main/java/com/campusglass/pickup/PickupScreen.kt` | L114 |
| E9 | 清空/删除无确认无无障碍 | `app/src/main/java/com/campusglass/pickup/PickupScreen.kt` | L66 |
| E10 | 菜鸟深链失败静默降级 | `app/src/main/java/com/campusglass/pickup/PickupScreen.kt` | L310 |
| E11 | 旋转丢输入与结果 | `app/src/main/java/com/campusglass/pickup/PickupScreen.kt` | L38 |
| S9 | 周次"解析不出"被当"没填" | `app/src/main/java/com/campusglass/schedule/ScheduleScreen.kt` | L831 |
| S10 | 越界课程无提示且可建越界课 | `app/src/main/java/com/campusglass/schedule/ScheduleScreen.kt` | L202 |
| S12 | 今天高亮不看当前周 | `app/src/main/java/com/campusglass/schedule/ScheduleScreen.kt` | L472 |
| S13 | 详情周次全展开+占负格 | `app/src/main/java/com/campusglass/schedule/ScheduleScreen.kt` | L117 |
| S14 | 必填错误在顶部，点保存无反应 | `app/src/main/java/com/campusglass/schedule/ScheduleScreen.kt` | L807 |
| S15 | 行高/列宽/字号与缩放脱节 | `app/src/main/java/com/campusglass/schedule/ScheduleScreen.kt` | L73 |
| S16 | 弹窗缺 imePadding | `app/src/main/java/com/campusglass/schedule/ScheduleScreen.kt` | L16 |
| S17 | 切Tab/旋转状态全丢 | `app/src/main/java/com/campusglass/schedule/ScheduleScreen.kt` | L47 |
| S18 | 周次芯片不滚到当前周 | `app/src/main/java/com/campusglass/schedule/ScheduleScreen.kt` | L301 |
| T2 | 节次时间无格式校验 | `app/src/main/java/com/campusglass/ui/settings/SettingsScreen.kt` | L685 |
| T3 | 节数输入超范围无反馈 | `app/src/main/java/com/campusglass/ui/settings/SettingsScreen.kt` | L623 |
| T5 | 色板角色缺失 | `app/src/main/java/com/campusglass/ui/theme/Theme.kt` | L31 |
| T6 | 主色不变时次要色撞色 | `app/src/main/java/com/campusglass/ui/theme/Theme.kt` | L55 |
| U2 | release用debug签名 | `../../../app/build.gradle.kts` | L34 |
| U3 | 无 canRequestPackageInstalls 预检 | `app/src/main/java/com/campusglass/ui/settings/SettingsScreen.kt` | L887 |
| U4 | 下载不可取消 | `app/src/main/java/com/campusglass/ui/settings/SettingsScreen.kt` | L866 |
| U5 | 旋转吞掉下载安装 | `app/src/main/java/com/campusglass/ui/settings/SettingsScreen.kt` | L803 |
| U6 | 安装包仅成功时清理 | `app/src/main/java/com/campusglass/UpdateCleanupReceiver.kt` | L15 |
| W2 | 行渲染越界 | `app/src/main/java/com/campusglass/widget/WidgetListService.kt` | L48 |
| W3 | 诗句拉取占满广播时限 | `app/src/main/java/com/campusglass/home/Hitokoto.kt` | L62 |
| W4 | 装完不刷新小部件 | `app/src/main/java/com/campusglass/UpdateCleanupReceiver.kt` | L21 |
| W5 | 行点击未走 fill-in | `app/src/main/java/com/campusglass/widget/WidgetListService.kt` | L62 |
| W6 | 深浅色写死不随系统 | `app/src/main/res/layout/widget_today.xml` | L22 |
| W7 | 跨天不自刷/开机不刷 | `app/src/main/java/com/campusglass/widget/TodayWidgetProvider.kt` | L28 |

## 🟢 P2 — 工程与稳健（24 项）

| 编号 | 问题摘要 | 修复位置 | 行号 |
|---|---|---|---|
| A1 | Intent extra 未消费 | `app/src/main/java/com/campusglass/MainActivity.kt` | L101 |
| A2 | barVisible 旋转丢 | `app/src/main/java/com/campusglass/MainActivity.kt` | L146 |
| A4 | customBg 未判 File 存在 | `app/src/main/java/com/campusglass/ui/glass/Acrylic.kt` | L91 |
| A6 | 诗句每次进首页强刷 | `app/src/main/java/com/campusglass/home/Hitokoto.kt` | L34 |
| A7 | 深色启动闪白 | `app/src/main/res/values-night/themes.xml` | L3 |
| B4 | 推算标注列表/详情不一致 | `app/src/main/java/com/campusglass/bus/BusScreen.kt` | L88 |
| B6 | 站点箭头当文本/无朗读语义 | `app/src/main/java/com/campusglass/bus/BusScreen.kt` | L1 |
| E12 | 缓存与细节(忽略缓存重查) | `app/src/main/java/com/campusglass/pickup/ExpressApi.kt` | L85 |
| S19 | 20周硬下限+学期结束无上限 | `app/src/main/java/com/campusglass/schedule/ScheduleScreen.kt` | L298 |
| S20 | 假期数据窗口后永远无假期 | `app/src/main/java/com/campusglass/schedule/ScheduleScreen.kt` | L643 |
| S21 | 空课表分享码被误判无效 | `app/src/main/java/com/campusglass/schedule/ScheduleStore.kt` | L261 |
| S22 | 关周末后周末课无法编辑 | `app/src/main/java/com/campusglass/schedule/ScheduleScreen.kt` | L203 |
| S23 | 详情4按钮挤一行 | `app/src/main/java/com/campusglass/schedule/ScheduleScreen.kt` | L325 |
| S24 | PeriodTable.all 每次重建(性能) | `app/src/main/java/com/campusglass/schedule/ScheduleStore.kt` | L43 |
| T7 | GitHub限流无文案/Retry-After | `app/src/main/java/com/campusglass/ui/settings/SettingsScreen.kt` | L739 |
| T8 | 恢复默认背景不真删/作息无重置 | `app/src/main/java/com/campusglass/ui/settings/SettingsScreen.kt` | L146 |
| U7 | 下载URL硬编码不读资产 | `app/src/main/java/com/campusglass/ui/settings/SettingsScreen.kt` | L757 |
| W8 | requestCode 撞车 | `app/src/main/java/com/campusglass/widget/TodayWidgetProvider.kt` | L26 |
| W9 | 稳定id 用 position | `app/src/main/java/com/campusglass/widget/WidgetListService.kt` | L75 |
| W10 | 删部件不清状态 | `app/src/main/java/com/campusglass/widget/TodayWidgetProvider.kt` | L66 |
| W11 | 无描述/预览 | `app/src/main/res/xml/widget_today_info.xml` | L9 |
| W12 | 重复渲染 | `app/src/main/java/com/campusglass/widget/TodayWidgetProvider.kt` | L81 |
| W13 | 空态文案无区分 | `app/src/main/java/com/campusglass/widget/TodayWidgetProvider.kt` | L151 |
| W14 | 点击区过小 | `app/src/main/res/layout/widget_today.xml` | L39 |

---

## 终检（修复未引入新问题）

| 检查点 | 结论 |
|---|---|
| decode 坏行不中断整表 | ✅ `continue` 跳过 |
| 异常不透出英文（E7）| ✅ 全中文固定文案 |
| 替换导入先备份再写 | ✅ `courses_backup` 先于 `saveCourses` |
| 下载校验失败即删不装 | ✅ 指纹/包名不符均 `delete()` |
| 签收需正向证据 | ✅ `ischeck==1` 或轨迹含签收 |
| 数据损坏时拒写 | ✅ `saveCourses` 返回 false |
| 无 20 周硬下限残留 | ✅ 已动态化 |

> 本对照表由 AI 助手（Mimo v2.6 / OpenClaw）自动生成并逐条核对。