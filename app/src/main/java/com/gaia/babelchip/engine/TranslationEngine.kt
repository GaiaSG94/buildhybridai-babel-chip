package com.gaia.babelchip.engine

import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI

/**
 * Two-stage hybrid translation:
 *
 *  1. on-device pass (`ONLY_ON_DEVICE`) - fast, free, private;
 *  2. cloud pass (`ONLY_IN_CLOUD`) - nuance, only when the device pass is
 *     unsure (see [Parsing.needsEscalation]).
 *
 * Requires the Firebase app to be configured (google-services.json) and
 * App Check to be set up; call [configure] before translating.
 */
@OptIn(PublicPreviewAPI::class)
class TranslationEngine(
    private val cloudModelName: String = DEFAULT_CLOUD_MODEL,
    private val escalationThreshold: Double = DEFAULT_THRESHOLD,
) {

    var isReady: Boolean = false
        private set

    private var localModel: GenerativeModel? = null
    private var cloudModel: GenerativeModel? = null

    /** Build the two model instances; returns false when Firebase is not set up. */
    fun configure(ai: FirebaseAI): TranslationEngine {
        localModel = ai.generativeModel(
            modelName = cloudModelName,
            onDeviceConfig = OnDeviceConfig(mode = InferenceMode.ONLY_ON_DEVICE),
        )
        cloudModel = ai.generativeModel(
            modelName = cloudModelName,
            onDeviceConfig = OnDeviceConfig(mode = InferenceMode.ONLY_IN_CLOUD),
        )
        isReady = true
        return this
    }

    suspend fun translate(text: String, sourceLanguage: String, targetLanguage: String): Bubble {
        check(isReady) { "engine not configured: call configure(ai) first" }
        val started = System.currentTimeMillis()

        val local = localModel?.let { model ->
            try {
                val draft = Parsing.parseLocalOutput(
                    model.generateContent(
                        Prompts.buildLocalPrompt(text, sourceLanguage, targetLanguage),
                    ).text.orEmpty(),
                )
                if (draft.translation.isEmpty()) null else draft
            } catch (t: Throwable) {
                null
            }
        }

        val localLatency = (System.currentTimeMillis() - started).toInt()
        if (local != null && !Parsing.needsEscalation(local, escalationThreshold)) {
            return Bubble(
                original = text,
                translation = local.translation,
                source = InferenceSource.ON_DEVICE,
                latencyMs = localLatency,
                confidence = local.confidence,
                escalated = false,
            )
        }

        val cloud = cloudModel?.generateContent(
            Prompts.buildCloudPrompt(text, sourceLanguage, targetLanguage),
        ).text.orEmpty()
        val totalLatency = (System.currentTimeMillis() - started).toInt()
        return Bubble(
            original = text,
            translation = Parsing.parseCloudOutput(cloud),
            source = InferenceSource.IN_CLOUD,
            latencyMs = totalLatency,
            confidence = local?.confidence,
            escalated = local != null,
        )
    }

    companion object {
        // gemini-2.5-flash was retired on Vertex on 2026-10-20; this stable
        // model needs no billing account on the Gemini Developer API.
        const val DEFAULT_CLOUD_MODEL = "gemini-3.5-flash-lite"
        const val DEFAULT_THRESHOLD = 0.75
    }
}
