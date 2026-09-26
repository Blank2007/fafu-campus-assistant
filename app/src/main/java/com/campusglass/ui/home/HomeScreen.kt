package com.campusglass.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 开屏首页：整体居中布局（内容垂直+水平居中，顶部由 Scaffold 安全区托底，
 * 不再与状态栏重合），功能一键直达（也可走底部功能栏）。
 */
@Composable
fun HomeScreen(onGoto: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "校园助手",
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
        )
        Text(
            "FAFUer 专用",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            "取件码快捷跳转 · 手动课表 · 南平校区公交",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
        )

        Button(
            onClick = { onGoto("pickup") },
            modifier = Modifier.fillMaxWidth(0.72f),
        ) { Text("取件码 · 快捷跳转") }

        OutlinedButton(
            onClick = { onGoto("schedule") },
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .padding(top = 10.dp),
        ) { Text("课表 · 手动添加") }

        OutlinedButton(
            onClick = { onGoto("bus") },
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .padding(top = 10.dp),
        ) { Text("公交 · 南平校区") }

        OutlinedButton(
            onClick = { onGoto("about") },
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .padding(top = 10.dp),
        ) { Text("关于 · 开源致谢") }
    }
}
