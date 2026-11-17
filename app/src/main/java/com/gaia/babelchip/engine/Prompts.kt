package com.gaia.babelchip.engine

/**
 * Prompts for the two inference passes.
 *
 * The on-device pass answers with three lines so that a small model can be
 * parsed reliably:
 *   TRANSLATION: ...
 *   CONFIDENCE: 0.82
 *   NUANCE: yes|no
 */
object Prompts {

    fun buildLocalPrompt(text: String, sourceLanguage: String, targetLanguage: String): String {
        return listOf(
            "You translate short spoken sentences from $sourceLanguage to $targetLanguage.",
            "Answer with exactly three lines, no markdown:",
            "TRANSLATION: the translated sentence, nothing else.",
            "CONFIDENCE: a number between 0 and 1.",
            "NUANCE: yes if the sentence contains idioms, slang, culture or tone that a simple translation would lose, otherwise no.",
            "",
            "SENTENCE: $text",
        ).joinToString("\n")
    }

    fun buildCloudPrompt(text: String, sourceLanguage: String, targetLanguage: String): String {
        return listOf(
            "You are a careful translator from $sourceLanguage to $targetLanguage.",
            "The first pass may have missed idioms, tone or cultural nuance.",
            "Answer with exactly one line, no markdown:",
            "TRANSLATION: the best natural translation, nothing else.",
            "",
            "SENTENCE: $text",
        ).joinToString("\n")
    }
}
