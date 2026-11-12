package com.rockjesus.icarplay.carlife

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.diplay.carlife.core.CarLife
import com.diplay.carlife.core.CarLifeMessages
import com.rockjesus.icarplay.PageUi
import com.shilapi.xcertplay.carlife.CarLifeEngine
import com.shilapi.xcertplay.carlife.CarLifePersistence
import com.shilapi.xcertplay.carlife.CarLifeSessionLog
import com.shilapi.xcertplay.carlife.SubnetScanner
import com.shilapi.xcertplay.carlife.WirelessNetwork
import io.github.jqssun.airplay.R
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single-APK CarLife+ receiver page.
 *
 * The receiver core is the GPL-3.0 DiPlay/Limo CarLife implementation, which
 * was tested against a real CarLife+ phone app. It owns the AOA/TCP session,
 * H.264 decoder, audio tracks, microphone and touch uplink. This activity is
 * only the car-side shell: one video surface, a wired/wireless trigger and a
 * way back to iCarPlay.
 */
class CarLifePlusActivity : Activity(), CarLifeEngine.UiListener {

    private lateinit var root: FrameLayout
    private lateinit var video: TextureView
    private lateinit var status: TextView
    private lateinit var connectBar: LinearLayout
    private lateinit var idlePanel: LinearLayout
    private lateinit var statusDot: View

    private val main = Handler(Looper.getMainLooper())
    private var engine: CarLifeEngine? = null
    private var pendingStart: CarLifeEngine.Mode? = null
    private var scanning = false
    private var scanCancel: AtomicBoolean? = null
    private var scanEpoch = 0
    private var phoneWidth = CarLife.DEFAULT_VIDEO_WIDTH
    private var phoneHeight = CarLife.DEFAULT_VIDEO_HEIGHT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        setContentView(root)

