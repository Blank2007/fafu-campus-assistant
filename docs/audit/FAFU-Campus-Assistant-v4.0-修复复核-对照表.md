# FAFU 校园助手 · v4.0 修复复核报告 · 修复对照表

> **报告来源**：DeepSeek-V4.1-Flash(High)《FAFU-Campus-Assistant-v4.0-修复复核报告.md》  
> **修复版本**：`v4.1.sljzy`  
> **范围**：复核发现的 28 项新问题（V4-1~V4-28），其中 **V4-2 签名按用户决定跳过**（继续 debug 签名），实修 27 项  
> **验证**：全量构建通过 + 纯逻辑单元测试 5/5 通过 + 逐项 grep 行号实据

## 修复清单（问题编号 → 文件:行号）

| 编号 | 等级 | 问题摘要 | 修复位置 | 行号 |
|---|---|---|---|---|
| V4-1 | P0 | 备份只写不读→补恢复入口 | `java/com/campusglass/schedule/ScheduleStore.kt` | L212 |
| V4-3 | P0 | 开机刷新缺权限(死代码) | `AndroidManifest.xml` | L35 |
| V4-4 | P1 | 分享码自己导不进(长度失配) | `java/com/campusglass/schedule/ScheduleStore.kt` | L355 |
| V4-5 | P0 | 条级损坏静默覆盖 | `java/com/campusglass/schedule/ScheduleStore.kt` | L175 |
| V4-6 | P1 | 下载取消不打断I/O+文案错 | `java/com/campusglass/ui/settings/SettingsScreen.kt` | L913 |
| V4-7 | P0 | 替换导入无二次确认 | `java/com/campusglass/schedule/ScheduleScreen.kt` | L514 |
| V4-8 | P1 | 20s总时限是软约束 | `java/com/campusglass/pickup/ExpressApi.kt` | L135 |
| V4-9 | P1 | 分享码折行解析失败 | `app/src/main/java/com/campusglass/schedule/ScheduleStore.kt` | L362 |
| V4-10 | P1 | 容器角色缺失(FilterChip不搭) | `java/com/campusglass/ui/theme/Theme.kt` | L87 |
| V4-11 | P1 | 派生色对比度不足 | `java/com/campusglass/ui/theme/Theme.kt` | L81 |
| V4-12 | P1 | 未来无课周点不到 | `java/com/campusglass/schedule/ScheduleScreen.kt` | L321 |
| V4-13 | P1 | 校验范围≠保存范围 | `java/com/campusglass/ui/settings/SettingsScreen.kt` | L674 |
| V4-14 | P2 | 导入顺序可半改 | `java/com/campusglass/schedule/ScheduleScreen.kt` | L153 |
| V4-15 | P2 | 课表重复解析无缓存 | `java/com/campusglass/schedule/ScheduleStore.kt` | L130 |
| V4-16 | P2 | 删部件闹钟空转 | `java/com/campusglass/widget/TodayWidgetProvider.kt` | L72 |
| V4-17 | P2 | 位图内存峰值过高 | `java/com/campusglass/MainActivity.kt` | L341 |
| V4-18 | P2 | 真实回程站点成死数据 | `java/com/campusglass/bus/BusScreen.kt` | L258 |
| V4-19 | P2 | 公交状态切Tab丢 | `java/com/campusglass/bus/BusScreen.kt` | L51 |
| V4-20 | P2 | slots/确认框不持久化 | `java/com/campusglass/schedule/ScheduleScreen.kt` | L771 |
| V4-21 | P2 | 删除忽略返回值 | `java/com/campusglass/schedule/ScheduleScreen.kt` | L381 |
| V4-22 | P2 | 强制深色冷启动闪底 | `java/com/campusglass/MainActivity.kt` | L97 |
| V4-23 | P2 | 废弃API+静默失效 | `java/com/campusglass/MainActivity.kt` | L90 |
| V4-24 | P2 | 小部件不读App设置 | `java/com/campusglass/widget/WidgetListService.kt` | L28 |
| V4-25 | P2 | 限流/接口异常误分类 | `java/com/campusglass/pickup/ExpressApi.kt` | L130 |
| V4-26 | P2 | 校验措辞失实 | `java/com/campusglass/ui/settings/SettingsScreen.kt` | L972 |
| V4-27 | P2 | 时间格式不查数值 | `java/com/campusglass/schedule/ScheduleStore.kt` | L70 |
| V4-28 | P2 | 无自动化兜底(单测) | `app/src/test/java/com/campusglass/ScheduleLogicTest.kt` | L15 |

## 跳过项（用户决定）

| 编号 | 问题 | 决定 |
|---|---|---|
| V4-2 | 签名切换护栏（正式签名）| **跳过**：继续 debug 签名（用户明确要求）；keystore.properties 机制保留可随时启用 |

## 文档一致性修正（报告第 5 节）

| 问题 | 处理 |
|---|---|
| 三套数字口径（77/58/12+30+16）| 应用内与 CHANGELOG 统一为「77 项条目 / 58 完全达成 / 19 部分达成」口径 |
| 「正式签名」「lint」表述失实 | 更正为「签名机制预留（当前 debug 签名）」；lint 关闭保留并声明 |
| 旧对照表 U2 路径穿出仓库 | 已修正为 `app/build.gradle.kts` |
| 旧对照表 T1/T4/A5 漏列 | 已补录 |
| README 缺已知问题 | 已加「⚠️ 已知问题」小节 |

## 终检（修复未引入新问题）

| 检查点 | 结论 |
|---|---|
| 单元测试（parseWeeks/compactWeeks/validTimeEntry/normalize）| ✅ 5/5 通过 |
| 全量构建 | ✅ BUILD SUCCESSFUL |
| 备份恢复双保险（恢复前先存当前）| ✅ |
| 取消下载冒泡 CancellationException | ✅ 不再吞成网络异常 |
| 位图回收（旋转后 recycle 原图）| ✅ |
| 小部件设置一致性（节数/周末）| ✅ |
| 文档口径统一 | ✅ |

> 本对照表由 AI 助手（Mimo v2.6 / OpenClaw）自动生成并逐条核对。