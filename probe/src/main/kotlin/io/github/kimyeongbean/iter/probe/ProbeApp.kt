package io.github.kimyeongbean.iter.probe

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/** Logs every process start and the system power broadcasts the process can hear. */
class ProbeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ProbeLog.write(this, "PROCESS_START sdk=${Build.VERSION.SDK_INT} release=${Build.VERSION.RELEASE} model=${Build.MODEL}")
        val power =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    ProbeLog.write(context, "BROADCAST ${intent.action}")
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SHUTDOWN)
                addAction(Intent.ACTION_USER_PRESENT)
            }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(power, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(power, filter)
        }
    }
}
