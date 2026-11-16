package com.campusglass
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

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
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.campusglass.ui.motion.LocalMotion
import com.campusglass.ui.motion.MotionPrefs
import com.campusglass.ui.motion.motionSpec
import com.campusglass.ui.motion.rememberMotionSpec
import com.campusglass.ui.motion.pressable
import com.campusglass.ui.settings.SettingsScreen
import com.campusglass.ui.theme.CampusGlassTheme
import com.campusglass.ui.theme.ThemePrefs
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        // 消除底部黑块：系统栏透明交给 enableEdgeToEdge；关闭系统强制对比度遮罩（V4-23）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            window.isStatusBarContrastEnforced = false
            @Suppress("DEPRECATION")
            window.isNavigationBarContrastEnforced = false
        }
        // V4-22：App 内强制深/浅色时，启动主题同步（防冷启动闪相反底色）
        runCatching {
            val forcedDark = ThemePrefs.themeMode.value == ThemePrefs.ThemeMode.DARK
            val forcedLight = ThemePrefs.themeMode.value == ThemePrefs.ThemeMode.LIGHT
            val sysDark = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            val darkNow = forcedDark || (sysDark && !forcedLight)
            setTheme(if (darkNow) R.style.Theme_CampusGlass_Dark else R.style.Theme_CampusGlass)
        }
        super.onCreate(savedInstanceState)
        WidgetNav.pendingTab.value = intent?.getStringExtra("openTab")   // 小部件带参直达
        intent?.removeExtra("openTab")                                  // A1：消费后移除，旋转不再弹回
        ThemePrefs.load(this)
        MotionPrefs.load(this)                             // v5.5：动效偏好（设置页可调）
        com.campusglass.update.UpdatePolicy.load(this)     // v4.18：启动自动检查更新的用户偏好
        setContent {
            CampusGlassTheme {
                // B-4：首次启动强制同意隐私政策（未同意不可使用）
                val activity = this
                var privacyAgreed by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(
                        com.campusglass.ui.settings.PrivacyPrefs.agreed(activity)
                    )
                }
                if (!privacyAgreed) {
                    com.campusglass.ui.settings.PrivacyConsentDialog(
                        onAgree = {
                            com.campusglass.ui.settings.PrivacyPrefs.setAgreed(activity)
                            privacyAgreed = true
                        },
                        onExit = { finish() },
                    )
                }
                // v5.5：把用户调好的动效令牌注入整棵树（含 AutoUpdateHost 的更新横幅）
                CompositionLocalProvider(LocalMotion provides rememberMotionSpec()) {
                    CampusGlassApp()
                    // v4.18：启动时按用户偏好自动检查更新（OFF 时不联网）
                    com.campusglass.update.AutoUpdateHost()
                }
            }
        }
    }
}

/** 小部件 → App 页面跳转信号 */
object WidgetNav {
    val pendingTab = androidx.compose.runtime.mutableStateOf<String?>(null)
}

/**
 * 逐层解包 ContextWrapper 找到宿主 Activity。
 * Compose 的 LocalContext 通常就是 Activity，但在被包装的场景下（部分 ROM / 宿主容器）
 * 直接强转会失败，导致系统栏图标深浅设置静默失效（V4-23 的原始意图）。
 */
