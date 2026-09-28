package com.campusglass

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.campusglass.bus.BusScreen
import com.campusglass.pickup.PickupScreen
import com.campusglass.schedule.ScheduleScreen
import com.campusglass.ui.glass.LocalGlass
import com.campusglass.ui.glass.acrylic
import com.campusglass.ui.glass.glassBackground
import com.campusglass.ui.glass.rememberGlassState
import com.campusglass.ui.home.HomeScreen
import com.campusglass.ui.settings.SettingsScreen
import com.campusglass.ui.theme.CampusGlassTheme
import com.campusglass.ui.theme.ThemePrefs
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ThemePrefs.load(this)
        setContent {
            CampusGlassTheme {
                CampusGlassApp()
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("首页", Icons.Filled.Home),
    PICKUP("取件码", Icons.Filled.QrCodeScanner),
    SCHEDULE("课表", Icons.Filled.School),
    BUS("公交", Icons.Filled.DirectionsBus),
    SETTINGS("设置", Icons.Filled.Settings),
}

/**
 * 底部悬浮亚克力功能栏 + 全屏背景（异形屏/挖孔屏铺满：背景画在 Scaffold 之下，
 * 状态栏、挖孔、手势条区域全部覆盖）。
 */
@Composable
fun CampusGlassApp() {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val glass = rememberGlassState()
    val dark = ThemePrefs.darkMode.value

    Box(Modifier.fillMaxSize()) {
        // 背景铺满整个窗口（含异形屏安全区之外）
        AppBackground(glass)

        CompositionLocalProvider(LocalGlass provides glass) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            bottomBar = {
                // 悬浮亚克力底栏
                Box(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                        .padding(start = 18.dp, end = 18.dp, bottom = 10.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    NavigationBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .acrylic(glass, dark, shape = RoundedCornerShape(26.dp)),
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                    ) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon, contentDescription = t.label) },
                                label = { Text(t.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                    .padding(padding)
            ) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val forward = targetState.ordinal >= initialState.ordinal
                        val dir = if (forward) 1 else -1
                        (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 4 * dir }) togetherWith
                            (fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { -it / 4 * dir })
                    },
                    label = "tab",
                ) { t ->
                    when (t) {
                        Tab.HOME -> HomeScreen(onGoto = { tab = homeTabOf(it) })
                        Tab.PICKUP -> PickupScreen()
                        Tab.SCHEDULE -> ScheduleScreen()
                        Tab.BUS -> BusScreen()
                        Tab.SETTINGS -> SettingsScreen()
                    }
                }
            }
        }
        }
    }
}

/** 软件背景：自定义图片（设置页可设）或默认纯色 */
@Composable
private fun AppBackground(glass: dev.chrisbanes.haze.HazeState) {
    val path = ThemePrefs.bgImagePath.value
    val bitmap = remember(path) {
        if (path.isNotBlank() && File(path).exists())
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        else null
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .glassBackground(glass),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .glassBackground(glass)
        )
    }
}

/** Tab 便捷映射（首页快捷入口跳转用） */
private fun homeTabOf(name: String): Tab = when (name) {
    "pickup" -> Tab.PICKUP
    "schedule" -> Tab.SCHEDULE
    "bus" -> Tab.BUS
    else -> Tab.SETTINGS
}
