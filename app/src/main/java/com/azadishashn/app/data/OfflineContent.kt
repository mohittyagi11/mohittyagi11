package com.azadishashn.app.data

import com.azadishashn.app.model.Dilemma
import com.azadishashn.app.model.OptionCard
import com.azadishashn.app.model.RoundData
import com.azadishashn.app.model.Scenario

/**
 * A small bundled set of rounds so the game still works with no API key and no
 * signal. Claude generates the real, endless variety; this is the fallback deck.
 */
object OfflineContent {

    private fun opt(id: String, ideology: String, label: String, summary: String) =
        OptionCard(id, ideology, label, summary)

    val ROUNDS: List<RoundData> = listOf(
        RoundData(
            scenario = Scenario(
                title = "The Oil Shock",
                setting = "An industrial nation dependent on imported oil",
                era = "1973",
                situation = "Exporting nations cut supply overnight. Petrol queues stretch for " +
                    "miles, factories idle, and inflation is climbing fast. The cabinet must act " +
                    "before winter.",
            ),
            dilemma = Dilemma(
                question = "How does the government keep the country running through the shortage?",
                realWorldNote = "In 1973 several states tried rationing, price controls, and " +
                    "crash programs for domestic energy — with very mixed results.",
            ),
            options = listOf(
                opt("a", "Socialism", "Ration fuel by need", "Nationalise distribution and issue " +
                    "fuel coupons so essential workers and hospitals come first."),
                opt("b", "Libertarianism", "Let prices float", "Remove all controls; let the price " +
                    "rise until demand falls and new supply appears."),
                opt("c", "Technocracy", "Crash energy programme", "Pour the budget into experts, " +
                    "nuclear plants and efficiency mandates planned by a technical board."),
                opt("d", "Nationalism", "Energy self-reliance", "Subsidise domestic drilling and " +
                    "coal at any cost to break dependence on foreign suppliers."),
            ),
        ),
        RoundData(
            scenario = Scenario(
                title = "A New Constitution",
                setting = "A newly independent island state",
                era = "Near future",
                situation = "After winning independence, the founders must agree how power will be " +
                    "held and checked before the first election. Old rivalries simmer.",
            ),
            dilemma = Dilemma(
                question = "What kind of state should the founders build?",
                realWorldNote = "Post-independence constitutions have ranged from strong " +
                    "presidencies to councils of elders — the choice shaped each nation for decades.",
            ),
            options = listOf(
                opt("a", "Liberalism", "Rights and courts first", "Entrench free speech, elections " +
                    "and an independent judiciary above any leader."),
                opt("b", "Authoritarianism", "Strong founding leader", "Concentrate power for one " +
                    "term to push reforms through before factions stall everything."),
                opt("c", "Theocracy", "Law from faith", "Root the constitution in the shared " +
                    "religious tradition that united the independence movement."),
                opt("d", "Anarchism", "Power to the localities", "Keep no strong centre at all; let " +
                    "self-governing communities federate only when they choose."),
            ),
        ),
        RoundData(
            scenario = Scenario(
                title = "Water on Mars",
                setting = "A struggling Mars colony",
                era = "2140",
                situation = "The recycler is failing and the next resupply is eight months away. " +
                    "Two thousand colonists, finite water, and a council meeting tonight.",
            ),
            dilemma = Dilemma(
                question = "How is the water allocated until resupply arrives?",
                realWorldNote = "Closed-life-support planning has long debated whether to ration " +
                    "equally, by productivity, or by lottery under scarcity.",
            ),
            options = listOf(
                opt("a", "Communism", "Equal share for all", "Every colonist gets the identical " +
                    "ration regardless of job or status."),
                opt("b", "Technocracy", "Ration by mission value", "An expert board gives engineers " +
                    "and farmers more, to keep life support and food running."),
                opt("c", "Populism", "Let the colony vote", "Put the rationing plan to a direct " +
                    "vote of every colonist tonight and follow the majority."),
                opt("d", "Environmentalism", "Cut consumption to zero-waste", "Shut every non-" +
                    "essential system and rebuild around a strict closed-loop until resupply."),
            ),
        ),
    )
}
