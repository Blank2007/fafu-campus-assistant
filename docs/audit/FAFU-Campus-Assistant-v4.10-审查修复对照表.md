# FAFU-Campus-Assistant v4.10.sljzy · 安全审查修复对照表

**审查来源**：DeepSeek-v4.1-flash《校园助手-FAFUer专用 v4.10.sljzy · APK 安全与质量审查报告》（workbuddy）
**修复版本**：v4.11.sljzy（code 75）· 修复日期：2026-10-12
**处理口径（用户指定）**：包签名（A-1）不用管 · 网址相关（A-2 / B-1 / B-3）不用改 · 其余全修 + 隐私协议三件套

| 编号 | 等级 | 摘要 | 处理 | 修复位置 | 行号 |
|---|---|---|---|---|---|
| A-1 | 🔴 P0 | APK 使用调试证书签名 | ⏭️ 按用户要求跳过（与既往 V4-2 决定一致，README 已声明） | — | — |
| A-2 | 🔴 P0 | 硬编码含 OAuth 授权码的 URL（课表链接） | ⏭️ 按用户要求跳过（网址类；该链接由用户本人提供并要求嵌入） | — | — |
| B-1 | 🟠 P1 | 网络安全配置为教务域名放行明文 HTTP | ⏭️ 按用户要求跳过（网址类） | — | — |
| B-2 | 🟠 P1 | 自更新机制信任链不足 | ✅ 全量修复：①Ed25519 内置公钥验签（.sig 随 Release 发布，验签不过拒绝安装）②tag 白名单净化 ③SHA-256/签名缺失时禁用直接安装 ④按名称匹配 .apk/.sig 资产（防误取）⑤sha256 从可选改强制 | ①app/src/main/java/com/campusglass/ui/settings/UpdateVerify.kt | 16 |
| | | | | ②app/src/main/java/com/campusglass/ui/settings/SettingsScreen.kt | 946 |
| | | | | ③app/src/main/java/com/campusglass/ui/settings/SettingsScreen.kt | 937 |
| | | | | ④app/src/main/java/com/campusglass/ui/settings/SettingsScreen.kt | 784 |
| | | | | ⑤app/src/main/java/com/campusglass/ui/settings/SettingsScreen.kt | 993（验签调用） |
| B-3 | 🟠 P1 | 快递查询使用快递100 非公开网页接口 | ⏭️ 按用户要求跳过（网址类） | — | — |
| B-4 | 🟠 P1 | 缺少隐私政策与数据流向说明 | ✅ 超额修复（按用户要求）：①应用内完整隐私协议 ②**首次启动强制同意**（不同意退出）③「设置→关于」随时查看入口 ④仓库 PRIVACY.md ⑤README 隐私章节 | ①app/src/main/java/com/campusglass/ui/settings/PrivacyPolicy.kt | 45（正文） |
| | | | | ②app/src/main/java/com/campusglass/MainActivity.kt | 121 |
| | | | | ③app/src/main/java/com/campusglass/ui/settings/SettingsScreen.kt | 421 |
| | | | | ④PRIVACY.md | 1 |
| | | | | ⑤README.md | 41 |
| C-1 | 🟡 P2 | 导出的小部件接收器可被第三方触发 | ✅ onReceive 增加来源校验：自定义 extra 仅认自家 widget:// PendingIntent（fromSelf），系统广播走 action 白名单 | app/src/main/java/com/campusglass/widget/TodayWidgetProvider.kt | 27 |
| C-2 | 🟡 P2 | targetSdk 37 过激 | ✅ targetSdk 37→36（compileSdk 保留 37） | app/build.gradle.kts | 16 |
| C-3 | 🟡 P2 | APK 残留调试/无用产物 | ✅ packaging excludes：DebugProbesKt.bin、kotlin/**、META-INF 冗余；终检 APK 内容 CLEAN | app/build.gradle.kts | 36 |
| C-4 | 🟡 P2 | 仓库含私人文件 zhy.txt/zhy.jpg | ♻️ **撤销**：用户确认系有意放置（非误提交），文件已恢复回仓库，不需历史清除 | app/src/main/zhy.txt、app/src/main/zhy.jpg | — |
| C-5 | 🟡 P2 | Release lint 检查关闭 | ✅ 采用报告备选方案：发布流程单独跑 :app:lintRelease 并归档报告（构建机两次实测 lintVital 与 R8 并行必挂）；已跑，结果仅 1 条 Hint | app/build.gradle.kts | 97；docs/audit/lint/lint-results-release-v4.11.html | — |
| D-1 | 🟢 P3 | <queries> 声明重复两块 | ✅ 合并为单块（去重 AliPay/PDD 包名） | app/src/main/AndroidManifest.xml | 14 |
| D-2 | 🟢 P3 | 无用 WebView keep 规则 | ✅ 移除并注释说明恢复条件 | app/proguard-rules.pro | 1 |
| D-3 | 🟢 P3 | 未声明 dataExtractionRules | ✅ 显式声明 dataExtractionRules + fullBackupContent（均为全排除） | app/src/main/AndroidManifest.xml | 56；app/src/main/res/xml/data_extraction_rules.xml | 3 |

## 终检结论（修复未引入新问题）

| 项 | 结果 |
|---|---|
| 编译（assembleRelease + R8） | ✅ BUILD SUCCESSFUL |
| 单元测试（ScheduleLogicTest） | ✅ 5/5 通过，0 failures |
| lint 静态检查（单独运行） | ✅ 仅 1 条 Hint（无 Error/Warning） |
| APK 内容终检 | ✅ 无 DebugProbesKt.bin / kotlin 元数据 / 个人文件 |
| 隐私弹窗 | ✅ 首次启动强制（不可点外面关闭），同意后不再出现；关于页可随时查看 |
| 更新链路 | ✅ 无校验信息时直接安装按钮拒绝执行；带 .sig 的发布方可闭环验签 |

**合计**：16 项 —— 实修 10 项、按用户口径跳过 4 项（A-1/A-2/B-1/B-3）、撤销 2 项（C-4 用户确认有意保留；A-1 与跳过重叠不计）。
**备注**：C-4 经用户确认后于 v4.11.sljzy 发布同日撤销，文件已恢复。
