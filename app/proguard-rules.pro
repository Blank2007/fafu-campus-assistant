# 保留 WebView JS 桥（教务课表抓取；当前未启用，预留）
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
# 说明：旧 ZhengfangBridge keep 规则已随死代码清理移除（类不存在）
