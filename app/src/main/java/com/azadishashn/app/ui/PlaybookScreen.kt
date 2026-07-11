package com.azadishashn.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azadishashn.app.data.Lesson
import com.azadishashn.app.data.LessonBranch
import com.azadishashn.app.data.Lessons
import com.azadishashn.app.game.GameViewModel
import com.azadishashn.app.model.CausalStep
import com.azadishashn.app.ui.components.Avatar
import com.azadishashn.app.ui.components.AzadiScaffold
import com.azadishashn.app.ui.components.IdeologyBadge
import com.azadishashn.app.ui.components.IdeologyDistributionBar
import com.azadishashn.app.ui.components.NationMeter
import com.azadishashn.app.ui.components.StatTile
import com.azadishashn.app.ui.components.StrengthMeter
import com.azadishashn.app.ui.components.causal.BlueprintSurface
import com.azadishashn.app.ui.components.causal.ExhibitHeader
import com.azadishashn.app.ui.components.causal.NodeCard
import com.azadishashn.app.ui.components.poster.PosterHero
import com.azadishashn.app.ui.theme.Dim
import com.azadishashn.app.ui.theme.Gold
import com.azadishashn.app.ui.theme.IdeologyTheme
import com.azadishashn.app.ui.theme.LaserAmber
import com.azadishashn.app.ui.theme.LaserBlue
import com.azadishashn.app.ui.theme.LaserGreen
import com.azadishashn.app.ui.theme.LaserRed
import com.azadishashn.app.ui.theme.OverlineStyle

/** Each rule strip's accent colour — the comic's ink per chapter. */
private fun accentFor(id: String): Color = when (id) {
    Lessons.BAR -> Gold
    Lessons.MOOD -> LaserBlue
    Lessons.WHIP -> IdeologyTheme.of("Supremo").brand
    Lessons.TRIPWIRE_ARM -> LaserAmber
    Lessons.TRIPWIRE_FIRE -> LaserRed
    Lessons.MATCHPOINT -> LaserAmber
    Lessons.BLOCS -> LaserGreen
    Lessons.DEFECTION -> LaserRed
    Lessons.NATION -> IdeologyTheme.of("Capitalist").brand
    Lessons.LADDER -> Gold
    Lessons.MILESTONE -> Gold
    Lessons.GRANTS -> LaserGreen
    Lessons.SCANDAL -> LaserRed
    else -> LaserBlue
}

/** The four leads, introduced in sentences: voice, pay, home turf. */
private val CAST: List<Pair<String, String>> = listOf(
    "Capitalist" to "The CAPITALIST speaks for markets and money. Every Capitalist " +
        "verdict pays in FUNDS — and the ECONOMY meter is Capitalist home turf.",
    "Supremo" to "The SUPREMO speaks for order and force. Every Supremo verdict " +
        "pays in CLOUT — and STABILITY is Supremo home turf.",
    "Showstopper" to "The SHOWSTOPPER speaks for the street and the spectacle. " +
        "Every Showstopper verdict pays in MEDIA — and LIBERTY is Showstopper home turf.",
    "Idealist" to "The IDEALIST speaks for principle and institutions. Every " +
        "Idealist verdict pays in TRUST — and the TRUST meter is Idealist home turf.",
)

/**
 * The Playbook — the app's house rules as an illustrated comic: a cast of
 * characters, the loop that turns a turn into power, then one drawn-out strip
 * per rule, each with a live demo built from the game's real components. The
 * same rules also introduce themselves in play via one-time coach cards.
 */
