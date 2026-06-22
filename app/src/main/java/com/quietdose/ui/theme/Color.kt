package com.quietdose.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Dose palette — dark-first, restrained. One accent, lots of near-black space.
 * The goal is calm: surfaces are barely-there elevations of the background, and
 * colour is spent almost entirely on a single periwinkle accent.
 */

// Backgrounds / surfaces
val Ink = Color(0xFF0B0B0D)        // app background, near-black
val Surface1 = Color(0xFF141418)   // cards
val Surface2 = Color(0xFF1C1C22)   // raised / pressed
val Outline = Color(0xFF2A2A31)    // hairline dividers

// Text
val TextHigh = Color(0xFFF2F3F7)   // primary
val TextMid = Color(0xFFA7A9B4)    // secondary / notes
val TextLow = Color(0xFF6E707A)    // tertiary / disabled

// The one accent and its quiet relatives
val Accent = Color(0xFF7C9CFF)     // periwinkle
val AccentSoft = Color(0xFF2B3354) // accent tint on dark
val AccentText = Color(0xFF0B0B0D) // text on accent

// Semantic (used sparingly)
val Done = Color(0xFF6FCF97)       // calm green for completion
val WarnSoft = Color(0xFFE2B36B)   // low-stock amber, muted
