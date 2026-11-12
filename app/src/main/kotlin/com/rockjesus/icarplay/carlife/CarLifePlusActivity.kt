package com.rockjesus.icarplay.carlife

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.diplay.carlife.core.CarLife
import com.diplay.carlife.core.CarLifeMessages
import com.shilapi.xcertplay.carlife.CarLifeEngine
import com.shilapi.xcertplay.carlife.SubnetScanner
import java.net.InetAddress

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

    private val main = Handler(Looper.getMainLooper())
    private var engine: CarLifeEngine? = null
    private var pendingStart: CarLifeEngine.Mode? = null
    private var scanning = false
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
        top.setPadding(dp(10), dp(6), dp(10), dp(6))
        top.setBackgroundColor(0xCC000000.toInt())
        top.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP,
        )

        val back = Button(this)
        back.text = "← iCarPlay"
        back.setTextColor(Color.WHITE)
        back.textSize = 13f
        back.setAllCaps(false)
        back.setPadding(dp(12), 0, dp(12), 0)
        back.minWidth = 0
        back.minimumWidth = 0
        back.minHeight = 0
        back.minimumHeight = 0
        back.setBackgroundColor(0x33FFFFFF)
        back.setOnClickListener { finish() }
        top.addView(back, LinearLayout.LayoutParams(dp(108), dp(36)))

        status = TextView(this)
        status.setTextColor(0xFFE6E6E6.toInt())
        status.textSize = 12.5f
        status.setSingleLine(false)
        status.maxLines = 2
        status.setPadding(dp(10), 0, 0, 0)
        top.addView(status, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(top)

        connectBar = LinearLayout(this)
        connectBar.orientation = LinearLayout.HORIZONTAL
        connectBar.gravity = Gravity.CENTER
        connectBar.setPadding(dp(8), dp(6), dp(8), dp(8))
        connectBar.setBackgroundColor(0xCC000000.toInt())
        connectBar.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        )
        connectBar.addView(barButton("有线连接") { startWired() })
        connectBar.addView(barButton("无线连接") { startWireless() })
        connectBar.addView(barButton("停止") { stopEngine() })
        root.addView(connectBar)

        status.text = "请用 USB 连接手机，打开 CarLife+ 后点“有线连接”"
    }

    private fun barButton(label: String, action: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.setTextColor(Color.WHITE)
        b.textSize = 13f
        b.setAllCaps(false)
        b.setPadding(dp(10), 0, dp(10), 0)
        b.minWidth = 0
        b.minimumWidth = 0
        b.minHeight = 0
        b.minimumHeight = 0
        b.setBackgroundColor(0x2EFFFFFF)
        b.setOnClickListener { action() }
        val lp = LinearLayout.LayoutParams(0, dp(38), 1f)
        lp.setMargins(dp(4), 0, dp(4), 0)
        b.layoutParams = lp
        return b
    }

    private fun startWired() {
        if (engine != null) {
            status.text = "CarLife+ 已在连接中；如需切换请先点“停止”"
            return
        }
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
        scanning = true
        status.text = "正在同一 WiFi 下搜索手机（手机请先点 CarLife+ 的无线连接）…"
        SubnetScanner.findPhone({ host ->
            main.post {
                if (scanning) {
                    scanning = false
                    val surface = video.surfaceTexture?.let { Surface(it) }
                    if (surface != null) {
                        startEngine(surface, CarLifeEngine.Mode.WIRELESS, host)
                    } else {
                        status.text = "画面未就绪，请重试"
                    }
                }
            }
        }, {
            main.post {
                if (scanning) {
                    scanning = false
                    status.text = "未找到手机：确认手机与车机在同一 WiFi，且手机已点“无线连接”"
                }
            }
        })
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
        status.text = if (mode == CarLifeEngine.Mode.WIRED) {
            "正在等待手机（USB）…"
        } else {
            "正在连接手机 ${host?.hostAddress ?: ""}…"
        }
    }

    private fun stopEngine() {
        pendingStart = null
        engine?.stop()
        engine = null
        status.text = "已停止。可重新连接手机"
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
        scanning = false
        engine?.stop()
        engine = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
