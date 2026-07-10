package com.azadishashn.app.data

/** One outcome branch of a house rule: what you did / what happened → what it pays or costs. */
data class LessonBranch(val label: String, val outcome: String)

/**
 * A teachable house rule. None of these mechanics are in the SHASN rulebook,
 * so the app teaches them by scenario — the SAME content renders as a one-time
 * [com.azadishashn.app.ui.components.CoachMark] the first time the rule fires,
 * and as the full reference in the Playbook screen. One source of truth: the
 * numbers here can never drift from what the coach cards say.
 */
data class Lesson(
    val id: String,
    val title: String,
    /** A one-line "you are there" micro-scenario that sets the scene. */
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
            scenario = "You argued Capitalist at strength 6. A card ALWAYS lands — " +
                "the bar only decides whether it stays on the ideology you argued.",
            branches = listOf(
                LessonBranch(
                    "Cleared the bar",
                    "Strength ≥ the bar: the card stays on your primary — keep stacking.",
                ),
                LessonBranch(
                    "Fell short",
                    "The card diverts to your SECONDARY ideology instead. Nothing is " +
                        "lost — but the stack you were building didn't grow.",
                ),
                LessonBranch(
                    "Where the bar sits",
                    "Your own table's recent level (median of the last 8 verdicts, −1), " +
                        "+1 for every card you hold above the table average. " +
                        "Hoarding one ideology raises YOUR bar for it.",
                ),
            ),
            why = "The judge isn't dice: a table of orators scores high across the " +
                "board, so the bar follows the table — every table lives on the same " +
                "drama curve. And the more you farm one ideology, the harder it gets " +
                "to keep it.",
        ),
        Lesson(
            id = MOOD,
            title = "The question's mood",
            scenario = "Every question has a mood — one ideology rides the wind " +
                "tonight, one fights it.",
            branches = listOf(
                LessonBranch("Argue the favoured line", "The bar drops 1 — an easy card."),
                LessonBranch("Argue the suspected line", "The bar rises 1 — you're fighting the room."),
                LessonBranch("Argue anything else", "The bar is unchanged."),
            ),
            why = "The questioner set this weather with their theme picks. Ride it " +
                "for the cheap card — or fight it, because your board build needs a " +
                "different ideology than the room wants to hear.",
        ),
        Lesson(
            id = WHIP,
            title = "The party whip",
            scenario = "A sealed envelope: the party demands a line — always one of " +
                "the TWO ideologies you hold least, pulling you off your build. " +
                "Argue however you want; the table finds out at the verdict.",
            branches = listOf(
                LessonBranch(
                    "Obey — argue the whip's line",
                    "+3 poll and +1 resource of the whip's ideology. Patronage flows.",
                ),
                LessonBranch(
                    "Rebel, magnificently — defy at strength 7+",
                    "+5 poll. The crowd loves a rebel.",
                ),
                LessonBranch(
                    "Rebel, weakly — defy under strength 7",
                    "−4 poll. The party remembers.",
                ),
            ),
            why = "Obeying pays but bends your card away from the stack you're " +
                "farming; rebelling protects the build but only pays if you're " +
                "brilliant. Peek with press-and-hold so the table can't see.",
        ),
        Lesson(
            id = TRIPWIRE_ARM,
            title = "The tripwire — the questioner's ambush",
            scenario = "You're asking. Secretly arm a crisis that will ambush the " +
                "answerer MID-ARGUMENT — aimed at their most-stacked ideology.",
            branches = listOf(
                LessonBranch("Landmine word", "Fires the instant their answer contains your secret word."),
                LessonBranch("Timebomb", "Fires at a hidden random moment, 25–70 seconds in."),
                LessonBranch(
                    "The target",
                    "Always their STRONGEST ideology — you're striking their base. " +
                        "How it settles: see \"A crisis breaks\".",
                ),
            ),
            why = "Arm it when they're at match point: they must either argue their " +
                "targeted line through the storm or swerve off it.",
        ),
        Lesson(
            id = TRIPWIRE_FIRE,
            title = "A crisis breaks",
            scenario = "⚡ BREAKING, mid-argument — a crisis targets your " +
                "most-stacked ideology. Three ways it settles at the verdict:",
            branches = listOf(
                LessonBranch(
                    "Weathered",
                    "You argued the targeted ideology AND kept the card: +1 bonus " +
                        "resource and +2 poll. Courage under fire.",
                ),
                LessonBranch(
                    "Claimed",
                    "You argued it and the card diverted: that ideology's home " +
                        "nation-meter −3, and −3 poll.",
                ),
                LessonBranch(
                    "Swerved",
                    "You argued something else: no hit now — but the record " +
                        "remembers the pivot.",
                ),
            ),
        ),
        Lesson(
            id = MATCHPOINT,
            title = "Match point — and it's public",
            scenario = "⚡ One card from a power level (2/4/6 cards of one ideology " +
                "→ L1/L2/L3). The WHOLE table can see it — including the questioner.",
            branches = listOf(
                LessonBranch("Convert", "Keep the card and claim the level's board power."),
                LessonBranch(
                    "Expect fire",
                    "Match point is public on purpose: expect tripwires, hostile " +
                        "moods and hard questions aimed at your lunge.",
                ),
            ),
        ),
        Lesson(
            id = BLOCS,
            title = "The blocs are watching",
            scenario = "Named blocs — farmers, traders, students — watch every " +
                "answer and shift −2..+2 toward or away from you at each verdict.",
            branches = listOf(
                LessonBranch("Reach +3 support", "The bloc ENDORSES you — exclusively."),
                LessonBranch(
                    "While endorsed",
                    "Every POSITIVE poll swing you get is amplified +1 per " +
                        "endorsement, and endorsements break ties in the standings.",
                ),
                LessonBranch("Drop below 0", "An endorsement you held is lost."),
            ),
        ),
        Lesson(
            id = DEFECTION,
            title = "Endorsements can be stolen",
            scenario = "A bloc backs ONE patron at a time. Rivals can court the " +
                "same bloc out from under you.",
            branches = listOf(
                LessonBranch(
                    "Won",
                    "Reach +3 with an unclaimed bloc and it's yours — plus a +1 " +
                        "resource gift that turn.",
                ),
                LessonBranch(
                    "Stolen",
                    "A rival whose support STRICTLY exceeds yours takes the bloc. " +
                        "🔥 Defection — their machine now works for them.",
                ),
                LessonBranch("Lost", "Let your support with the bloc fall below 0 and they walk."),
            ),
        ),
        Lesson(
            id = NATION,
            title = "The nation is a player",
            scenario = "Four meters, each one ideology's home turf: " +
                "Economy↔Capitalist, Stability↔Supremo, Liberty↔Showstopper, " +
                "Trust↔Idealist.",
            branches = listOf(
                LessonBranch(
                    "Crisis (meter < 25)",
                    "That ideology argues at +1 strength — the hour demands it — " +
                        "and scenarios confront the crisis head-on.",
                ),
                LessonBranch(
                    "Golden age (meter > 75)",
                    "−1 strength. Complacency: nothing to rail against.",
                ),
                LessonBranch(
                    "Neglect decay",
                    "Each completed round, every ideology that received NO card " +
                        "rots its home meter −3. Monoculture breeds crises.",
                ),
            ),
            why = "Farm one ideology and the untended fronts slide into crisis — " +
                "which then empowers exactly the ideologies you neglected. The " +
                "world pushes back.",
        ),
        Lesson(
            id = LADDER,
            title = "The power ladder",
            scenario = "Cards are the score — but RANK is power. 2/4/6 cards of " +
                "one ideology unlock its Level 1/2/3 board power.",
            branches = listOf(
                LessonBranch(
                    "Power points",
                    "L1 = 1, L2 = 2, L3 = 3, summed across your ideologies — " +
                        "that's the ranking.",
                ),
                LessonBranch("Ties", "Broken by endorsements, then by approval."),
                LessonBranch(
                    "Why not total cards",
                    "Everyone earns exactly one card per turn — the total can't " +
                        "separate anyone. Depth in a set is what counts.",
                ),
            ),
        ),
        Lesson(
            id = MILESTONE,
            title = "Milestone — claim your power",
            scenario = "🏛 You just completed a set: 2, 4 or 6 cards of one ideology.",
            branches = listOf(
                LessonBranch(
                    "On the board",
                    "Physically claim that ideology's L1/L2/L3 ideologue power in " +
                        "SHASN — the app announces it; you take it.",
                ),
            ),
        ),
        Lesson(
            id = GRANTS,
            title = "Politics pays in resources",
            scenario = "Beyond the standard +2 primary / +1 secondary every " +
                "verdict, the politics itself pays out — take (or return) these " +
                "physically:",
            branches = listOf(
                LessonBranch("Whip patronage", "+1 resource for obeying the whip."),
                LessonBranch("Bloc gift", "+1 resource with each newly-won endorsement."),
                LessonBranch("Crisis bonus", "+1 resource for weathering a crisis."),
                LessonBranch(
                    "The mandate",
                    "Cross 65% approval → +1 resource (donors pour in). Sink to " +
                        "35% → give one back (donors flee).",
                ),
            ),
        ),
        Lesson(
            id = SCANDAL,
            title = "Scandals",
            scenario = "About one turn in four, a skeleton surfaces from YOUR own " +
                "record — a stand you actually took earlier this game. The press " +
                "wants an answer before the round starts.",
            branches = listOf(
                LessonBranch(
                    "Face the press",
                    "Your response is judged purely as damage control — the snap " +
                        "poll moves on how you handle it.",
                ),
                LessonBranch("\"No comment.\"", "−3 approval. The press smells blood."),
            ),
        ),
        Lesson(
            id = CROSSEXAM,
            title = "Cross-examination",
            scenario = "After the answer, the QUESTIONER may take the floor to " +
                "prosecute — pass the phone; the argument stays hidden behind the " +
                "handoff.",
            branches = listOf(
                LessonBranch("Rebuttal", "The questioner gets 20 seconds to tear the answer apart."),
                LessonBranch(
                    "Closer",
                    "The answerer gets 15 seconds to close; the judge weighs the " +
                        "WHOLE exchange.",
                ),
                LessonBranch("Waive", "Either side can skip — the argument stands as given."),
            ),
        ),
    )

    fun byId(id: String): Lesson? = ALL.firstOrNull { it.id == id }
}
