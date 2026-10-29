package com.medisense.app

import com.medisense.app.data.local.Converters
import com.medisense.app.data.local.entity.PredictionHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionHistoryUnitTest {

    private val converters = Converters()

    @Test
    fun testPredictionHistoryEntity_creationAndDefaults() {
        val entity = PredictionHistoryEntity(
            id = 1L,
            userId = "user-uuid-1234",
            predictedDisease = "Fungal Infection",
            confidence = 0.92f,
            symptoms = listOf("itching", "skin_rash", "nodal_skin_eruptions"),
            explanationSummary = "Itching and skin rash strongly contributed to the result.",
            predictionTimestamp = 1690000000000L,
            modelVersion = "1.0",
            pendingSync = true
        )

        assertEquals(1L, entity.id)
        assertEquals("user-uuid-1234", entity.userId)
        assertEquals("Fungal Infection", entity.predictedDisease)
        assertEquals(0.92f, entity.confidence, 0.001f)
        assertEquals(3, entity.symptoms.size)
        assertEquals("itching", entity.symptoms[0])
        assertEquals("Itching and skin rash strongly contributed to the result.", entity.explanationSummary)
        assertEquals(1690000000000L, entity.predictionTimestamp)
        assertEquals("1.0", entity.modelVersion)
        assertTrue(entity.pendingSync)
    }

    @Test
    fun testConverters_stringListSerialization() {
        val symptoms = listOf("Fever", "Cough", "Fatigue", "Body Pain")
        val json = converters.fromStringList(symptoms)
        assertNotNull(json)

        val deserialized = converters.toStringList(json)
        assertEquals(4, deserialized.size)
        assertEquals("Fever", deserialized[0])
        assertEquals("Body Pain", deserialized[3])
    }

    @Test
    fun testConverters_emptyAndNullHandling() {
        val emptyJson = converters.fromStringList(emptyList())
        val deserializedEmpty = converters.toStringList(emptyJson)
        assertTrue(deserializedEmpty.isEmpty())

        val nullResult = converters.toStringList(null)
        assertTrue(nullResult.isEmpty())

        val blankResult = converters.toStringList("   ")
        assertTrue(blankResult.isEmpty())

        val malformedResult = converters.toStringList("not a json")
        assertTrue(malformedResult.isEmpty())
    }

    @Test
    fun testConverters_intListSerialization() {
        val values = listOf(1, 2, 3, 42)
        val json = converters.fromIntList(values)
        assertNotNull(json)

        val deserialized = converters.toIntList(json)
        assertEquals(4, deserialized.size)
        assertEquals(1, deserialized[0])
        assertEquals(42, deserialized[3])
    }

    @Test
    fun testConverters_intListEmptyAndNullHandling() {
        val emptyJson = converters.fromIntList(emptyList())
        val deserializedEmpty = converters.toIntList(emptyJson)
        assertTrue(deserializedEmpty.isEmpty())

        val nullResult = converters.toIntList(null)
        assertTrue(nullResult.isEmpty())

        val blankResult = converters.toIntList("   ")
        assertTrue(blankResult.isEmpty())

        val malformedResult = converters.toIntList("malformed json")
        assertTrue(malformedResult.isEmpty())
    }
}
