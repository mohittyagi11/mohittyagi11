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

// Caution — a quiet, warm amber for "mind this", never alarming red.
val Caution = Color(0xFFD9A65C)    // muted amber for caution cues

// Analysis surfaces & severity tints — barely-there washes so severity is a
// whisper (a small dot / faint tint), never a loud coloured row.
val VerdictSurface = Color(0xFF15161D) // the verdict header card, a touch cooler
val GoodTint = Color(0x1A6FCF97)       // ~10% green wash
val CautionTint = Color(0x1FD9A65C)    // ~12% amber wash
val AccentTint = Color(0x147C9CFF)     // ~8% periwinkle wash

// Mood ambience — the analysing screen breathes a faint wash behind the glyph,
// keyed to the model's mood. Kept near-black so it's atmosphere, never colour.
// (Used as a low-alpha glow/background tint; pair with Ink underneath.)
val MoodCalm = Color(0xFF7C9CFF)       // periwinkle — settled, neutral
val MoodCurious = Color(0xFF8FB7C9)    // soft cyan — leaning in
val MoodFavorable = Color(0xFF6FCF97)  // calm green — liking it
val MoodCautious = Color(0xFFD9A65C)   // warm amber — taking care
val MoodSkeptical = Color(0xFFC58A78)  // clay — holding back
val MoodReflective = Color(0xFF9B8CE0) // indigo — weighing it

// Product-glyph category bases — a stable, calm tint per container family that
// the per-product hash blends toward, so a serum reads cool and a balm warm
// before the name even nudges the hue. Desaturated to sit on the dark canvas.
val GlyphSkincare = Color(0xFF8FB7E0)  // cool blue — serums, essences, toners
val GlyphHaircare = Color(0xFF9B8CE0)  // soft violet — oils, scalp care
val GlyphDevice = Color(0xFF8FB89A)    // sage — tools, devices
val GlyphSupplement = Color(0xFFE0B877) // warm gold — pills, powders
val GlyphNeutral = Color(0xFF9AA0B4)   // fallback grey-blue

// Glyph palette sampled from the REAL packaging photo — we blend the sampled
// colours toward these dark anchors so the product reads true-to-life yet stays
// premium on the near-black canvas (never a loud, raw photo colour).
val GlyphSurfaceSink = Color(0xFF15151B) // the calm dark the sampled body blends toward
val GlyphCapSink = Color(0xFF0E0E13)     // a touch darker, for the cap

// The visibility floor for a glyph body: when a sampled/derived body would sink
// into Surface2 (#1C1C22) and vanish, we lift it toward this calm, premium slate
// so a monogram + silhouette always read on the dark canvas (never neon).
val GlyphBodyFloor = Color(0xFF3A3B46)   // lifted body target — clearly above the surface
// A subtle ring/backing drawn behind the hero glyph for separation from its plate.
val GlyphBacking = Color(0xFF202028)     // soft backing disc behind the glyph
val GlyphRing = Color(0xFF34353F)        // hairline ring for crisp separation

// Benefit orbit dots — a small, calm spectrum cycled around the glyph so each
// primary benefit gets its own quiet tint (dot colour ↔ chip in the legend).
// Desaturated to sit beside the product without shouting.
val BenefitOrbit = listOf(
    Color(0xFF8FB7E0), // cool blue
    Color(0xFF6FCF97), // green
    Color(0xFFE0B877), // gold
    Color(0xFF9B8CE0), // violet
    Color(0xFF8FB89A), // sage
    Color(0xFFC58A78), // clay
)

// Per-group tints — deliberately desaturated so colour codes the day without
// shouting on the near-black canvas. One hue per routine.
val TintMorning = Color(0xFFE0B877) // warm gold — on waking
val TintIron = Color(0xFFC58A78)    // clay — the afternoon dose
val TintEvening = Color(0xFF8FA6F0) // periwinkle — arriving home
val TintNight = Color(0xFF9B8CE0)   // indigo/violet — wind-down
val TintMonthly = Color(0xFF8FB89A) // sage — the monthly pulse
val TintNeutral = Color(0xFF9AA0B4) // fallback for custom groups
