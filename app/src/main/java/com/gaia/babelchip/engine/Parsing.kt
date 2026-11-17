package com.gaia.babelchip.engine

/** Where an inference actually ran. */
enum class InferenceSource { ON_DEVICE, IN_CLOUD }

/** Result of the on-device pass: a draft translation and how sure it is. */
data class LocalDraft(
    val translation: String,
    val confidence: Double,
    val nuanceNeeded: Boolean,
)

/** One translated message, with the telemetry shown in the UI. */
data class Bubble(
    val original: String,
    val translation: String,
    val source: InferenceSource,
    val latencyMs: Int,
    val confidence: Double?,
    val escalated: Boolean,
)

object Parsing {

    /** Parse the three lines returned by the on-device model. */
    fun parseLocalOutput(raw: String): LocalDraft {
        var translation = ""
        var confidence = 0.0
        var nuance = false
        for (line in raw.lines()) {
            val clean = line.trim()
            when {
                clean.startsWith("TRANSLATION:", ignoreCase = true) ->
                    translation = valueAfter(clean, "TRANSLATION:")
                clean.startsWith("CONFIDENCE:", ignoreCase = true) ->
                    confidence = parseConfidence(valueAfter(clean, "CONFIDENCE:"))
                clean.startsWith("NUANCE:", ignoreCase = true) ->
                    nuance = valueAfter(clean, "NUANCE:").equals("yes", ignoreCase = true)
            }
        }
        return LocalDraft(translation, confidence, nuance)
    }

    /** Parse the single line returned by the cloud model. */
    fun parseCloudOutput(raw: String): String {
        for (line in raw.lines()) {
            val clean = line.trim()
            if (clean.startsWith("TRANSLATION:", ignoreCase = true)) {
                return valueAfter(clean, "TRANSLATION:")
            }
        }
        return raw.trim()
    }

    /** Clamp a confidence value into [0, 1]; unparseable values mean "unknown". */
    fun parseConfidence(raw: String): Double {
        val value = raw.trim().removeSuffix("%").toDoubleOrNull() ?: return 0.0
        val normalized = if (value > 1.0) value / 100.0 else value
        return normalized.coerceIn(0.0, 1.0)
    }

    /** The on-device pass is trusted above the threshold; otherwise we escalate. */
    fun needsEscalation(draft: LocalDraft, threshold: Double): Boolean {
        return draft.nuanceNeeded || draft.confidence < threshold
    }

    private fun valueAfter(line: String, marker: String): String {
        val index = line.indexOf(':')
        if (index < 0) return ""
        return line.substring(index + 1).trim()
    }
}
