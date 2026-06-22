package com.quietdose.ui.brain

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quietdose.ui.stack.androidxClickable
import com.quietdose.ui.theme.Accent
import com.quietdose.ui.theme.Ink
import com.quietdose.ui.theme.Surface1
import com.quietdose.ui.theme.TextHigh
import com.quietdose.ui.theme.TextMid

/**
 * A full-screen in-app browser for fetching gated model files. The user signs in
 * and accepts the licence here; when they tap a file download, the WebView's
 * [android.webkit.DownloadListener] hands us the resolved URL plus the session
 * cookie + user-agent, which we forward to [com.quietdose.brain.ModelManager] so
 * the app controls the fetch (progress, verification) instead of the system
 * download manager. Keeps everything visible and on-device-controlled.
 *
 * Uses the platform WebView — no extra dependency.
 */
@Composable
fun ModelBrowserDialog(
    startUrl: String,
    title: String,
    onClose: () -> Unit,
    onDownload: (url: String, cookie: String?, userAgent: String?) -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().background(Surface1).padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp).clip(CircleShape).androidxClickable(onClose),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextMid, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.size(8.dp))
                    Column {
                        Text("Get $title", style = MaterialTheme.typography.titleMedium, color = TextHigh)
                        Text(
                            "Sign in, accept the licence, then tap the .task file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMid,
                        )
                    }
                }

                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            val cookies = CookieManager.getInstance()
                            cookies.setAcceptCookie(true)
                            cookies.setAcceptThirdPartyCookies(this, true)
                            webViewClient = WebViewClient()
                            setDownloadListener { downloadUrl, userAgent, _, _, _ ->
                                val cookie = runCatching { CookieManager.getInstance().getCookie(downloadUrl) }.getOrNull()
                                onDownload(downloadUrl, cookie, userAgent)
                            }
                            loadUrl(startUrl)
                        }
                    },
                )
            }
        }
    }
}
