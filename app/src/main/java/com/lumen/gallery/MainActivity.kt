package com.lumen.gallery

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lumen.gallery.ui.AlbumsScreen
import com.lumen.gallery.ui.AnalysisScreen
import com.lumen.gallery.ui.BottomBar
import com.lumen.gallery.ui.BottomTab
import com.lumen.gallery.ui.CalendarScreen
import com.lumen.gallery.ui.EditorScreen
import com.lumen.gallery.ui.GalleryScreen
import com.lumen.gallery.ui.LumenViewModel
import com.lumen.gallery.ui.LockScreen
import com.lumen.gallery.ui.MapScreen
import com.lumen.gallery.ui.MemoriesScreen
import com.lumen.gallery.ui.PinStore
import com.lumen.gallery.ui.SettingsScreen
import com.lumen.gallery.ui.SlideshowLauncherRow
import com.lumen.gallery.ui.SlideshowScreen
import com.lumen.gallery.ui.StatsScreen
import com.lumen.gallery.ui.VideoPlayerScreen
import com.lumen.gallery.ui.theme.LumenTheme
import com.lumen.gallery.ui.theme.Bg0

class MainActivity : ComponentActivity() {

    private val vm: LumenViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.lumen.gallery.data.UnitsPrefs.init(this)
        com.lumen.gallery.ui.theme.applyAccent(
            com.lumen.gallery.ui.theme.currentAccentName(this),
        )
        // v37 · OLED 纯黑模式（设置开关，SharedPreferences 持久化）
        com.lumen.gallery.ui.theme.applyOledMode(
            getSharedPreferences("lumen_prefs", Context.MODE_PRIVATE).getBoolean("oled_mode", false),
        )
        // v27 · 应用打开即刷新桌面小部件（最新底片速览）
        com.lumen.gallery.widget.LumenWidgetProvider.pushAll(this)
        setContent {
            LumenTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Root(vm)
                }
            }
        }
        // v40 · 外部分享/查看图片 → 直接透视
        handleIncoming(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    /** v40 · SEND（分享）/ VIEW（打开图片）入口统一走外部透视通道 */
    private fun handleIncoming(intent: android.content.Intent?) {
        if (intent == null) return
        val uri = when (intent.action) {
            android.content.Intent.ACTION_SEND ->
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
            android.content.Intent.ACTION_VIEW -> intent.data
            else -> null
        } ?: return
        // 权限就绪前先记下，等 loadLibrary 完成后再透视
        pendingExternal = uri
    }

    /** 待处理的外部图片（权限授予后由 Root 消费） */
    var pendingExternal: android.net.Uri? = null
        private set

    /** Root 消费掉外部图片 */
    fun consumeExternal(): android.net.Uri? {
        val u = pendingExternal
        pendingExternal = null
        return u
    }
}

@Composable
private fun Root(vm: LumenViewModel) {
    val activity = androidx.compose.ui.platform.LocalContext.current as? MainActivity
        ?: return
    val state by vm.state.collectAsState()

    val permissions = buildList {
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        // 无此权限时系统会在 FUSE 层抹除 GPS 元数据（隐私脱敏），透视仪需要真实数据
        if (Build.VERSION.SDK_INT >= 29) {
            add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
    }.toTypedArray()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) vm.loadLibrary()
    }

    LaunchedEffect(Unit) {
        if (state.permissionGranted) vm.loadLibrary() else launcher.launch(permissions)
    }
    // v40 · 权限就绪后消费外部传入的图片（分享/查看直达透视）
    LaunchedEffect(state.permissionGranted) {
        if (state.permissionGranted) {
            activity.consumeExternal()?.let { vm.openExternal(it) }
        }
    }

    val current = vm.current
    // v45 · 默认启动页（设置页可配：0 相册 / 1 相册集 / 2 回忆）
    val defaultTab = remember {
        activity.getSharedPreferences("lumen_prefs", Context.MODE_PRIVATE).getInt("default_tab", 0)
    }
    var tab by remember { mutableStateOf(defaultTab.coerceIn(0, 6)) }
    var bucketFilter by remember { mutableStateOf<String?>(null) }
    var slideshowRunning by remember { mutableStateOf(false) }

    // v22 · 应用锁
    var locked by remember { mutableStateOf(PinStore.hasPin(activity)) }
    if (locked) {
        LockScreen(
            context = activity,
            firstTime = false,
            onUnlocked = { locked = false },
        )
        return
    }

    if (slideshowRunning) {
        SlideshowScreen(items = state.items, startIndex = 0) { slideshowRunning = false }
        return
    }

    if (current != null) {
        AnalysisScreen(vm, current)
        return
    }

    val tabs = listOf(
        BottomTab(0, "🖼", "相册"),
        BottomTab(1, "📚", "相册集"),
        BottomTab(2, "🕰", "回忆"),
        BottomTab(3, "🗺", "地图"),
        BottomTab(4, "📅", "日历"),
        BottomTab(5, "📊", "统计"),
        BottomTab(6, "⚙", "设置"),
    )
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f)) {
            when (tab) {
                0 -> GalleryScreen(vm, bucketFilter = bucketFilter, onClearBucket = { bucketFilter = null })
                1 -> AlbumsScreen(items = state.items) { folder ->
                    bucketFilter = folder
                    tab = 0
                }
                2 -> MemoriesScreen(vm)
                3 -> MapScreen(vm)
                4 -> CalendarScreen(vm)
                5 -> StatsScreen(vm)
                6 -> SettingsScreen(vm)
            }
        }
        // 幻灯片入口（相册 tab 底部）
        if (tab == 0 && state.items.size > 1) {
            SlideshowLauncherRow(items = state.items) { slideshowRunning = true }
        }
        BottomBar(current = tab, onSelect = { tab = it }, tabs = tabs)
    }
}
