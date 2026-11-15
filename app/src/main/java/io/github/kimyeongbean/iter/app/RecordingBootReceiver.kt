package io.github.kimyeongbean.iter.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Starts the recording service after a boot and after an app update, so recording does not wait
 * for the app to be opened (#125). Android delivers neither before the app is first opened, nor
 * after a force stop until it is opened again.
 */
class RecordingBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action in STARTING_ACTIONS) RecordingService.start(context)
    }

    private companion object {
        val STARTING_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
