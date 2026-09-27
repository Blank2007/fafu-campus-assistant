# 校园助手-FAFUer专用 🎒

> 一个人 + AI 协作完成的 Android 开源项目
> **全部代码由 AI 助手（Xiaomi Mimo v2.6-Pro / OpenClaw 编程助手）独立编写**，需求设计、提示词（Prompt）与测试反馈由 **Void_Blank** 提供。

**系统要求**：Android 15+（兼容 Android 12~17，优先适配小米澎湃 OS 4）· 安装包约 2MB · 原生 Material Design 3

## ✨ 功能

| 模块 | 说明 |
|---|---|
| 📦 取件码跳转 | ① 支付宝·菜鸟取件码一键直达（免装菜鸟App）② 拼多多个人中心入口|
| 🚚 快递单号查询 | 输入单号直接看物流轨迹（API 直出，目前仅支持部分快递公司）；可读取剪贴板快捷填单号；查询历史记录最多保留10条 |
| 📚 课表 | WakeUp 风格周课表格，手动添加课程（起止周/单双周/起止节次），节假日标记，日期显示（暂不完善） |
| 🚌 南平校区公交 | 103/105/107/K2 路站点与时刻表，目的地筛选（建发悦城/万达广场/动车站），点线路看详情（部分信息有误） |

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

- **代码编写**：AI 助手（Mimo v2.6 / OpenClaw 编程助手）—— 从架构设计到每一行代码全部由 AI 独立完成
- **需求与提示词**：[Void_Blank](https://github.com/Blank2007) —— 功能构想、界面意见、逐轮测试反馈
- 简单说：**人出想法，AI 写代码**

## 📮 联系 · 交流 · 反馈

- GitHub：[Blank2007](https://github.com/Blank2007)（Void_Blank）
- 邮箱：**1553008865@qq.com**

**非常欢迎交流与反馈！** 不管是发现 Bug、想要新功能、对实现好奇想聊聊，还是想一起折腾点别的好玩的，都欢迎来找。你提的每一条建议，都可能出现在下一个版本的更新日志里 🚀

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

**致谢声明**：直接依赖的开源库版权归其作者所有，随分发保留原始许可证与署名；标注"间接参考"的仅借鉴公开思路、未复制源码；公交数据引自官方公开通告（时刻以站牌为准）；快递查询由快递100提供接口，仅作个人查询展示用途。本项目免费开源，仅供学习交流。

## 📄 License

本项目代码以 MIT 协议开源（第三方依赖遵循其各自许可证）。
