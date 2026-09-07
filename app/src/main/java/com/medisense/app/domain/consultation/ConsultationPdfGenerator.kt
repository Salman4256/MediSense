package com.medisense.app.domain.consultation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.medisense.app.domain.model.ConsultationSummary
import com.medisense.app.domain.model.HealthReportExportResult
import com.medisense.app.domain.security.SecureLogger
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Native Android [PdfDocument] generator for exporting multi-page [ConsultationSummary] PDF documents.
 * Operates 100% locally and offline without external PDF library dependencies.
 */
object ConsultationPdfGenerator {

    private const val TAG = "ConsultationPdfGen"

    // Standard A4 dimensions in points (72 points/inch)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    private const val MARGIN_HORIZONTAL = 40f
    private const val MARGIN_TOP = 45f
    private const val MARGIN_BOTTOM = 55f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_HORIZONTAL * 2)

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun generatePdf(context: Context, summary: ConsultationSummary): HealthReportExportResult {
        var pdfDocument: PdfDocument? = null
        return try {
            val consultationsDir = File(context.cacheDir, "consultations").apply {
                if (!exists()) mkdirs()
            }
            val timestamp = System.currentTimeMillis()
            val outputFile = File(consultationsDir, "medisense_consultation_summary_$timestamp.pdf")

            pdfDocument = PdfDocument()
            val renderer = PdfPageRenderer(pdfDocument, summary)
            renderer.renderAllSections()

            // Write to file BEFORE closing document
            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }

            SecureLogger.d(TAG, "Successfully generated local consultation summary PDF: ${outputFile.name}")
            HealthReportExportResult.Success(
                file = outputFile,
                contentUri = android.net.Uri.EMPTY // Replaced with FileProvider URI by repository
            )
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to generate consultation summary PDF", e)
            HealthReportExportResult.Error(e.message ?: "Failed to generate PDF", e)
        } finally {
            try {
                pdfDocument?.close()
            } catch (ignored: Exception) {}
        }
    }

    private class PdfPageRenderer(
        private val document: PdfDocument,
        private val summary: ConsultationSummary
    ) {
        private var currentPageNumber = 0
        private var currentPage: PdfDocument.Page? = null
        private var currentCanvas: Canvas? = null
        private var currentY = MARGIN_TOP

        // Paints
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59) // Slate 800
            textSize = 9.5f
            typeface = Typeface.DEFAULT
        }

        private val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42) // Slate 900
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
        }

        private val sectionHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 58, 138) // Deep Blue
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
        }

        private val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85) // Slate 700
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
        }

        private val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139) // Slate 500
            textSize = 8.5f
            typeface = Typeface.DEFAULT
        }

        private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 232, 240) // Slate 200
            strokeWidth = 0.8f
        }

        private fun canvas(): Canvas = currentCanvas
            ?: throw IllegalStateException("No active PDF page canvas")

        fun renderAllSections() {
            startNewPage()

            // 1. Header
            drawMainHeader()

            // 2. Safety Disclaimer Banner
            drawSafetyDisclaimerBanner()

            // 3. Baseline Health Profile
            drawProfileSection()

            // 4. Recent Symptoms & Observations
            drawSymptomsSection()

            // 5. Model Prediction Screenings
            drawPredictionsSection()

            // 6. Active Medications & Adherence
            drawMedicationsSection()

            // 7. Doctor Appointments
            drawAppointmentsSection()

            // 8. Longitudinal Trends & Temporal Context
            drawTrendsSection()

            // 9. Questions to Discuss with Doctor (Checklist)
            drawQuestionsSection()

            // 10. Data Quality & Completeness
            drawDataQualitySection()

            finishCurrentPage()
        }

        private fun startNewPage() {
            if (currentPage != null) {
                finishCurrentPage()
            }
            currentPageNumber++
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
            val page = document.startPage(pageInfo)
            currentPage = page
            currentCanvas = page.canvas
            currentY = MARGIN_TOP

            // Draw Running Header on page 2+
            if (currentPageNumber > 1) {
                currentCanvas?.drawText(
                    "MediSense — Doctor Consultation Preparation Summary (Page $currentPageNumber)",
                    MARGIN_HORIZONTAL,
                    30f,
                    mutedPaint
                )
                currentCanvas?.drawLine(MARGIN_HORIZONTAL, 35f, PAGE_WIDTH - MARGIN_HORIZONTAL, 35f, linePaint)
            }
        }

        private fun finishCurrentPage() {
            val page = currentPage ?: return

            // Footer
            val footerY = PAGE_HEIGHT - 25f
            currentCanvas?.drawLine(MARGIN_HORIZONTAL, footerY - 10f, PAGE_WIDTH - MARGIN_HORIZONTAL, footerY - 10f, linePaint)
            currentCanvas?.drawText(
                "Confidential Doctor Visit Preparation — Generated by MediSense Healthcare Assistant",
                MARGIN_HORIZONTAL,
                footerY,
                mutedPaint
            )
            val pageStr = "Page $currentPageNumber"
            val textWidth = mutedPaint.measureText(pageStr)
            currentCanvas?.drawText(pageStr, PAGE_WIDTH - MARGIN_HORIZONTAL - textWidth, footerY, mutedPaint)

            document.finishPage(page)
            currentPage = null
            currentCanvas = null
        }

        private fun ensureSpace(requiredHeight: Float) {
            if (currentY + requiredHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                startNewPage()
            }
        }

        private fun drawMainHeader() {
            ensureSpace(58f)

            boldPaint.textSize = 17f
            boldPaint.color = Color.rgb(15, 23, 42)
            canvas().drawText("MediSense", MARGIN_HORIZONTAL, currentY, boldPaint)
            currentY += 16f

            subHeaderPaint.textSize = 12f
            subHeaderPaint.color = Color.rgb(30, 58, 138)
            canvas().drawText("Doctor Consultation Preparation Summary", MARGIN_HORIZONTAL, currentY, subHeaderPaint)
            currentY += 14f

            val dateStr = "Summary Period: ${summary.visitOverview.dataPeriodLabel}  |  Generated: ${DATE_FORMAT.format(Date(summary.visitOverview.generationTimestamp))}"
            canvas().drawText(dateStr, MARGIN_HORIZONTAL, currentY, mutedPaint)
            currentY += 8f

            canvas().drawLine(MARGIN_HORIZONTAL, currentY, PAGE_WIDTH - MARGIN_HORIZONTAL, currentY, linePaint)
            currentY += 12f

            boldPaint.textSize = 9.5f
            subHeaderPaint.textSize = 10.5f
        }

        private fun drawSafetyDisclaimerBanner() {
            val lines = wrapText(summary.safetyDisclaimer, textPaint, CONTENT_WIDTH - 20f)
            val boxHeight = (lines.size * 11.5f) + 14f

            ensureSpace(boxHeight + 10f)

            fillPaint.color = Color.rgb(254, 243, 199) // Amber 100
            val rect = RectF(MARGIN_HORIZONTAL, currentY, PAGE_WIDTH - MARGIN_HORIZONTAL, currentY + boxHeight)
            canvas().drawRoundRect(rect, 4f, 4f, fillPaint)

            badgePaint.color = Color.rgb(217, 119, 6) // Amber 600
            badgePaint.style = Paint.Style.STROKE
            badgePaint.strokeWidth = 0.8f
            canvas().drawRoundRect(rect, 4f, 4f, badgePaint)
            badgePaint.style = Paint.Style.FILL

            var textY = currentY + 12f
            boldPaint.color = Color.rgb(146, 64, 14) // Amber 800
            boldPaint.textSize = 8.5f
            for (line in lines) {
                ensureSpace(11.5f)
                canvas().drawText(line, MARGIN_HORIZONTAL + 10f, textY, boldPaint)
                textY += 11.5f
            }
            boldPaint.textSize = 9.5f
            boldPaint.color = Color.rgb(15, 23, 42)

            currentY += boxHeight + 12f
        }

        private fun drawSectionTitle(title: String) {
            ensureSpace(26f)
            canvas().drawText(title, MARGIN_HORIZONTAL, currentY, sectionHeaderPaint)
            currentY += 4f
            canvas().drawLine(MARGIN_HORIZONTAL, currentY, PAGE_WIDTH - MARGIN_HORIZONTAL, currentY, linePaint)
            currentY += 10f
        }

        private fun drawProfileSection() {
            drawSectionTitle("1. Baseline Health Profile")
            val p = summary.profile

            val rows = listOf(
                "Patient Name" to (p.fullName ?: "Not recorded"),
                "Age / DOB" to (p.ageOrDob ?: "Not recorded"),
                "Gender / Blood Group" to "${p.gender ?: "Not specified"}  |  ${p.bloodGroup ?: "Not specified"}",
                "Known Allergies" to (p.allergies ?: "No known allergies recorded"),
                "Chronic Diagnoses" to (p.existingConditions ?: "No existing conditions recorded"),
                "Profile Med Notes" to (p.currentMedications ?: "None recorded")
            )

            for ((label, value) in rows) {
                ensureSpace(13f)
                canvas().drawText(label, MARGIN_HORIZONTAL, currentY, boldPaint)
                val valueLines = wrapText(value, textPaint, CONTENT_WIDTH - 140f)
                for (vl in valueLines) {
                    ensureSpace(13f)
                    canvas().drawText(vl, MARGIN_HORIZONTAL + 140f, currentY, textPaint)
                    currentY += 13f
                }
            }
            currentY += 6f
        }

        private fun drawSymptomsSection() {
            drawSectionTitle("2. Recent Symptoms & Observations")
            val s = summary.symptoms

            ensureSpace(14f)
            canvas().drawText("Total Symptoms Logged in Window: ${s.totalSymptomsCount}", MARGIN_HORIZONTAL, currentY, boldPaint)
            currentY += 14f

            if (s.frequentSymptoms.isNotEmpty()) {
                ensureSpace(14f)
                canvas().drawText("Frequent Symptoms: ${s.frequentSymptoms.joinToString(", ")}", MARGIN_HORIZONTAL, currentY, textPaint)
                currentY += 14f
            }

            if (s.recentObservations.isNotEmpty()) {
                ensureSpace(14f)
                canvas().drawText("Recent Observations:", MARGIN_HORIZONTAL, currentY, subHeaderPaint)
                currentY += 13f

                for ((sym, date) in s.recentObservations.take(5)) {
                    ensureSpace(13f)
                    canvas().drawText("• $sym ($date)", MARGIN_HORIZONTAL + 10f, currentY, textPaint)
                    currentY += 13f
                }
            } else if (s.totalSymptomsCount == 0) {
                ensureSpace(13f)
                canvas().drawText("No specific symptoms recorded during this time period.", MARGIN_HORIZONTAL, currentY, textPaint)
                currentY += 13f
            }
            currentY += 6f
        }

        private fun drawPredictionsSection() {
            drawSectionTitle("3. Model Prediction History (Educational Support)")
            val p = summary.predictions

            ensureSpace(14f)
            canvas().drawText("Screening Records Evaluated: ${p.totalPredictionsCount}", MARGIN_HORIZONTAL, currentY, boldPaint)
            currentY += 14f

            if (p.recentPredictions.isNotEmpty()) {
                for (pred in p.recentPredictions.take(4)) {
                    ensureSpace(24f)
                    val symText = if (pred.reportedSymptoms.isNotEmpty()) " (Symptoms: ${pred.reportedSymptoms.joinToString(", ")})" else ""
                    canvas().drawText("• ${pred.predictionDate}: ${pred.predictedCondition} [${pred.confidencePercentage}% model confidence]$symText", MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
                    currentY += 13f
                }
            } else {
                ensureSpace(13f)
                canvas().drawText("No disease prediction screening records found in this time window.", MARGIN_HORIZONTAL, currentY, textPaint)
                currentY += 13f
            }

            ensureSpace(13f)
            canvas().drawText("*Note: Model indications are assistive decision support and not confirmed medical diagnoses.", MARGIN_HORIZONTAL, currentY, mutedPaint)
            currentY += 17f
        }

        private fun drawMedicationsSection() {
            drawSectionTitle("4. Current Medications & Adherence")
            val m = summary.medications

            ensureSpace(14f)
            canvas().drawText("Overall Adherence: ${m.adherenceSummary}", MARGIN_HORIZONTAL, currentY, boldPaint)
            currentY += 14f

            if (m.activeMedications.isNotEmpty()) {
                ensureSpace(14f)
                canvas().drawText("Active Regimens (${m.activeMedications.size}):", MARGIN_HORIZONTAL, currentY, subHeaderPaint)
                currentY += 13f

                for (med in m.activeMedications) {
                    ensureSpace(14f)
                    val inst = if (med.instructions.isNotBlank()) " [Instructions: ${med.instructions}]" else ""
                    val medText = "• ${med.name} (${med.dosage}) — ${med.frequency}$inst"
                    val medLines = wrapText(medText, textPaint, CONTENT_WIDTH - 15f)
                    for (line in medLines) {
                        ensureSpace(13f)
                        canvas().drawText(line, MARGIN_HORIZONTAL + 10f, currentY, textPaint)
                        currentY += 13f
                    }
                }
            } else {
                ensureSpace(13f)
                canvas().drawText("No active medication schedules recorded on file.", MARGIN_HORIZONTAL, currentY, textPaint)
                currentY += 13f
            }

            if (m.missedOrSkippedCount > 0) {
                ensureSpace(13f)
                canvas().drawText("Attention: ${m.missedOrSkippedCount} missed or skipped doses recorded in this period.", MARGIN_HORIZONTAL, currentY, mutedPaint)
                currentY += 13f
            }
            currentY += 6f
        }

        private fun drawAppointmentsSection() {
            drawSectionTitle("5. Doctor Appointments")
            val a = summary.appointments

            if (a.upcomingAppointmentDoctor != null) {
                ensureSpace(14f)
                canvas().drawText("Upcoming Appointment:", MARGIN_HORIZONTAL, currentY, boldPaint)
                currentY += 13f

                ensureSpace(13f)
                val type = a.upcomingAppointmentType ?: "Consultation"
                canvas().drawText("• Doctor: ${a.upcomingAppointmentDoctor} ($type) on ${a.upcomingAppointmentDate ?: "Scheduled"}", MARGIN_HORIZONTAL + 10f, currentY, textPaint)
                currentY += 13f

                a.upcomingAppointmentClinic?.let {
                    ensureSpace(13f)
                    canvas().drawText("  Location: $it", MARGIN_HORIZONTAL + 10f, currentY, textPaint)
                    currentY += 13f
                }
            } else {
                ensureSpace(13f)
                canvas().drawText("No upcoming doctor appointments currently scheduled.", MARGIN_HORIZONTAL, currentY, textPaint)
                currentY += 13f
            }

            if (a.recentPastAppointmentsCount > 0) {
                ensureSpace(13f)
                canvas().drawText("Past visits in time window: ${a.recentPastAppointmentsCount}", MARGIN_HORIZONTAL, currentY, mutedPaint)
                currentY += 13f
            }
            currentY += 6f
        }

        private fun drawTrendsSection() {
            drawSectionTitle("6. Longitudinal Trends & Health Patterns")
            val t = summary.trends

            val rows = listOf(
                "Symptom Trajectory" to t.symptomTrend,
                "Adherence Trajectory" to t.adherenceTrend
            )

            for ((label, value) in rows) {
                ensureSpace(13f)
                canvas().drawText(label, MARGIN_HORIZONTAL, currentY, boldPaint)
                canvas().drawText(value, MARGIN_HORIZONTAL + 140f, currentY, textPaint)
                currentY += 13f
            }

            if (t.detectedPatterns.isNotEmpty()) {
                ensureSpace(14f)
                canvas().drawText("Temporal Observations:", MARGIN_HORIZONTAL, currentY, subHeaderPaint)
                currentY += 13f
                for (pattern in t.detectedPatterns) {
                    val pLines = wrapText("• $pattern", textPaint, CONTENT_WIDTH - 15f)
                    for (line in pLines) {
                        ensureSpace(13f)
                        canvas().drawText(line, MARGIN_HORIZONTAL + 10f, currentY, textPaint)
                        currentY += 13f
                    }
                }
            }
            currentY += 6f
        }

        private fun drawQuestionsSection() {
            drawSectionTitle("7. Questions to Discuss with Your Doctor")

            val selectedSuggested = summary.suggestedQuestions.filter { it.isSelected }
            val selectedCustom = summary.customQuestions.filter { it.isSelected }
            val allSelected = selectedSuggested + selectedCustom

            if (allSelected.isNotEmpty()) {
                ensureSpace(14f)
                canvas().drawText("Selected Discussion Checklist (${allSelected.size} questions):", MARGIN_HORIZONTAL, currentY, subHeaderPaint)
                currentY += 14f

                allSelected.forEachIndexed { idx, q ->
                    val prefix = "[  ] ${idx + 1}. "
                    val qLines = wrapText("$prefix${q.questionText}", boldPaint, CONTENT_WIDTH - 10f)

                    for ((lineIdx, line) in qLines.withIndex()) {
                        ensureSpace(13f)
                        canvas().drawText(line, MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
                        currentY += 13f
                    }

                    if (q.rationale.isNotBlank() && !q.isCustom) {
                        val rLines = wrapText("     Context: ${q.rationale}", mutedPaint, CONTENT_WIDTH - 20f)
                        for (rLine in rLines) {
                            ensureSpace(11f)
                            canvas().drawText(rLine, MARGIN_HORIZONTAL + 8f, currentY, mutedPaint)
                            currentY += 11f
                        }
                    }
                    currentY += 3f
                }
            } else {
                ensureSpace(13f)
                canvas().drawText("[  ] 1. What routine health screenings, tests, or lifestyle habits are recommended for my current profile?", MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
                currentY += 15f
            }
            currentY += 6f
        }

        private fun drawDataQualitySection() {
            drawSectionTitle("8. Data Quality & Completeness")
            val dq = summary.dataQuality

            ensureSpace(14f)
            canvas().drawText("Data Completeness: ${summary.visitOverview.completenessStatus.label}  |  Quality Status: ${dq.dataQualityStatus.name}", MARGIN_HORIZONTAL, currentY, boldPaint)
            currentY += 14f

            if (dq.notices.isNotEmpty()) {
                for (notice in dq.notices) {
                    val nLines = wrapText("• $notice", textPaint, CONTENT_WIDTH - 15f)
                    for (line in nLines) {
                        ensureSpace(13f)
                        canvas().drawText(line, MARGIN_HORIZONTAL + 10f, currentY, textPaint)
                        currentY += 13f
                    }
                }
            } else {
                ensureSpace(13f)
                canvas().drawText("All major health categories pass consistency and validation checks.", MARGIN_HORIZONTAL, currentY, textPaint)
                currentY += 13f
            }
            currentY += 6f
        }

        private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
            val lines = mutableListOf<String>()
            val paragraphs = text.split("\n")
            for (paragraph in paragraphs) {
                if (paragraph.isBlank()) {
                    lines.add("")
                    continue
                }
                val words = paragraph.split(" ")
                var currentLine = StringBuilder()
                for (word in words) {
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    val width = paint.measureText(testLine)
                    if (width <= maxWidth) {
                        currentLine.append(if (currentLine.isEmpty()) word else " $word")
                    } else {
                        if (currentLine.isNotEmpty()) {
                            lines.add(currentLine.toString())
                        }
                        currentLine = StringBuilder(word)
                    }
                }
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
            }
            return lines
        }
    }
}
