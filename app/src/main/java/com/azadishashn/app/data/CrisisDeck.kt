package com.azadishashn.app.data

/**
 * Pre-baked mid-argument crises for the questioner's tripwire. Offline and
 * instant — the drama is in the TIMING (a landmine word or a hidden timebomb) —
 * but never mere flavor: every crisis TARGETS one ideology (the answerer's
 * most-stacked, i.e. their base), and the stakes are resolved mechanically at
 * the verdict: hold your line and clear the bar → +1 bonus resource of that
 * ideology; argue it and fumble → its home nation-meter takes the hit.
 */
object CrisisDeck {

    private val LINES: Map<String, List<String>> = mapOf(
        "Capitalist" to listOf(
            "BREAKING: the markets are in free fall — traders say they're reacting to YOUR words, live.",
            "BREAKING: your largest industrial backer threatens to move everything offshore tonight.",
            "BREAKING: a leaked audit says the money for this simply is not there.",
        ),
        "Supremo" to listOf(
            "BREAKING: the army chief has called an unscheduled press conference in one hour.",
            "BREAKING: border units report they will not enforce an unpopular order. Discipline is cracking.",
            "BREAKING: your own security council is leaking against you — they call this decision weak.",
        ),
        "Showstopper" to listOf(
            "BREAKING: a viral video claims to show the human cost of your position. Millions have seen it.",
            "BREAKING: the three biggest channels just cut away from you mid-sentence — a coordinated blackout.",
            "BREAKING: an old speech of yours arguing the exact opposite is trending. Reconcile it, live.",
        ),
        "Idealist" to listOf(
            "BREAKING: crowds have breached the secretariat gates over this very question — they want an answer NOW.",
            "BREAKING: a welfare scheme you championed is tonight's scandal — funds missing, families on camera.",
            "BREAKING: your party's youth wing is chanting outside — against you.",
        ),
    )

    /** Draw a crisis line aimed at [ideology] (falls back to any line). */
    fun draw(ideology: String): String =
        (LINES[ideology] ?: LINES.values.flatten()).random()
}
