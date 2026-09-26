package com.campusglass

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.campusglass.about.AboutScreen
import com.campusglass.bus.BusScreen
import com.campusglass.pickup.PickupScreen
import com.campusglass.schedule.ScheduleScreen
import com.campusglass.ui.home.HomeScreen
import com.campusglass.ui.theme.CampusGlassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
    ABOUT("关于", Icons.Filled.Info),
}

/**
 * 底部功能栏（微信式）：所有功能直接摆底栏。
 * Scaffold 自带安全区（状态栏/手势条）处理，页面内容不再与状态栏重合。
 */
@Composable
fun CampusGlassApp() {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab 切换过渡动画：按切换方向滑动 + 淡入淡出
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
                    Tab.ABOUT -> AboutScreen()
                }
            }
        }
    }
}

/** Tab 便捷映射（首页快捷入口跳转用） */
private fun homeTabOf(name: String): Tab = when (name) {
    "pickup" -> Tab.PICKUP
    "schedule" -> Tab.SCHEDULE
    "bus" -> Tab.BUS
    else -> Tab.ABOUT
}
