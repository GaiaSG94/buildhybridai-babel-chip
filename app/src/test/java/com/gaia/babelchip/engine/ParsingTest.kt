package com.gaia.babelchip.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsingTest {

    @Test
    fun readsTheThreeLocalLines() {
        val raw = """
            TRANSLATION: Je voudrais un cafe, s'il vous plait.
            CONFIDENCE: 0.91
            NUANCE: no
        """.trimIndent()
        val draft = Parsing.parseLocalOutput(raw)
        assertEquals("Je voudrais un cafe, s'il vous plait.", draft.translation)
        assertEquals(0.91, draft.confidence, 0.001)
        assertFalse(draft.nuanceNeeded)
    }

    @Test
    fun flagsNuance() {
        val raw = "TRANSLATION: Ca marche.\nCONFIDENCE: 0.95\nNUANCE: yes"
        assertTrue(Parsing.parseLocalOutput(raw).nuanceNeeded)
    }

    @Test
    fun missingSectionsDoNotCrash() {
        val draft = Parsing.parseLocalOutput("I cannot help with that")
        assertEquals("", draft.translation)
        assertEquals(0.0, draft.confidence, 0.001)
    }

    @Test
    fun parsesPercentagesAndClamps() {
        assertEquals(0.85, Parsing.parseConfidence("85%"), 0.001)
        assertEquals(0.85, Parsing.parseConfidence("0.85"), 0.001)
        assertEquals(1.0, Parsing.parseConfidence("7"), 0.001)
        assertEquals(0.0, Parsing.parseConfidence("nope"), 0.001)
    }

    @Test
    fun escalatesOnLowConfidence() {
        val low = LocalDraft("Bonjour", 0.4, false)
        assertTrue(Parsing.needsEscalation(low, 0.75))
    }

    @Test
    fun escalatesOnNuanceEvenWithHighConfidence() {
        val draft = LocalDraft("Bonjour", 0.95, true)
        assertTrue(Parsing.needsEscalation(draft, 0.75))
    }

    @Test
    fun keepsTheLocalPassWhenConfident() {
        val draft = LocalDraft("Bonjour", 0.9, false)
        assertFalse(Parsing.needsEscalation(draft, 0.75))
    }

    @Test
    fun readsTheCloudLine() {
        assertEquals("Bonjour", Parsing.parseCloudOutput("TRANSLATION: Bonjour"))
        assertEquals("raw text", Parsing.parseCloudOutput("raw text"))
    }
}
