package com.quietdose.ui.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.quietdose.brain.enrich.ProductEnricher
import com.quietdose.ui.MainActivity
import com.quietdose.ui.theme.DoseTheme
import com.quietdose.ui.theme.Ink

/**
 * Entry point for "share a product link to Dose". Appears in the Android share
 * sheet for links (and handles direct http(s) opens). Pulls the URL out of the
 * shared payload and hands it to [ShareConfirmScreen], which enriches it
 * on-device into a draft and routes it through the contextual analysis before
 * saving. Finishes itself when done or dismissed.
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val url = extractUrl(intent)
        setContent {
            DoseTheme {
                Box(Modifier.fillMaxSize().background(Ink)) {
                    ShareConfirmScreen(
                        url = url,
                        onClose = { finish() },
                        // After a successful add, bring Dose itself to the front (its own
                        // task) so the user lands in the app on their stack — not dropped
                        // back into Amazon. The app takes control.
                        onSaved = { openAppAndFinish() },
                    )
                }
            }
        }
    }

    /** Re-shared while still open (singleTask) — handle the new link in place. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun openAppAndFinish() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
            )
        }
        finish()
    }

    private fun extractUrl(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> ProductEnricher.extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
            Intent.ACTION_VIEW -> intent.dataString
            else -> ProductEnricher.extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT)) ?: intent.dataString
        }
    }
}
