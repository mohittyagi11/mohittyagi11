package com.quietdose.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Alarms and geofences do not survive a reboot, so on BOOT_COMPLETED (and the
 * quick-boot / locked-boot variants) we re-arm the entire trigger engine. The
 * heavy lifting is [Reconciler.reArmAll]; the receiver only bridges the broadcast
 * to a coroutine via [goAsync].
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            ACTION_QUICKBOOT,
            ACTION_HTC_QUICKBOOT,
            TriggerContract.ACTION_BOOT_REARM -> Unit
            else -> return
        }

        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Reconciler.reArmAll(app)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        // OEM quick-boot variants some devices send instead of BOOT_COMPLETED.
        const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
        const val ACTION_HTC_QUICKBOOT = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
}
