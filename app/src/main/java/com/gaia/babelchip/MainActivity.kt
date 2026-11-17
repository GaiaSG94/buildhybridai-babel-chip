package com.gaia.babelchip

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.gaia.babelchip.engine.Bubble
import com.gaia.babelchip.engine.InferenceSource
import com.gaia.babelchip.engine.TranslationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Babel Chip - live conversation translation with a visible hybrid handoff.
 *
 * The on-device model translates first; when its confidence is low (or the
 * sentence carries nuance) the cloud model takes over, and the bubble says
 * which path was used.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var engine: TranslationEngine
    private lateinit var statusView: TextView
    private lateinit var bubbles: LinearLayout
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private lateinit var sourceSpinner: Spinner
    private lateinit var targetSpinner: Spinner

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        setupLanguages()
        setupFirebase()
        sendButton.setOnClickListener { send() }
    }

    private fun bindViews() {
        statusView = findViewById(R.id.status)
        bubbles = findViewById(R.id.bubbles)
        input = findViewById(R.id.input)
        sendButton = findViewById(R.id.send)
        sourceSpinner = findViewById(R.id.source_language)
        targetSpinner = findViewById(R.id.target_language)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scroll)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
    }

    private fun setupLanguages() {
        val languages = listOf("English", "French", "Spanish", "Arabic", "Japanese")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languages)
        sourceSpinner.adapter = adapter
        targetSpinner.adapter = adapter
        sourceSpinner.setSelection(0)
        targetSpinner.setSelection(1)
    }

    private fun setupFirebase() {
        engine = TranslationEngine()
        try {
            FirebaseApp.initializeApp(this)
            FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            val ai = FirebaseAI.getInstance(backend = GenerativeBackend.googleAI())
            engine.configure(ai)
            statusView.text = getString(R.string.status_ready)
        } catch (t: Throwable) {
            statusView.text = getString(R.string.status_no_firebase)
            sendButton.isEnabled = false
        }
    }

    private fun send() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        val source = sourceSpinner.selectedItem.toString()
        val target = targetSpinner.selectedItem.toString()
        input.setText("")
        sendButton.isEnabled = false
        scope.launch {
            val bubble = try {
                withContext(Dispatchers.IO) {
                    engine.translate(text, source, target)
                }
            } catch (t: Throwable) {
                Bubble(text, "error: ${t.message}", InferenceSource.IN_CLOUD, 0, null, false)
            }
            bubbles.addView(buildBubble(bubble))
            statusView.text = if (bubble.source == InferenceSource.ON_DEVICE) {
                getString(R.string.status_ready)
            } else {
                getString(R.string.status_no_model)
            }
            sendButton.isEnabled = true
        }
    }

    private fun buildBubble(bubble: Bubble): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.VERTICAL
        row.setPadding(0, 16, 0, 16)

        val original = TextView(this)
        original.text = bubble.original
        original.alpha = 0.65f
        row.addView(original)

        val translation = TextView(this)
        translation.text = bubble.translation
        translation.textSize = 18f
        row.addView(translation)

        val meta = TextView(this)
        meta.text = metaText(bubble)
        meta.textSize = 12f
        meta.alpha = 0.8f
        row.addView(meta)

        return row
    }

    private fun metaText(bubble: Bubble): String {
        val confidence = bubble.confidence?.let { String.format("%.2f", it) } ?: "-"
        return when {
            bubble.source == InferenceSource.ON_DEVICE ->
                getString(R.string.meta_local, bubble.latencyMs, confidence)
            bubble.escalated ->
                getString(R.string.meta_escalated, bubble.latencyMs, confidence)
            else -> getString(R.string.meta_cloud, bubble.latencyMs)
        }
    }
}
