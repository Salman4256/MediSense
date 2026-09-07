package com.medisense.app

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.medisense.app.domain.model.*
import com.medisense.app.domain.report.HealthReportPdfGenerator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented tests for Module 17 – Comprehensive Personal Health Report & Secure PDF Export.
 * Runs on a real Android device / emulator to exercise the native PdfDocument engine.
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class HealthReportPdfGeneratorInstrumentedTest {

    private lateinit var context: Context
    private lateinit var minimalReport: HealthReport

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        minimalReport = buildMinimalHealthReport()
    }

    @Test
    fun generatePdf_createsNonEmptyFile() {
        val result = HealthReportPdfGenerator.generatePdf(context, minimalReport)
        assertTrue("Expected Success result", result is HealthReportExportResult.Success)
        val success = result as HealthReportExportResult.Success
        assertTrue("PDF file must exist on disk", success.file.exists())
        assertTrue("PDF file must not be empty", success.file.length() > 0L)
    }

    @Test
    fun generatePdf_fileIsInCacheReportsDir() {
        val result = HealthReportPdfGenerator.generatePdf(context, minimalReport) as HealthReportExportResult.Success
        val expectedParent = File(context.cacheDir, "reports")
        assertEquals(expectedParent.canonicalPath, result.file.parentFile?.canonicalPath)
    }

    @Test
    fun generatePdf_fileHasPdfMagicBytes() {
        val result = HealthReportPdfGenerator.generatePdf(context, minimalReport) as HealthReportExportResult.Success
        val header = result.file.inputStream().use { it.readNBytes(5) }
        assertEquals("%PDF-", String(header, Charsets.ISO_8859_1))
    }

    @Test
    fun generatePdf_doesNotReturnFileSchemeUri() {
        val result = HealthReportPdfGenerator.generatePdf(context, minimalReport) as HealthReportExportResult.Success
        val scheme = result.contentUri.scheme
        assertFalse("PDF generator must NOT return a file:// URI (FileUriExposedException risk)", scheme == "file")
    }

    @Test
    fun generatePdf_doesNotCrashWithMinimalData() {
        val result = HealthReportPdfGenerator.generatePdf(context, buildMinimalHealthReport())
        assertTrue(result is HealthReportExportResult.Success)
    }

    @Test
    fun generatePdf_doesNotCrashWithRichData() {
        val result = HealthReportPdfGenerator.generatePdf(context, buildRichHealthReport())
        assertTrue(result is HealthReportExportResult.Success)
        val file = (result as HealthReportExportResult.Success).file
        assertTrue(file.exists())
        assertTrue(file.length() > 0L)
    }

    @Test
    fun generatePdf_multipleCallsProduceDistinctFiles() {
        val result1 = HealthReportPdfGenerator.generatePdf(context, minimalReport) as HealthReportExportResult.Success
        Thread.sleep(5)
        val result2 = HealthReportPdfGenerator.generatePdf(context, minimalReport) as HealthReportExportResult.Success
        assertNotEquals(result1.file.absolutePath, result2.file.absolutePath)
        assertTrue(result1.file.exists())
        assertTrue(result2.file.exists())
    }

    private fun buildMinimalHealthReport(): HealthReport = HealthReport(
        metadata = HealthReportMetadata(title = "Test Report", reportVersion = "1.0", generatedTimestamp = System.currentTimeMillis(), appVersion = "test", format = "PDF"),
        profile = HealthReportProfile(null, null, null, null, null, null, null, null, null, null, null, null, null, null, 0, listOf("fullName")),
        dataQuality = HealthReportDataQuality(HealthDataQualityStatus.INSUFFICIENT_DATA, null, 5, 0, 5, 0, 0, emptyList()),
        predictions = HealthReportPredictionSummary(0, emptyList(), null, null),
        medications = HealthReportMedicationSummary(0, 0, emptyList(), 0, 0, 0, 0, null, "No Data"),
        appointments = HealthReportAppointmentSummary(0, emptyList(), emptyList()),
        trends = HealthReportTrendSummary("Past 30 Days", "NO_DATA", emptyList(), "NO_DATA", "NO_DATA", "NO_DATA", emptyList()),
        context = HealthReportContextSummary(0, "No profile data", "None", "None", "None", "No data", "None"),
        rchr = HealthReportRchrSummary("1.0", emptyList(), 0, null),
        riskPriority = HealthReportRiskSummary(ContextualRiskLevel.INSUFFICIENT_DATA, null, false, emptyList(), "Insufficient data."),
        guidance = HealthReportGuidanceSummary(0, emptyList()),
        syncStatus = HealthReportSyncSummary("LOCAL_ONLY", null, 0, true)
    )

    private fun buildRichHealthReport(): HealthReport = minimalReport.copy(
        profile = minimalReport.profile.copy(
            fullName = "Test Patient", dateOfBirth = "1990-05-15", gender = "Male",
            bloodGroup = "A+", heightCm = 175.0, weightKg = 70.5, bmi = 23.0,
            allergies = "Penicillin", existingDiseases = "Hypertension",
            emergencyContactName = "Jane Doe", emergencyContactNumber = "+91 9876543210",
            completenessPercentage = 95, incompleteFields = listOf("notes")
        ),
        predictions = HealthReportPredictionSummary(
            totalPredictions = 1,
            recentPredictions = listOf(HealthReportPredictionItem(1L, "Hypertension", 87, "High", "2026-08-10", "v3.0", listOf("Headache"), null, "Confident")),
            mostFrequentCondition = "Hypertension",
            averageConfidencePercentage = 87
        ),
        medications = HealthReportMedicationSummary(
            activeMedicationsCount = 1, totalMedicationsCount = 1,
            activeMedications = listOf(HealthReportMedicationItem(1L, "Amlodipine", "5mg", "Once Daily", listOf("08:00"), "2026-01-01", null, "Take with water", true)),
            totalDosesLogged = 50, takenDosesCount = 45, skippedDosesCount = 3, missedDosesCount = 2,
            adherencePercentage = 90, adherenceRating = "Good"
        ),
        riskPriority = minimalReport.riskPriority.copy(
            priorityLevel = ContextualRiskLevel.MODERATE, priorityScore = 55, hasSufficientData = true,
            contributingFactors = listOf("Hypertension", "90% adherence"),
            plainLanguageExplanation = "Moderate priority: hypertension controlled but needs monitoring."
        )
    )
}