@Composable
fun PlaybookScreen(vm: GameViewModel) {
    AzadiScaffold(
        title = "The Playbook",
        subtitle = "House rules, drawn out",
        onBack = vm::closePlaybook,
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dim.screenH)
                .padding(bottom = Dim.sectionGap),
        ) {
            PosterHero(
                title = "The Playbook",
                kicker = "HOW THIS TABLE PLAYS",
                subtitle = "Cards are the score. Everything else is how you take them.",
            )
            Spacer(Modifier.height(Dim.sectionGap))

            // -- The cast --------------------------------------------------
            Text("CAST OF CHARACTERS", style = OverlineStyle, color = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(Dim.tight))
            CAST.forEach { (ideology, line) ->
                BlueprintSurface(
                    accent = IdeologyTheme.of(ideology).brand,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    IdeologyBadge(ideology)
                    Spacer(Modifier.height(6.dp))
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
            BlueprintSurface(accent = Gold, modifier = Modifier.padding(bottom = 8.dp)) {
                ExhibitHeader(kicker = "THE STAGE", title = "The table itself", accent = Gold)
                Spacer(Modifier.height(6.dp))
                KeywordLine(
                    "THE BAR",
                    "The number an argument must hit for its card to stay on the " +
                        "line you argued. It sits on the Round screen, live, for all " +
                        "four lines — nobody argues blind.",
                )
                KeywordLine(
                    "THE DOSSIER",
                    "The public record: every stand every player has taken this " +
                        "game. The press reads it, consistency is judged against " +
                        "it, and scandals crawl out of it.",
                )
                KeywordLine(
                    "THE SNAP POLL",
                    "Your approval, 0–100. Verdicts, the whip, crises and scandals " +
                        "all move it. Cross 65 and the mandate pays; sink to 35 and " +
                        "the donors flee.",
                )
                KeywordLine(
                    "THE NATION",
                    "Four living meters the whole table pushes around — crisis " +
                        "under 25, golden age over 75, and rot for every front the " +
                        "round ignored.",
                )
            }

            // -- The loop --------------------------------------------------
            Spacer(Modifier.height(Dim.itemGap))
            BlueprintSurface(accent = Gold, modifier = Modifier.padding(bottom = 8.dp)) {
                ExhibitHeader(kicker = "EXHIBIT · THE LOOP", title = "How a turn becomes power", accent = Gold)
                Spacer(Modifier.height(8.dp))
                NodeCard(1, CausalStep("You argue the dilemma, in your own words"), Gold)
                ChainLink("the judge scores it 1–10")
                NodeCard(2, CausalStep("THE BAR decides whether the card stays on your line"), Gold)
                ChainLink("clear it and the stack grows")
                NodeCard(3, CausalStep("Stacks climb THE POWER LADDER — 2/4/6 cards → L1/L2/L3 powers"), Gold)
                ChainLink("meanwhile, on the side…")
                NodeCard(4, CausalStep("Blocs, polls, the whip and the nation pay resources in — and out"), Gold)
            }

            // -- One turn, played out (the flow diagram) --------------------
            Spacer(Modifier.height(Dim.itemGap))
            TurnFlowStrip()

            // -- The rules, drawn out --------------------------------------
            Spacer(Modifier.height(Dim.itemGap))
            Text("THE RULES, DRAWN OUT", style = OverlineStyle, color = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(Dim.tight))
            Lessons.ALL.forEachIndexed { i, lesson -> LessonStrip(i + 1, lesson) }

            Spacer(Modifier.height(Dim.sectionGap))
            var replayed by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = {
                    vm.resetLessons()
                    replayed = true
                },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (replayed) "Coach cards re-armed ✓" else "Replay the coach cards") }
            Text(
                "Each rule will introduce itself again, the first time it fires in play.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** The `↓ caption` beat between two panels of the loop. */
@Composable
private fun ChainLink(text: String) {
    Text(
        "↓  $text",
        style = MaterialTheme.typography.labelSmall,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
    )
}

/** A bold term followed by its full-sentence explanation — the glossary voice. */
@Composable
private fun KeywordLine(term: String, sentence: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(term, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Gold)
        Text(
            sentence,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One rule as a comic strip: caption panel, live demo, branch panels. */
@Composable
private fun LessonStrip(number: Int, lesson: Lesson) {
    val accent = accentFor(lesson.id)
    BlueprintSurface(accent = accent, modifier = Modifier.padding(bottom = 10.dp)) {
        ExhibitHeader(kicker = "RULE ${"%02d".format(number)}", title = lesson.title, accent = accent)
        Spacer(Modifier.height(8.dp))
        Text(lesson.scenario, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
        LessonDemo(lesson.id)
        Spacer(Modifier.height(8.dp))
        lesson.branches.forEachIndexed { i, b ->
            BranchPanel(i + 1, b, accent)
            Spacer(Modifier.height(6.dp))
        }
        if (lesson.why.isNotBlank()) {
            Text(
                "WHY IT'S HERE — ${lesson.why}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One outcome branch as a numbered comic panel, inked by its polarity. */
@Composable
private fun BranchPanel(index: Int, branch: LessonBranch, accent: Color) {
    val tint = when (branch.polarity) {
        "gain" -> LaserGreen
        "cost" -> MaterialTheme.colorScheme.error
        "mixed" -> LaserAmber
        else -> accent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(tint.copy(alpha = 0.07f))
            .border(1.dp, tint.copy(alpha = 0.35f), MaterialTheme.shapes.medium)
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(tint.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$index", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tint)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(branch.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                branch.outcome,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The bespoke visual demo panel for a rule — built from the game's REAL components. */
@Composable
private fun LessonDemo(id: String) {
    when (id) {
        Lessons.BAR -> Column(Modifier.padding(top = 10.dp)) {
            StrengthMeter(strength = 8, required = 6, ideology = "Capitalist")
            DemoCaption("Strength 8 clears a bar of 6 — the card STAYS on Capitalist.")
            Spacer(Modifier.height(10.dp))
            StrengthMeter(strength = 5, required = 7, ideology = "Supremo")
            DemoCaption("Strength 5 misses a bar of 7 — the card DIVERTS to the secondary.")
        }
        Lessons.MOOD -> Row(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatTile("Ride the wind", "bar 6 → 5", IdeologyTheme.container("Capitalist"), Modifier.weight(1f))
            StatTile("Fight it", "bar 6 → 7", IdeologyTheme.container("Supremo"), Modifier.weight(1f))
            StatTile("Elsewhere", "bar 6", MaterialTheme.colorScheme.surfaceVariant, Modifier.weight(1f))
        }
        Lessons.WHIP -> Row(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatTile("Obey", "+3 poll · +1 res", IdeologyTheme.container("Capitalist"), Modifier.weight(1f))
            StatTile("Rebel 7+", "+5 poll", IdeologyTheme.container("Idealist"), Modifier.weight(1f))
            StatTile("Rebel <7", "−4 poll", IdeologyTheme.container("Supremo"), Modifier.weight(1f))
        }
        Lessons.TRIPWIRE_FIRE -> Column(Modifier.padding(top = 10.dp)) {
            Text(
                "⚡ BREAKING: MARKETS CRASH AS THE HOUSE DEBATES — TARGETS CAPITALIST",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Weathered", "+1 res · +2 poll", IdeologyTheme.container("Capitalist"), Modifier.weight(1f))
                StatTile("Claimed", "−3 meter · −3 poll", IdeologyTheme.container("Supremo"), Modifier.weight(1f))
                StatTile("Swerved", "on the record", MaterialTheme.colorScheme.surfaceVariant, Modifier.weight(1f))
            }
        }
        Lessons.MATCHPOINT -> Text(
            "⚡ MATCH POINT: Asha is 1 card from L2 Supremo",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = IdeologyTheme.of("Supremo").brand,
            modifier = Modifier.padding(top = 10.dp),
        )
        Lessons.NATION -> Column(Modifier.padding(top = 10.dp)) {
            NationMeter("Economy", 18, "Capitalist")
            DemoCaption("Under 25 — CRISIS. Capitalist arguments land at +1 strength.")
            Spacer(Modifier.height(6.dp))
            NationMeter("Trust", 80, "Idealist")
            DemoCaption("Over 75 — GOLDEN AGE. Idealist arguments sag −1.")
        }
        Lessons.LADDER -> Column(Modifier.padding(top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IdeologyBadge("Supremo", count = 2)
                Text("  →  L1 · 1 power", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IdeologyBadge("Capitalist", count = 4)
                Text("  →  L2 · +2 power", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IdeologyBadge("Idealist", count = 6)
                Text("  →  L3 · +3 power", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            IdeologyDistributionBar(mapOf("Capitalist" to 4, "Supremo" to 2, "Idealist" to 1))
            DemoCaption("Seven cards stacked 4·2·1 = 4 power. The same seven spread 3·2·2 = 3. Depth wins.")
        }
        Lessons.GRANTS -> Column(Modifier.padding(top = 10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Whip patronage", "+1 · Clout", IdeologyTheme.container("Supremo"), Modifier.weight(1f))
                StatTile("Bloc gift", "+1 · Funds", IdeologyTheme.container("Capitalist"), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Crisis bonus", "+1 · Media", IdeologyTheme.container("Showstopper"), Modifier.weight(1f))
                StatTile("The mandate", "+1 / −1", IdeologyTheme.container("Idealist"), Modifier.weight(1f))
            }
        }
        else -> Unit
    }
}

/**
 * "ONE TURN, PLAYED OUT" — the gameplay flow diagram: a worked example turn
 * (Ravi asks, Asha answers) drawn as numbered nodes with fork panels at every
 * choice point. Every number here is consistent with the real rules.
 */
@Composable
private fun TurnFlowStrip() {
    BlueprintSurface(accent = LaserBlue, modifier = Modifier.padding(bottom = 8.dp)) {
        ExhibitHeader(kicker = "EXHIBIT · THE FLOW", title = "One turn, played out", accent = LaserBlue)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar("Ravi", seed = 1, size = 24.dp)
            Spacer(Modifier.width(4.dp))
            Text("Ravi asks", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text("   ·   ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Avatar("Asha", seed = 2, size = 24.dp)
            Spacer(Modifier.width(4.dp))
            Text("Asha answers", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Asha holds 3 Supremo — ⚡ MATCH POINT for L2, and the whole table can see it.",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
        )
        Spacer(Modifier.height(10.dp))

        FlowNode(
            1, "Ravi", 1, "Picks themes — the question generates",
            "It arrives with a MOOD: favours Idealist (bar −1), suspects Supremo " +
                "(bar +1). Asha's lunge just got taxed.",
            LaserBlue,
        )
        FlowFork(
            "Twist (×2)" to "make the easy answer costly",
            "Change" to "swap for a fresh card",
        )
        ChainLink("happy with the question…")
        FlowNode(
            2, "Ravi", 1, "Sets a trap — secret",
            "Arms a landmine on \"farmers\" — she's at match point, strike the " +
                "base. Any key word of an armed sentence fires.",
            LaserBlue,
        )
        ChainLink("pass the phone")
        FlowNode(
            3, "Asha", 2, "Answers — 90 seconds",
            "TONIGHT'S BAR: Supremo needs 7 (table anchor 5, +1 hoarding, +1 " +
                "mood). The whip's sealed envelope demands Capitalist — she " +
                "argues Supremo anyway. Her build needs the card.",
            LaserBlue,
        )
        FlowFork(
            "Obey the whip" to "+3 poll · +1 Funds",
            "Rebel" to "at 7+: +5 poll · under 7: −4",
        )
        ChainLink("mid-argument, she says \"farmers\"…")
        FlowNode(
            4, null, 0, "⚡ BREAKING — the tripwire fires",
            "The crisis targets SUPREMO, her base. Hold the line and clear the " +
                "bar → +1 Clout, +2 poll. Fumble → meter −3, poll −3. Swerve → " +
                "the record remembers.",
            LaserRed,
        )
        ChainLink("she rests her case — the phone goes back")
        FlowNode(
            5, "Ravi", 1, "The prosecutor's call",
            "He posed the question, he decides: cross-examine, or send it " +
                "straight to the judge. Ravi prosecutes.",
            LaserBlue,
        )
        FlowFork(
            "Cross-examine" to "20s rebuttal → Asha closes in 15s",
            "Straight to the judge" to "the argument stands as given",
        )
        ChainLink("the judge weighs the whole exchange")
        FlowNode(
            6, null, 0, "The verdict",
            "Strength 8, primary Supremo. The bar was 7 — CLEARED, the card " +
                "stays. Her 4th Supremo → 🏛 L2 MILESTONE: claim the board power.",
            Gold,
        )
        Spacer(Modifier.height(6.dp))
        StrengthMeter(strength = 8, required = 7, ideology = "Supremo")
        DemoCaption("Strength 8 against the bar of 7 — the tick sits behind the fill. Card kept.")
        ChainLink("the morning after")
        FlowNode(
            7, null, 0, "The payouts land",
            "Whip revealed: she rebelled at strength 8 → +5 poll. Crisis " +
                "weathered → +1 Clout, +2 poll. The Farmers hit +3 → 🤝 " +
                "endorsement + gift. Snap poll 58% (+9). Take at the table:",
            LaserGreen,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Supremo", "+2 · Clout", IdeologyTheme.container("Supremo"), Modifier.weight(1f))
            StatTile("Crisis bonus", "+1 · Clout", IdeologyTheme.container("Supremo"), Modifier.weight(1f))
            StatTile("Gift: Farmers", "+1 · Clout", IdeologyTheme.container("Supremo"), Modifier.weight(1f))
        }
        ChainLink("next player — and when the round wraps…")
        FlowNode(
            8, null, 0, "The world pushes back",
            "Nobody argued Showstopper this round → LIBERTY rots −3. Starve a " +
                "front long enough and its crisis empowers exactly the ideology " +
                "you ignored.",
            LaserAmber,
        )
    }
}

/** One step of the worked turn: numbered disc, optional owner avatar, story beat. */
@Composable
private fun FlowNode(step: Int, who: String?, seed: Int, title: String, detail: String, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(accent.copy(alpha = 0.06f))
            .border(1.dp, accent.copy(alpha = 0.35f), MaterialTheme.shapes.medium)
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(accent.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$step", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = accent)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (who != null) {
                    Avatar(who, seed = seed, size = 20.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A choice point in the flow: slim side-by-side option panels. */
@Composable
private fun FlowFork(vararg options: Pair<String, String>) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (label, note) ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), MaterialTheme.shapes.medium)
                    .padding(8.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DemoCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}
