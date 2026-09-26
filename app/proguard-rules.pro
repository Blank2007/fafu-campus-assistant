# 保留 WebView JS 桥（教务课表抓取）
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.campusglass.schedule.ZhengfangBridge { *; }
