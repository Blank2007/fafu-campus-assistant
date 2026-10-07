package com.campusglass

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 更新安装完成后：删除安装包（U6）+ 立即刷新桌面小部件（W-4）。
 */
class UpdateCleanupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            runCatching {
                val dir = java.io.File(context.getExternalFilesDir(null), "updates")
                dir.listFiles()?.forEach { it.delete() }
            }
            runCatching {
                context.getSharedPreferences("update", Context.MODE_PRIVATE)
                    .edit().remove("ready_path").apply()
            }
            runCatching { com.campusglass.widget.TodayWidgetProvider.pushUpdate(context) }   // W-4
        }
    }
}
