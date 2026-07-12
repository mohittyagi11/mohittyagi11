package com.azadishashn.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azadishashn.app.game.LiveBar
import com.azadishashn.app.ui.theme.IdeologyTheme
import com.azadishashn.app.ui.theme.NumberStyle
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * TONIGHT'S BAR — the live number each ideology demands of the current
 * answerer to KEEP its card, computed with the exact math the verdict will
 * use, on screen through EVERY phase of the turn (question, answer, rebuttal,
 * closer, mid-crisis).
 *
 * The numbers explain themselves and demand to be noticed:
 *  - a tile whose number is news (first look this turn, a moved bar, a crisis
 *    landing on it) PULSES until tapped — tapping acknowledges it;
 *  - the same tap opens THE WHY: the bar's full math in sentences (anchor,
 *    hoarding, mood, meters, crisis stakes);
 *  - THE TABLE fold shows what tonight's question would demand of every
 *    player, so the whole room argues with open books.
 */
/** One player's row in THE TABLE fold: identity, press credits, four bars. */
data class TableEntry(
    val id: Int,
    val name: String,
    val credits: Int,
    val bars: List<LiveBar>,
)

@Composable
fun LiveBarRow(
    bars: List<LiveBar>,
    forName: String,
    playerId: Int,
    crisisTarget: String?,
    acked: Map<String, Int>,
    onAck: (String, Int) -> Unit,
    table: List<TableEntry>,
    modifier: Modifier = Modifier,
) {
    if (bars.isEmpty()) return
    var expanded by remember { mutableStateOf<String?>(null) }
    var showTable by remember { mutableStateOf(false) }
    val pulse by rememberInfiniteTransition(label = "barPulse").animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "barPulseAlpha",
    )

    fun keyOf(b: LiveBar) = "$playerId:${b.ideology}"
    fun signatureOf(b: LiveBar) = b.required * 10 + (if (b.ideology == crisisTarget) 1 else 0)

    Column(modifier.fillMaxWidth().animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "TONIGHT'S BAR — ${forName.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (showTable) "THE TABLE ▾" else "THE TABLE ▸",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { showTable = !showTable }.padding(4.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            bars.forEach { b ->
                val brand = IdeologyTheme.of(b.ideology).brand
                val key = keyOf(b)
                val sig = signatureOf(b)
                val unseen = acked[key] != sig
                val previous = acked[key]?.div(10)?.takeIf { it != b.required }
                val underFire = b.ideology == crisisTarget
                Column(
                    Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(IdeologyTheme.container(b.ideology))
                        .border(
                            width = if (unseen) 1.5.dp else 0.5.dp,
                            color = if (unseen) brand.copy(alpha = pulse)
                            else brand.copy(alpha = 0.25f),
                            shape = MaterialTheme.shapes.medium,
                        )
                        .clickable {
                            onAck(key, sig)
                            expanded = if (expanded == b.ideology) null else b.ideology
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IdeologyDot(b.ideology, size = 8.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            b.ideology,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedCounter(value = b.required, style = NumberStyle, color = brand)
                        Spacer(Modifier.width(4.dp))
                        val marks = buildList {
                            if (previous != null) add("was $previous")
                            if (b.moodAdj < 0) add("▼ mood")
                            if (b.moodAdj > 0) add("▲ mood")
                            if (b.meterAdj > 0) add("⚡+1")
                            if (b.meterAdj < 0) add("☼−1")
                        }
                        if (marks.isNotEmpty()) {
                            Text(
                                marks.joinToString(" "),
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    b.meterAdj > 0 || b.moodAdj < 0 -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.error
                                },
                                maxLines = 1,
                            )
                        }
                    }
                    Text(
                        if (underFire) "⚡ TARGET" else "hold ${b.held}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (underFire) FontWeight.Bold else null,
                        color = if (underFire) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // THE WHY — the tapped line's full math, in sentences.
        bars.firstOrNull { it.ideology == expanded }?.let { b ->
            Spacer(Modifier.height(4.dp))
            Text(
                whyText(b, forName, b.ideology == crisisTarget),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(IdeologyTheme.container(b.ideology))
                    .padding(8.dp),
            )
        }

        // THE TABLE — what tonight's question would demand of every player.
        if (showTable) {
            Spacer(Modifier.height(4.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(8.dp),
            ) {
                table.forEach { entry ->
                    val answering = entry.id == playerId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Avatar(entry.name, seed = entry.id, size = 18.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            entry.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (answering) FontWeight.Bold else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "📰${entry.credits}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (entry.credits > 0) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        entry.bars.forEach { b ->
                            IdeologyDot(b.ideology, size = 7.dp)
                            Spacer(Modifier.width(2.dp))
                            Text(
                                "${b.required}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = IdeologyTheme.of(b.ideology).brand,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
                Text(
                    "What each line would demand of them tonight (hoarders pay more) — and " +
                        "📰 press credits: spent to cross-examine, earned by feats.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // First-information caption only: once the table has acknowledged any
        // tile, the row earns its keep without a permanent footnote.
        if (acked.isEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                "The strength each line needs tonight to KEEP its card. A pulsing tile is " +
                    "news you haven't seen — tap it for the WHY and it settles.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The bar's arithmetic told as sentences — anchor, hoarding, mood, meters, crisis. */
private fun whyText(b: LiveBar, forName: String, underFire: Boolean): String = buildString {
    append("${b.ideology.uppercase()} demands ${b.required} of $forName tonight. ")
    append("The table's recent verdicts set the anchor at ${fmt(b.anchor)}")
    val hoard = b.held - b.tableAvg
    when {
        hoard > 0.05 -> append("; holding ${b.held} — ${fmt(hoard)} above the table average — raises it")
        hoard < -0.05 -> append("; holding ${b.held} — ${fmt(-hoard)} below the table average — lowers it")
        else -> append("; holding ${b.held}, level with the table, moves it nowhere")
    }
    when {
        b.moodAdj > 0 -> append("; and the question's mood SUSPECTS this line (+1)")
        b.moodAdj < 0 -> append("; and the question's mood FAVOURS this line (−1)")
    }
    append(". Clear ${b.required} and the card stays — fall short and it is diverted to the second line.")
    if (b.meterAdj > 0) append(" Its home meter is in CRISIS, so arguing it lands at +1 strength.")
    if (b.meterAdj < 0) append(" Its home meter basks in a golden age, so arguing it lands at −1 strength.")
    if (underFire) {
        append(
            " ⚡ THE CRISIS TARGETS THIS LINE: hold it AND clear the bar for +1 resource and +2 poll — " +
                "argue it and fumble, and its meter drops 3 with the poll.",
        )
    }
}

private fun fmt(d: Double): String =
    if (abs(d - d.roundToInt()) < 0.05) "${d.roundToInt()}" else "%.1f".format(d)
