package com.muhabbet.app.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TurkishExtractiveSummarizerTest {

    @Test
    fun should_return_empty_when_input_is_empty() {
        assertEquals("", TurkishExtractiveSummarizer.summarize(""))
    }

    @Test
    fun should_return_empty_when_input_is_blank() {
        assertEquals("", TurkishExtractiveSummarizer.summarize("   \n  \t "))
    }

    @Test
    fun should_return_single_sentence_as_is_when_only_one_sentence() {
        val input = "Yarın saat üçte buluşalım"
        assertEquals(input, TurkishExtractiveSummarizer.summarize(input))
    }

    @Test
    fun should_strip_trailing_terminator_for_single_sentence() {
        // One terminator → splits to a single sentence; returned trimmed (terminator dropped).
        val result = TurkishExtractiveSummarizer.summarize("Toplantı iptal oldu.")
        assertEquals("Toplantı iptal oldu", result)
    }

    @Test
    fun should_pick_the_salient_sentence_from_multi_sentence_turkish_input() {
        // "proje" and "teslim" recur → the sentence carrying both should rank highest.
        val transcript = """
            Merhaba nasılsın bugün hava çok güzel.
            Proje teslim tarihi yaklaşıyor ve proje teslim için acele etmeliyiz.
            Akşam belki sinemaya gideriz.
        """.trimIndent()

        val summary = TurkishExtractiveSummarizer.summarize(transcript)

        assertTrue(summary.contains("Proje teslim"), "Expected salient project sentence, got: $summary")
        assertTrue(!summary.contains("sinemaya"), "Filler sentence should not be chosen: $summary")
    }

    @Test
    fun should_respect_the_char_cap() {
        val transcript = "Birinci cümle burada. İkinci cümle de burada. Üçüncü cümle yine burada."
        val cap = 25
        val summary = TurkishExtractiveSummarizer.summarize(transcript, maxChars = cap)
        assertTrue(summary.length <= cap, "Summary exceeded cap ($cap): '${summary}' len=${summary.length}")
    }

    @Test
    fun should_exclude_stop_words_from_scoring() {
        // A sentence stuffed only with stop-words must not out-score a content-bearing sentence,
        // even though it has more raw tokens.
        val transcript =
            "Ve ama fakat ancak veya çünkü gibi için kadar.\n" +
            "Ödeme bugün bankaya yapıldı ödeme tamamlandı."

        val summary = TurkishExtractiveSummarizer.summarize(transcript)

        assertTrue(summary.contains("Ödeme"), "Content sentence should win over stop-word sentence: $summary")
        // The pure stop-word sentence carries no signal → must not be padded into the summary.
        assertTrue(!summary.contains("fakat"), "Pure stop-word sentence should be excluded: $summary")
    }

    @Test
    fun should_emit_at_most_two_sentences() {
        val transcript =
            "Rapor hazır. Rapor gönderildi yöneticiye. Rapor onaylandı bugün. Başka konu yok."
        val summary = TurkishExtractiveSummarizer.summarize(transcript, maxChars = 500)
        // Count sentence-ish segments by splitting on the join space between restored sentences.
        // Each chosen sentence had its terminator stripped, so re-count via known markers.
        val chosenCount = summary.split(Regex("(?<=[a-zçğıöşü0-9])\\s(?=[A-ZÇĞİÖŞÜ])")).size
        assertTrue(chosenCount <= 2, "Expected at most 2 sentences, got $chosenCount in: $summary")
    }

    @Test
    fun should_handle_turkish_dotted_dotless_i_in_lowercasing() {
        // "İstanbul" and "istanbul" should be treated as the same content word for frequency.
        val transcript =
            "İstanbul bugün çok kalabalık.\n" +
            "Istanbul trafiği yüzünden istanbul merkezine geç vardık.\n" +
            "Hava serindi."
        val summary = TurkishExtractiveSummarizer.summarize(transcript)
        // The Istanbul-heavy sentence should be chosen (frequency unified across I/İ casing).
        assertTrue(
            summary.lowercase().contains("istanbul"),
            "Istanbul-frequency sentence should be salient: $summary"
        )
    }
}
