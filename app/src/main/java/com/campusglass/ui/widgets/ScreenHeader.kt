package com.campusglass.ui.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.campusglass.ui.glass.LocalGlass
import com.campusglass.ui.glass.acrylic
import com.campusglass.ui.glass.hasCustomBackground
import com.campusglass.ui.glass.isAppInDarkTheme
import com.campusglass.ui.theme.ThemePrefs

/**
 * 页面标题（Tab 根页面用，无返回键；顶部安全区由 Scaffold 处理）。
 *
 * v3.3：启用自定义背景图时给标题加一层亚克力背板。
 * 旧版标题是裸 Text，直接压在用户照片上 —— 深色照片配浅色主题（或反之）会看不清。
 */
@Composable
fun ScreenHeader(title: String) {
    val glass = LocalGlass.current
    val useGlass = glass != null && hasCustomBackground() && ThemePrefs.acrylicEnabled.value
    if (useGlass && glass != null) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp)
                .acrylic(
                    glass,
                    isAppInDarkTheme(),
                    blurRadius = 18.dp,
                    shape = RoundedCornerShape(16.dp),
                    tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                ),
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                HeaderTitle(title)
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
        ) {
            HeaderTitle(title)
        }
    }
}

@Composable
private fun HeaderTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
