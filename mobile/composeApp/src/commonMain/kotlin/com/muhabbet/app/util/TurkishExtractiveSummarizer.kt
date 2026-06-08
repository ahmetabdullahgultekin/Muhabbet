package com.muhabbet.app.util

/**
 * Pure on-device extractive summarizer for Turkish voice-note transcripts (D5 — Pillar C).
 *
 * This is a REAL, deterministic algorithm — no network, no LLM, no mock. It runs entirely
 * in [commonMain] so it is testable on the JVM and shared by Android + iOS.
 *
 * Algorithm (classic frequency-based extractive summarization):
 *  1. Split the transcript into sentences on Turkish sentence terminators (. ! ? … and newlines).
 *  2. Tokenize each sentence into lowercase words (Turkish locale-aware lowercasing), stripping
 *     punctuation. Build a word-frequency table over all CONTENT words — i.e. excluding a curated
 *     Turkish stop-word list (and very short tokens) so that filler words don't dominate scoring.
 *  3. Score each sentence as the sum of its content words' corpus frequencies, normalized by the
 *     number of content words in the sentence (so long sentences aren't unfairly favored).
 *  4. Pick the top-scoring sentence(s) in ORIGINAL order, appending until the [maxChars] budget
 *     would be exceeded. Always emit at least the single best sentence.
 *
 * Edge cases:
 *  - Empty / blank transcript → empty string.
 *  - One sentence (or fewer) → returned as-is (trimmed, capped to [maxChars]).
 */
object TurkishExtractiveSummarizer {

    /** Default character budget for the rendered "Özet" line (1–2 short sentences). */
    const val DEFAULT_MAX_CHARS: Int = 160

    /** Tokens shorter than this (after stripping) never count toward frequency scoring. */
    private const val MIN_CONTENT_WORD_LENGTH: Int = 3

    /**
     * Curated Turkish stop-word list — high-frequency function words (conjunctions, pronouns,
     * postpositions, common fillers) that carry little topical signal. Kept deliberately small
     * and hand-picked (KISS); these are excluded from frequency scoring.
     */
    private val STOP_WORDS: Set<String> = setOf(
        "ve", "ile", "ama", "fakat", "ancak", "veya", "ya", "de", "da", "ki", "ne",
        "bu", "şu", "o", "bir", "birşey", "her", "hep", "çok", "az", "daha", "en",
        "için", "gibi", "kadar", "göre", "sonra", "önce", "şey", "ben", "sen", "biz",
        "siz", "onlar", "bana", "sana", "ona", "bize", "size", "beni", "seni", "onu",
        "benim", "senin", "onun", "bizim", "sizin", "mi", "mı", "mu", "mü", "değil",
        "var", "yok", "olarak", "yani", "ise", "eğer", "hem", "ya da", "ne", "hangi",
        "şöyle", "böyle", "öyle", "işte", "tamam", "evet", "hayır", "şimdi", "artık",
        "çünkü", "ayrıca", "bütün", "bazı", "kendi", "diğer", "böylece", "ancak"
    )

    /** Sentence terminators recognized in Turkish transcripts. */
    private val SENTENCE_TERMINATORS = charArrayOf('.', '!', '?', '…', '\n')

