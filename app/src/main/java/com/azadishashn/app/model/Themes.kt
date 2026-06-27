package com.azadishashn.app.model

/**
 * Question dimensions the scenario can lean into. The questioner picks one or a
 * few (or none → random), so questions range far beyond economic/industrial
 * crises. Covers PESTEL plus deep/dark/trigger and many more.
 */
object Themes {
    val ALL: List<String> = listOf(
        // PESTEL
        "Political power", "Economy & markets", "Society & culture",
        "Technology", "Environment", "Law & rights",
        // tone dimensions
        "Deep & philosophical", "Dark & grim", "Trigger / taboo",
        // domains
        "Revolution", "War & peace", "Surveillance state", "Censorship",
        "Propaganda", "Corruption", "Justice & punishment", "Wealth gap",
        "Education", "Healthcare", "Gender & identity", "Race & caste",
        "Nationalism", "Globalism", "Migration", "Refugees",
        "Religion & state", "Colonial legacy", "Indigenous rights",
        "Climate crisis", "Energy", "Water & food", "AI & automation",
        "Cyberwar", "Nuclear", "Pandemic", "Famine", "Crime & policing",
        "Privacy vs security", "Elections", "Coup & succession",
        "Monarchy vs republic", "Federal vs central", "Labour & unions",
        "Tax & welfare", "Drug policy", "Capital punishment",
        "Reproductive rights", "Animal & nature", "Space colony",
        "Genetic engineering", "Disinformation", "Urban vs rural",
        "Tradition vs progress", "Secession & borders", "Whistleblowers",
        "The military", "Data & power",
    )

    /** A random subset to show as chips. */
    fun sample(n: Int): List<String> = ALL.shuffled().take(n)

    fun random(): String = ALL.random()
}
