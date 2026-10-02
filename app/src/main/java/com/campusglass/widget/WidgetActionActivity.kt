package com.campusglass.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.campusglass.home.Hitokoto

/**
 * 小部件点击中转（透明 Activity）。
 * 部分系统会丢弃发给小部件接收器的自定义广播，改走 Activity 投递 100% 可靠。
 * op = next（今/明切换）| quote（换一句）| open（进课表页）
 */
class WidgetActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val op = intent.getStringExtra("op") ?: "open"
        val wid = intent.getIntExtra("wid", -1)

        when (op) {
            "next" -> {
                if (wid >= 0) {
                    val prefs = getSharedPreferences("widget", MODE_PRIVATE)
                    prefs.edit()
                        .putInt("off_$wid", if (prefs.getInt("off_$wid", 0) == 0) 1 else 0)
                        .apply()
                }
                TodayWidgetProvider.pushUpdate(this)
                finish()
            }

            "quote" -> {
                Thread {
                    runCatching {
                        Hitokoto.fetchFresh(this)
                        TodayWidgetProvider.pushUpdate(this)
                    }
                    runOnUiThread { finish() }
                }.start()
            }

            else -> {
                runCatching {
                    startActivity(
                        Intent(this, com.campusglass.MainActivity::class.java)
                            .putExtra("openTab", "schedule")
                            .addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                            )
                    )
                }
                finish()
            }
        }
    }
}
