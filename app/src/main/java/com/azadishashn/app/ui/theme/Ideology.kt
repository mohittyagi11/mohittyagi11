package com.azadishashn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.azadishashn.app.model.Ideologies

/**
 * The brand identity of one ideology: a colour, an icon, a tinted card
 * container (per theme mode), and the resource word. Keyed on the ideology
 * name so it lines up with [Ideologies.ALL] — never re-declares the four.
 */
data class IdeologyVisual(
    val name: String,
    val brand: Color,
    val onBrand: Color,
    val containerLight: Color,
    val containerDark: Color,
    val icon: ImageVector,
    val label: String,
)

private val VISUALS = listOf(
    IdeologyVisual(
        name = "Capitalist", brand = Color(0xFFE6A100), onBrand = Color(0xFF231A00),
        containerLight = Color(0xFFFFF1CC), containerDark = Color(0xFF3A2E00),
        icon = Icons.Filled.Paid, label = "Funds",
    ),
    IdeologyVisual(
        name = "Supremo", brand = Color(0xFFE65555), onBrand = Color.White,
        containerLight = Color(0xFFFFE0E0), containerDark = Color(0xFF4A1414),
        icon = Icons.Filled.Shield, label = "Clout",
    ),
    IdeologyVisual(
        name = "Showstopper", brand = Color(0xFF12B99B), onBrand = Color(0xFF00271F),
        containerLight = Color(0xFFCFF6EE), containerDark = Color(0xFF053A30),
        icon = Icons.Filled.Campaign, label = "Media",
    ),
    IdeologyVisual(
        name = "Idealist", brand = Color(0xFF8A63F0), onBrand = Color.White,
        containerLight = Color(0xFFEAE0FF), containerDark = Color(0xFF2E1E5A),
        icon = Icons.Filled.Balance, label = "Trust",
    ),
)

object IdeologyTheme {
    /** In game order, matched to [Ideologies.ALL]. */
    val ALL: List<IdeologyVisual> = Ideologies.ALL.map { ideo -> of(ideo.name) }

    fun of(name: String): IdeologyVisual =
        VISUALS.firstOrNull { it.name == name } ?: VISUALS.first()

    /** Theme-aware tinted card background for an ideology. */
    @Composable
    fun container(name: String): Color =
        of(name).let { if (isSystemInDarkTheme()) it.containerDark else it.containerLight }
}