    /**
     * Produce a 1–2 sentence extractive summary of [transcript].
     *
     * @param maxChars upper bound on the returned summary length (must be > 0).
     * @return the summary, or an empty string when [transcript] is blank.
     */
    fun summarize(transcript: String, maxChars: Int = DEFAULT_MAX_CHARS): String {
        val cap = if (maxChars > 0) maxChars else DEFAULT_MAX_CHARS
        val cleaned = transcript.trim()
        if (cleaned.isEmpty()) return ""

        val sentences = splitSentences(cleaned)
        // Zero or one sentence → return as-is (capped). Use the split form (if any) so a lone
        // trailing terminator is dropped; fall back to the cleaned text when nothing split out.
        if (sentences.size <= 1) {
            return capToChars(sentences.firstOrNull() ?: cleaned, cap)
        }

        // 1. Build the corpus frequency table over content words.
        val frequencies = HashMap<String, Int>()
        for (sentence in sentences) {
            for (word in contentWords(sentence)) {
                frequencies[word] = (frequencies[word] ?: 0) + 1
            }
        }

        // 2. Score each sentence (normalized by content-word count).
        val scored = sentences.mapIndexed { index, sentence ->
            val words = contentWords(sentence)
            val rawScore = words.sumOf { frequencies[it] ?: 0 }
            val normalized = if (words.isEmpty()) 0.0 else rawScore.toDouble() / words.size
            ScoredSentence(index = index, text = sentence, score = normalized)
        }

        // 3. Rank by score (desc), break ties by original order (asc) for determinism.
        val ranked = scored.sortedWith(
            compareByDescending<ScoredSentence> { it.score }.thenBy { it.index }
        )

        // 4. Greedily take top sentences within the char budget, then restore original order.
        val chosen = ArrayList<ScoredSentence>()
        var runningLength = 0
        for (candidate in ranked) {
            val addition = candidate.text.length + if (chosen.isEmpty()) 0 else 1 // +1 for joining space
            if (chosen.isEmpty()) {
                // Always include the single best sentence, even if it alone exceeds the cap.
                chosen.add(candidate)
                runningLength = candidate.text.length
            } else if (candidate.score > 0.0 && runningLength + addition <= cap) {
                // Only pad a second sentence in if it actually carries signal (skip pure
                // stop-word / filler sentences) and it fits the budget.
                chosen.add(candidate)
                runningLength += addition
            }
            if (chosen.size >= MAX_SENTENCES) break
        }

        val summary = chosen
            .sortedBy { it.index }
            .joinToString(" ") { it.text }

        return capToChars(summary, cap)
    }

    /** Maximum sentences emitted in the summary ("1–2 sentence" requirement). */
    private const val MAX_SENTENCES: Int = 2

    private data class ScoredSentence(val index: Int, val text: String, val score: Double)

    /** Split on Turkish sentence terminators, trimming and dropping empties. */
    private fun splitSentences(text: String): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        for (ch in text) {
            if (ch in SENTENCE_TERMINATORS) {
                val sentence = current.toString().trim()
                if (sentence.isNotEmpty()) result.add(sentence)
                current.clear()
            } else {
                current.append(ch)
            }
        }
        val tail = current.toString().trim()
        if (tail.isNotEmpty()) result.add(tail)
        return result
    }

    /**
     * Extract scoring tokens from a sentence: lowercase (Turkish-aware), strip surrounding
     * punctuation, drop stop-words and tokens shorter than [MIN_CONTENT_WORD_LENGTH].
     */
    private fun contentWords(sentence: String): List<String> =
        sentence.split(' ', '\t', ',', ';', ':', '"', '\'', '(', ')', '«', '»')
            .asSequence()
            .map { it.trim().lowercaseTr() }
            .map { it.trim { c -> !c.isLetterOrDigit() } }
            .filter { it.length >= MIN_CONTENT_WORD_LENGTH }
            .filter { it !in STOP_WORDS }
            .toList()

    /**
     * Turkish-aware lowercasing for the dotted/dotless I pair. KMP commonMain has no Locale, so
     * we map the two Turkish-specific uppercase forms explicitly before a generic lowercase.
     *  - 'I' (U+0049) → 'ı' (dotless i)
     *  - 'İ' (U+0130) → 'i' (dotted i)
     */
    private fun String.lowercaseTr(): String =
        this.replace('I', 'ı').replace('İ', 'i').lowercase()

    /** Cap [text] to [maxChars], trimming on a word boundary where possible. */
    private fun capToChars(text: String, maxChars: Int): String {
        val trimmed = text.trim()
        if (trimmed.length <= maxChars) return trimmed
        val hardCut = trimmed.substring(0, maxChars).trimEnd()
        val lastSpace = hardCut.lastIndexOf(' ')
        val body = if (lastSpace > maxChars / 2) hardCut.substring(0, lastSpace) else hardCut
        return body.trimEnd().trimEnd('.', ',', ';', ':') + "…"
    }
}
