package com.quietdose.ui.stack

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quietdose.ui.theme.Accent
import com.quietdose.ui.theme.Ink
import com.quietdose.ui.theme.Outline
import com.quietdose.ui.theme.Surface1
import com.quietdose.ui.theme.Surface2
import com.quietdose.ui.theme.TextHigh
import com.quietdose.ui.theme.TextLow
import com.quietdose.ui.theme.TextMid

/* ----------------------------- Section + labels ----------------------------- */

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextLow,
        modifier = modifier.padding(bottom = 6.dp),
    )
}

@Composable
fun EditorSection(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        FieldLabel(label)
        content()
    }
}

/* ----------------------------- Text input ----------------------------- */

@Composable
fun StackTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    accent: Color = Accent,
    singleLine: Boolean = true,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = TextLow) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Surface2,
            unfocusedContainerColor = Surface2,
            disabledContainerColor = Surface2,
            cursorColor = accent,
            focusedTextColor = TextHigh,
            unfocusedTextColor = TextHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/* ----------------------------- Choice chips ----------------------------- */

/** A pill that quietly fills with the accent tint when selected. */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        if (selected) accent.copy(alpha = 0.22f) else Surface2,
        label = "chipBg",
    )
    val fg = if (selected) accent else TextMid
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(bg)
            .then(
                if (selected) Modifier.border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(11.dp))
                else Modifier,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

/** A wrapping row of single-select chips over [options]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipGroup(
    options: List<T>,
    selected: T,
    accent: Color,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEach { opt ->
            ChoiceChip(label(opt), opt == selected, accent) { onSelect(opt) }
        }
    }
}

/** A wrapping row of toggle chips (multi-select), used for behaviour flags. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToggleChipRow(
    options: List<Pair<Int, String>>,
    isOn: (Int) -> Boolean,
    accent: Color,
    onToggle: (Int) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEach { (bit, text) ->
            ChoiceChip(text, isOn(bit), accent) { onToggle(bit) }
        }
    }
}

/* ----------------------------- Stepper ----------------------------- */

/** A round +/- control around a value readout — calm, tactile number entry. */
@Composable
fun Stepper(
    value: String,
    accent: Color,
    onDec: () -> Unit,
    onInc: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        StepperButton(Icons.Rounded.Remove, accent, onDec)
        Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = TextHigh)
        }
        StepperButton(Icons.Rounded.Add, accent, onInc)
    }
}

@Composable
private fun StepperButton(icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Surface2)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
    }
}

/* ----------------------------- Toggle row ----------------------------- */

@Composable
fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextHigh)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMid)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink,
                checkedTrackColor = accent,
                checkedBorderColor = accent,
                uncheckedThumbColor = TextMid,
                uncheckedTrackColor = Surface1,
                uncheckedBorderColor = Outline,
            ),
        )
    }
}

/* ----------------------------- Actions ----------------------------- */

@Composable
fun FilledButton(label: String, accent: Color, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) accent.copy(alpha = 0.22f) else Surface2)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(vertical = 14.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) accent else TextLow,
        )
    }
}

@Composable
fun TextButtonGhost(label: String, modifier: Modifier = Modifier, color: Color = TextMid, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/* ----------------------------- Icon picker ----------------------------- */

@Composable
fun IconKeyPicker(selectedKey: String, accent: Color, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconChoices.forEach { choice ->
            val selected = choice.key == selectedKey
            val bg by animateColorAsState(
                if (selected) accent.copy(alpha = 0.20f) else Surface2,
                label = "iconBg",
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(bg)
                    .then(
                        if (selected) Modifier.border(1.5.dp, accent, RoundedCornerShape(13.dp))
                        else Modifier,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(choice.key) },
            ) {
                Icon(
                    choice.icon,
                    contentDescription = choice.label,
                    tint = if (selected) accent else TextMid,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/* ----------------------------- Tint swatches ----------------------------- */

/** A row of tint swatches; the currently-chosen one gets a ring + check. */
@Composable
fun TintSwatches(selectedArgb: Int, onSelect: (Int) -> Unit) {
    FlowRowSwatches(selectedArgb, onSelect)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowSwatches(selectedArgb: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        StackTints.forEach { argb ->
            val color = Color(argb)
            val selected = argb == selectedArgb
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (selected) Modifier.border(2.dp, TextHigh, CircleShape) else Modifier,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(argb) },
            ) {
                if (selected) {
                    Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Ink, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** No-ripple clickable — the calm, indication-free tap used throughout the app. */
@Composable
fun Modifier.androidxClickable(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
    ) { onClick() }
