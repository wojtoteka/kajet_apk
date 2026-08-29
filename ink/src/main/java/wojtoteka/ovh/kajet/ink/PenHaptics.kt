package wojtoteka.ovh.kajet.ink

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import wojtoteka.ovh.kajet.core.model.InkTool
import java.util.concurrent.ConcurrentHashMap

data class PenProfile(val brush: Int, val strength: Float)

/** Haptyka rysika wyłącznie przez publiczne Android API. */
object PenHaptics {
    private const val TAG = "Kajet"
    private const val MOVE_GAP_MS = 42L
    private const val PULSE_MS = 7L

    const val BALL_PEN = 32
    const val PENCIL = 33
    const val CHISEL_MARKER = 34
    const val ERASER = 35
    const val LENOVO_PEN = 36

    val WRITING = PenProfile(BALL_PEN, 1f)

    @Volatile private var depth = 0
    @Volatile private var profile: PenProfile = WRITING
    @Volatile private var lastPulseAt = 0L
    private val diagnosed = ConcurrentHashMap.newKeySet<Int>()
    private val attemptLogged = ConcurrentHashMap.newKeySet<Int>()

    fun profileFor(tool: EditorTool, penKind: InkTool): PenProfile = when {
        tool.isEraser -> PenProfile(ERASER, 1.2f)
        tool == EditorTool.HIGHLIGHTER -> PenProfile(CHISEL_MARKER, 0.7f)
        !tool.writes -> PenProfile(BALL_PEN, 0.6f)
        penKind == InkTool.PENCIL -> PenProfile(PENCIL, 1f)
        penKind == InkTool.HIGHLIGHTER -> PenProfile(CHISEL_MARKER, 0.7f)
        else -> WRITING
    }

    fun enter(context: Context) { depth += 1 }
    fun leave(context: Context) { depth = (depth - 1).coerceAtLeast(0) }
    fun use(context: Context, wanted: PenProfile) { profile = wanted }

    // Punkty zgodności dla powierzchni Compose i dotychczasowego cyklu życia.
    // Publiczny vibrator urządzenia wejściowego nie wymaga rejestracji.
    fun register(context: Context) = Unit
    fun quiet(context: Context) = Unit
    fun refresh(context: Context) = Unit
    fun wake(context: Context) = Unit
    fun surfaceHover(context: Context, over: Boolean) = Unit
    fun settle(context: Context) = Unit

    /** Loguje urządzenie przy pierwszym zdarzeniu, bez uruchamiania wibracji. */
    fun diagnose(event: MotionEvent) {
        val device = event.device ?: return
        diagnoseOnce(device, attempted = false, vibratorIds = vibratorIds(device))
    }

    /** Próba krótkiej haptyki z vibratora należącego do rysika. */
    fun vibrate(view: View, event: MotionEvent, pointerIndex: Int, moving: Boolean) {
        if (depth <= 0 || pointerIndex !in 0 until event.pointerCount) return
        val tool = event.getToolType(pointerIndex)
        if (tool != MotionEvent.TOOL_TYPE_STYLUS && tool != MotionEvent.TOOL_TYPE_ERASER) return

        val now = SystemClock.uptimeMillis()
        if (moving && now - lastPulseAt < MOVE_GAP_MS) return
        lastPulseAt = now

        val device = event.device
        val ids = if (device != null) vibratorIds(device) else intArrayOf()
        val stylusSource = device != null &&
            (device.sources and InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS
        var attempted = false
        var delivered = false

        if (device != null && stylusSource && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (id in ids) {
                val vibrator = runCatching { device.vibratorManager.getVibrator(id) }.getOrNull()
                    ?: continue
                if (!runCatching { vibrator.hasVibrator() }.getOrDefault(false)) continue
                attempted = true
                val amplitude = (70f * profile.strength).toInt().coerceIn(25, 120)
                delivered = runCatching {
                    vibrator.vibrate(VibrationEffect.createOneShot(PULSE_MS, amplitude))
                    true
                }.getOrDefault(false)
                if (delivered) break
            }
        }

        if (device != null) {
            diagnoseOnce(device, attempted, ids)
            if (attemptLogged.add(device.id)) {
                Log.i(
                    TAG,
                    "Rysik: deviceId=${device.id}, hapticAttempted=$attempted, " +
                        "delivered=$delivered, fallback=${!delivered && !moving}",
                )
            }
        }

        // Fallback tylko na początek kontaktu. Przy ruchu wibrowałby tabletem
        // dziesiątki razy na sekundę.
        if (!delivered && !moving) {
            runCatching { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
        }
    }

    private fun vibratorIds(device: InputDevice): IntArray =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { device.vibratorManager.vibratorIds }.getOrDefault(intArrayOf())
        } else {
            intArrayOf()
        }

    private fun diagnoseOnce(device: InputDevice, attempted: Boolean, vibratorIds: IntArray) {
        if (!diagnosed.add(device.id)) return
        val stylus = (device.sources and InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS
        Log.i(
            TAG,
            "Rysik: name=${device.name}, deviceId=${device.id}, " +
                "sources=0x${device.sources.toString(16)}, SOURCE_STYLUS=$stylus, " +
                "vibratorIds=${vibratorIds.contentToString()}, hapticAttempted=$attempted",
        )
    }
}
