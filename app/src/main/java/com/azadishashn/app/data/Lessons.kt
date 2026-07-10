package com.azadishashn.app.data

/**
 * One outcome branch of a house rule. [polarity] tints it in the Playbook:
 * "gain" (it pays), "cost" (it bills), "mixed" (depends), "" (neutral fact).
 */
data class LessonBranch(
    val label: String,
    val outcome: String,
    val polarity: String = "",
)

/**
 * A teachable house rule, narrated comic-book style — second person, present
 * tense, every number real. None of these mechanics are in the SHASN rulebook,
 * so the app teaches them by scenario: the SAME content renders as a one-time
 * [com.azadishashn.app.ui.components.CoachMark] the first time the rule fires,
 * and as an illustrated strip in the Playbook. One source of truth — the
 * numbers here can never drift from what the coach cards say.
 */
data class Lesson(
    val id: String,
    val title: String,
    /** The "you are there" caption panel that opens the strip. */
    val scenario: String,
    /** Every branch the rule can take, with its real numbers. */
    val branches: List<LessonBranch>,
    /** Playbook-only: the tension this rule creates at the table. */
    val why: String = "",
)

object Lessons {
    const val BAR = "bar"
    const val MOOD = "mood"
    const val WHIP = "whip_envelope"
    const val TRIPWIRE_ARM = "tripwire_arm"
    const val TRIPWIRE_FIRE = "tripwire_fire"
    const val MATCHPOINT = "matchpoint"
    const val BLOCS = "blocs"
    const val DEFECTION = "defection"
    const val NATION = "nation"
    const val LADDER = "ladder"
    const val MILESTONE = "milestone"
    const val GRANTS = "grants"
    const val SCANDAL = "scandal"
    const val CROSSEXAM = "crossexam"

