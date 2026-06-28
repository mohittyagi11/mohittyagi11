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

// Authentic SHASN token colours: Capitalist = green (Funds), Supremo = red
// (Clout), Showstopper = blue (Media), Idealist = yellow (Trust).
private val VISUALS = listOf(
    IdeologyVisual(
        name = "Capitalist", brand = Color(0xFF2FB16B), onBrand = Color(0xFF00210F),
        containerLight = Color(0xFFD3F3DF), containerDark = Color(0xFF0B3A22),
        icon = Icons.Filled.Paid, label = "Funds",
    ),
    IdeologyVisual(
        name = "Supremo", brand = Color(0xFFE5484D), onBrand = Color.White,
        containerLight = Color(0xFFFFE0E0), containerDark = Color(0xFF4A1414),
        icon = Icons.Filled.Shield, label = "Clout",
    ),
    IdeologyVisual(
        name = "Showstopper", brand = Color(0xFF3B82F6), onBrand = Color.White,
        containerLight = Color(0xFFD6E6FF), containerDark = Color(0xFF0E2A55),
        icon = Icons.Filled.Campaign, label = "Media",
    ),
    IdeologyVisual(
        name = "Idealist", brand = Color(0xFFE8B61E), onBrand = Color(0xFF241A00),
        containerLight = Color(0xFFFFF0C2), containerDark = Color(0xFF3D2E00),
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
