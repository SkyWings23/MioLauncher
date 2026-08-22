package com.miolauncher.app

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.miolauncher.app.controls.FclSettingsPanel
import com.miolauncher.app.data.MioRepository
import com.miolauncher.backend.GameLaunch
import com.miolauncher.backend.NativeInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.MainActivity
import net.kdt.pojavlaunch.MinecraftGLSurface
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.customcontrols.ControlLayout
import net.kdt.pojavlaunch.customcontrols.mouse.Touchpad
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import org.lwjgl.glfw.CallbackBridge

/**
 * 游戏宿主 Activity：ControlLayout 作父容器，内含 MinecraftGLSurface（游戏画面+触摸/鼠标）
 * + FCL 控件（ControlButton/ControlDrawer/ControlJoystick）+ Touchpad（虚拟鼠标板）。
 */
class GameActivity : ComponentActivity() {

    @Volatile
    private var launched = false
    private lateinit var controlLayout: net.kdt.pojavlaunch.customcontrols.ControlLayout
    private val launchSettings by lazy { com.miolauncher.app.data.LaunchSettingsStore.load(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val versionId = intent.getStringExtra("version_id")
        android.util.Log.d("GameActivity", "onCreate versionId=$versionId")
        if (versionId == null) {
            finish()
            return
        }
        // 尽早写启动标记：即使启动早期崩溃（JRE 解压/JNI 加载等），下次启动也能检测到
        com.miolauncher.app.data.CrashLogManager.markGameStart(this, versionId)
        // 调试：THREADDUMP 广播 → SIGQUIT（Android 13+ 必须指定 RECEIVER_EXPORTED/NOT_EXPORTED）
        val threadDumpReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                android.os.Process.sendSignal(android.os.Process.myPid(), 3)
            }
        }
        val threadDumpFilter = android.content.IntentFilter("com.miolauncher.app.THREADDUMP")
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(threadDumpReceiver, threadDumpFilter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(threadDumpReceiver, threadDumpFilter)
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )

        // 必须在 ControlData 静态初始化前设置好尺寸与偏好
        Tools.updateWindowSize(this)
        LauncherPreferences.loadPreferences(this)
        MainActivity.init(this)
        // FCL 布局表达式的 screen_width/height 取物理显示尺寸
        CallbackBridge.physicalWidth = Tools.currentDisplayMetrics.widthPixels
        CallbackBridge.physicalHeight = Tools.currentDisplayMetrics.heightPixels
        android.util.Log.d("GameActivity", "physical=${CallbackBridge.physicalWidth}x${CallbackBridge.physicalHeight}")

        redirectStderr()

        controlLayout = ControlLayout(this)

        val touchpad = Touchpad(this)
        // 虚拟鼠标设置：关闭时不显示触控板
        touchpad.visibility = if (launchSettings.virtualMouseEnabled) View.VISIBLE else View.GONE
        MainActivity.touchpad = touchpad
        CallbackBridge.addGrabListener(touchpad)

        val gl = MinecraftGLSurface(this)
        gl.setSurfaceReadyListener { launchGame(versionId) }
        CallbackBridge.addGrabListener(gl)

