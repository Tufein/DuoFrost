package io.github.tufein.duofrost.tools

import android.content.Context
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.Locale

/**
 * Experiment for the version 3 screen readout: asks AYN's PServerBinder command
 * channel to run one `screencap` into a folder this app can read, then reports
 * who ran it, how long it took and the average colour. Started only by an
 * explicit tap; the screenshot is deleted straight after it is sampled.
 */
internal object ScreenReadoutProbe {
    private const val TAG = "ScreenReadoutProbe"
    private const val WAIT_MS = 4000L
    private val FILES = listOf("id.txt", "frame.raw", "err.txt", "exit.txt")

    fun run(context: Context): String {
        val binder = LedController.findBinder() ?: return "PServerBinder is not available on this device."
        val folders = listOfNotNull(
            context.getExternalFilesDir(null)?.let { File(it, "readout-probe") to "external files" },
            File(context.cacheDir, "readout-probe") to "app cache"
        )
        val report = StringBuilder()
        for ((folder, label) in folders) {
            report.append("[").append(label).append("] ")
            report.append(tryFolder(binder, folder)).append("\n\n")
        }
        return report.toString().trim()
    }

    private fun tryFolder(binder: IBinder, folder: File): String {
        folder.mkdirs()
        clean(folder)
        val dir = folder.absolutePath
        val command = "id > '$dir/id.txt' 2>&1; getenforce >> '$dir/id.txt' 2>&1; " +
            "screencap '$dir/frame.raw' 2> '$dir/err.txt'; echo \$? > '$dir/exit.txt'"
        val started = SystemClock.elapsedRealtime()
        try {
            if (!transact(binder, command)) return "transact failed"
            val marker = File(folder, "exit.txt")
            while (!(marker.exists() && marker.length() > 0) && SystemClock.elapsedRealtime() - started < WAIT_MS) {
                Thread.sleep(20)
            }
            val elapsed = SystemClock.elapsedRealtime() - started
            if (!marker.exists()) return "no result after ${WAIT_MS} ms (folder not writable for the service?)"
            val who = read(File(folder, "id.txt")).ifBlank { "unknown" }.replace('\n', ' ')
            val exit = read(marker).trim()
            val error = read(File(folder, "err.txt")).trim()
            val frame = File(folder, "frame.raw")
            val lines = StringBuilder("ran as: $who\nscreencap exit: $exit")
            if (error.isNotEmpty()) lines.append("\nerror: ").append(error.take(200))
            lines.append("\ncommand to file: $elapsed ms")
            if (!frame.exists() || !frame.canRead()) return lines.append("\nframe file missing or unreadable").toString()
            val readStart = SystemClock.elapsedRealtime()
            val summary = RawScreenFrame.summarize(frame.readBytes())
            val readMs = SystemClock.elapsedRealtime() - readStart
            if (summary == null) return lines.append("\nframe is not a readable 8-bit frame (${frame.length()} bytes)").toString()
            lines.append("\nframe: ${summary.width}x${summary.height}, format ${summary.format}, ${frame.length()} bytes")
            lines.append("\nread and sampled in $readMs ms")
            lines.append("\naverage ${hex(summary.average)}, left ${hex(summary.left)}, right ${hex(summary.right)}")
            return lines.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Probe failed", e)
            return "failed: ${e.javaClass.simpleName}"
        } finally {
            clean(folder)
        }
    }

    private fun transact(binder: IBinder, command: String): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeStringArray(arrayOf(command, "1"))
            binder.transact(0, data, reply, IBinder.FLAG_ONEWAY)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Probe transact failed", e)
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun clean(folder: File) = FILES.forEach { runCatching { File(folder, it).delete() } }

    private fun read(file: File): String = runCatching { file.readText() }.getOrDefault("")

    private fun hex(color: Int) = String.format(Locale.US, "#%06X", color and 0xFFFFFF)
}
