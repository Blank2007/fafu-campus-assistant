package com.campusglass

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 更新安装完成后自动删除安装包（ACTION_MY_PACKAGE_REPLACED）。
 * 「直接下载安装」的 APK 放在 external-files/updates/，装完即清。
 */
class UpdateCleanupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            runCatching {
                val dir = java.io.File(context.getExternalFilesDir(null), "updates")
                dir.listFiles()?.forEach { it.delete() }
            }
        }
    }
}
