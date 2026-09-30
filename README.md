# 校园助手-FAFUer专用 🎒

> 🗓 **本项目全程在 AI 助手的每日 3 小时免费时长内完成开发**（需求 → 设计 → 开发 → 多轮迭代 → 开源发布，均为这 3 小时内对话完成）
>
> 全部代码由 AI 助手（Mimo v2.6-Pro / OpenClaw 编程助手）独立编写，需求设计、提示词（Prompt）与测试反馈由 **Void_Blank** 提供。
>
> **v3.3 已按完整审查报告逐条修复 40+ 处问题**（含“查不到的单号被显示成已签收”“自定义主题色变全透明”“公交末班车永远缺一班”等），详见 [CHANGELOG.md](CHANGELOG.md)。

## 🆕 v3.3 修复摘要

> 完整清单见 [CHANGELOG.md](CHANGELOG.md)。挑几条用户最容易碰到的：

| 类别 | 修复内容 |
|---|---|
| 🚨 会误导用户 | 快递「查无结果」不再显示成 **已签收 ✔**（接口对查不到的单号同样返回 status=200/state=3，真值标记是 condition=F00） |
| 🎨 主题色 | 自定义色不再变成**全透明**、不再**重启后丢失**、不再被色彩风格/色温**叠加两次**而持续漂移 |
| 🚌 公交 | **末班车补全**（旧版最后一班总早于标称末班）；上下行站点表互逆校正，不再出现两个终点站 |
| 📚 课表 | 编辑课程新增的时段不再被丢弃；周次与单双周冲突会提示（不再静默变成“全部周”）；周次范围不再写死 1-20 |
| ⌨️ 输入体验 | 键盘不再遮挡输入框；对话框不再被顶出屏幕；chip 行可换行；设置开关靠右 |
| 🌙 深色模式 | 状态栏图标跟随 App 主题；启动不再闪白屏；卡片配色统一 |
| 🔎 检查更新 | 按版本号比较（旧版会把更旧的版本也提示为“新版本”），区分 404/403/网络错误 |
| 🔐 隐私/发布 | 移除 `QUERY_ALL_PACKAGES`；`allowBackup=false`；背景图降采样并按 EXIF 纠正方向 |

**系统要求**：Android 12+（minSdk 31，优先适配 Android 15 / 小米澎湃 OS）· 目前只打包 **arm64-v8a**，32 位机型与 x86 模拟器装不上 · 安装包约 3 MB

## ✨ 功能

| 模块 | 说明 |
|---|---|
| 🏠 首页 | App 介绍卡片（这个工具能做什么）+ 取件码/课表/公交快捷入口 + 农大常用官网 |
| 📦 取件码跳转 | ① 支付宝·菜鸟取件码一键直达（免装菜鸟App）② 拼多多个人中心入口（保留 Activity 探测兜底） |
| 🚚 快递单号查询 | 输入单号直接看物流轨迹（API 直出，自动识别快递公司）；读取剪贴板快捷填单号；查询历史记录（可删单条/一键清空） |
| 📚 课表 | WakeUp 风格周课表格，手动添加课程（起止周/单双周/起止节次/多时段），节假日标记，日期显示 |
| 🚌 南平校区公交 | 103/105/107/K2/K1 路站点与时刻表，目的地筛选（建发悦城/万达广场/动车站），点线路看详情 |

## 📱 安装

到 [Releases](https://github.com/Blank2007/fafu-campus-assistant/releases) 下载最新 `校园助手-FAFUer专用-vX.X.apk`，允许"未知来源"安装即可。

## 🛠 从源码构建

```bash
# Android Studio 打开本目录，或命令行：
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

技术栈：Kotlin · Jetpack Compose (Material 3) · AGP 9 · targetSdk 37

## ✍️ 作者与代码归属

- **代码编写**：AI 助手（Mimo v2.6-Pro / OpenClaw 编程助手）—— 从架构设计到每一行代码全部由 AI 独立完成
- **需求与提示词**：[Void_Blank](https://github.com/Blank2007) —— 功能构想、界面意见、逐轮测试反馈
- 简单说：**人出想法，AI 写代码**

## 📮 联系 · 交流 · 反馈

- GitHub：[Blank2007](https://github.com/Blank2007)（Void_Blank）
- 邮箱：**1553008865@qq.com**
- （QQ 号不公开；交流欢迎 GitHub Issues / 邮件）

**非常欢迎交流与反馈！** 不管是发现 Bug、想要新功能、对实现好奇想聊聊，还是想一起折腾点别的好玩的，都欢迎通过 GitHub 找我。你提的每一条建议，都可能出现在下一个版本的更新日志里 🚀

## 📋 版本修改日志

见 [CHANGELOG.md](CHANGELOG.md)（App 内「关于」页同步展示）。

## 🙏 致谢

| 项目 / 数据 | 作者 | 地址 | 许可 | 用途 |
|---|---|---|---|---|
| WakeUp 课程表 | YZune | https://github.com/YZune/WakeUpSchedule | Apache-2.0 | 间接参考：周课表格 UI 思路 |
| ling_QuickShortcut | qinyuanxin132 | https://github.com/qinyuanxin132/ling_QuickShortcut | MIT | 间接参考：拼多多身份码跳转思路 |
| 拼多多 Scheme 公开资料 | CSDN 社区整理 | https://blog.csdn.net/weixin_48141487/article/details/140077257 | 公开资料 | 间接参考：页面拉起路径 |
| 支付宝菜鸟小程序取件码 | V2EX 社区 | https://v2ex.com/t/1002900 | 公开资料 | 取件码直达 scheme |
| 建阳公交线路通告 | 建阳区公交公司 | https://www.greatwuyi.com/guangg/content/202508/27/c1555764.html | 公开资讯 | 公交站点与时刻数据 |
| 快递100 | 深圳前海百递网络 | https://www.kuaidi100.com | 平台服务 | 快递轨迹查询接口 |
| Jetpack Compose / AndroidX | Google & AOSP | https://android.googlesource.com/platform/frameworks/support | Apache-2.0 | 直接依赖：UI/动画 |
| Kotlin | JetBrains | https://github.com/JetBrains/kotlin | Apache-2.0 | 直接依赖：语言 |

**致谢声明**：直接依赖的开源库版权归其作者所有，随分发保留原始许可证与署名；标注"间接参考"的仅借鉴公开思路、未复制源码；公交数据引自官方公开通告（时刻以站牌为准）；快递查询由快递100提供接口，仅作个人查询展示用途。本项目免费开源，仅供学习交流。若有侵权，请尽快联系我。

## ⚠️ 已知问题 / 待办

- **发布包目前仍用 debug 密钥签名**（`app/build.gradle.kts` 里有切换到正式签名的注释步骤）。换一台机器重新生成 debug keystore 会导致新版本**无法覆盖安装**，老用户必须卸载重装（课表与历史会丢），也无法上架应用商店。
- 只打包 `arm64-v8a`：32 位安卓机型、x86_64 模拟器无法安装。
- 界面文案仍然全部硬编码在 Kotlin 里（`res/values/strings.xml` 只有 `app_name`），暂时无法做多语言；`supportsRtl` 也就没有实际意义。
- 公交时刻为**公开资料整理 + 按间隔推算**，站点与班次请以站牌为准；上下行站点表已按“去程互逆”校正，真实存在的单向站点未能核实，故未展示。
- 快递查询使用快递100 的公开查询接口（无鉴权），请求频繁时可能被限流（表现为“网络异常”提示），请间隔几秒再试。

## 📄 License

本项目代码以 MIT 协议开源（第三方依赖遵循其各自许可证）。
