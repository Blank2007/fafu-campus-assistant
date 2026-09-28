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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import com.campusglass.bus.BusScreen
import com.campusglass.pickup.PickupScreen
import com.campusglass.schedule.ScheduleScreen
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

/** 底部功能栏（微信式）+ 可自定义背景 */
@Composable
fun CampusGlassApp() {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }

    Scaffold(
        containerColor = Color.Transparent,
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
            AppBackground()
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

/** 软件背景（设置页可换） */
@Composable
private fun AppBackground() {
    when (ThemePrefs.bgMode.value) {
        ThemePrefs.BgMode.DEFAULT ->
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))

        ThemePrefs.BgMode.SKY ->
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFFD6E4FF), Color(0xFFF2F7FF)))
                )
            )

        ThemePrefs.BgMode.MINT ->
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFFD7F5E9), Color(0xFFF0FBF6)))
                )
            )

        ThemePrefs.BgMode.AURORA ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFFD6E4FF), Color(0xFFF3F0FF), Color(0xFFD9F4EC))))
                    .drawBehind {
                        val blobs = listOf(
                            Triple(0.12f, 0.10f, Color(0xFF5B7BFF).copy(alpha = 0.75f)),
                            Triple(0.92f, 0.18f, Color(0xFFC77DFF).copy(alpha = 0.65f)),
                            Triple(0.18f, 0.88f, Color(0xFF3ED8B0).copy(alpha = 0.60f)),
                            Triple(0.88f, 0.82f, Color(0xFFFFA45B).copy(alpha = 0.55f)),
                        )
                        blobs.forEach { (fx, fy, color) ->
                            val center = Offset(size.width * fx, size.height * fy)
                            val radius = size.width * 0.75f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(color, color.copy(alpha = 0f)),
                                    center = center, radius = radius,
                                ),
                                radius = radius, center = center,
                            )
                        }
                    }
            )

        ThemePrefs.BgMode.IMAGE -> {
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
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
        }
    }
}

/** Tab 便捷映射（首页快捷入口跳转用） */
private fun homeTabOf(name: String): Tab = when (name) {
    "pickup" -> Tab.PICKUP
    "schedule" -> Tab.SCHEDULE
    "bus" -> Tab.BUS
    else -> Tab.SETTINGS
}
