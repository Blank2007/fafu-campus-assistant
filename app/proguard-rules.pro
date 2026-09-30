# v3.3：清理历史残留规则。
# parked/schedule 里的 WebView 教务抓取源码并未编入 APK（已核对 dex 内无 WebView 调用），
# 因此不需要为不存在的 com.campusglass.schedule.ZhengfangBridge 保留规则，
# 也不需要 JavascriptInterface 规则。若将来重新启用 parked/schedule，请一并恢复这两条：
#
# -keepclassmembers class * {
#     @android.webkit.JavascriptInterface <methods>;
# }
# -keep class com.campusglass.schedule.ZhengfangBridge { *; }