    val ALL: List<Lesson> = listOf(
        Lesson(
            id = BAR,
            title = "The bar — who keeps the card",
            scenario = "You've just argued your case. The judge scores it 1–10. " +
                "A card ALWAYS lands — the only question is whether it stays on " +
                "the line you argued.",
            branches = listOf(
                LessonBranch(
                    "You clear the bar",
                    "Strength meets the number. The card stays on your primary. " +
                        "Your stack grows.",
                    polarity = "gain",
                ),
                LessonBranch(
                    "You fall short",
                    "The card slips to your SECONDARY line instead. Nothing is " +
                        "lost — but the stack you're building didn't move.",
                    polarity = "mixed",
                ),
                LessonBranch(
                    "Who sets the bar",
                    "Your own table does: the middle of its last 8 verdicts, " +
                        "minus 1 — then +1 for every card you hold over the table " +
                        "average. Hoard a line, and that line gets expensive.",
                ),
            ),
            why = "The judge isn't dice: a table of orators scores high across " +
                "the board, so the bar follows the table — every table lives on " +
                "the same drama curve. And the deeper you farm one ideology, the " +
                "harder it fights to stay yours.",
        ),
        Lesson(
            id = MOOD,
            title = "The question's mood",
            scenario = "Every question blows a wind. Tonight one ideology rides " +
                "it and one fights it — the room decided before you opened your " +
                "mouth.",
            branches = listOf(
                LessonBranch(
                    "Ride the wind",
                    "Argue the favoured line: the bar drops 1. The cheap card.",
                    polarity = "gain",
                ),
                LessonBranch(
                    "Fight it",
                    "Argue the suspected line: the bar climbs 1. Do it anyway if " +
                        "your build needs exactly that card.",
                    polarity = "cost",
                ),
                LessonBranch(
                    "Stay out of it",
                    "Any other line argues at the plain bar.",
                ),
            ),
            why = "The questioner set this weather with their theme picks. The " +
                "mood is the table's cheapest mind game: dangle an easy card off " +
                "someone's build, or tax the line they need.",
        ),
        Lesson(
            id = WHIP,
            title = "The party whip",
            scenario = "A sealed envelope lands in YOUR hand — press and hold; " +
                "nobody else sees it. The party demands a line, and it's always " +
                "one of the two you hold LEAST. Argue however you like. The " +
                "verdict tells the table what the envelope said.",
            branches = listOf(
                LessonBranch(
                    "Obey",
                    "Argue the whip's line: +3 poll, +1 resource of that line. " +
                        "Patronage flows.",
                    polarity = "gain",
                ),
                LessonBranch(
                    "Rebel — magnificently",
                    "Defy it at strength 7 or better: +5 poll. The crowd loves " +
                        "a rebel.",
                    polarity = "mixed",
                ),
                LessonBranch(
                    "Rebel — and fumble",
                    "Defy it under strength 7: −4 poll. The party remembers.",
                    polarity = "cost",
                ),
            ),
            why = "The whip is aimed at your build on purpose: obeying pays but " +
                "bends your card off the stack you're farming; rebelling protects " +
                "the build and only pays if you're brilliant.",
        ),
        Lesson(
            id = TRIPWIRE_ARM,
            title = "The tripwire — your ambush",
            scenario = "You're asking tonight. Before you hand the phone over, " +
                "set a trap: a crisis wired to detonate MID-ARGUMENT, aimed " +
                "square at the answerer's strongest ideology.",
            branches = listOf(
                LessonBranch(
                    "The landmine",
                    "Type a secret word — or a whole sentence: ANY key word of it " +
                        "stands guard (filler like \"the\" doesn't count). The " +
                        "instant their answer hits one — boom.",
                ),
                LessonBranch(
                    "The timebomb",
                    "No word, just a hidden fuse: 25 to 70 seconds in.",
                ),
                LessonBranch(
                    "The blast",
                    "It always targets their most-stacked line — you're striking " +
                        "the base. See A CRISIS BREAKS for the bill.",
                    polarity = "cost",
                ),
            ),
            why = "Arm it when they're at match point: they either argue their " +
                "targeted line through the storm, or swerve off the card they need.",
        ),
        Lesson(
            id = TRIPWIRE_FIRE,
            title = "A crisis breaks",
            scenario = "⚡ BREAKING — mid-sentence, the world lurches. The crisis " +
                "has your name on it and your strongest ideology in its sights. " +
                "Three ways this ends at the verdict:",
            branches = listOf(
                LessonBranch(
                    "Weathered",
                    "You argue the targeted line AND keep the card: +1 resource, " +
                        "+2 poll. Courage under fire.",
                    polarity = "gain",
                ),
                LessonBranch(
                    "Claimed",
                    "You argue it and the card diverts: its home meter −3, your " +
                        "poll −3. The nation pays for your fumble.",
                    polarity = "cost",
                ),
                LessonBranch(
                    "Swerved",
                    "You argue something else. No hit today — but the record " +
                        "remembers a politician who ran.",
                    polarity = "mixed",
                ),
            ),
        ),
        Lesson(
            id = MATCHPOINT,
            title = "Match point — and it's public",
            scenario = "⚡ You're one card from a power level — and the chip is " +
                "on the screen where EVERYONE can read it, including the person " +
                "writing your question.",
            branches = listOf(
                LessonBranch(
                    "Convert",
                    "Keep the card, claim the level: 2/4/6 cards of one line → " +
                        "its L1/L2/L3 board power.",
                    polarity = "gain",
                ),
                LessonBranch(
                    "Survive the fire",
                    "Match point is public on purpose. Expect tripwires, hostile " +
                        "moods, and questions built to make you swerve.",
                    polarity = "cost",
                ),
            ),
        ),
        Lesson(
            id = BLOCS,
            title = "The blocs are watching",
            scenario = "Farmers. Traders. Students. Named blocs watch every " +
                "answer, and every verdict moves each one −2..+2 toward or away " +
                "from you.",
            branches = listOf(
                LessonBranch(
                    "Hit +3 support",
                    "The bloc ENDORSES you — and a bloc backs ONE patron only.",
                    polarity = "gain",
                ),
                LessonBranch(
                    "While endorsed",
                    "Every positive poll swing you take amplifies +1 per " +
                        "endorsement, and endorsements break standings ties.",
                    polarity = "gain",
                ),
                LessonBranch(
                    "Fall below 0",
                    "The endorsement dies.",
                    polarity = "cost",
                ),
            ),
        ),
        Lesson(
            id = DEFECTION,
            title = "Endorsements can be stolen",
            scenario = "An endorsement is not a trophy — it's a hostage. A bloc " +
                "backs one patron at a time, and your rivals can court it out " +
                "from under you.",
            branches = listOf(
                LessonBranch(
                    "Win one",
                    "Reach +3 with an unclaimed bloc: it's yours — plus a +1 " +
                        "resource gift that turn.",
                    polarity = "gain",
                ),
                LessonBranch(
                    "Steal one",
                    "Beat the incumbent's support outright and the bloc walks " +
                        "across the floor. 🔥 DEFECTION.",
                    polarity = "mixed",
                ),
                LessonBranch(
                    "Lose one",
                    "Let your support with the bloc sink below 0 and it's gone.",
                    polarity = "cost",
                ),
            ),
        ),
        Lesson(
            id = NATION,
            title = "The nation is a player",
            scenario = "Four meters, four home turfs: the Economy is Capitalist " +
                "country, Stability belongs to the Supremo, Liberty to the " +
                "Showstopper, Trust to the Idealist. The nation is playing too.",
            branches = listOf(
                LessonBranch(
                    "Crisis — meter under 25",
                    "That ideology argues at +1 strength (the hour demands it), " +
                        "and the scenarios come hunting for the crisis.",
                    polarity = "mixed",
                ),
                LessonBranch(
                    "Golden age — meter over 75",
                    "−1 strength. Fat and happy — nothing to rail against.",
                    polarity = "cost",
                ),
                LessonBranch(
                    "Neglect decay",
                    "Every completed round, each line that won NO card rots its " +
                        "home meter −3. Farm one line, and the fronts you ignore " +
                        "catch fire.",
                    polarity = "cost",
                ),
            ),
            why = "Monoculture breeds crises — which then empower exactly the " +
                "ideologies you neglected. The world pushes back on the farm.",
        ),
        Lesson(
            id = LADDER,
            title = "The power ladder",
            scenario = "Cards are the score — POWER is the ranking. Two, four, " +
                "six cards of one line unlock its Level 1, 2, 3 board power.",
            branches = listOf(
                LessonBranch(
                    "Power points",
                    "L1 = 1, L2 = 2, L3 = 3, summed across your lines. Highest " +
                        "total leads the house.",
                ),
                LessonBranch(
                    "Ties",
                    "Broken by endorsements, then by approval.",
                ),
                LessonBranch(
                    "Why not count cards",
                    "Everyone banks exactly one card per turn — totals tie " +
                        "forever. DEPTH in a set is what separates you.",
                ),
            ),
        ),
        Lesson(
            id = MILESTONE,
            title = "Milestone — claim your power",
            scenario = "🏛 The set is complete: 2, 4 or 6 cards of one ideology, " +
                "banked.",
            branches = listOf(
                LessonBranch(
                    "Claim it on the board",
                    "Take that line's L1/L2/L3 ideologue power in SHASN, " +
                        "physically. The app announces; you collect.",
                    polarity = "gain",
                ),
            ),
        ),
        Lesson(
            id = GRANTS,
            title = "Politics pays in resources",
            scenario = "The verdict always pays +2 primary, +1 secondary. But " +
                "politics tips on the side — when a tile appears under a verdict, " +
                "take it (or hand it back) at the table:",
            branches = listOf(
                LessonBranch(
                    "Whip patronage",
                    "+1 resource for obeying the whip.",
                    polarity = "gain",
                ),
                LessonBranch(
                    "Bloc gift",
                    "+1 resource when a bloc newly endorses you.",
                    polarity = "gain",
                ),
                LessonBranch(
                    "Crisis bonus",
                    "+1 resource for weathering a crisis.",
                    polarity = "gain",
                ),
                LessonBranch(
                    "The mandate",
                    "Cross 65% approval → +1 resource, donors pour in. Sink to " +
                        "35% → give one back. Donors flee.",
                    polarity = "mixed",
                ),
            ),
        ),
        Lesson(
            id = SCANDAL,
            title = "Scandals",
            scenario = "About one turn in four, the press digs up something YOU " +
                "actually said earlier this game — and wants an answer before " +
                "the round begins.",
            branches = listOf(
                LessonBranch(
                    "Face the press",
                    "Your response is judged as pure damage control — the snap " +
                        "poll moves on how you handle it.",
                    polarity = "mixed",
                ),
                LessonBranch(
                    "\"No comment.\"",
                    "−3 approval, instantly. The press smells blood.",
                    polarity = "cost",
                ),
            ),
        ),
        Lesson(
            id = CROSSEXAM,
            title = "Cross-examination",
            scenario = "You rest your case — and the phone goes BACK to the " +
                "questioner. Every answer ends up in front of its prosecutor; " +
                "they decide what happens next.",
            branches = listOf(
                LessonBranch(
                    "Cross-examine",
                    "The questioner takes the floor: 20 seconds to tear the " +
                        "answer apart, then you get 15 to close. The judge weighs " +
                        "the WHOLE exchange.",
                    polarity = "mixed",
                ),
                LessonBranch(
                    "Straight to the judge",
                    "The questioner waives. The argument stands as given.",
                ),
            ),
        ),
    )

    fun byId(id: String): Lesson? = ALL.firstOrNull { it.id == id }
}