private fun findActivity(context: android.content.Context): android.app.Activity? {
    var ctx: android.content.Context? = context
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
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
    // 动效令牌按用户偏好现取（设置页一改，这里立刻重组）
    val m = motionSpec()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    // 选中位置用一个连续浮点插值（0..4）：底栏指示块在 Tab 之间"滑过去"，
    // 而不是瞬间换色 —— 手指点哪，高亮就从上一个位置跑过去，方向感最直观。
    val selPos by animateFloatAsState(
        targetValue = tab.ordinal.toFloat(),
        animationSpec = m.fluid,
        label = "tabSelPos",
    )
    // 小部件点击直达课表页
    androidx.compose.runtime.LaunchedEffect(WidgetNav.pendingTab.value) {
        WidgetNav.pendingTab.value?.let { k ->
            tab = homeTabOf(k)
            WidgetNav.pendingTab.value = null
        }
    }
    val glass = rememberGlassState()
    // ---- 液态玻璃采样源（v5.6）----
    // drawBackdrop 需要「被采样的背景」是一个真实的 GraphicsLayer：
    //   bgBackdrop      ← 背景（纯色 / 壁纸），全屏
    //   contentBackdrop ← 页面内容（卡片、列表）
    // 两者合成后喂给底栏。底栏自己绝不能落在记录层里，否则会采到上一帧的自己（无限镜 / 拖影），
    // 所以这里只记录「背景 + 内容区」，与底栏（Scaffold 内 bottomBar）同级但互不包含。
    val bgBackdrop = rememberLayerBackdrop()
    val contentBackdrop = rememberLayerBackdrop()
    val barBackdrop = rememberCombinedBackdrop(bgBackdrop, contentBackdrop)
    val dark = when (ThemePrefs.themeMode.value) {
        ThemePrefs.ThemeMode.DARK -> true
        ThemePrefs.ThemeMode.LIGHT -> false
        ThemePrefs.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val acrylicOn = ThemePrefs.acrylicEnabled.value

    // 底栏自动隐藏：内容下滑隐藏，上滑出现
    var barVisible by rememberSaveable { mutableStateOf(true) }   // A2
    // 切 Tab 时内容自动回到顶部，底栏必须跟着重新出现，否则新页面根本没有导航入口
    androidx.compose.runtime.LaunchedEffect(tab) { barVisible = true }
    // 滞后判定：下滑要累计超过 28dp 才隐藏（列表轻微回弹不再让底栏"抖"），上滑则立刻出现
    val hideThresholdPx = with(LocalDensity.current) { 28.dp.toPx() }
    // 用户在设置里关掉「底栏自动隐藏」时，底栏必须常驻（否则界面又没有导航入口了）
    val autoHide = m.barAutoHide
    androidx.compose.runtime.LaunchedEffect(autoHide) { if (!autoHide) barVisible = true }
    val scrollConn = remember(hideThresholdPx, autoHide) {
        object : NestedScrollConnection {
            /** 本次下滑的累计位移（向上滑立即清零） */
            var downAcc = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!autoHide) return Offset.Zero
                val dy = available.y
                if (dy > 0f) {
                    // 上滑：出现要快，不留滞后
                    downAcc = 0f
                    if (!barVisible) barVisible = true
                } else if (dy < 0f && barVisible) {
                    downAcc -= dy
                    if (downAcc > hideThresholdPx) {
                        barVisible = false
                        downAcc = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(bgBackdrop)) {
            AppBackground(glass)
        }

        CompositionLocalProvider(
            LocalGlass provides glass,
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        ) {
            // UI-1：系统栏图标深浅跟随 App 主题（而非系统设置）
            val winCtx = androidx.compose.ui.platform.LocalContext.current
            // V4-23：ContextWrapper 逐层解包找 Activity（不再静默失效）
            androidx.compose.runtime.SideEffect {
                val act = findActivity(winCtx) ?: return@SideEffect
                val controller = androidx.core.view.WindowInsetsControllerCompat(act.window, act.window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    AnimatedVisibility(
                        visible = barVisible,
                        enter = m.barEnter,
                        exit = m.barExit,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                                .padding(start = 18.dp, end = 18.dp, bottom = 10.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            // v5.6：液态玻璃底栏（Kyant0/AndroidLiquidGlass，酷安同款）
                            // 面板不自己画渐变/描边：模糊、边缘折射、色散、内外阴影全部由库的
                            // drawBackdrop 完成 —— 它真的去采样背后的图层，而不是"假装透明"。
                            val barShape = RoundedCornerShape(26.dp)
                            // 面板底色：真实模糊 + 折射已经给了玻璃感，这层只留很淡的主题色，
                            // 用于保住图标/文字的对比度；压太重会把背后的内容抹平。
                            // 深色主题下页面背景本身就是深棕黑，若再压一层深色 tint，面板与背景几乎同色，
                            // 玻璃感全靠不存在——所以深色用"提亮"策略（一层淡白），浅色用主题色淡染。
                            val glassTint =
                                if (dark) Color.White.copy(alpha = 0.14f)
                                else androidx.compose.ui.graphics.lerp(
                                    Color.White,
                                    MaterialTheme.colorScheme.primary,
                                    0.05f,
                                ).copy(alpha = 0.50f)
                            val reduce = MotionPrefs.reduceMotion.value
                            var pressing by remember { mutableStateOf(false) }
                            // 液态形变：按下时整条栏极轻微鼓起，强度跟随「按压反馈」设置
                            val pressScale by animateFloatAsState(
                                targetValue = if (pressing) {
                                    1f + 0.012f * MotionPrefs.press.value.strength
                                } else 1f,
                                animationSpec = m.fluid,
                                label = "lgScale",
                            )
                            // 只「观察」按下状态，绝不消费事件 —— 否则底栏 Tab 就点不动了
                            val pressObserver = Modifier.pointerInput(reduce) {
                                if (reduce) return@pointerInput
                                awaitPointerEventScope {
                                    while (true) {
                                        val e = awaitPointerEvent(
                                            androidx.compose.ui.input.pointer.PointerEventPass.Initial
                                        )
                                        if (e.changes.any { it.pressed }) {
                                            pressing = true
                                        } else if (e.changes.isNotEmpty()) {
                                            pressing = false
                                        }
                                    }
                                }
                            }
                            // 设置里关掉液态玻璃时退回纯色面板（默认开）
                            val liquidOn = ThemePrefs.liquidGlassEnabled.value
                            val barSurface: Modifier = if (liquidOn) {
                                Modifier.drawBackdrop(
                                    backdrop = barBackdrop,
                                    shape = { barShape },
                                    effects = {
                                        vibrancy()                            // 提亮背景色彩，玻璃才"透"
                                        blur(6.dp.toPx())                     // 背后真实模糊
                                        lens(22.dp.toPx(), 26.dp.toPx())      // 边缘折射（液态玻璃的灵魂）
                                    },
                                    highlight = {
                                        Highlight.Default.copy(alpha = if (dark) 0.9f else 1f)
                                    },
                                    shadow = { Shadow(alpha = if (dark) 0.6f else 0.45f) },
                                    innerShadow = {
                                        InnerShadow(radius = 6.dp, alpha = if (dark) 0.45f else 0.25f)
                                    },
                                    onDrawSurface = { drawRect(glassTint) },
                                )
                            } else {
                                Modifier
                                    .clip(barShape)
                                    .background(MaterialTheme.colorScheme.surface)
                            }
                            BoxWithConstraints(
                                Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = pressScale
                                        scaleY = pressScale
                                    }
                            ) {
                                val tabW = maxWidth / Tab.entries.size
                                // 把整条底栏（面板 + 图标文字）录成一层，胶囊用这层做 backdrop —— 否则胶囊的 backdrop
                                // 只有页面背景，会把压在上面的图标整块不透明地盖掉（实测首页 tab 直接消失）。
                                val barLayer = rememberLayerBackdrop()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(barSurface)
                                        .layerBackdrop(barLayer)
                                        .then(pressObserver),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                Tab.entries.forEach { t ->
                                    // 高亮强度 = 1 - 距离（选中项 1，相邻项随插值连续衰减）
                                    val sel = (1f - kotlin.math.abs(t.ordinal - selPos)).coerceIn(0f, 1f)
                                    val tint = androidx.compose.ui.graphics.lerp(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                                        MaterialTheme.colorScheme.primary,
                                        sel,
                                    )
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(18.dp))
                                            .pressable(pressedScale = 0.93f) { tab = t }
                                            .padding(vertical = 9.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Box(
                                            Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    t.icon,
                                                    contentDescription = t.label,
                                                    tint = tint,
                                                    modifier = Modifier.graphicsLayer {
                                                        // 选中图标轻轻放大一点点（1.0 → 1.07）
                                                        val s = 1f + 0.07f * sel
                                                        scaleX = s
                                                        scaleY = s
                                                    },
                                                )
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
                                // 滑动玻璃胶囊（酷安同款）：选中项不是一块死色块，而是一片真的会折射背后内容的凸透镜，
                                // 位置随 selPos 连续滑动；半透明，所以图标/文字照样透得出来。
                                // 选中胶囊用主题色淡染，玻璃底 + 一点品牌色（酷安的配色思路）。
                                val capsuleTint =
                                    MaterialTheme.colorScheme.primary.copy(
                                        alpha = if (dark) 0.20f else 0.18f
                                    )
                                if (liquidOn) {
                                    Box(
                                        Modifier
                                            .align(Alignment.CenterStart)
                                            .offset(x = tabW * selPos + 6.dp)
                                            .width(tabW - 12.dp)
                                            .height(52.dp)
                                            .drawBackdrop(
                                                backdrop = barLayer,
                                                shape = { RoundedCornerShape(20.dp) },
                                                effects = {
                                                    lens(
                                                        12.dp.toPx(),
                                                        16.dp.toPx(),
                                                        chromaticAberration = true,
                                                    )
                                                },
                                                highlight = {
                                                    Highlight.Default.copy(alpha = if (dark) 0.8f else 1f)
                                                },
                                                shadow = { Shadow(alpha = if (dark) 0.5f else 0.35f) },
                                                innerShadow = {
                                                    InnerShadow(radius = 4.dp, alpha = if (dark) 0.55f else 0.30f)
                                                },
                                                onDrawSurface = {
                                                    // 选中胶囊用主题色淡染，酷安那种「玻璃底 + 一点品牌色」
                                                    drawRect(capsuleTint)
                                                },
                                            )
                                    )
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
                        .layerBackdrop(contentBackdrop)
                ) {
                    AnimatedContent(
                        targetState = tab,
                        // fade-through：旧页 110ms 先退，新页淡入 + 轻微放大归位。
                        // 不再横向滑 1/4 屏 —— 底栏 Tab 是"换内容"不是"翻页"，横向位移会让人误以为能左右滑。
                        transitionSpec = { m.tabEnter togetherWith m.tabExit },
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

    // A3：异步 + 降采样 + EXIF 方向（不再主线程整图解码）
    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(
        initialValue = null, path, nonce, crop, screen,
    ) {
        value = null
        if (path.isNotBlank() && File(path).exists()) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val decoded = decodeBgBitmap(path, screen.first, screen.second)
                decoded?.let { runCatching { cropToScreen(it, screen.first, screen.second, crop) }.getOrNull() }
            }
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
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
        ) {
            // 液态玻璃底栏需要「背后有东西」才能显出玻璃感。无壁纸时整屏是一块纯色，
            // 玻璃条模糊/折射的都是一模一样的像素 —— 结果就是与背景彻底融为一体，
            // 只剩一圈描边。这里在屏幕下缘铺一层极淡的主题色柔光，
            // 给玻璃提供可折射的层次，同时让悬浮底栏与背景自然拉开一档。
            if (ThemePrefs.liquidGlassEnabled.value) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.52f to Color.Transparent,
                                0.84f to MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                1f to MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                            )
                        )
                )
            }
        }
    }
}

/** 读取设备屏幕像素尺寸 */
private fun screenSizePx(context: Context): Pair<Int, Int> {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val b = wm.currentWindowMetrics.bounds
    return b.width() to b.height()
}

/** 按屏幕比例裁切（居中/顶部/底部/适应宽度） */
/** A3：降采样 + EXIF 方向的背景解码（IO 线程调用） */
private fun decodeBgBitmap(path: String, reqW: Int, reqH: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    // V4-17：按总像素上限采样（大图短边也降），避免一次性 48MB 分配
    var sample = 1
    while (bounds.outWidth * bounds.outHeight / (sample * sample) > 4_000_000) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = BitmapFactory.decodeFile(path, opts) ?: return null
    val rot = runCatching {
        val exif = androidx.exifinterface.media.ExifInterface(path)
        when (exif.getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
        )) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
    if (rot == 0f) bmp else {
        val m = android.graphics.Matrix().apply { postRotate(rot) }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (rotated !== bmp) bmp.recycle()          // V4-17：回收原图
        rotated
    }
}.getOrNull()

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
