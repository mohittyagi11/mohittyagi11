package com.quietdose.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quietdose.ui.theme.Accent
import com.quietdose.ui.theme.AccentSoft
import com.quietdose.ui.theme.Done
import com.quietdose.ui.theme.Ink
import com.quietdose.ui.theme.Outline
import com.quietdose.ui.theme.Surface1
import com.quietdose.ui.theme.Surface2
import com.quietdose.ui.theme.TextHigh
import com.quietdose.ui.theme.TextLow
import com.quietdose.ui.theme.TextMid

/* ----------------------------- Cards & sections ----------------------------- */

/** A calm settings card: a labelled section over Surface1, generous padding. */
@Composable
fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(color = Surface1, shape = RoundedCornerShape(20.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextLow)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMid)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

/* ----------------------------- Status checklist ----------------------------- */

/** One permission row: a check/dot status glyph, a title + rationale, optional action. */
@Composable
fun StatusRow(
    granted: Boolean,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        StatusGlyph(granted)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = TextHigh,
            )
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = TextMid)
        }
        if (!granted && actionLabel != null && onAction != null) {
            Spacer(Modifier.size(10.dp))
            PillButton(actionLabel, onAction)
        }
    }
}

@Composable
private fun StatusGlyph(granted: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(26.dp)
            .background(
                if (granted) Done.copy(alpha = 0.16f) else Surface2,
                CircleShape,
            ),
    ) {
        if (granted) {
            Icon(Icons.Rounded.Check, contentDescription = "Granted", tint = Done, modifier = Modifier.size(15.dp))
        } else {
            Box(Modifier.size(7.dp).background(TextLow, CircleShape))
        }
    }
}

/* ----------------------------- Buttons ----------------------------- */

/** A soft accent pill — the primary call to action inside a card. */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Accent,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) tint.copy(alpha = 0.18f) else Surface2)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) tint else TextLow,
        )
    }
}

/** A quiet text button for secondary / destructive actions. */
@Composable
fun GhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = TextMid,
) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) color else TextLow,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/* ----------------------------- Toggle row ----------------------------- */

@Composable
fun ToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextHigh)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = TextMid)
        }
        Spacer(Modifier.size(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink,
                checkedTrackColor = Accent,
                checkedBorderColor = Accent,
                uncheckedThumbColor = TextMid,
                uncheckedTrackColor = Surface2,
                uncheckedBorderColor = Outline,
            ),
        )
    }
}

/* ----------------------------- Stepper ----------------------------- */

/** A labelled −/＋ stepper for an integer-ish value (radius, hour). */
@Composable
fun Stepper(
    label: String,
    valueLabel: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    canDecrement: Boolean = true,
    canIncrement: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = TextHigh)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StepButton(Icons.Rounded.Remove, "Decrease $label", canDecrement, onDecrement)
            Box(Modifier.size(width = 64.dp, height = 26.dp), contentAlignment = Alignment.Center) {
                Text(valueLabel, style = MaterialTheme.typography.titleMedium, color = TextHigh)
            }
            StepButton(Icons.Rounded.Add, "Increase $label", canIncrement, onIncrement)
        }
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (enabled) AccentSoft else Surface2)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) Accent else TextLow,
            modifier = Modifier.size(18.dp),
        )
    }
}

/* ----------------------------- Misc ----------------------------- */

@Composable
fun HairLine(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Outline))
}
