package com.medisense.app

import com.medisense.app.data.local.dao.*
import com.medisense.app.data.local.entity.*
import com.medisense.app.data.remote.supabase.AuthService
import com.medisense.app.data.repository.*
import com.medisense.app.domain.engine.HealthTimelineEngine
import com.medisense.app.domain.guidance.PersonalizedGuidanceEngine
import com.medisense.app.domain.model.*
import com.medisense.app.domain.quality.*
import com.medisense.app.domain.risk.ContextAwareRiskEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HealthTimelineUnitTest {

    private lateinit var fakeHealthProfileDao: FakeHealthProfileDao
    private lateinit var fakeMedicationDao: FakeMedicationDao
    private lateinit var fakeMedicationHistoryDao: FakeMedicationHistoryDao
    private lateinit var fakeAppointmentDao: FakeAppointmentDao
    private lateinit var fakePredictionHistoryDao: FakePredictionHistoryDao
    private lateinit var fakeSecurityAuditDao: FakeSecurityAuditEventDao
    private lateinit var fakeAuthService: FakeAuthService

    private lateinit var securityAuditRepository: SecurityAuditRepository
    private lateinit var healthDataQualityRepository: HealthDataQualityRepository
    private lateinit var personalHealthContextRepository: PersonalHealthContextRepository
    private lateinit var longitudinalHealthRepository: LongitudinalHealthRepository
    private lateinit var rchrRepository: RchrRepository
    private lateinit var contextualRiskRepository: ContextualRiskRepository
    private lateinit var personalizedGuidanceRepository: PersonalizedGuidanceRepository
    private lateinit var healthTimelineRepository: HealthTimelineRepository

    private val testUserId = "user-timeline-12345"
    private val otherUserId = "user-timeline-99999"

    @Before
    fun setUp() {
        fakeHealthProfileDao = FakeHealthProfileDao()
        fakeMedicationDao = FakeMedicationDao()
        fakeMedicationHistoryDao = FakeMedicationHistoryDao()
        fakeAppointmentDao = FakeAppointmentDao()
        fakePredictionHistoryDao = FakePredictionHistoryDao()
        fakeSecurityAuditDao = FakeSecurityAuditEventDao()

        fakeAuthService = FakeAuthService(userId = testUserId, email = "timeline_user@medisense.app")
        securityAuditRepository = SecurityAuditRepository(fakeSecurityAuditDao, fakeAuthService)

        val qualityEngine = HealthDataQualityEngine(
            profileValidator = ProfileDataValidator(),
            medicationValidator = MedicationDataValidator(),
            appointmentValidator = AppointmentDataValidator(),
            predictionValidator = PredictionDataValidator(),
            temporalValidator = TemporalConsistencyValidator()
        )

        healthDataQualityRepository = HealthDataQualityRepository(
            authService = fakeAuthService,
            qualityEngine = qualityEngine,
            healthProfileDao = fakeHealthProfileDao,
            medicationDao = fakeMedicationDao,
            medicationHistoryDao = fakeMedicationHistoryDao,
            appointmentDao = fakeAppointmentDao,
            predictionHistoryDao = fakePredictionHistoryDao,
            syncMetadataDao = FakeSyncMetadataDao(),
            securityAuditRepository = securityAuditRepository
        )

        personalHealthContextRepository = PersonalHealthContextRepository(
            healthProfileDao = fakeHealthProfileDao,
            predictionHistoryDao = fakePredictionHistoryDao,
            medicationDao = fakeMedicationDao,
            medicationHistoryDao = fakeMedicationHistoryDao,
            appointmentDao = fakeAppointmentDao,
            authService = fakeAuthService
        )

        longitudinalHealthRepository = LongitudinalHealthRepository(
            predictionHistoryDao = fakePredictionHistoryDao,
            medicationDao = fakeMedicationDao,
            medicationHistoryDao = fakeMedicationHistoryDao,
            appointmentDao = fakeAppointmentDao,
            authService = fakeAuthService
        )

        rchrRepository = RchrRepository(
            healthProfileDao = fakeHealthProfileDao,
            predictionHistoryDao = fakePredictionHistoryDao,
            medicationDao = fakeMedicationDao,
            medicationHistoryDao = fakeMedicationHistoryDao,
            appointmentDao = fakeAppointmentDao,
            authService = fakeAuthService
        )

        contextualRiskRepository = ContextualRiskRepository(
            personalHealthContextRepository = personalHealthContextRepository,
            longitudinalHealthRepository = longitudinalHealthRepository,
            rchrRepository = rchrRepository,
            riskEngine = ContextAwareRiskEngine(),
            authService = fakeAuthService
        )

        personalizedGuidanceRepository = PersonalizedGuidanceRepository(
            personalHealthContextRepository = personalHealthContextRepository,
            longitudinalHealthRepository = longitudinalHealthRepository,
            rchrRepository = rchrRepository,
            contextualRiskRepository = contextualRiskRepository,
            guidanceEngine = PersonalizedGuidanceEngine(),
            authService = fakeAuthService
        )

        healthTimelineRepository = HealthTimelineRepository(
            authService = fakeAuthService,
            healthProfileDao = fakeHealthProfileDao,
            medicationDao = fakeMedicationDao,
            medicationHistoryDao = fakeMedicationHistoryDao,
            appointmentDao = fakeAppointmentDao,
            predictionHistoryDao = fakePredictionHistoryDao,
            securityAuditEventDao = fakeSecurityAuditDao,
            personalHealthContextRepository = personalHealthContextRepository,
            longitudinalHealthRepository = longitudinalHealthRepository,
            contextualRiskRepository = contextualRiskRepository,
            personalizedGuidanceRepository = personalizedGuidanceRepository,
            healthDataQualityRepository = healthDataQualityRepository,
            securityAuditRepository = securityAuditRepository
        )
    }

    // =========================================================================
    // 1. Timeline Event Conversion Across Sources
    // =========================================================================
    @Test
    fun testTimelineEventConversion_acrossAllEntities() {
        val now = System.currentTimeMillis()

        val profile = HealthProfileEntity(
            id = "prof-1",
            userId = testUserId,
            fullName = "Jane Doe",
            dateOfBirth = "1990-01-01",
            gender = "Female",
            bloodGroup = "A+",
            height = 168.0,
            weight = 62.0,
            allergies = "Peanuts",
            existingDiseases = "Hypertension",
            currentMedications = "Amlodipine",
            familyHistory = "Diabetes",
            emergencyContactName = "John",
            emergencyContactNumber = "555-0100",
            notes = "Test notes",
            createdAt = now.toString(),
            updatedAt = now.toString()
        )

        val prediction = PredictionHistoryEntity(
            id = 101L,
            userId = testUserId,
            predictedDisease = "Hypertension",
            confidence = 0.88f,
            symptoms = listOf("headache", "dizziness"),
            explanationSummary = "Elevated blood pressure indicators detected",
            predictionTimestamp = now - 10000L
        )

        val medication = MedicationEntity(
            id = 201L,
            userId = testUserId,
            medicineName = "Amlodipine",
            dosage = "5",
            dosageUnit = "mg",
            frequency = "ONCE_DAILY",
            scheduledTimes = listOf("08:00"),
            startDate = now - 20000L,
            active = true
        )

        val medHistory = MedicationHistoryEntity(
            id = 301L,
            medicationId = 201L,
            userId = testUserId,
            medicineName = "Amlodipine",
            dosage = "5 mg",
            scheduledDate = now - 15000L,
            scheduledTime = "08:00",
            actionTime = now - 15000L,
            status = "TAKEN"
        )

        val appointment = AppointmentEntity(
            id = 401L,
            userId = testUserId,
            doctorName = "Dr. Smith",
            clinicName = "Central Clinic",
            appointmentType = "Cardiology Consultation",
            appointmentDate = "2026-09-15",
            appointmentTime = "10:00",
            appointmentTimestamp = now + 50000L,
            status = "SCHEDULED"
        )

        val (events, summary) = HealthTimelineEngine.buildTimeline(
            userId = testUserId,
            profile = profile,
            predictions = listOf(prediction),
            medications = listOf(medication),
            medicationHistory = listOf(medHistory),
            appointments = listOf(appointment),
            currentTime = now
        )

        assertTrue(events.isNotEmpty())
        assertEquals(5, events.size)
        assertEquals(5, summary.totalEventsCount)

        val types = events.map { it.eventType }
        assertTrue(types.contains(HealthTimelineEventType.PROFILE_UPDATED))
        assertTrue(types.contains(HealthTimelineEventType.PREDICTION))
        assertTrue(types.contains(HealthTimelineEventType.MEDICATION_STARTED))
        assertTrue(types.contains(HealthTimelineEventType.MEDICATION_TAKEN))
        assertTrue(types.contains(HealthTimelineEventType.APPOINTMENT_SCHEDULED))
    }

    // =========================================================================
    // 2. Chronological Sorting (Newest First vs Oldest First)
    // =========================================================================
    @Test
    fun testChronologicalSorting_newestFirst_and_oldestFirst() {
        val now = System.currentTimeMillis()

        val pred1 = PredictionHistoryEntity(id = 1L, userId = testUserId, predictedDisease = "Condition A", confidence = 0.7f, symptoms = listOf("s1"), predictionTimestamp = now - 30000L)
        val pred2 = PredictionHistoryEntity(id = 2L, userId = testUserId, predictedDisease = "Condition B", confidence = 0.8f, symptoms = listOf("s2"), predictionTimestamp = now - 10000L)
        val pred3 = PredictionHistoryEntity(id = 3L, userId = testUserId, predictedDisease = "Condition C", confidence = 0.9f, symptoms = listOf("s3"), predictionTimestamp = now - 20000L)

        // Newest First (Default)
        val (newestEvents, _) = HealthTimelineEngine.buildTimeline(
            userId = testUserId,
            predictions = listOf(pred1, pred2, pred3),
            filter = HealthTimelineFilter(sortOrder = HealthTimelineSortOrder.NEWEST_FIRST),
            currentTime = now
        )

        assertEquals(3, newestEvents.size)
        assertEquals("prediction_2", newestEvents[0].eventId) // timestamp: now - 10000
        assertEquals("prediction_3", newestEvents[1].eventId) // timestamp: now - 20000
        assertEquals("prediction_1", newestEvents[2].eventId) // timestamp: now - 30000

        // Oldest First
        val (oldestEvents, _) = HealthTimelineEngine.buildTimeline(
            userId = testUserId,
            predictions = listOf(pred1, pred2, pred3),
            filter = HealthTimelineFilter(sortOrder = HealthTimelineSortOrder.OLDEST_FIRST),
            currentTime = now
        )

        assertEquals("prediction_1", oldestEvents[0].eventId)
        assertEquals("prediction_3", oldestEvents[1].eventId)
        assertEquals("prediction_2", oldestEvents[2].eventId)
    }

    // =========================================================================
    // 3. Deterministic Same-Timestamp Ordering
    // =========================================================================
    @Test
    fun testDeterministicSameTimestampOrdering() {
        val sameTime = 1725700000000L

        val predA = PredictionHistoryEntity(id = 10L, userId = testUserId, predictedDisease = "Disease A", confidence = 0.7f, symptoms = listOf("s1"), predictionTimestamp = sameTime)
        val predB = PredictionHistoryEntity(id = 20L, userId = testUserId, predictedDisease = "Disease B", confidence = 0.8f, symptoms = listOf("s2"), predictionTimestamp = sameTime)

        val (events1, _) = HealthTimelineEngine.buildTimeline(
            userId = testUserId,
            predictions = listOf(predB, predA),
            filter = HealthTimelineFilter(sortOrder = HealthTimelineSortOrder.NEWEST_FIRST),
            currentTime = sameTime + 5000L
        )

        val (events2, _) = HealthTimelineEngine.buildTimeline(
            userId = testUserId,
            predictions = listOf(predA, predB),
            filter = HealthTimelineFilter(sortOrder = HealthTimelineSortOrder.NEWEST_FIRST),
            currentTime = sameTime + 5000L
        )

        assertEquals(events1.size, events2.size)
        assertEquals(events1[0].eventId, events2[0].eventId)
        assertEquals(events1[1].eventId, events2[1].eventId)
    }

    // =========================================================================
    // 4. Event Grouping for Close-in-Time Redundant Medication Actions
    // =========================================================================
    @Test
    fun testMedicationHistoryGrouping_deduplicatesWithinThreshold() {
        val now = System.currentTimeMillis()

        val hist1 = MedicationHistoryEntity(id = 1L, medicationId = 100L, userId = testUserId, medicineName = "Metformin", dosage = "500mg", scheduledDate = now - 50000L, scheduledTime = "08:00", actionTime = now - 50000L, status = "TAKEN")
        // Redundant duplicate action logged 1 minute later
        val hist2 = MedicationHistoryEntity(id = 2L, medicationId = 100L, userId = testUserId, medicineName = "Metformin", dosage = "500mg", scheduledDate = now - 50000L, scheduledTime = "08:00", actionTime = now - 50000L + (60 * 1000L), status = "TAKEN")
        // Different medication action
        val hist3 = MedicationHistoryEntity(id = 3L, medicationId = 200L, userId = testUserId, medicineName = "Aspirin", dosage = "100mg", scheduledDate = now - 50000L, scheduledTime = "08:00", actionTime = now - 50000L, status = "TAKEN")

        val (events, _) = HealthTimelineEngine.buildTimeline(
            userId = testUserId,
            medicationHistory = listOf(hist1, hist2, hist3),
            currentTime = now
        )

        // hist1 and hist2 are grouped into 1 entry; hist3 is separate
        assertEquals(2, events.size)
    }

    // =========================================================================
    // 5. Time-Range Filtering (7d, 30d, 90d, This Year, All)
    // =========================================================================
    @Test
    fun testTimeRangeFiltering() {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L

        val pred3d = PredictionHistoryEntity(id = 1L, userId = testUserId, predictedDisease = "P1", confidence = 0.8f, symptoms = listOf("s"), predictionTimestamp = now - (3 * dayMs))
        val pred15d = PredictionHistoryEntity(id = 2L, userId = testUserId, predictedDisease = "P2", confidence = 0.8f, symptoms = listOf("s"), predictionTimestamp = now - (15 * dayMs))
        val pred60d = PredictionHistoryEntity(id = 3L, userId = testUserId, predictedDisease = "P3", confidence = 0.8f, symptoms = listOf("s"), predictionTimestamp = now - (60 * dayMs))
        val pred200d = PredictionHistoryEntity(id = 4L, userId = testUserId, predictedDisease = "P4", confidence = 0.8f, symptoms = listOf("s"), predictionTimestamp = now - (200 * dayMs))

        val allPreds = listOf(pred3d, pred15d, pred60d, pred200d)

        // 7 Days
        val (events7d, _) = HealthTimelineEngine.buildTimeline(userId = testUserId, predictions = allPreds, filter = HealthTimelineFilter(period = HealthTimelinePeriod.LAST_7_DAYS), currentTime = now)
        assertEquals(1, events7d.size)
        assertEquals("prediction_1", events7d[0].eventId)

        // 30 Days
        val (events30d, _) = HealthTimelineEngine.buildTimeline(userId = testUserId, predictions = allPreds, filter = HealthTimelineFilter(period = HealthTimelinePeriod.LAST_30_DAYS), currentTime = now)
        assertEquals(2, events30d.size)

        // 90 Days
        val (events90d, _) = HealthTimelineEngine.buildTimeline(userId = testUserId, predictions = allPreds, filter = HealthTimelineFilter(period = HealthTimelinePeriod.LAST_90_DAYS), currentTime = now)
        assertEquals(3, events90d.size)

        // All Time
        val (eventsAll, _) = HealthTimelineEngine.buildTimeline(userId = testUserId, predictions = allPreds, filter = HealthTimelineFilter(period = HealthTimelinePeriod.ALL), currentTime = now)
        assertEquals(4, eventsAll.size)
    }

    // =========================================================================
    // 6. Event-Category Filtering
    // =========================================================================
    @Test
    fun testEventCategoryFiltering() {
        val now = System.currentTimeMillis()

        val pred = PredictionHistoryEntity(id = 1L, userId = testUserId, predictedDisease = "Flu", confidence = 0.8f, symptoms = listOf("fever"), predictionTimestamp = now)
        val med = MedicationEntity(id = 10L, userId = testUserId, medicineName = "Ibuprofen", dosage = "200", dosageUnit = "mg", frequency = "PRN", startDate = now)
        val appt = AppointmentEntity(id = 20L, userId = testUserId, doctorName = "Dr. House", clinicName = "Clinic", appointmentType = "Consult", appointmentDate = "2026-09-10", appointmentTime = "10:00", appointmentTimestamp = now + 1000L)

        // Filter: PREDICTIONS only
        val (predEvents, _) = HealthTimelineEngine.buildTimeline(
            userId = testUserId,
            predictions = listOf(pred),
            medications = listOf(med),
            appointments = listOf(appt),
            filter = HealthTimelineFilter(category = HealthTimelineCategoryFilter.PREDICTIONS),
            currentTime = now
        )
        assertEquals(1, predEvents.size)
        assertEquals(HealthTimelineCategoryFilter.PREDICTIONS, predEvents[0].eventType.category)

        // Filter: MEDICATIONS only
        val (medEvents, _) = HealthTimelineEngine.buildTimeline(
            userId = testUserId,
            predictions = listOf(pred),
            medications = listOf(med),
            appointments = listOf(appt),
            filter = HealthTimelineFilter(category = HealthTimelineCategoryFilter.MEDICATIONS),
            currentTime = now
        )
        assertEquals(1, medEvents.size)
        assertEquals(HealthTimelineCategoryFilter.MEDICATIONS, medEvents[0].eventType.category)

        // Filter: APPOINTMENTS only
        val (apptEvents, _) = HealthTimelineEngine.buildTimeline(
            userId = testUserId,
            predictions = listOf(pred),
            medications = listOf(med),
            appointments = listOf(appt),
            filter = HealthTimelineFilter(category = HealthTimelineCategoryFilter.APPOINTMENTS),
            currentTime = now
        )
        assertEquals(1, apptEvents.size)
        assertEquals(HealthTimelineCategoryFilter.APPOINTMENTS, apptEvents[0].eventType.category)
    }

    // =========================================================================
    // 7. Strict User Isolation
    // =========================================================================
    @Test
    fun testUserIsolation_ignoresOtherUserRecords() {
        val now = System.currentTimeMillis()

        val userPred = PredictionHistoryEntity(id = 1L, userId = testUserId, predictedDisease = "Condition A", confidence = 0.8f, symptoms = listOf("s1"), predictionTimestamp = now)
        val otherPred = PredictionHistoryEntity(id = 2L, userId = otherUserId, predictedDisease = "Condition B (SECRET)", confidence = 0.9f, symptoms = listOf("s2"), predictionTimestamp = now)

        val userMed = MedicationEntity(id = 10L, userId = testUserId, medicineName = "Medicine A", dosage = "10mg", frequency = "DAILY", startDate = now)
        val otherMed = MedicationEntity(id = 20L, userId = otherUserId, medicineName = "Medicine B (SECRET)", dosage = "50mg", frequency = "DAILY", startDate = now)

        val (events, _) = HealthTimelineEngine.buildTimeline(
            userId = testUserId,
            predictions = listOf(userPred, otherPred),
            medications = listOf(userMed, otherMed),
            currentTime = now
        )

        assertEquals(2, events.size)
        assertTrue(events.none { it.userId == otherUserId })
        assertTrue(events.none { it.title.contains("SECRET") })
        assertTrue(events.none { it.shortDescription.contains("SECRET") })
    }

    // =========================================================================
    // 8. Empty & Unauthenticated Timeline
    // =========================================================================
    @Test
    fun testEmptyAndUnauthenticatedTimeline() {
        val (emptyEvents, emptySummary) = HealthTimelineEngine.buildTimeline(
            userId = testUserId,
            predictions = emptyList(),
            medications = emptyList(),
            appointments = emptyList()
        )
        assertTrue(emptyEvents.isEmpty())
        assertEquals(0, emptySummary.totalEventsCount)

        val (unauthEvents, unauthSummary) = HealthTimelineEngine.buildTimeline(
            userId = ""
        )
        assertTrue(unauthEvents.isEmpty())
        assertEquals(0, unauthSummary.totalEventsCount)
    }

    // =========================================================================
    // 9. Single-Event Timeline
    // =========================================================================
    @Test
    fun testSingleEventTimeline() {
        val now = System.currentTimeMillis()
        val singleAppt = AppointmentEntity(
            id = 55L,
            userId = testUserId,
            doctorName = "Dr. Watson",
            clinicName = "Baker Clinic",
            appointmentType = "Checkup",
            appointmentDate = "2026-09-12",
            appointmentTime = "14:00",
            appointmentTimestamp = now + 10000L
        )

        val (events, summary) = HealthTimelineEngine.buildTimeline(
            userId = testUserId,
            appointments = listOf(singleAppt),
            currentTime = now
        )

        assertEquals(1, events.size)
        assertEquals(1, summary.totalEventsCount)
        assertEquals(1, summary.upcomingAppointmentsCount)
        assertEquals("appt_55", events[0].eventId)
        assertEquals("Appointment Scheduled: Dr. Watson", events[0].title)
    }

    // =========================================================================
    // 10. Large Event History Resilience & Performance
    // =========================================================================
    @Test
    fun testLargeEventHistoryPerformance() {
        val now = System.currentTimeMillis()
        val preds = (1..50).map { i ->
            PredictionHistoryEntity(
                id = i.toLong(),
                userId = testUserId,
                predictedDisease = "Condition $i",
                confidence = 0.5f + (i % 50) * 0.01f,
                symptoms = listOf("symptom_$i"),
                predictionTimestamp = now - (i * 100000L)
            )
        }

        val meds = (1..50).map { i ->
            MedicationHistoryEntity(
                id = (100 + i).toLong(),
                medicationId = i.toLong(),
                userId = testUserId,
                medicineName = "Drug $i",
                dosage = "${i * 5}mg",
                scheduledDate = now - (i * 100000L),
                scheduledTime = "09:00",
                actionTime = now - (i * 100000L),
                status = if (i % 2 == 0) "TAKEN" else "SKIPPED"
            )
        }

        val start = System.currentTimeMillis()
        val (events, summary) = HealthTimelineEngine.buildTimeline(
            userId = testUserId,
            predictions = preds,
            medicationHistory = meds,
            currentTime = now
        )
        val elapsed = System.currentTimeMillis() - start

        assertEquals(100, events.size)
        assertEquals(100, summary.totalEventsCount)
        assertTrue("Aggregation must complete in under 200ms", elapsed < 200)
    }

    // =========================================================================
    // 11. Missing Optional Fields & Incomplete Records Resilience
    // =========================================================================
    @Test
    fun testMissingOptionalFieldsResilience() {
        val now = System.currentTimeMillis()

        val incompleteProfile = HealthProfileEntity(
            id = "prof-inc",
            userId = testUserId,
            fullName = null,
            dateOfBirth = null,
            gender = null,
            bloodGroup = null,
            height = null,
            weight = null,
            allergies = null,
            existingDiseases = null,
            currentMedications = null,
            familyHistory = null,
            emergencyContactName = null,
            emergencyContactNumber = null,
            notes = null
        )

        val incompleteMed = MedicationEntity(
            id = 500L,
            userId = testUserId,
            medicineName = "Incomplete Drug",
            dosage = "",
            dosageUnit = "",
            frequency = "",
            scheduledTimes = emptyList(),
            startDate = 0L,
            instructions = ""
        )

        val (events, summary) = HealthTimelineEngine.buildTimeline(
            userId = testUserId,
            profile = incompleteProfile,
            medications = listOf(incompleteMed),
            currentTime = now
        )

        assertEquals(2, events.size)
        assertNotNull(events[0].explanation)
        assertNotNull(events[1].explanation)
        assertFalse(events[0].title.isBlank())
        assertFalse(events[1].title.isBlank())
    }

    // =========================================================================
    // 12. Non-Diagnostic Medical Safety Wording
    // =========================================================================
    @Test
    fun testNonDiagnosticMedicalSafetyWording() {
        val now = System.currentTimeMillis()
        val pred = PredictionHistoryEntity(
            id = 1L,
            userId = testUserId,
            predictedDisease = "Pneumonia",
            confidence = 0.95f,
            symptoms = listOf("cough", "fever", "chest_pain"),
            predictionTimestamp = now
        )

        val (events, summary) = HealthTimelineEngine.buildTimeline(
            userId = testUserId,
            predictions = listOf(pred),
            currentTime = now
        )

        val event = events[0]

        // Must state "prediction", "model output", "estimated", NOT diagnosis
        assertTrue(event.shortDescription.contains("Model output", ignoreCase = true) || event.shortDescription.contains("confidence", ignoreCase = true))
        assertTrue(event.detailedDescription.contains("not a medical diagnosis", ignoreCase = true) || event.detailedDescription.contains("educational", ignoreCase = true))
        assertFalse(event.shortDescription.contains("You have diagnosed", ignoreCase = true))
        assertFalse(event.detailedDescription.contains("You are diagnosed with", ignoreCase = true))

        // Safety disclaimer must be present on summary
        assertTrue(summary.safetyDisclaimer.contains("not medical diagnoses", ignoreCase = true))
    }

    // =========================================================================
    // 13. Medication Event Wording (No dosage alterations or prescriptions)
    // =========================================================================
    @Test
    fun testMedicationEventWording() {
        val now = System.currentTimeMillis()
        val med = MedicationEntity(
            id = 1L,
            userId = testUserId,
            medicineName = "Lisinopril",
            dosage = "10",
            dosageUnit = "mg",
            frequency = "DAILY",
            startDate = now
        )

        val (events, _) = HealthTimelineEngine.buildTimeline(
            userId = testUserId,
            medications = listOf(med),
            currentTime = now
        )

        val event = events[0]
        assertFalse(event.detailedDescription.contains("Take more", ignoreCase = true))
        assertFalse(event.detailedDescription.contains("Change your dose", ignoreCase = true))
        assertFalse(event.detailedDescription.contains("Stop taking", ignoreCase = true))
    }

    // =========================================================================
    // 14. Data Quality & Sync Event Handling
    // =========================================================================
    @Test
    fun testDataQualityAndSyncEventHandling() {
        val now = System.currentTimeMillis()

        val dqSummary = HealthDataQualitySummary(
            status = HealthDataQualityStatus.NEEDS_ATTENTION,
            qualityScore = 75,
            totalChecks = 10,
            passedChecks = 8,
            errorCount = 0,
            warningCount = 2,
            infoCount = 0,
            issues = listOf(
                HealthDataQualityIssue(
                    id = "dq-1",
                    category = HealthDataQualityCategory.PROFILE_COMPLETENESS,
                    severity = HealthDataQualitySeverity.WARNING,
                    title = "Incomplete Health Profile",
                    explanation = "Missing blood group and height entries.",
                    affectedRecordType = "HEALTH_PROFILE",
                    recordId = null,
                    suggestedCorrection = "Complete your health profile in Profile settings."
                )
            ),
            categoryBreakdown = emptyMap(),
            evaluatedTimestamp = now
        )

        val auditSync = SecurityAuditEventEntity(
            id = 99L,
            userId = testUserId,
            eventType = SecurityAuditEventType.CLOUD_SYNC.name,
            timestamp = now - 5000L,
            description = "Synced 5 local records with cloud storage."
        )

        val (events, summary) = HealthTimelineEngine.buildTimeline(
            userId = testUserId,
            auditEvents = listOf(auditSync),
            dataQualitySummary = dqSummary,
            currentTime = now
        )

        assertEquals(2, events.size)
        assertEquals(HealthDataQualityStatus.NEEDS_ATTENTION, summary.dataQualityStatus)

        val types = events.map { it.eventType }
        assertTrue(types.contains(HealthTimelineEventType.DATA_QUALITY_ISSUE))
        assertTrue(types.contains(HealthTimelineEventType.SYNC_EVENT))
    }

    // =========================================================================
    // 15. Reactive Repository Observation
    // =========================================================================
    @Test
    fun testHealthTimelineRepository_observeTimelineFlow() = runBlocking {
        val now = System.currentTimeMillis()

        fakePredictionHistoryDao.insertPredictionHistory(
            PredictionHistoryEntity(
                id = 1L,
                userId = testUserId,
                predictedDisease = "Migraine",
                confidence = 0.85f,
                symptoms = listOf("headache", "nausea"),
                predictionTimestamp = now
            )
        )

        val (events, summary) = healthTimelineRepository.observeTimeline(flowOf(HealthTimelineFilter())).first()

        assertTrue(events.isNotEmpty())
        assertTrue(summary.totalEventsCount >= 1)
        val predEvent = events.firstOrNull { it.eventId == "prediction_1" }
        assertNotNull(predEvent)
        assertEquals(HealthTimelineSource.DISEASE_PREDICTION, predEvent?.source)
    }

    // =========================================================================
    // 16. Repository Snapshot Execution
    // =========================================================================
    @Test
    fun testHealthTimelineRepository_getTimelineSnapshot() = runBlocking {
        val now = System.currentTimeMillis()

        fakeAppointmentDao.insertAppointment(
            AppointmentEntity(
                id = 12L,
                userId = testUserId,
                doctorName = "Dr. Adams",
                clinicName = "City Health",
                appointmentType = "Dental",
                appointmentDate = "2026-09-20",
                appointmentTime = "11:00",
                appointmentTimestamp = now + 100000L
            )
        )

        val (events, summary) = healthTimelineRepository.getTimelineSnapshot(HealthTimelineFilter())

        assertTrue(events.isNotEmpty())
        val apptEvent = events.firstOrNull { it.eventId == "appt_12" }
        assertNotNull(apptEvent)
        assertEquals(1, summary.upcomingAppointmentsCount)
    }

    // =========================================================================
    // 17. Search Query Filtering
    // =========================================================================
    @Test
    fun testSearchQueryFiltering() {
        val now = System.currentTimeMillis()

        val pred = PredictionHistoryEntity(id = 1L, userId = testUserId, predictedDisease = "Asthma", confidence = 0.8f, symptoms = listOf("wheezing"), predictionTimestamp = now)
        val med = MedicationEntity(id = 2L, userId = testUserId, medicineName = "Albuterol Inhaler", dosage = "2 puffs", frequency = "PRN", startDate = now)

        val (results, _) = HealthTimelineEngine.buildTimeline(
            userId = testUserId,
            predictions = listOf(pred),
            medications = listOf(med),
            filter = HealthTimelineFilter(searchQuery = "Inhaler"),
            currentTime = now
        )

        assertEquals(1, results.size)
        assertEquals("med_start_2", results[0].eventId)
    }

    // =========================================================================
    // 18. Deterministic Output Across Repeated Executions
    // =========================================================================
    @Test
    fun testDeterministicOutputAcrossRepeatedExecutions() {
        val now = System.currentTimeMillis()
        val pred = PredictionHistoryEntity(id = 1L, userId = testUserId, predictedDisease = "Diabetes", confidence = 0.9f, symptoms = listOf("fatigue", "thirst"), predictionTimestamp = now)
        val med = MedicationEntity(id = 2L, userId = testUserId, medicineName = "Metformin", dosage = "500mg", frequency = "DAILY", startDate = now)

        val (run1Events, run1Summary) = HealthTimelineEngine.buildTimeline(userId = testUserId, predictions = listOf(pred), medications = listOf(med), currentTime = now)
        val (run2Events, run2Summary) = HealthTimelineEngine.buildTimeline(userId = testUserId, predictions = listOf(pred), medications = listOf(med), currentTime = now)

        assertEquals(run1Events.size, run2Events.size)
        assertEquals(run1Summary.totalEventsCount, run2Summary.totalEventsCount)
        assertEquals(run1Events[0].eventId, run2Events[0].eventId)
        assertEquals(run1Events[0].title, run2Events[0].title)
        assertEquals(run1Events[1].eventId, run2Events[1].eventId)
        assertEquals(run1Events[1].title, run2Events[1].title)
    }

    // =========================================================================
    // Test Doubles / In-memory Fakes
    // =========================================================================

    private class FakeAuthService(
        private var userId: String?,
        private var email: String?
    ) : AuthService(
        io.github.jan.supabase.createSupabaseClient(
            supabaseUrl = "https://placeholder.supabase.co",
            supabaseKey = "dummy-anon-key"
        ) {},
        com.medisense.app.data.local.session.SharedPreferencesSessionManager(null)
    ) {
        override fun getCurrentUserId(): String? = userId
        override fun getCurrentUserEmail(): String? = email
    }

    private class FakeHealthProfileDao : HealthProfileDao {
        val profiles = mutableListOf<HealthProfileEntity>()
        override fun observeHealthProfile(userId: String): Flow<HealthProfileEntity?> = flowOf(profiles.find { it.userId == userId })
        override suspend fun getHealthProfile(userId: String): HealthProfileEntity? = profiles.find { it.userId == userId }
        override suspend fun insertHealthProfile(profile: HealthProfileEntity) {
            profiles.removeAll { it.userId == profile.userId }
            profiles.add(profile)
        }
        override suspend fun updateHealthProfile(profile: HealthProfileEntity) {
            profiles.removeAll { it.userId == profile.userId }
            profiles.add(profile)
        }
        override suspend fun deleteHealthProfile(profile: HealthProfileEntity) { profiles.remove(profile) }
        override suspend fun deleteHealthProfileByUserId(userId: String) { profiles.removeAll { it.userId == userId } }
        override suspend fun getPendingSyncProfiles(): List<HealthProfileEntity> = emptyList()
        override suspend fun getPendingSyncProfileForUser(userId: String): HealthProfileEntity? = null
        override suspend fun markProfileSynced(userId: String) {}
    }

    private class FakeMedicationDao : MedicationDao {
        val meds = mutableListOf<MedicationEntity>()
        override fun getMedicationsForUser(userId: String): Flow<List<MedicationEntity>> = flowOf(meds.filter { it.userId == userId })
        override fun getActiveMedicationsForUser(userId: String): Flow<List<MedicationEntity>> = flowOf(meds.filter { it.userId == userId && it.active })
        override suspend fun getActiveMedicationsForUserSync(userId: String): List<MedicationEntity> = meds.filter { it.userId == userId && it.active }
        override suspend fun getAllActiveMedicationsSync(): List<MedicationEntity> = meds.filter { it.active }
        override suspend fun getMedicationById(id: Long, userId: String): MedicationEntity? = meds.find { it.id == id && it.userId == userId }
        override suspend fun getMedicationByIdSync(id: Long): MedicationEntity? = meds.find { it.id == id }
        override suspend fun insertMedication(medication: MedicationEntity): Long {
            meds.add(medication)
            return medication.id
        }
        override suspend fun updateMedication(medication: MedicationEntity) {
            meds.removeAll { it.id == medication.id }
            meds.add(medication)
        }
        override suspend fun deleteMedicationById(id: Long, userId: String) { meds.removeAll { it.id == id && it.userId == userId } }
        override suspend fun deleteAllMedicationsForUser(userId: String) { meds.removeAll { it.userId == userId } }
        override suspend fun getPendingSyncMedications(userId: String): List<MedicationEntity> = emptyList()
        override suspend fun getAllMedicationsForUserSync(userId: String): List<MedicationEntity> = meds.filter { it.userId == userId }
        override suspend fun markMedicationSynced(id: Long, userId: String) {}
        override suspend fun upsertMedications(medications: List<MedicationEntity>) { meds.addAll(medications) }
    }

    private class FakeMedicationHistoryDao : MedicationHistoryDao {
        val history = mutableListOf<MedicationHistoryEntity>()
        override fun getHistoryForUser(userId: String): Flow<List<MedicationHistoryEntity>> = flowOf(history.filter { it.userId == userId })
        override fun getHistoryForMedication(medicationId: Long, userId: String): Flow<List<MedicationHistoryEntity>> = flowOf(history.filter { it.medicationId == medicationId && it.userId == userId })
        override suspend fun getOccurrenceHistory(medicationId: Long, date: Long, time: String, userId: String): MedicationHistoryEntity? = null
        override suspend fun findExistingRecord(medicationId: Long, date: Long, time: String): MedicationHistoryEntity? = null
        override fun getHistoryForDateFlow(userId: String, date: Long): Flow<List<MedicationHistoryEntity>> = flowOf(history.filter { it.userId == userId && it.scheduledDate == date })
        override fun getHistoryForDateRangeFlow(userId: String, startDate: Long, endDate: Long): Flow<List<MedicationHistoryEntity>> = flowOf(emptyList())
        override suspend fun insertHistory(hist: MedicationHistoryEntity): Long {
            history.add(hist)
            return hist.id
        }
        override suspend fun updateHistory(hist: MedicationHistoryEntity) {}
        override suspend fun deleteAllMedicationHistoryForUser(userId: String) { history.removeAll { it.userId == userId } }
        override suspend fun getAllHistoryForUserSync(userId: String): List<MedicationHistoryEntity> = history.filter { it.userId == userId }
        override suspend fun upsertHistory(historyList: List<MedicationHistoryEntity>) { history.addAll(historyList) }
    }

    private class FakeAppointmentDao : AppointmentDao {
        val appts = mutableListOf<AppointmentEntity>()
        override fun observeAppointments(userId: String): Flow<List<AppointmentEntity>> = flowOf(appts.filter { it.userId == userId })
        override fun observeUpcomingAppointments(userId: String, currentTime: Long): Flow<List<AppointmentEntity>> = flowOf(appts.filter { it.userId == userId && it.appointmentTimestamp >= currentTime })
        override suspend fun getAllUpcomingScheduledAppointmentsSync(currentTime: Long): List<AppointmentEntity> = appts.filter { it.appointmentTimestamp >= currentTime }
        override fun observeCompletedAppointments(userId: String): Flow<List<AppointmentEntity>> = flowOf(appts.filter { it.userId == userId && it.status == "COMPLETED" })
        override fun observeCancelledAppointments(userId: String): Flow<List<AppointmentEntity>> = flowOf(appts.filter { it.userId == userId && it.status == "CANCELLED" })
        override suspend fun getAppointment(id: Long, userId: String): AppointmentEntity? = appts.find { it.id == id && it.userId == userId }
        override suspend fun insertAppointment(appointment: AppointmentEntity): Long {
            appts.add(appointment)
            return appointment.id
        }
        override suspend fun updateAppointment(appointment: AppointmentEntity) {}
        override suspend fun deleteAppointment(appointment: AppointmentEntity) { appts.remove(appointment) }
        override suspend fun deleteAppointmentById(id: Long, userId: String) { appts.removeAll { it.id == id && it.userId == userId } }
        override suspend fun deleteAllAppointmentsForUser(userId: String) { appts.removeAll { it.userId == userId } }
        override suspend fun updateAppointmentStatus(id: Long, userId: String, status: String, updatedAt: Long) {}
        override suspend fun getPendingSyncAppointments(userId: String): List<AppointmentEntity> = emptyList()
        override suspend fun getAllAppointmentsForUserSync(userId: String): List<AppointmentEntity> = appts.filter { it.userId == userId }
        override suspend fun markAppointmentSynced(id: Long, userId: String) {}
        override suspend fun upsertAppointments(appointments: List<AppointmentEntity>) { appts.addAll(appointments) }
    }

    private class FakePredictionHistoryDao : PredictionHistoryDao {
        val preds = mutableListOf<PredictionHistoryEntity>()
        override fun observePredictionHistory(userId: String): Flow<List<PredictionHistoryEntity>> = flowOf(preds.filter { it.userId == userId })
        override suspend fun getPredictionHistoryById(id: Long, userId: String): PredictionHistoryEntity? = preds.find { it.id == id && it.userId == userId }
        override suspend fun insertPredictionHistory(entity: PredictionHistoryEntity): Long {
            preds.add(entity)
            return entity.id
        }
        override suspend fun deletePredictionHistory(id: Long, userId: String) { preds.removeAll { it.id == id && it.userId == userId } }
        override suspend fun deleteAllPredictionHistory(userId: String) { preds.removeAll { it.userId == userId } }
        override suspend fun getPendingSyncPredictionHistory(userId: String): List<PredictionHistoryEntity> = emptyList()
        override suspend fun getAllPredictionHistoryForUserSync(userId: String): List<PredictionHistoryEntity> = preds.filter { it.userId == userId }
        override suspend fun markPredictionSynced(id: Long, userId: String) {}
        override suspend fun upsertPredictionHistory(predictions: List<PredictionHistoryEntity>) { preds.addAll(predictions) }
    }

    private class FakeSecurityAuditEventDao : SecurityAuditEventDao {
        val events = mutableListOf<SecurityAuditEventEntity>()
        override fun observeRecentAuditEvents(userId: String, limit: Int): Flow<List<SecurityAuditEventEntity>> = flowOf(events.filter { it.userId == userId }.take(limit))
        override suspend fun getAuditEventsForUser(userId: String): List<SecurityAuditEventEntity> = events.filter { it.userId == userId }
        override suspend fun insertAuditEvent(event: SecurityAuditEventEntity): Long {
            events.add(event)
            return event.id
        }
        override suspend fun deleteAllAuditEventsForUser(userId: String) { events.removeAll { it.userId == userId } }
    }

    private class FakeSyncMetadataDao : SyncMetadataDao {
        override fun observeSyncMetadata(userId: String): Flow<SyncMetadataEntity?> = flowOf(null)
        override suspend fun getSyncMetadata(userId: String): SyncMetadataEntity? = null
        override suspend fun upsertSyncMetadata(metadata: SyncMetadataEntity) {}
        override suspend fun deleteSyncMetadataForUser(userId: String) {}
    }
}
