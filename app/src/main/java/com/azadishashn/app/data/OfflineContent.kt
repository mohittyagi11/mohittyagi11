package com.azadishashn.app.data

import com.azadishashn.app.model.Dilemma
import com.azadishashn.app.model.OptionCard
import com.azadishashn.app.model.RoundData
import com.azadishashn.app.model.Scenario

/**
 * A small bundled set of rounds so the game still works with no API key and no
 * signal. Claude generates the real, endless variety; this is the fallback deck.
 * Every round has one option per SHASN ideology: Capitalist, Supremo,
 * Showstopper, Idealist.
 */
object OfflineContent {

    private fun opt(id: String, ideology: String, label: String, summary: String) =
        OptionCard(id, ideology, label, summary)

    val ROUNDS: List<RoundData> = listOf(
        RoundData(
            scenario = Scenario(
                title = "The Oil Shock",
                dimension = "Energy · Economy",
                setting = "An industrial nation dependent on imported oil",
                era = "1973",
                situation = "Exporting nations cut supply overnight. Petrol queues stretch for " +
                    "miles, factories idle, and inflation is climbing fast. The cabinet must act " +
                    "before winter.",
            ),
            dilemma = Dilemma(
                question = "How does the government keep the country running through the shortage?",
                realWorldNote = "In 1973 governments tried rationing, price controls, blaming " +
                    "foreign cartels, and televised 'share the sacrifice' appeals — with mixed results.",
            ),
            options = listOf(
                opt("a", "Capitalist", "Let prices float", "Scrap controls; let the price climb " +
                    "until demand falls and new supply appears. The market rations it."),
                opt("b", "Supremo", "Seize and self-supply", "Nationalise the refineries, strong-arm " +
                    "foreign suppliers, and make energy a matter of national strength."),
                opt("c", "Showstopper", "Rally the nation on air", "A primetime address and a " +
                    "dramatic 'everyone sacrifices together' campaign to carry public mood."),
                opt("d", "Idealist", "Ration by need", "Issue fair coupons so hospitals, the elderly " +
                    "and essential workers come first, whatever the optics."),
            ),
        ),
        RoundData(
            scenario = Scenario(
                title = "A New Constitution",
                dimension = "Founding · Power",
                setting = "A newly independent state",
                era = "Near future",
                situation = "After winning independence, the founders must agree how power will be " +
                    "held and checked before the first election. Old rivalries simmer.",
            ),
            dilemma = Dilemma(
                question = "What kind of state should the founders build?",
                realWorldNote = "Post-independence constitutions have ranged from strongman " +
                    "presidencies to rights-first charters — the choice shaped each nation for decades.",
            ),
            options = listOf(
                opt("a", "Capitalist", "A charter for enterprise", "Entrench property rights, free " +
                    "trade and a light state so growth and investment lead the way."),
                opt("b", "Supremo", "One nation, one leader", "Concentrate power in a strong founding " +
                    "leader to push the new order through before factions stall it."),
                opt("c", "Showstopper", "Rule by the crowd", "A charismatic figurehead governing " +
                    "through rallies and frequent referendums — politics as spectacle."),
                opt("d", "Idealist", "Rights and courts first", "Entrench free speech, welfare and an " +
                    "independent judiciary above any single leader."),
            ),
        ),
        RoundData(
            scenario = Scenario(
                title = "Water on Mars",
                dimension = "Scarcity · Survival",
                setting = "A struggling Mars colony",
                era = "2140",
                situation = "The recycler is failing and the next resupply is eight months away. " +
                    "Two thousand colonists, finite water, and a council meeting tonight.",
            ),
            dilemma = Dilemma(
                question = "How is the water allocated until resupply arrives?",
                realWorldNote = "Closed-life-support planning has long debated rationing equally, by " +
                    "productivity, by decree, or by public buy-in under scarcity.",
            ),
            options = listOf(
                opt("a", "Capitalist", "Price the water", "Let colonists trade water rations; those " +
                    "who produce the most value can buy what they need."),
                opt("b", "Supremo", "The director decides", "Suspend the council; one strong " +
                    "administrator rations by decree and keeps order first."),
                opt("c", "Showstopper", "Broadcast the sacrifice", "Turn rationing into a unifying " +
                    "colony-wide drama on the screens to keep everyone bought in."),
                opt("d", "Idealist", "Equal share for all", "Every colonist gets the identical " +
                    "ration regardless of job or status."),
            ),
        ),
    )
}
