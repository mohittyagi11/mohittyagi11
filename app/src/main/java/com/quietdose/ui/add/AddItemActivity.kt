package com.quietdose.ui.add

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietdose.brain.analysis.ProductSignals
import com.quietdose.brain.enrich.ProductEnricher
import com.quietdose.brain.skills.DraftItem
import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.entity.ItemEntity
import com.quietdose.di.ServiceLocator
import com.quietdose.ui.MainActivity
import com.quietdose.ui.analysis.AnalysisScreen
import com.quietdose.ui.scan.ScanScreen
import com.quietdose.ui.stack.FilledButton
import com.quietdose.ui.theme.Accent
import com.quietdose.ui.theme.DoseTheme
import com.quietdose.ui.theme.Ink
import com.quietdose.ui.theme.TextHigh
import com.quietdose.ui.theme.TextMid
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The ONE add-item Activity — every channel (typed, link, and soon scan) runs the
 * same flow here: resolve → unified [AddConfirm] → [AnalysisScreen] → save. Being a
 * real full-screen Activity (own task, no bottom nav) means the action button is
 * never hidden, and there's no overlay/foreground-background drama. Also handles the
 * external "share a link to Dose" intent.
 */
class AddItemActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra(EXTRA_MODE)
            ?: if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_VIEW) MODE_LINK else MODE_TYPED
        val url = intent.getStringExtra(EXTRA_URL) ?: sharedUrl(intent)
        val groupId = intent.getLongExtra(EXTRA_GROUP, -1L)

        setContent {
            DoseTheme {
                Box(Modifier.fillMaxSize().background(Ink).statusBarsPadding()) {
                    AddFlow(mode = mode, url = url, groupId = groupId, onDone = { finishToApp() })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); recreate()
    }

    /** Land the user in Dose on their stack, not back in the sharing app. */
    private fun finishToApp() {
        if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_VIEW) {
            runCatching {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            }
        }
        finish()
    }

    private fun sharedUrl(intent: Intent): String? = when (intent.action) {
        Intent.ACTION_SEND -> ProductEnricher.extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
        Intent.ACTION_VIEW -> intent.dataString
        else -> null
    }

    companion object {
        const val MODE_TYPED = "typed"
        const val MODE_LINK = "link"
        const val MODE_SCAN = "scan"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_URL = "url"
        private const val EXTRA_GROUP = "group"

        fun typed(context: Context, groupId: Long): Intent =
            Intent(context, AddItemActivity::class.java).putExtra(EXTRA_MODE, MODE_TYPED).putExtra(EXTRA_GROUP, groupId)

        fun link(context: Context, url: String): Intent =
            Intent(context, AddItemActivity::class.java).putExtra(EXTRA_MODE, MODE_LINK).putExtra(EXTRA_URL, url)

        fun scan(context: Context): Intent =
            Intent(context, AddItemActivity::class.java).putExtra(EXTRA_MODE, MODE_SCAN)
    }
}

private sealed interface AddPhase {
    data object Resolving : AddPhase
    data object Capturing : AddPhase
    data class Failed(val reason: String) : AddPhase
    data class Confirm(
        val draft: DraftItem,
        val provenance: AddProvenance,
        val product: ProductSignals?,
        val purchaseUrl: String?,
        val groupId: Long?,
    ) : AddPhase
    data class Analyze(val item: ItemEntity, val product: ProductSignals?) : AddPhase
}

@Composable
private fun AddFlow(mode: String, url: String?, groupId: Long, onDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { ServiceLocator.repository(context) }
    val groups by remember { repo.observeGroups() }
        .collectAsStateWithLifecycle(initialValue = emptyList<GroupEntity>())
    val scope = rememberCoroutineScope()

    var phase by remember {
        mutableStateOf<AddPhase>(
            when (mode) {
                AddItemActivity.MODE_LINK -> AddPhase.Resolving
                AddItemActivity.MODE_SCAN -> AddPhase.Capturing
                else -> AddPhase.Confirm(DraftItem(name = ""), AddProvenance.Typed, null, null, groupId.takeIf { it > 0 })
            },
        )
    }

    if (mode == AddItemActivity.MODE_LINK) {
        LaunchedEffect(url) {
            if (url.isNullOrBlank()) {
                phase = AddPhase.Failed("No link found in what you shared.")
                return@LaunchedEffect
            }
            val res = ProductEnricher.enrich(context, url)
            phase = if (res.draft != null) {
                AddPhase.Confirm(res.draft, AddProvenance.Linked(url, res.sourceTitle), res.signals, url, null)
            } else {
                AddPhase.Failed(res.note ?: "Couldn't read that link.")
            }
        }
    }

    when (val p = phase) {
        AddPhase.Capturing -> ScanScreen(
            onScanned = { draft, ocr, shots ->
                phase = AddPhase.Confirm(
                    draft,
                    AddProvenance.Scanned(shots, ocr),
                    ProductSignals(ingredientsText = ocr.ifBlank { null }),
                    null,
                    groupId.takeIf { it > 0 },
                )
            },
            onClose = onDone,
        )
        AddPhase.Resolving -> Center {
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(16.dp))
            Text("Reading the link…", style = MaterialTheme.typography.titleMedium, color = TextHigh)
            Text("On-device. Pulling out the product.", style = MaterialTheme.typography.bodyMedium, color = TextMid)
        }
        is AddPhase.Failed -> Center {
            Text("Nothing to add", style = MaterialTheme.typography.titleLarge, color = TextHigh)
            Spacer(Modifier.height(8.dp))
            Text(p.reason, style = MaterialTheme.typography.bodyMedium, color = TextMid)
            Spacer(Modifier.height(20.dp))
            FilledButton(label = "Close", accent = Accent, onClick = onDone)
        }
        is AddPhase.Confirm -> AddConfirm(
            draft = p.draft,
            provenance = p.provenance,
            groups = groups,
            purchaseUrl = p.purchaseUrl,
            onClose = onDone,
            onConfirm = { item -> phase = AddPhase.Analyze(item, p.product) },
            initialGroupId = p.groupId,
        )
        is AddPhase.Analyze -> AnalysisScreen(
            item = p.item,
            onAdd = { configured ->
                scope.launch {
                    withContext(NonCancellable) { repo.upsertItem(configured) }
                    onDone()
                }
            },
            onDismiss = onDone,
            product = p.product,
        )
    }
}

@Composable
private fun Center(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
        content = content,
    )
}
