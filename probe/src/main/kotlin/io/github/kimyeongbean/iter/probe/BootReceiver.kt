package io.github.kimyeongbean.iter.probe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Logs boot broadcasts and tries to start the service from there (the real question of #124). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        ProbeLog.write(context, "BOOT_BROADCAST ${intent.action}")
        ProbeService.start(context, from = "boot:${intent.action?.substringAfterLast('.')}")
    }
}
