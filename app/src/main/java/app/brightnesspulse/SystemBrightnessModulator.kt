package app.brightnesspulse

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import kotlin.math.roundToInt

object SystemBrightnessModulator {

    // 자동밝기 기준(가능하면 시스템이 산출한 현재 밝기)을 읽어 약간 올렸다가 복원
    fun pulseUp(ctx: Context, delta: Float, durationMs: Long) {
        try {
            if (!Settings.System.canWrite(ctx)) return

            val resolver = ctx.contentResolver
            val oldMode = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)

            val baseline01 = currentBrightness01(ctx)
            val boosted01 = (baseline01 + delta).coerceIn(0f, 1f)

            // 수동모드로 전환 후 잠깐 올림
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS,
                (boosted01 * 255).roundToInt())

            Thread.sleep(durationMs)

            // 원래 모드로 복구(자동이면 자동으로)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, oldMode)
        } catch (_: Throwable) {}
    }

    private fun currentBrightness01(ctx: Context): Float {
        // 시스템 밝기 값(0~255)을 0~1 범위로 변환
        return try {
            val v = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            (v / 255f).coerceIn(0f, 1f)
        } catch (_: Throwable) {
            0.5f
        }
    }
}
