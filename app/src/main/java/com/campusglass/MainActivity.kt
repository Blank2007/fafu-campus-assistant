package com.campusglass
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp
import com.campusglass.ui.glass.hairlineBorder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.LocalShipping
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.border
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
        // 消除底部黑块：系统导航栏透明 + 关闭系统强制对比度遮罩（黑色横条的真凶）
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
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
    SCHEDULE("课表", Icons.Filled.School),
    PICKUP("快递", Icons.Filled.LocalShipping),
    BUS("公交", Icons.Filled.DirectionsBus),
    SETTINGS("设置", Icons.Filled.Settings),
}

/**
 * 悬浮亚克力底栏（下拉滚动时自动隐藏）+ 全屏背景（异形屏铺满、按屏幕尺寸裁切）。
 */
@Composable
fun CampusGlassApp() {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val glass = rememberGlassState()
    val dark = when (ThemePrefs.themeMode.value) {
        ThemePrefs.ThemeMode.DARK -> true
        ThemePrefs.ThemeMode.LIGHT -> false
        ThemePrefs.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val acrylicOn = ThemePrefs.acrylicEnabled.value

    // 底栏自动隐藏：内容下滑隐藏，上滑出现
    var barVisible by remember { mutableStateOf(true) }
    val scrollConn = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -12f) barVisible = false
                else if (available.y > 12f) barVisible = true
                return Offset.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AppBackground(glass)

        CompositionLocalProvider(
            LocalGlass provides glass,
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    AnimatedVisibility(
                        visible = barVisible,
                        enter = slideInVertically(tween(220)) { it },
                        exit = slideOutVertically(tween(180)) { it },
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                                .padding(start = 18.dp, end = 18.dp, bottom = 10.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            val barModifier = if (acrylicOn) {
                                Modifier
                                    .fillMaxWidth()
                                    .acrylic(glass, dark, shape = RoundedCornerShape(26.dp))
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                            }
                            val hairline = MaterialTheme.colorScheme.outline.copy(
                                alpha = if (dark) 0.35f else 0.22f
                            )
                            Row(
                                modifier = barModifier.hairlineBorder(hairline),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Tab.entries.forEach { t ->
                                    val sel = tab == t
                                    val tint = if (sel) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(18.dp))
                                            .clickable { tab = t }
                                            .padding(vertical = 9.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                                    else Color.Transparent
                                                )
                                                .padding(horizontal = 14.dp, vertical = 5.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(t.icon, contentDescription = t.label, tint = tint)
                                                Text(
                                                    t.label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.sp,
                                                    color = tint,
                                                )
                                            }
                                        }
                                    }
                                }
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
                        .nestedScroll(scrollConn)
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

/** 软件背景：自定义图片（按屏幕尺寸裁切）或默认纯色 */
@Composable
private fun AppBackground(glass: dev.chrisbanes.haze.HazeState) {
    val context = LocalContext.current
    val path = ThemePrefs.bgImagePath.value
    val nonce = ThemePrefs.bgNonce.value
    val crop = ThemePrefs.bgCropMode.value
    val screen = remember { screenSizePx(context) }

    val bitmap = remember(path, nonce, crop, screen) {
        if (path.isNotBlank() && File(path).exists()) {
            runCatching {
                val src = BitmapFactory.decodeFile(path) ?: return@runCatching null
                cropToScreen(src, screen.first, screen.second, crop)
            }.getOrNull()
        } else null
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

/** 读取设备屏幕像素尺寸 */
private fun screenSizePx(context: Context): Pair<Int, Int> {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val b = wm.currentWindowMetrics.bounds
    return b.width() to b.height()
}

/** 按屏幕比例裁切（居中/顶部/底部/适应宽度） */
private fun cropToScreen(src: Bitmap, sw: Int, sh: Int, mode: ThemePrefs.BgCrop): Bitmap {
    if (sw <= 0 || sh <= 0) return src
    val target = sw.toFloat() / sh
    val srcRatio = src.width.toFloat() / src.height
    var w = src.width
    var h = src.height
    var x = 0
    var y = 0
    if (mode == ThemePrefs.BgCrop.FIT_WIDTH) {
        // 适应宽度铺满，多出的高度从顶部裁掉
        h = (src.width / target).toInt().coerceAtMost(src.height)
        w = src.width
        y = 0
    } else if (srcRatio > target) {
        w = (src.height * target).toInt()
        x = when (mode) {
            ThemePrefs.BgCrop.TOP -> 0
            ThemePrefs.BgCrop.BOTTOM -> src.width - w
            else -> (src.width - w) / 2
        }
    } else {
        h = (src.width / target).toInt()
        y = when (mode) {
            ThemePrefs.BgCrop.TOP -> 0
            ThemePrefs.BgCrop.BOTTOM -> src.height - h
            else -> (src.height - h) / 2
        }
    }
    return runCatching { Bitmap.createBitmap(src, x, y, w.coerceAtLeast(1), h.coerceAtLeast(1)) }
        .getOrDefault(src)
}

private fun homeTabOf(name: String): Tab = when (name) {
    "schedule" -> Tab.SCHEDULE
    "pickup" -> Tab.PICKUP
    "bus" -> Tab.BUS
    else -> Tab.SETTINGS
}