        controlLayout.addView(gl, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // 加载控制布局：优先恢复上次使用的按钮组，否则用 FCL 默认（default.json）
        val defaultPath = extractDefaultLayout()
        val controlMapDir = java.io.File(filesDir, "mio/controlmap")
        val lastGroup = controlLayout.getLastUsedGroupName()
        val savedLayout = java.io.File(controlMapDir, "$lastGroup.json")
        val layoutPath = if (savedLayout.exists()) savedLayout.absolutePath else defaultPath
        try {
            controlLayout.loadLayout(layoutPath)
            android.util.Log.d("GameActivity", "控制布局加载: $layoutPath")
        } catch (e: Throwable) {
            android.util.Log.e("GameActivity", "加载控制布局失败", e)
            try {
                controlLayout.loadLayout(defaultPath)
            } catch (e2: Throwable) {
                android.util.Log.e("GameActivity", "回退默认布局失败", e2)
            }
        }

        controlLayout.addView(touchpad, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // 物品栏（点击切换物品）——FCL HotbarView，对齐游戏底部热键栏
        val hotbar = net.kdt.pojavlaunch.customcontrols.mouse.HotbarView(this)
        controlLayout.addView(hotbar, FrameLayout.LayoutParams(1, 1))

        // 显示控件（FCL 启动时 toggleControlVisible）
        controlLayout.setControlVisible(true)

        // FPS / 内存覆盖层
        val fpsOverlay = TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            textSize = 13f
            setShadowLayer(3f, 0f, 0f, android.graphics.Color.BLACK)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            visibility = android.view.View.GONE
        }
        controlLayout.addView(fpsOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            setMargins(dp(12), dp(10), 0, 0)
        })
        val memOverlay = TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            textSize = 12f
            setShadowLayer(3f, 0f, 0f, android.graphics.Color.BLACK)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            visibility = android.view.View.GONE
        }
        controlLayout.addView(memOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            setMargins(dp(12), dp(30), 0, 0)
        })

        // 输入法桥接框：TouchCharInput 调出系统输入法，并把字符转发给游戏（聊天/命令）
        val touchCharInput = net.kdt.pojavlaunch.customcontrols.keyboard.TouchCharInput(this).apply {
            visibility = android.view.View.GONE
            textSize = 14f
        }
        controlLayout.addView(touchCharInput, FrameLayout.LayoutParams(1, 1).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
        })
        net.kdt.pojavlaunch.MainActivity.touchCharInput = touchCharInput

        // 统合设置面板（悬浮球 + 右侧滑出 + 遮罩）
        var settingsPanel: FclSettingsPanel? = null
        settingsPanel = FclSettingsPanel(this, object : FclSettingsPanel.Listener {
            override fun onForceClose() {
                System.exit(0)
            }
            override fun onSendCustomKey() {
                sendCustomKeyDialog()
            }
            override fun onOpenQuickInput() {
                quickInputDialog()
            }
            override fun onOpenKeyBinding() {
                android.widget.Toast.makeText(this@GameActivity, "按键绑定编辑", android.widget.Toast.LENGTH_SHORT).show()
            }
            override fun onOpenCascadeMenu() {
                controlLayout.notifyAppMenu()
            }
            override fun onOpenMultiplayer() {
                multiplayerDialog()
            }
            override fun onViewLog() {
                logPanelDialog()
            }
            override fun onResolutionChanged() {
                gl.refreshSize()
            }
            override fun onToggleControls() {
                controlLayout.setControlVisible(!controlLayout.areControlVisible())
            }
            override fun onCustomControls() {
                val entering = !controlLayout.getModifiable()
                controlLayout.setModifiable(entering)
                android.widget.Toast.makeText(this@GameActivity,
                    if (entering) "编辑模式：拖动按钮/点按钮编辑" else "已退出编辑模式",
                    android.widget.Toast.LENGTH_SHORT).show()
            }
        })
        controlLayout.setMenuListener { settingsPanel?.toggle() }

        // 初始化 MC 选项读写（MinecraftGLSurface.realStart 会 save）
        java.io.File(net.kdt.pojavlaunch.Tools.DIR_GAME_NEW).mkdirs()
        net.kdt.pojavlaunch.utils.MCOptionUtils.load(net.kdt.pojavlaunch.Tools.DIR_GAME_NEW)

        // 应用设置页的「分辨率缩放」：真正改变实际渲染窗口尺寸 + 触摸坐标映射。
        // PREF_SCALE_FACTOR 由 MinecraftGLSurface.refreshSize 与触摸事件使用，
        // 必须在 gl.start()（触发 surface 初始化/refreshSize）之前设置。
        net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_SCALE_FACTOR =
            (launchSettings.resolutionScale / 100f).coerceIn(0.25f, 2.0f)

        gl.start(false, touchpad)

        // 悬浮层必须在 gl.start() 之后添加，否则被 SurfaceView 的"hole punch"盖住（不可见）
        controlLayout.addView(settingsPanel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        settingsPanel.translationZ = 1000f  // 优先级高于操作按钮，防止被遮挡

        // 游戏启动加载动画：渲染出第一帧前显示覆盖层
        // translationZ 故意低于 settingsPanel(1000) 与悬浮球，避免盖住设置/弹窗/悬浮窗
        val loadingOverlay = LoadingOverlayView(this).apply {
            setSubtitle("版本 $versionId · 正在启动 JVM…")
            translationZ = 400f
        }
        controlLayout.addView(loadingOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        startLoadingHideWatch(loadingOverlay)

        // 持续性能模式
        if (settingsPanel.prefs.sustainedPerformance && android.os.Build.VERSION.SDK_INT >= 24) {
            window.setSustainedPerformanceMode(true)
        }

        // 局内键盘按钮：打开「局内键盘按钮」开关后，右下角显示键盘悬浮按钮，
        // 按下即调出输入法（用于游戏内输入指令/聊天）。translationZ 低于设置面板。
        val kbButton = androidx.appcompat.widget.AppCompatButton(this).apply {
            text = "⌨"
            textSize = 18f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundResource(android.R.drawable.btn_default)
            alpha = 0.85f
            visibility = if (settingsPanel.prefs.showKeyboardButton) android.view.View.VISIBLE
            else android.view.View.GONE
            setOnClickListener {
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                // 若当前无窗口可接收输入法，尝试直接显示
                if (imm != null && !imm.isAcceptingText) {
                    val r = android.graphics.Rect()
                    this.getWindowVisibleDisplayFrame(r)
                    imm.showSoftInput(this, 0)
                }
            }
        }
        controlLayout.addView(kbButton, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
            setMargins(0, 0, dp(16), dp(90))
        })
        kbButton.translationZ = 600f  // 高于加载动画(400)、低于设置面板(1000)

        // FPS/内存覆盖层刷新
        startOverlayLoop(settingsPanel, fpsOverlay, memOverlay)

        setContentView(controlLayout)
    }

    // 导入按钮组（FCL 布局 JSON）
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == net.kdt.pojavlaunch.customcontrols.ControlLayout.LAYOUT_IMPORT_REQUEST
            && resultCode == android.app.Activity.RESULT_OK && data?.data != null) {
            controlLayout.importLayoutFromUri(data.data!!)
        }
    }

    private fun dp(v: Number): Int = Math.round(v.toFloat() * resources.displayMetrics.density)

    /**
     * 监听游戏渲染出第一帧（swapCount 增长）后隐藏加载动画；
     * 超时（90s）兜底强制隐藏，避免卡在加载层。
     * native 库可能尚未加载，需容错；基线在首次成功读取时建立。
     */
    private fun startLoadingHideWatch(overlay: LoadingOverlayView) {
        val startTime = System.currentTimeMillis()
        val baseline = java.util.concurrent.atomic.AtomicInteger(-1)
        android.os.Handler(mainLooper).post(object : Runnable {
            override fun run() {
                try {
                    val count = com.miolauncher.backend.NativeInput.getSwapCount()
                    if (baseline.get() < 0) baseline.set(count)
                    val rendered = count > baseline.get()
                    val timedOut = System.currentTimeMillis() - startTime > 90_000
                    if (rendered || timedOut) {
                        if (overlay.isAttachedToWindow) overlay.hide()
                        return
                    }
                } catch (_: Throwable) {
                    // native 库未加载，等下一轮
                }
                android.os.Handler(mainLooper).postDelayed(this, 700)
            }
        })
    }

    private fun startOverlayLoop(panel: FclSettingsPanel, fps: android.widget.TextView, mem: android.widget.TextView) {
        android.os.Handler(mainLooper).post(object : Runnable {
            var lastCount = -1
            var lastTime = 0L
            override fun run() {
                try {
                    val p = panel.prefs
                    if (p.showFps) {
                        val count = com.miolauncher.backend.NativeInput.getSwapCount()
                        val now = System.currentTimeMillis()
                        if (lastCount >= 0 && now > lastTime) {
                            val fpsVal = (count - lastCount) * 1000 / (now - lastTime)
                            fps.text = "FPS: " + fpsVal
                        }
                        lastCount = count
                        lastTime = now
                        fps.visibility = android.view.View.VISIBLE
                    } else {
                        fps.visibility = android.view.View.GONE
                    }
                    if (p.showMemory) {
                        val max = java.lang.Runtime.getRuntime().maxMemory()
                        val used = java.lang.Runtime.getRuntime().totalMemory() - java.lang.Runtime.getRuntime().freeMemory()
                        mem.text = "内存: " + (used / 1048576) + "MB / " + (max / 1048576) + "MB"
                        mem.visibility = android.view.View.VISIBLE
                    } else {
                        mem.visibility = android.view.View.GONE
                    }
                } catch (e: Throwable) { }
                android.os.Handler(mainLooper).postDelayed(this, 500)
            }
        })
    }

    /**
     * 游戏内联机菜单：显示服务器列表，点「连接」设置待连接服务器并重启进服。
     */
    private fun multiplayerDialog() {
        val servers = com.miolauncher.app.data.ServerManager.list(this)
        if (servers.isEmpty()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("联机")
                .setMessage("还没有服务器。请到启动器「联机」页面添加服务器，或先开一个服务器。")
                .setPositiveButton("知道了", null)
                .show()
            return
        }
        val names = servers.map { "${it.name}（${it.address}）" }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("联机 · 选择服务器")
            .setItems(names) { _, which ->
                val s = servers[which]
                com.miolauncher.app.data.ServerManager.setPendingServer(this, s.address)
                android.app.AlertDialog.Builder(this)
                    .setTitle("加入 ${s.name}")
                    .setMessage("将退出当前游戏，重新进入后自动加入：\n${s.address}\n\n（如果弹窗后没有自动进服，请在启动器点「开始游戏」）")
                    .setPositiveButton("退出并进服") { _, _ ->
                        System.exit(0)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun quickInputDialog() {        val items = arrayOf("W", "A", "S", "D", "空格", "E", "Q", "T", "Shift", "Ctrl", "Esc", "F3")
        val codes = intArrayOf(87, 65, 83, 68, 32, 69, 81, 84, 340, 341, 256, 292)
        android.app.AlertDialog.Builder(this)
            .setTitle("快捷输入")
            .setItems(items) { _, which ->
                NativeInput.sendKey(codes[which], 0, 1, 0)
                NativeInput.sendKey(codes[which], 0, 0, 0)
            }
            .show()
    }

    private fun logPanelDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("游戏日志")
            .setMessage(kotlin.runCatching {
                java.io.File(filesDir, "mio/logs/game.log").readText().takeLast(1500)
            }.getOrElse { "无法读取日志" })
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun sendCustomKeyDialog() {
        val keys = arrayOf(
            "Esc (256)", "Tab (258)", "F1 (290)", "F3 (292)", "F5 (294)",
            "W (87)", "A (65)", "S (83)", "D (68)", "空格 (32)",
            "E (69)", "Q (81)", "T (84)", "Shift (340)", "Ctrl (341)")
        android.app.AlertDialog.Builder(this)
            .setTitle("发送自定义键码")
            .setItems(keys) { _, which ->
                val code = keys[which].substringAfter("(").substringBefore(")").trim().toInt()
                NativeInput.sendKey(code, 0, 1, 0)
                NativeInput.sendKey(code, 0, 0, 0)
            }
            .show()
    }

    private fun redirectStderr() {
        try {
            // 关闭「显示日志」时，stderr 直接丢弃（不写文件，避免日志膨胀）
            val target: java.io.OutputStream
            val stderr = filesDir.resolve("stderr.log")
            if (launchSettings.showLog) {
                val fd = android.system.Os.open(stderr.absolutePath,
                    android.system.OsConstants.O_CREAT or android.system.OsConstants.O_WRONLY or android.system.OsConstants.O_APPEND,
                    420)
                android.system.Os.dup2(fd, 2)
            }
        } catch (e: Exception) {
            android.util.Log.w("GameActivity", "redirect stderr failed", e)
        }
    }

    private fun extractDefaultLayout(): String {
        val out = java.io.File(filesDir, "default.json")
        try {
            assets.open("default.json").use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            android.util.Log.e("GameActivity", "extract default.json failed", e)
        }
        return out.absolutePath
    }

    private fun launchGame(versionId: String) {
        if (launched) return
        launched = true
        val username = intent.getStringExtra("username") ?: "Player"
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    android.util.Log.d("GameActivity", "launch: setup input bridge")
                    System.loadLibrary("pojavexec")
                    val ndir = applicationInfo.nativeLibraryDir
                    val settings = launchSettings
                    val renderer = settings.renderer
                    // 按渲染后端预载对应 GL 库（ANGLE/OSMesa 已移除，仅 gl4es 家族）
                    android.util.Log.d("GameActivity", "launch: renderer=${renderer.id} (${renderer.glLibName})")
                    net.kdt.pojavlaunch.utils.JREUtils.dlopen("$ndir/${renderer.glLibName}")
                    net.kdt.pojavlaunch.utils.JREUtils.dlopen("$ndir/libspirv-cross-c-shared.so")
                    net.kdt.pojavlaunch.utils.JREUtils.dlopen("$ndir/libopenal.so")
                    NativeInput.setInputReady(true)
                    NativeInput.sendWindowSize(CallbackBridge.windowWidth, CallbackBridge.windowHeight)
                    android.util.Log.d("GameActivity", "launch: repo")
                    val repo = MioRepository(this@GameActivity)
                    repo.ensureLibraries(versionId)
                    // 组件隔离：若当前版本启用隔离，使用独立实例目录作为游戏目录
                    val vCfg = com.miolauncher.app.data.VersionConfigStore.load(this@GameActivity, versionId)
                    val effectiveGameDir = if (vCfg.isolated) {
                        val instDir = com.miolauncher.app.data.VersionConfigStore.ensureInstanceDir(
                            this@GameActivity, versionId, repo.gameDir)
                        if (instDir != null) {
                            android.util.Log.i("GameActivity", "launch: 组件隔离启用，实例目录=$instDir")
                            instDir
                        } else repo.gameDir
                    } else repo.gameDir
                    android.util.Log.d("GameActivity", "launch: buildCommand window=${CallbackBridge.windowWidth}x${CallbackBridge.windowHeight}")
                    // 服务器地址：intent 优先，否则待连接服务器（联机菜单设置，进服后清空）
                    val serverAddress = intent.getStringExtra("server_address")
                        ?: com.miolauncher.app.data.ServerManager.takePendingServer(this@GameActivity)
                    val mem = if (vCfg.customMemoryMb > 0) vCfg.customMemoryMb
                    else settings.javaMemory
                    // 内存扩展：用户显式开启后允许超过安全上限，否则钳制在安全值内
                    val memCap = if (settings.extendedMemory)
                        com.miolauncher.app.data.DeviceInfo.extendedMemoryLimit(this@GameActivity)
                    else
                        com.miolauncher.app.data.DeviceInfo.safeGameMemoryMb(this@GameActivity)
                    val memFinal = mem.coerceIn(512, memCap)
                    android.util.Log.d("GameActivity", "launch: maxMemory=${memFinal}MB renderer=${renderer.id} server=${serverAddress ?: "-"} isolated=${vCfg.isolated}")
                    val cmd = GameLaunch.buildCommand(this@GameActivity, effectiveGameDir, versionId, username,
                        CallbackBridge.windowWidth, CallbackBridge.windowHeight, memFinal, serverAddress)
                    android.util.Log.d("GameActivity", "launch: start JVM")
                    val cfg = com.miolauncher.backend.LaunchConfig()
                    cfg.resolutionScale = settings.resolutionScale
                    cfg.renderDistance = settings.renderDistance
                    cfg.simulationDistance = settings.simulationDistance
                    cfg.maxFps = settings.maxFps
                    cfg.fov = settings.fov
                    cfg.guiScale = settings.guiScale
                    cfg.lang = settings.lang
                    cfg.vsync = settings.vsync
                    cfg.particles = settings.particles
                    cfg.extraJvmArgs = settings.extraJvmArgs
                    // 追加版本专属 JVM 参数
                    if (vCfg.customJvmArgs.isNotBlank()) {
                        cfg.extraJvmArgs = if (cfg.extraJvmArgs.isNullOrBlank()) vCfg.customJvmArgs
                        else cfg.extraJvmArgs + " " + vCfg.customJvmArgs
                    }
                    // 启动崩溃兜底：上次尝试快速失败时附带低并发数重试（libjimage 并发类加载竞态随线程数下降）
                    val retryArg = intent.getStringExtra("retry_jvm_arg")
                    if (retryArg != null) {
                        cfg.extraJvmArgs = if (cfg.extraJvmArgs.isNullOrBlank()) retryArg
                        else cfg.extraJvmArgs + " " + retryArg
                    }
                    // 记录启动前已有崩溃证据时间，用于本次会话崩溃检测
                    val crashBaseline = com.miolauncher.app.data.CrashLogManager.newestCrashTime(this@GameActivity)
                    // 写启动标记：任何异常退出（native 崩溃/被系统杀）都会残留，下次启动可检测
                    com.miolauncher.app.data.CrashLogManager.markGameStart(this@GameActivity, versionId)
                    val startMs = System.currentTimeMillis()
                    val code = GameLaunch.launch(this@GameActivity, effectiveGameDir, cmd,
                        CallbackBridge.windowWidth, CallbackBridge.windowHeight, renderer, cfg)
                    val elapsed = System.currentTimeMillis() - startMs
                    android.util.Log.d("GameActivity", "launch: JVM returned code=$code after ${elapsed}ms")
                    // JVM 正常返回（游戏主动退出）→ 清除启动标记
                    if (code == 0) {
                        com.miolauncher.app.data.CrashLogManager.markGameExited(this@GameActivity)
                    }
                    // 本次会话产生新崩溃 或 JVM 异常返回码 → 运行日志自动分析 + 弹出崩溃日志
                    val newCrash = com.miolauncher.app.data.CrashLogManager.newestCrashTime(this@GameActivity) > crashBaseline
                    val jvmFailed = code != 0
                    if (newCrash || jvmFailed) {
                        // 日志自动分析
                        val diagnoses = com.miolauncher.backend.GameLogAnalyzer.analyzeGameLogs(this@GameActivity)
                        if (diagnoses.isNotEmpty()) {
                            android.util.Log.w("GameActivity", "launch: 日志诊断发现 ${diagnoses.size} 个问题 (newCrash=$newCrash jvmFailed=$jvmFailed)")
                            val report = com.miolauncher.app.data.CrashLogManager.collect(this@GameActivity)
                            if (report != null) {
                                withContext(Dispatchers.Main) { showCrashDialog(report, diagnoses) }
                            } else {
                                // 无 crash 文件但 JVM 失败：用日志构造一个报告
                                val content = runCatching {
                                    java.io.File(filesDir, "mio/logs/game.log").readText().takeLast(4000)
                                }.getOrDefault("（无日志）")
                                val fallback = com.miolauncher.app.data.CrashLogManager.CrashReport(
                                    title = "游戏启动异常",
                                    summary = "JVM 返回码 $code，请查看下方日志了解原因。",
                                    primaryPath = null,
                                    evidence = emptyList(),
                                    combined = content,
                                )
                                withContext(Dispatchers.Main) { showCrashDialog(fallback, diagnoses) }
                            }
                        } else {
                            val report = com.miolauncher.app.data.CrashLogManager.collect(this@GameActivity)
                            if (report != null) {
                                android.util.Log.w("GameActivity", "launch: 检测到崩溃：${report.title}")
                                withContext(Dispatchers.Main) { showCrashDialog(report, emptyList()) }
                            }
                        }
                    } else if (elapsed < 3000 && intent.getStringExtra("retry_jvm_arg") == null) {
                        // 快速失败：先分析日志，再决定是否自动重试
                        val diagnoses = com.miolauncher.backend.GameLogAnalyzer.analyzeGameLogs(this@GameActivity)
                        android.util.Log.w("GameActivity", "launch: 疑似启动即失败(code=$code)，诊断 ${diagnoses.size} 个问题")
                        // 如果诊断出可自动修复的问题（如库文件缺失），先修复再重试
                        val autoFixed = com.miolauncher.backend.GameLogAnalyzer.attemptAutoFix(this@GameActivity, diagnoses)
                        if (autoFixed.isNotEmpty()) {
                            android.util.Log.i("GameActivity", "launch: 自动修复: $autoFixed")
                        }
                        // 无严重可修复错误时，用低并发数自动重启一次
                        val hasCriticalFixable = diagnoses.any {
                            it.severity == com.miolauncher.backend.GameLogAnalyzer.Severity.ERROR
                                    && it.fix?.action?.execute(this@GameActivity, repo.gameDir) == true
                        }
                        if (!hasCriticalFixable) {
                            val retry = android.content.Intent(this@GameActivity, GameActivity::class.java)
                            retry.putExtra("version_id", versionId)
                            retry.putExtra("username", intent.getStringExtra("username") ?: "Player")
                            retry.putExtra("server_address", serverAddress)
                            retry.putExtra("retry_jvm_arg", "-XX:ActiveProcessorCount=4")
                            retry.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            startActivity(retry)
                            finish()
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("GameActivity", "启动失败", e)
                    // 异常启动失败：分析日志并弹出诊断（放宽判定，确保用户能看到原因）
                    try {
                        val diagnoses = com.miolauncher.backend.GameLogAnalyzer.analyzeGameLogs(this@GameActivity)
                        val content = runCatching {
                            java.io.File(filesDir, "mio/logs/game.log").readText().takeLast(4000)
                        }.getOrDefault("（无日志）")
                        val fallback = com.miolauncher.app.data.CrashLogManager.CrashReport(
                            title = "游戏启动异常",
                            summary = "${e.javaClass.simpleName}: ${e.message ?: "未知错误"}",
                            primaryPath = null,
                            evidence = emptyList(),
                            combined = content,
                        )
                        withContext(Dispatchers.Main) { showCrashDialog(fallback, diagnoses) }
                    } catch (_: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@GameActivity, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 游戏退出（返回手势/系统回收）时释放进程内 JVM 占用的数 GB 内存，
        // 避免退出后 RSS 挂着、后续会话把设备内存压到被 LMK 误杀。
        if (isFinishing) {
            android.util.Log.d("GameActivity", "onDestroy: kill process to release in-process JVM memory")
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    /**
     * 崩溃日志弹窗：预览 + 查看/复制/分享/导出/关闭（FCL 式）。
     */
    private fun showCrashDialog(
        report: com.miolauncher.app.data.CrashLogManager.CrashReport,
        diagnoses: List<com.miolauncher.backend.GameLogAnalyzer.Diagnosis> = emptyList(),
    ) {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val dp = resources.displayMetrics.density
        fun dpf(v: Float) = (v * dp).toInt()

        fun addButton(container: android.widget.LinearLayout, text: String, onClick: () -> Unit) {
            val btn = android.widget.Button(this)
            btn.text = text
            btn.textSize = 14f
            btn.setOnClickListener { onClick() }
            val lp = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dpf(4f), dpf(4f), dpf(4f), dpf(4f))
            container.addView(btn, lp)
        }

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dpf(20f), dpf(16f), dpf(20f), dpf(12f))
        }
        root.addView(android.widget.TextView(this).apply {
            text = "游戏崩溃了"
            setTextSize(20f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(0xFFE53935.toInt())
        })
        root.addView(android.widget.TextView(this).apply {
            text = report.title
            setTextSize(13f)
            setPadding(0, dpf(2f), 0, dpf(8f))
        })

        // 日志自动诊断结果
        if (diagnoses.isNotEmpty()) {
            root.addView(android.widget.TextView(this).apply {
                text = "🔍 自动诊断结果"
                setTextSize(14f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dpf(4f), 0, dpf(4f))
            })
            val diagScroll = android.widget.ScrollView(this).apply {
                addView(android.widget.TextView(this@GameActivity).apply {
                    text = com.miolauncher.backend.GameLogAnalyzer.formatReport(diagnoses)
                    setTextSize(12f)
                    typeface = android.graphics.Typeface.MONOSPACE
                    setTextColor(0xFFFFFFFF.toInt())
                })
            }
            root.addView(diagScroll, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dpf(140f)))
        }

        val scroll = android.widget.ScrollView(this).apply {
            addView(android.widget.TextView(this@GameActivity).apply {
                text = report.summary
                setTextSize(12f)
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(0xFFFFFFFF.toInt())
            })
        }
        root.addView(scroll, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dpf(180f)))

        val row1 = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
        addButton(row1, "查看完整日志") {
            LogViewerActivity.start(this@GameActivity, report.title, report.combined, "crash.txt")
        }
        addButton(row1, "复制") {
            val cm = getSystemService(android.content.ClipboardManager::class.java)
            cm.setPrimaryClip(android.content.ClipData.newPlainText(report.title, report.combined))
            android.widget.Toast.makeText(this@GameActivity, "已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
        }
        root.addView(row1)

        val row2 = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
        addButton(row2, "分享") {
            // 分享完整日志文件，避免 EXTRA_TEXT 大文本截断
            val f = com.miolauncher.app.data.CrashLogManager.shareFile(
                this@GameActivity, "crash-${System.currentTimeMillis()}.txt", report.combined,
            )
            if (f == null) {
                android.widget.Toast.makeText(this@GameActivity, "生成分享文件失败", android.widget.Toast.LENGTH_SHORT).show()
                return@addButton
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this@GameActivity, packageName + ".fileprovider", f,
            )
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, report.title)
                putExtra(android.content.Intent.EXTRA_TEXT, report.title + "（完整日志见附件）")
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { startActivity(android.content.Intent.createChooser(send, "分享崩溃日志")) }
                .onFailure { android.widget.Toast.makeText(this@GameActivity, "没有可用的分享应用", android.widget.Toast.LENGTH_SHORT).show() }
        }
        addButton(row2, "导出文件") {
            val f = com.miolauncher.app.data.CrashLogManager.exportReport(this@GameActivity, report)
            if (f != null) android.widget.Toast.makeText(this@GameActivity, "已导出到：${f.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
            else android.widget.Toast.makeText(this@GameActivity, "导出失败", android.widget.Toast.LENGTH_SHORT).show()
        }
        addButton(row2, "关闭") {
            dialog.dismiss()
        }
        root.addView(row2)

        dialog.setContentView(root)
        dialog.setOnDismissListener {
            // 本次崩溃已呈现，标记已读，避免下次启动重复提示
            com.miolauncher.app.data.CrashLogManager.consume(this@GameActivity)
        }
        dialog.show()
    }
}
