package com.quietdose.ui.add

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.quietdose.brain.enrich.ProductEnricher

/**
 * A tiny, invisible trampoline for EXTERNAL shares ("Add to Dose" from Amazon etc.).
 *
 * It runs in its OWN task (empty taskAffinity, no UI), pulls the URL out of the shared
 * text, then launches the real [AddItemActivity] into Dose's own task with NEW_TASK and
 * finishes immediately. That guarantees the add flow comes to the FRONT as Dose — instead
 * of opening buried inside the sharing app's window (which made the flow look like it
 * "exited back to Amazon" and never completed). It leaves no trace in recents.
 */
class ShareReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = when (intent?.action) {
            Intent.ACTION_SEND -> ProductEnricher.extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        val next = if (!url.isNullOrBlank()) {
            AddItemActivity.link(this, url, fromShare = true)
        } else {
            // Nothing usable in the share — still open the add flow so the user can type/paste.
            AddItemActivity.typed(this, -1L).putExtra(AddItemActivity.EXTRA_FROM_SHARE, true)
        }
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { startActivity(next) }
        finish()
    }
}