        video = TextureView(this)
        video.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER,
        )
        root.addView(video)
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateVideoLayout() }
        video.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                st.setDefaultBufferSize(phoneWidth, phoneHeight)
                pendingStart?.let { mode ->
                    pendingStart = null
                    startEngine(Surface(st), mode)
                }
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                updateVideoLayout()
            }

            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                engine?.stop()
                engine = null
                return true
            }

            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
        }
        video.setOnTouchListener { v, event -> onVideoTouch(v, event) }

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(10), dp(7), dp(10), dp(7))
        top.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0xE6070C14.toInt(), 0xD90D1926.toInt()),
        ).apply {
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), 0x553B5876)
        }
        top.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP,
        ).apply {
            marginStart = dp(10)
            marginEnd = dp(10)
            topMargin = dp(10)
        }

        val back = Button(this)
        back.text = "← iCarPlay"
        back.setTextColor(Color.WHITE)
        back.textSize = 12.5f
        back.setAllCaps(false)
        back.setPadding(dp(10), 0, dp(10), 0)
        back.minWidth = 0
        back.minimumWidth = 0
        back.minHeight = 0
        back.minimumHeight = 0
        back.setBackground(PageUi.rounded(this, 0x2EFFFFFF, 0x55FFFFFF, 10f))
        back.setOnClickListener { finish() }
        top.addView(back, LinearLayout.LayoutParams(dp(112), dp(36)))

        statusDot = View(this)
        statusDot.setBackground(PageUi.rounded(this, PageUi.GREEN, 0, 4f))
        top.addView(statusDot, LinearLayout.LayoutParams(dp(8), dp(8)).apply {
            marginStart = dp(8)
            marginEnd = dp(7)
        })

        status = TextView(this)
        status.setTextColor(0xFFEAF4FF.toInt())
        status.textSize = 12.5f
        status.setSingleLine(false)
        status.maxLines = 2
        status.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        status.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        status.setPadding(0, 0, dp(8), 0)
        top.addView(status, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val log = Button(this)
        log.text = "日志"
        log.setTextColor(PageUi.BLUE)
        log.textSize = 12f
        log.setAllCaps(false)
        log.setPadding(dp(10), 0, dp(10), 0)
        log.minWidth = 0
        log.minimumWidth = 0
        log.minHeight = 0
        log.minimumHeight = 0
        log.setBackground(PageUi.rounded(this, PageUi.blend(PageUi.CARD, PageUi.BLUE, 0.16f),
            PageUi.blend(PageUi.BORDER, PageUi.BLUE, 0.55f), 10f))
        log.setOnClickListener { showLogDialog() }
        top.addView(log, LinearLayout.LayoutParams(dp(56), dp(36)))
        root.addView(top)

        idlePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(20), dp(24), dp(20))
            background = PageUi.surface(this@CarLifePlusActivity, PageUi.GOLD, 18f)
        }
        val idleIcon = ImageView(this)
        idleIcon.setImageResource(R.drawable.ic_ui_carlife)
        idleIcon.colorFilter = android.graphics.PorterDuffColorFilter(PageUi.GOLD, android.graphics.PorterDuff.Mode.SRC_IN)
        idlePanel.addView(idleIcon, LinearLayout.LayoutParams(dp(44), dp(44)))
        idlePanel.addView(TextView(this).apply {
            text = "CarLife+ 接收端"
            setTextColor(PageUi.TEXT)
            textSize = 22f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(6), 0, 0)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        idlePanel.addView(TextView(this).apply {
            text = "有线 USB  ·  无线同一局域网"
            setTextColor(PageUi.GOLD)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(10))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        idlePanel.addView(TextView(this).apply {
            text = "手机打开 CarLife+，用数据线连接后点“有线连接”；\n无线时让手机与车机处于同一 Wi‑Fi，再点“无线连接”。"
            setTextColor(PageUi.MUTED)
            textSize = 12.5f
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(idlePanel, FrameLayout.LayoutParams(
            dp(420), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER,
        ).apply {
            marginStart = dp(24)
            marginEnd = dp(24)
        })

        connectBar = LinearLayout(this)
        connectBar.orientation = LinearLayout.HORIZONTAL
        connectBar.gravity = Gravity.CENTER
        connectBar.setPadding(dp(8), dp(6), dp(8), dp(8))
        connectBar.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x990A111B.toInt(), 0xE6070C14.toInt()),
        ).apply {
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), 0x443B5876)
        }
        connectBar.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        ).apply {
            marginStart = dp(10)
            marginEnd = dp(10)
            bottomMargin = dp(10)
        }
        connectBar.addView(barButton("有线连接", PageUi.BLUE) { startWired() })
        connectBar.addView(barButton("无线连接", PageUi.GREEN) { startWireless() })
        connectBar.addView(barButton("手动 IP", PageUi.GOLD) { showManualConnectDialog() })
        connectBar.addView(barButton("停止", PageUi.RED) { stopEngine() })
        root.addView(connectBar)

        status.setOnLongClickListener {
            showLogDialog()
            true
        }
        status.text = "请用 USB 连接手机，打开 CarLife+ 后点“有线连接”\n${WirelessNetwork.describe(this)}"
    }

    private fun barButton(label: String, accent: Int, action: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.setTextColor(PageUi.TEXT)
        b.textSize = 13f
        b.setAllCaps(false)
        b.setPadding(dp(10), 0, dp(10), 0)
        b.minWidth = 0
        b.minimumWidth = 0
        b.minHeight = 0
        b.minimumHeight = 0
        b.setBackground(PageUi.rounded(this, PageUi.blend(PageUi.CARD, accent, 0.12f),
            PageUi.blend(PageUi.BORDER, accent, 0.55f), 10f))
        b.setOnClickListener { action() }
        val lp = LinearLayout.LayoutParams(0, dp(40), 1f)
        lp.setMargins(dp(4), 0, dp(4), 0)
        b.layoutParams = lp
        return b
    }

    private fun startWired() {
        if (engine != null) {
            status.text = "CarLife+ 已在连接中；如需切换请先点“停止”"
            return
        }
        cancelScan()
        val surface = video.surfaceTexture?.let { Surface(it) }
        if (surface == null) {
            pendingStart = CarLifeEngine.Mode.WIRED
            status.text = "正在准备画面…"
            return
        }
        startEngine(surface, CarLifeEngine.Mode.WIRED)
    }

    private fun startWireless() {
        if (engine != null) {
            status.text = "CarLife+ 已在连接中；如需切换请先点“停止”"
            return
        }
        if (scanning) return
        val lan = WirelessNetwork.active(this)
        if (lan == null) {
            status.text = "未连接 Wi-Fi 或有线网络：请先让车机接入手机所在网络"
            return
        }
        scanning = true
        val epoch = ++scanEpoch
        val cancel = AtomicBoolean(false)
        scanCancel = cancel
        val lastHost = CarLifePersistence.get(this).lastWirelessHost()
            ?.let { WirelessNetwork.parseIpv4(it) }
        CarLifeSessionLog.log(this, "SCAN", "lan=${lan.summary()} last=${lastHost?.hostAddress ?: "-"}")
        status.text = "正在 ${lan.summary()} 搜索手机…\n手机请先点 CarLife+ 的“无线连接”"
        SubnetScanner.findPhone(
            this,
            lastHost,
            cancel,
            onFound = { host ->
                main.post {
                    if (scanning && scanEpoch == epoch) {
                        scanning = false
                        scanCancel = null
                        CarLifePersistence.get(this).saveLastWirelessHost(host.hostAddress)
                        CarLifeSessionLog.log(this, "SCAN", "found=${host.hostAddress}")
                        startWirelessToHost(host, fromScan = true)
                    }
                }
            },
            onDone = {
                main.post {
                    if (scanning && scanEpoch == epoch) {
                        scanning = false
                        scanCancel = null
                        CarLifeSessionLog.log(this, "SCAN", "not found")
                        status.text = "未找到手机：确认手机与车机在同一网络，且手机已点“无线连接”\n" +
                            "仍失败可点“手动 IP”输入手机地址"
                    }
                }
            },
        )
    }

    private fun startWirelessToHost(host: InetAddress, fromScan: Boolean = false) {
        if (!fromScan) cancelScan()
        val surface = video.surfaceTexture?.let { Surface(it) }
        if (surface == null) {
            status.text = "画面未就绪，请重试"
            return
        }
        CarLifePersistence.get(this).saveLastWirelessHost(host.hostAddress)
        CarLifeSessionLog.log(this, "SCAN", "connect host=${host.hostAddress}")
        startEngine(surface, CarLifeEngine.Mode.WIRELESS, host)
    }

    private fun startEngine(
        surface: Surface,
        mode: CarLifeEngine.Mode,
        host: InetAddress? = null,
    ) {
        engine?.stop()
        val e = CarLifeEngine(applicationContext, surface, this)
        engine = e
        e.start(mode, host)
        idlePanel.visibility = View.GONE
        setEngineActive(true)
        statusDot.setBackground(PageUi.rounded(this, PageUi.GOLD, 0, 4f))
        status.text = if (mode == CarLifeEngine.Mode.WIRED) {
            "正在等待手机（USB）…"
        } else {
            "正在连接手机 ${host?.hostAddress ?: ""}…"
        }
    }

    private fun stopEngine() {
        pendingStart = null
        cancelScan()
        engine?.stop()
        engine = null
        idlePanel.visibility = View.VISIBLE
        setEngineActive(false)
        statusDot.setBackground(PageUi.rounded(this, PageUi.GOLD, 0, 4f))
        status.text = "已停止。可重新连接手机"
    }

    private fun setEngineActive(active: Boolean, label: String = "") {
        getSharedPreferences("icarplay", Context.MODE_PRIVATE).edit()
            .putBoolean("carlife_active", active)
            .putString("carlife_state", label)
            .apply()
    }

    /** Invalidates every in-flight scan callback, then stops the worker pool. */
    private fun cancelScan() {
        scanEpoch++
        scanning = false
        scanCancel?.set(true)
        scanCancel = null
    }

    private fun showManualConnectDialog() {
        if (engine != null) {
            status.text = "CarLife+ 已在连接中；如需切换请先点“停止”"
            return
        }
        val density = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * density).toInt(), (8 * density).toInt(), (18 * density).toInt(), 0)
        }
        val hintView = TextView(this).apply {
            text = "${WirelessNetwork.describe(this@CarLifePlusActivity)}\n" +
                "输入手机 CarLife+ 无线页显示的 IPv4 地址"
            textSize = 12.5f
            setTextColor(0xFF9AA4B2.toInt())
        }
        box.addView(hintView)
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            hint = "例如 192.168.43.1"
            setText(CarLifePersistence.get(this@CarLifePlusActivity).lastWirelessHost().orEmpty())
        }
        box.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = (10 * density).toInt() })

        val dialog = AlertDialog.Builder(this)
            .setTitle("手动连接 CarLife+")
            .setView(box)
            .setPositiveButton("连接", null)
            .setNeutralButton("最近日志", null)
            .setNegativeButton("取消", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val host = WirelessNetwork.parseIpv4(input.text.toString())
                if (host == null) {
                    input.error = "请输入有效的 IPv4 地址，例如 192.168.43.1"
                } else {
                    dialog.dismiss()
                    startWirelessToHost(host)
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss()
                showLogDialog()
            }
        }
        dialog.show()
    }

    /** On-screen tail of the session log: no adb needed during a drive test. */
    private fun showLogDialog() {
        val density = resources.displayMetrics.density
        val file = CarLifeSessionLog.currentFile()
        val text = runCatching { file?.readLines()?.takeLast(80)?.joinToString("\n") }
            .getOrNull().orEmpty()
        val body = if (text.isBlank()) {
            "本次会话还没有日志。\n\n日志目录：\n${file?.parent ?: "未创建"}"
        } else {
            "日志文件：${file?.absolutePath}\n\n$text"
        }
        val view = TextView(this).apply {
            setTextIsSelectable(true)
            textSize = 11f
            setPadding((16 * density).toInt(), (10 * density).toInt(), (16 * density).toInt(), 10)
            setText(body)
        }
        val scroll = ScrollView(this).apply { addView(view) }
        AlertDialog.Builder(this)
            .setTitle("CarLife+ 连接日志")
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun onVideoTouch(v: View, event: MotionEvent): Boolean {
        val e = engine ?: return true
        if (v.width <= 0 || v.height <= 0) return true
        val x = (event.x / v.width * phoneWidth).toInt().coerceIn(0, phoneWidth - 1)
        val y = (event.y / v.height * phoneHeight).toInt().coerceIn(0, phoneHeight - 1)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                e.sendTouch(CarLife.TOUCH_ACTION_DOWN, x, y)
            MotionEvent.ACTION_MOVE ->
                e.sendTouch(CarLife.TOUCH_ACTION_MOVE, x, y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                e.sendTouch(CarLife.TOUCH_ACTION_UP, x, y)
        }
        return true
    }

    private fun updateVideoLayout() {
        val rootW = root.width
        val rootH = root.height
        if (rootW <= 0 || rootH <= 0) return
        val videoRatio = phoneWidth.toFloat() / phoneHeight.toFloat()
        val rootRatio = rootW.toFloat() / rootH.toFloat()
        val w: Int
        val h: Int
        if (rootRatio > videoRatio) {
            h = rootH
            w = (h * videoRatio).toInt()
        } else {
            w = rootW
            h = (w / videoRatio).toInt()
        }
        val lp = video.layoutParams as FrameLayout.LayoutParams
        if (lp.width == w && lp.height == h) return
        lp.width = w
        lp.height = h
        lp.gravity = Gravity.CENTER
        video.layoutParams = lp
        video.surfaceTexture?.setDefaultBufferSize(phoneWidth, phoneHeight)
    }

    override fun onEngineState(state: String, detail: String?) {
        main.post {
            status.text = when {
                state == "scanning" -> "正在扫描 USB…请用数据线连接手机并打开 CarLife+"
                state == "connected" -> "已连接手机${detail?.let { "：$it" } ?: ""}"
                state == "reconnecting" -> "连接中断，正在重连…"
                state == "wireless-connecting" -> "正在连接手机 ${detail ?: ""}…"
                state == "wireless" -> "无线连接：${detail ?: ""}"
                state == "watchdog" -> "画面中断，正在重连…"
                state == "stopped" || state == "disconnected" -> "未连接。请检查数据线 / 手机 CarLife+ 状态"
                state.startsWith("phase:") -> {
                    val phase = state.removePrefix("phase:")
                    if (phase == "streaming") "CarLife+ 已连接，正在投屏" else "握手：$phase"
                }
                else -> state + (detail?.let { " · $it" } ?: "")
            }
            val active = state != "stopped" && state != "disconnected"
            setEngineActive(active, state)
            statusDot.setBackground(PageUi.rounded(this,
                if (active) PageUi.GREEN else PageUi.GOLD, 0, 4f))
            idlePanel.visibility = if (active) View.GONE else View.VISIBLE
        }
    }

    override fun onMobileInfo(info: CarLifeMessages.DeviceInfo) {
        main.post {
            val name = listOf(info.manufacturer, info.model)
                .filter { it.isNotBlank() }.joinToString(" ")
            if (name.isNotBlank()) status.text = "已连接手机：$name"
        }
    }

    override fun onEncoderSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        main.post {
            phoneWidth = width
            phoneHeight = height
            updateVideoLayout()
        }
    }

    override fun onDestroy() {
        cancelScan()
        pendingStart = null
        engine?.stop()
        engine = null
        setEngineActive(false)
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
