package app.brightnesspulse

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import android.app.KeyguardManager
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

class PulseService : Service() {

    companion object {
        const val EXTRA_USE_SYSTEM_BOOST = "use_system_boost"
        private const val CHANNEL_ID = "pulse_channel"
        private const val NOTIF_ID = 101

        private const val PULSE_PERIOD_MS = 2000L      // 한 주기
        private const val DIM_ALPHA = 0.12f            // 어둡게 정도(0~1)
        private const val BRIGHT_PHASE_MS = 700L       // 시스템 밝기 살짝 올리는 시간(옵션)
        private const val SYSTEM_DELTA = 0.08f         // 밝기 +8% 포인트 정도
    }

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var wm: WindowManager? = null
    private var overlay: FrameLayout? = null
    private var running = false
    private var useSystemBoost = false

    private lateinit var power: PowerManager
    private lateinit var keyguard: KeyguardManager

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> stopPulsing()
                Intent.ACTION_SCREEN_ON -> maybeStartIfUnlocked()
                Intent.ACTION_USER_PRESENT, Intent.ACTION_USER_UNLOCKED -> maybeStartIfUnlocked()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        power = getSystemService(Context.POWER_SERVICE) as PowerManager
        keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        startForeground(NOTIF_ID, buildNotification())
        registerReceivers()
        setupOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        useSystemBoost = intent?.getBooleanExtra(EXTRA_USE_SYSTEM_BOOST, false) == true
        maybeStartIfUnlocked()
        return START_STICKY
    }

    override fun onDestroy() {
        stopPulsing()
        unregisterReceiver(screenReceiver)
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "Brightness Pulse", NotificationManager.IMPORTANCE_MIN)
            mgr.createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Brightness Pulse 작동 중")
            .setContentText("화면이 켜지고 잠금이 풀렸을 때만 펄스")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    private fun registerReceivers() {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_USER_UNLOCKED)
        }
        registerReceiver(screenReceiver, f)
    }

    private fun isUnlockedAndInteractive(): Boolean {
        val interactive = if (Build.VERSION.SDK_INT >= 20) power.isInteractive else true
        val locked = keyguard.isKeyguardLocked
        return interactive && !locked
    }

    private fun maybeStartIfUnlocked() {
        if (isUnlockedAndInteractive()) startPulsing() else stopPulsing()
    }

    private fun setupOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay = FrameLayout(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        runCatching { wm?.addView(overlay, lp) }
    }

    private fun removeOverlay() {
        overlay?.let { v ->
            runCatching { wm?.removeView(v) }
        }
        overlay = null
        wm = null
    }

    private fun startPulsing() {
        if (running) return
        if (!Settings.canDrawOverlays(this)) return
        running = true
        scope.launch {
            while (isActive && running) {
                // 1) 밝게(=오버레이 투명) 단계
                animateOverlayAlpha(target = 0f, duration = PULSE_PERIOD_MS / 2)

                // 선택: 시스템 밝기 살짝 올리기(기기/설정에 따라 무시될 수 있음)
                if (useSystemBoost && Settings.System.canWrite(this@PulseService)) {
                    SystemBrightnessModulator.pulseUp(this@PulseService, SYSTEM_DELTA, BRIGHT_PHASE_MS)
                }

                // 2) 약간 어둡게 단계
                animateOverlayAlpha(target = DIM_ALPHA, duration = PULSE_PERIOD_MS / 2)
            }
        }
    }

    private fun stopPulsing() {
        if (!running) return
        running = false
        overlay?.let { v ->
            main.post { v.animate().cancel(); v.alpha = 0f }
        }
    }

    private fun animateOverlayAlpha(target: Float, duration: Long) {
        val v = overlay ?: return
        val start = v.alpha
        if (start == target) {
            Thread.sleep(duration)
            return
        }
        val steps = 30
        val interpolator = AccelerateDecelerateInterpolator()
        val stepTime = (duration / steps).coerceAtLeast(8)
        for (i in 1..steps) {
            if (!running) break
            val t = i / steps.toFloat()
            val a = start + (target - start) * interpolator.getInterpolation(t)
            main.post { v.alpha = a }
            Thread.sleep(stepTime)
        }
    }
}
