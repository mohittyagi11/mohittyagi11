package com.azadishashn.app.data

/**
 * Landmine matching for the questioner's tripwire. The armed text may be a
 * single word OR a whole sentence: the mine fires when the answer contains
 * the full phrase, or ANY significant word of it. Stopwords are dropped so a
 * casually-armed sentence can't detonate on "that" or "hai", and matching is
 * prefix-tolerant for words of 4+ letters, so "subsidy" also fires on
 * "subsidies" and "farm" on "farmers".
 */
object Tripwire {

    /** Filler that must never detonate a mine (English + Hinglish + Hindi). */
    private val STOP = setOf(
        // English
        "the", "a", "an", "and", "or", "but", "of", "to", "in", "on", "at",
        "for", "with", "is", "are", "was", "were", "be", "been", "am", "it",
        "its", "as", "by", "from", "that", "this", "these", "those", "will",
        "would", "can", "could", "should", "shall", "may", "might", "must",
        "have", "has", "had", "do", "does", "did", "not", "no", "yes", "so",
        "if", "then", "than", "they", "them", "their", "we", "our", "you",
        "your", "i", "me", "my", "he", "him", "his", "she", "her", "who",
        "what", "when", "where", "why", "how", "all", "any", "some", "there",
        "about", "into", "over", "under", "very", "just", "only", "also",
        // Hinglish (romanised Hindi)
        "ka", "ki", "ke", "ko", "se", "mein", "par", "aur", "ya", "hai",
        "hain", "ho", "tha", "thi", "the", "bhi", "nahi", "nahin", "na",
        "ye", "yeh", "woh", "wo", "kya", "ek", "hum", "tum", "aap", "main",
        "kar", "karo", "karna", "raha", "rahi", "rahe", "liye", "wala",
        // Hindi (Devanagari)
        "का", "की", "के", "को", "से", "में", "पर", "और", "या", "है", "हैं",
        "हो", "था", "थी", "थे", "भी", "नहीं", "ना", "ये", "यह", "वह", "वो",
        "क्या", "एक", "हम", "तुम", "आप", "मैं", "कर", "रहा", "रही", "रहे", "लिए",
    )

    private val SPLIT = Regex("[^\\p{L}\\p{N}]+")

    private fun tokens(text: String): List<String> =
        text.lowercase().split(SPLIT).filter { it.isNotBlank() }

    /** The words of the armed text that actually stand guard. */
    fun watchedWords(armed: String): List<String> =
        tokens(armed).filter { it !in STOP && it.length >= 2 }.distinct()

    /** True the moment [argument] trips the mine armed with [armed]. */
    fun matches(armed: String, argument: String): Boolean {
        if (armed.isBlank() || argument.isBlank()) return false
        // The full phrase, verbatim, always counts.
        if (argument.contains(armed.trim(), ignoreCase = true)) return true
        val watched = watchedWords(armed)
        if (watched.isEmpty()) return false
        val said = tokens(argument)
        return said.any { w ->
            watched.any { t ->
                w == t ||
                    (t.length >= 4 && w.startsWith(t)) ||
                    (w.length >= 4 && t.startsWith(w))
            }
        }
    }
}
