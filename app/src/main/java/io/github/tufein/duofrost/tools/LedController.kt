package io.github.tufein.duofrost.tools

import android.os.IBinder
import android.os.Parcel
import android.util.Log
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.roundToInt

class LedController {
    companion object {
        private const val TAG = "LedController"
    }

    private val pServerBinder: IBinder?
    private val lock = ReentrantLock()

    private var lastCommand: String? = null
    private var lastExecuteTime = 0L
    private val minExecuteInterval = 16L

    // Master brightness scale (0f..1f) multiplied into every RGB write. The LED
    // kernel ignores the 4th wire field, so "brightness" IS RGB magnitude —
    // scaling R/G/B here dims uniformly. Used for crossfades (see setMasterScale);
    // 1f in normal operation, so setLedColor is unaffected outside a fade.
    @Volatile private var masterScale: Float = 1f

    // Cap RGB magnitude before the independent crossfade scale. The fourth
    // hardware wire field cannot enforce this limit on the Thor.
    @Volatile private var outputBrightnessLimit = 255
    private val frameCache = LedFrameCache()

    init {
        pServerBinder = try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getDeclaredMethod("getService", String::class.java)
            getService.invoke(serviceManager, "PServerBinder") as? IBinder
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get PServerBinder", e)
            null
        }
    }

    fun setLedColor(
        red: Int,
        green: Int,
        blue: Int,
        brightness: Int = 255,
        leftTop: Boolean = true,
        leftBottom: Boolean = true,
        rightTop: Boolean = true,
        rightBottom: Boolean = true
    ) {
        val r = red.coerceIn(0, 255)
        val g = green.coerceIn(0, 255)
        val b = blue.coerceIn(0, 255)
        val br = brightness.coerceIn(0, 255)
        if (pServerBinder == null) return

        lock.withLock {
            val color = (r shl 16) or (g shl 8) or b
            frameCache.update(color, color, zoneMask(leftTop, leftBottom, rightTop, rightBottom))
            emit(r, g, b, br, leftTop, leftBottom, rightTop, rightBottom)
        }
    }

    /** Apply masterScale to (r,g,b) and write the selected zones. */
    private fun emit(
        r: Int, g: Int, b: Int, br: Int,
        leftTop: Boolean, leftBottom: Boolean, rightTop: Boolean, rightBottom: Boolean
    ) {
        val s = masterScale
        val color = BatterySaverBrightness.limitRgb((r shl 16) or (g shl 8) or b, outputBrightnessLimit)
        val sr = (((color ushr 16) and 255) * s).roundToInt().coerceIn(0, 255)
        val sg = (((color ushr 8) and 255) * s).roundToInt().coerceIn(0, 255)
        val sb = ((color and 255) * s).roundToInt().coerceIn(0, 255)

        val commandBuilder = StringBuilder(220)
        if (leftTop) {
            commandBuilder.append("echo 1-").append(sr).append(':').append(sg).append(':').append(sb).append(':').append(br)
                .append(" > /sys/class/sn3112l/led/brightness")
        }
        if (leftBottom) {
            if (commandBuilder.isNotEmpty()) commandBuilder.append(" && ")
            commandBuilder.append("echo 2-").append(sr).append(':').append(sg).append(':').append(sb).append(':').append(br)
                .append(" > /sys/class/sn3112l/led/brightness")
        }
        if (rightTop) {
            if (commandBuilder.isNotEmpty()) commandBuilder.append(" && ")
            commandBuilder.append("echo 1-").append(sr).append(':').append(sg).append(':').append(sb).append(':').append(br)
                .append(" > /sys/class/sn3112r/led/brightness")
        }
        if (rightBottom) {
            if (commandBuilder.isNotEmpty()) commandBuilder.append(" && ")
            commandBuilder.append("echo 2-").append(sr).append(':').append(sg).append(':').append(sb).append(':').append(br)
                .append(" > /sys/class/sn3112r/led/brightness")
        }

        if (commandBuilder.isNotEmpty()) {
            executeCommandDirect(commandBuilder.toString())
        }
    }

    /**
     * Write both sticks — left zones at the left colour, right zones at the right
     * colour — in a SINGLE transact, instead of the two setLedColor() takes when
     * the sticks differ. Halves the per-frame IPC and (since the writer process
     * runs a shell per command) the shell-forks on its little cores, which is the
     * residual stutter source under heavy load. Same masterScale + last-colour
     * bookkeeping as setLedColor so crossfades retain each zone's own color.
     */
    fun setLedColorDual(
        leftR: Int, leftG: Int, leftB: Int,
        rightR: Int, rightG: Int, rightB: Int,
        brightness: Int = 255,
        leftTop: Boolean = true,
        leftBottom: Boolean = true,
        rightTop: Boolean = true,
        rightBottom: Boolean = true
    ) {
        if (pServerBinder == null) return
        val lr = leftR.coerceIn(0, 255); val lg = leftG.coerceIn(0, 255); val lb = leftB.coerceIn(0, 255)
        val rr = rightR.coerceIn(0, 255); val rg = rightG.coerceIn(0, 255); val rb = rightB.coerceIn(0, 255)
        val br = brightness.coerceIn(0, 255)
        lock.withLock {
            frameCache.update(
                (lr shl 16) or (lg shl 8) or lb,
                (rr shl 16) or (rg shl 8) or rb,
                zoneMask(leftTop, leftBottom, rightTop, rightBottom)
            )
            emitDual(lr, lg, lb, rr, rg, rb, br, leftTop, leftBottom, rightTop, rightBottom)
        }
    }

    /** Build one &&-joined command covering all selected zones (left zones use the
     *  left colour, right zones the right) and write it in a single transact. */
    private fun emitDual(
        lr: Int, lg: Int, lb: Int, rr: Int, rg: Int, rb: Int, br: Int,
        leftTop: Boolean, leftBottom: Boolean, rightTop: Boolean, rightBottom: Boolean
    ) {
        val s = masterScale
        val left = BatterySaverBrightness.limitRgb((lr shl 16) or (lg shl 8) or lb, outputBrightnessLimit)
        val right = BatterySaverBrightness.limitRgb((rr shl 16) or (rg shl 8) or rb, outputBrightnessLimit)
        val slr = (((left ushr 16) and 255) * s).roundToInt().coerceIn(0, 255)
        val slg = (((left ushr 8) and 255) * s).roundToInt().coerceIn(0, 255)
        val slb = ((left and 255) * s).roundToInt().coerceIn(0, 255)
        val srr = (((right ushr 16) and 255) * s).roundToInt().coerceIn(0, 255)
        val srg = (((right ushr 8) and 255) * s).roundToInt().coerceIn(0, 255)
        val srb = ((right and 255) * s).roundToInt().coerceIn(0, 255)
        val cmd = StringBuilder(220)
        if (leftTop) cmd.append("echo 1-").append(slr).append(':').append(slg).append(':').append(slb).append(':').append(br)
            .append(" > /sys/class/sn3112l/led/brightness")
        if (leftBottom) { if (cmd.isNotEmpty()) cmd.append(" && "); cmd.append("echo 2-").append(slr).append(':').append(slg).append(':').append(slb).append(':').append(br)
            .append(" > /sys/class/sn3112l/led/brightness") }
        if (rightTop) { if (cmd.isNotEmpty()) cmd.append(" && "); cmd.append("echo 1-").append(srr).append(':').append(srg).append(':').append(srb).append(':').append(br)
            .append(" > /sys/class/sn3112r/led/brightness") }
        if (rightBottom) { if (cmd.isNotEmpty()) cmd.append(" && "); cmd.append("echo 2-").append(srr).append(':').append(srg).append(':').append(srb).append(':').append(br)
            .append(" > /sys/class/sn3112r/led/brightness") }
        if (cmd.isNotEmpty()) executeCommandDirect(cmd.toString())
    }

    /**
     * Set the master brightness scale (0f..1f) and immediately re-emit the last
     * colour at the new scale. Driving this from 1→0→1 around an animation swap
     * produces a dip-to-black crossfade that masks the hard cut, independent of
     * how fast the underlying animation renders.
     */
    fun setMasterScale(scale: Float) {
        lock.withLock {
            masterScale = scale.coerceIn(0f, 1f)
            emitCachedFrame()
        }
    }

    /** Apply a new limit immediately, preserving the animation and each zone. */
    fun setOutputBrightnessLimit(brightness: Int) {
        val limit = brightness.coerceIn(0, 255)
        lock.withLock {
            if (limit == outputBrightnessLimit) return
            outputBrightnessLimit = limit
            emitCachedFrame()
        }
    }

    private fun zoneMask(leftTop: Boolean, leftBottom: Boolean, rightTop: Boolean, rightBottom: Boolean): Int {
        return (if (leftTop) 1 else 0) or (if (leftBottom) 2 else 0) or
            (if (rightTop) 4 else 0) or (if (rightBottom) 8 else 0)
    }

    // Caller holds lock: redraw the last raw color independently for all four
    // zones, so a dual-color or partial-zone frame survives cap/fade changes.
    private fun emitCachedFrame() {
        if (pServerBinder == null || frameCache.zoneMask == 0) return
        val command = StringBuilder(256)
        val scale = masterScale
        for (zone in 0..3) {
            if ((frameCache.zoneMask and (1 shl zone)) == 0) continue
            val color = BatterySaverBrightness.limitRgb(frameCache.colorAt(zone), outputBrightnessLimit)
            val red = (((color ushr 16) and 255) * scale).roundToInt().coerceIn(0, 255)
            val green = (((color ushr 8) and 255) * scale).roundToInt().coerceIn(0, 255)
            val blue = ((color and 255) * scale).roundToInt().coerceIn(0, 255)
            if (command.isNotEmpty()) command.append(" && ")
            command.append("echo ").append(zone % 2 + 1).append('-')
                .append(red).append(':').append(green).append(':').append(blue).append(":255")
                .append(if (zone < 2) " > /sys/class/sn3112l/led/brightness" else " > /sys/class/sn3112r/led/brightness")
        }
        executeCommandDirect(command.toString())
    }

    /**
     * Drop the re-emit baseline to black (keeping zone selection). Called at the
     * crossfade midpoint so the fade-in spins up from black instead of briefly
     * re-showing the outgoing colour before the incoming animation's first frame.
     */
    fun resetFadeBaseline() {
        lock.withLock { frameCache.resetToBlack() }
    }

    fun setBrightness(brightness: Int) {
        val b = brightness.coerceIn(0, 255)
        val commands = listOf(
            "echo 1-0:0:0:$b > /sys/class/sn3112l/led/brightness",
            "echo 2-0:0:0:$b > /sys/class/sn3112l/led/brightness",
            "echo 1-0:0:0:$b > /sys/class/sn3112r/led/brightness",
            "echo 2-0:0:0:$b > /sys/class/sn3112r/led/brightness"
        )
        val command = commands.joinToString(" && ")
        executeCommandDirect(command)
    }

    private fun executeCommandDirect(command: String) {
        lock.withLock {
            val now = System.currentTimeMillis()

            if (command == lastCommand && now - lastExecuteTime < minExecuteInterval) {
                return
            }

            lastCommand = command
            lastExecuteTime = now

            pServerBinder?.let { binder ->
                val data = Parcel.obtain()
                val reply = Parcel.obtain()

                try {
                    data.writeStringArray(arrayOf(command, "1"))
                    binder.transact(0, data, reply, IBinder.FLAG_ONEWAY)
                } catch (e: Exception) {
                    Log.w(TAG, "LED transact failed", e)
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }
        }
    }

    fun shutdown() {
    }
}
