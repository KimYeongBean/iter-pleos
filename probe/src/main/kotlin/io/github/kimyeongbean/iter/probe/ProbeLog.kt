package io.github.kimyeongbean.iter.probe

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Appends one line per event to files/probe.log and logcat (tag IterProbe).
 * Every line carries the wall time, the pid (a new pid means a new process) and the deep sleep
 * total so far (elapsedRealtime - uptimeMillis grows only while the device is suspended).
 */
object ProbeLog {
    private const val TAG = "IterProbe"
    private const val MAX_BYTES = 2_000_000L
    private val format = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    fun file(context: Context) = File(context.filesDir, "probe.log")

    fun deepSleepSeconds() = (SystemClock.elapsedRealtime() - SystemClock.uptimeMillis()) / 1000

    @Synchronized
    fun write(
        context: Context,
        event: String,
    ) {
        val line = "${format.format(Date())} pid=${Process.myPid()} sleep=${deepSleepSeconds()}s $event"
        Log.i(TAG, line)
        runCatching {
            val file = file(context)
            if (file.length() > MAX_BYTES) file.writeText("(log cut at ${MAX_BYTES / 1000} KB)\n")
            file.appendText(line + "\n")
        }
    }

    fun tail(
        context: Context,
        lines: Int = 300,
    ): String = runCatching { file(context).readLines().takeLast(lines).joinToString("\n") }.getOrDefault("(no log yet)")

    @Synchronized
    fun clear(context: Context) {
        file(context).delete()
    }
}
