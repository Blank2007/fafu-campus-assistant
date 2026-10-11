# D-2（审查 v4.10）：WebView JS 桥 keep 规则已移除（包内无 WebView 代码，死规则）。
# 若未来在 parked/schedule 启用教务抓取，请恢复：
# -keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
# 说明：旧 ZhengfangBridge keep 规则已随死代码清理移除（类不存在）
