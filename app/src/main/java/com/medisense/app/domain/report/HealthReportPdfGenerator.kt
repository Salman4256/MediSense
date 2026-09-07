package com.medisense.app.domain.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.medisense.app.domain.model.HealthReport
import com.medisense.app.domain.model.HealthReportExportResult
import com.medisense.app.domain.security.SecureLogger
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Native Android [PdfDocument] generator for exporting multi-page [HealthReport] documents.
 * Operates 100% locally and offline without external PDF library dependencies.
 */
object HealthReportPdfGenerator {

    private const val TAG = "HealthReportPdfGen"

    // Standard A4 dimensions in points (72 points/inch)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    private const val MARGIN_HORIZONTAL = 40f
    private const val MARGIN_TOP = 45f
    private const val MARGIN_BOTTOM = 55f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_HORIZONTAL * 2)

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun generatePdf(context: Context, report: HealthReport): HealthReportExportResult {
        var pdfDocument: PdfDocument? = null
        return try {
            val reportsDir = File(context.cacheDir, "reports").apply {
                if (!exists()) mkdirs()
            }
            val timestamp = System.currentTimeMillis()
            val outputFile = File(reportsDir, "medisense_health_report_$timestamp.pdf")

            pdfDocument = PdfDocument()
            val renderer = PdfPageRenderer(pdfDocument, report)
            renderer.renderAllSections()

            // Write to file BEFORE closing the document
            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }

            SecureLogger.d(TAG, "Successfully generated local health report PDF: ${outputFile.name}")
            HealthReportExportResult.Success(
                file = outputFile,
                contentUri = android.net.Uri.EMPTY // Replaced with FileProvider URI by HealthReportRepository
            )
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to generate health report PDF", e)
            HealthReportExportResult.Error(e.message ?: "Failed to generate PDF", e)
        } finally {
            try {
                pdfDocument?.close()
            } catch (ignored: Exception) {}
        }
    }

    private class PdfPageRenderer(
        private val document: PdfDocument,
        private val report: HealthReport
    ) {
        private var currentPageNumber = 0
        private var currentPage: PdfDocument.Page? = null
        private var currentCanvas: Canvas? = null
        private var currentY = MARGIN_TOP

        // Paints
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59) // Slate 800
            textSize = 10f
            typeface = Typeface.DEFAULT
        }

        private val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42) // Slate 900
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
        }

        private val sectionHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 58, 138) // Deep Blue
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }

        private val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85) // Slate 700
            textSize = 11f
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

        /**
         * CRITICAL: Always call this AFTER ensureSpace() / startNewPage() to get
         * the currently active canvas. Never cache the canvas across page boundaries —
         * the old page's native peer becomes 0x0 after finishPage(), causing SIGSEGV.
         */
        private fun canvas(): Canvas = currentCanvas
            ?: throw IllegalStateException("No active PDF page canvas")

        fun renderAllSections() {
            startNewPage()

            // 1. Report Main Header
            drawMainHeader()

            // 2. Safety Notice Banner
            drawSafetyDisclaimerBanner()

            // 3. Personal Health Profile
            drawProfileSection()

            // 4. Data Quality
            drawDataQualitySection()

            // 5. Prediction History
            drawPredictionSection()

            // 6. Medication & Adherence
            drawMedicationSection()

            // 7. Appointments
            drawAppointmentSection()

            // 8. Longitudinal Trends
            drawTrendSection()

            // 9. Personal Health Context
            drawContextSection()

            // 10. RCHR Summary
            drawRchrSection()

            // 11. Contextual Risk Priority
            drawRiskSection()

            // 12. Personalized Guidance
            drawGuidanceSection()

            // 13. Sync Status & End Disclaimer
            drawSyncAndFooterSection()

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
                currentCanvas?.drawText("MediSense Health Report — Page $currentPageNumber", MARGIN_HORIZONTAL, 30f, mutedPaint)
                currentCanvas?.drawLine(MARGIN_HORIZONTAL, 35f, PAGE_WIDTH - MARGIN_HORIZONTAL, 35f, linePaint)
            }
        }

        private fun finishCurrentPage() {
            val page = currentPage ?: return

            // Draw Footer on current page
            val footerY = PAGE_HEIGHT - 25f
            currentCanvas?.drawLine(MARGIN_HORIZONTAL, footerY - 10f, PAGE_WIDTH - MARGIN_HORIZONTAL, footerY - 10f, linePaint)
            currentCanvas?.drawText(
                "Confidential Medical Summary — MediSense App v${report.metadata.appVersion}",
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
            ensureSpace(55f)

            boldPaint.textSize = 18f
            boldPaint.color = Color.rgb(15, 23, 42)
            canvas().drawText("MediSense", MARGIN_HORIZONTAL, currentY, boldPaint)
            currentY += 16f

            subHeaderPaint.textSize = 12f
            subHeaderPaint.color = Color.rgb(30, 58, 138)
            canvas().drawText(report.metadata.title, MARGIN_HORIZONTAL, currentY, subHeaderPaint)
            currentY += 14f

            val dateStr = "Generated: ${DATE_FORMAT.format(Date(report.metadata.generatedTimestamp))}  |  Version: ${report.metadata.reportVersion}"
            canvas().drawText(dateStr, MARGIN_HORIZONTAL, currentY, mutedPaint)
            currentY += 8f

            canvas().drawLine(MARGIN_HORIZONTAL, currentY, PAGE_WIDTH - MARGIN_HORIZONTAL, currentY, linePaint)
            currentY += 12f

            boldPaint.textSize = 10f
            subHeaderPaint.textSize = 11f
        }

        private fun drawSafetyDisclaimerBanner() {
            val lines = wrapText(report.safetyDisclaimer, textPaint, CONTENT_WIDTH - 20f)
            val boxHeight = (lines.size * 12f) + 16f

            ensureSpace(boxHeight + 10f)

            fillPaint.color = Color.rgb(254, 243, 199) // Amber 100
            val rect = RectF(MARGIN_HORIZONTAL, currentY, PAGE_WIDTH - MARGIN_HORIZONTAL, currentY + boxHeight)
            canvas().drawRoundRect(rect, 4f, 4f, fillPaint)

            badgePaint.color = Color.rgb(217, 119, 6) // Amber 600
            badgePaint.style = Paint.Style.STROKE
            badgePaint.strokeWidth = 0.8f
            canvas().drawRoundRect(rect, 4f, 4f, badgePaint)
            badgePaint.style = Paint.Style.FILL

            var textY = currentY + 14f
            boldPaint.color = Color.rgb(146, 64, 14) // Amber 800
            boldPaint.textSize = 9f
            for (line in lines) {
                ensureSpace(12f)
                canvas().drawText(line, MARGIN_HORIZONTAL + 10f, textY, boldPaint)
                textY += 12f
            }
            boldPaint.textSize = 10f
            boldPaint.color = Color.rgb(15, 23, 42)

            currentY += boxHeight + 14f
        }

        private fun drawSectionTitle(title: String) {
            ensureSpace(28f)
            canvas().drawText(title, MARGIN_HORIZONTAL, currentY, sectionHeaderPaint)
            currentY += 4f
            canvas().drawLine(MARGIN_HORIZONTAL, currentY, PAGE_WIDTH - MARGIN_HORIZONTAL, currentY, linePaint)
            currentY += 12f
        }

        private fun drawProfileSection() {
            drawSectionTitle("1. Personal Health Profile")
            val p = report.profile

            val rows = listOf(
                "Full Name" to (p.fullName ?: "Not specified"),
                "Date of Birth" to (p.dateOfBirth ?: "Not specified"),
                "Gender" to (p.gender ?: "Not specified"),
                "Blood Group" to (p.bloodGroup ?: "Not specified"),
                "Height / Weight" to "${p.heightCm?.let { "${it} cm" } ?: "—"}  /  ${p.weightKg?.let { "${it} kg" } ?: "—"}",
                "BMI" to (p.bmi?.let { "$it kg/m²" } ?: "—"),
                "Allergies" to (p.allergies ?: "None reported"),
                "Existing Conditions" to (p.existingDiseases ?: "None reported"),
                "Current Medications" to (p.currentMedications ?: "None reported"),
                "Emergency Contact" to "${p.emergencyContactName ?: "—"} (${p.emergencyContactNumber ?: "—"})",
                "Profile Completeness" to "${p.completenessPercentage}%"
            )

            for ((label, value) in rows) {
                ensureSpace(14f)
                canvas().drawText(label, MARGIN_HORIZONTAL, currentY, boldPaint)
                val valueLines = wrapText(value, textPaint, CONTENT_WIDTH - 150f)
                for (vl in valueLines) {
                    canvas().drawText(vl, MARGIN_HORIZONTAL + 140f, currentY, textPaint)
                    currentY += 12f
                }
            }

            if (p.incompleteFields.isNotEmpty()) {
                ensureSpace(16f)
                canvas().drawText("Incomplete Profile Fields: ${p.incompleteFields.joinToString(", ")}", MARGIN_HORIZONTAL, currentY, mutedPaint)
                currentY += 14f
            }
            currentY += 6f
        }

        private fun drawDataQualitySection() {
            drawSectionTitle("2. Health Data Quality & Consistency (Module 16)")
            val q = report.dataQuality

            ensureSpace(16f)
            val scoreText = if (q.qualityScore != null) "${q.qualityScore}%" else "Insufficient Data"
            canvas().drawText("Overall Data Quality: ${q.status.name}  |  Score: $scoreText  |  Passed Checks: ${q.passedChecks}/${q.totalChecks}", MARGIN_HORIZONTAL, currentY, boldPaint)
            currentY += 14f

            ensureSpace(14f)
            canvas().drawText("Issues Detected: ${q.errorCount} Errors, ${q.warningCount} Warnings, ${q.infoCount} Info notices", MARGIN_HORIZONTAL, currentY, textPaint)
            currentY += 12f

            if (q.unresolvedIssues.isNotEmpty()) {
                for (issue in q.unresolvedIssues.take(5)) {
                    ensureSpace(22f)
                    boldPaint.textSize = 9f
                    canvas().drawText("• [${issue.severity.name}] ${issue.title}", MARGIN_HORIZONTAL + 10f, currentY, boldPaint)
                    currentY += 10f
                    val expLines = wrapText(issue.explanation, mutedPaint, CONTENT_WIDTH - 20f)
                    for (el in expLines) {
                        ensureSpace(10f)
                        canvas().drawText(el, MARGIN_HORIZONTAL + 15f, currentY, mutedPaint)
                        currentY += 10f
                    }
                    boldPaint.textSize = 10f
                }
            }
            currentY += 6f
        }

        private fun drawPredictionSection() {
            drawSectionTitle("3. Disease Prediction History (Module 8 & 13)")
            val p = report.predictions

            ensureSpace(16f)
            canvas().drawText("Total Model Predictions: ${p.totalPredictions}  |  Avg Confidence: ${p.averageConfidencePercentage ?: "—"}%", MARGIN_HORIZONTAL, currentY, boldPaint)
            currentY += 14f

            if (p.recentPredictions.isEmpty()) {
                ensureSpace(14f)
                canvas().drawText("No historical disease predictions recorded.", MARGIN_HORIZONTAL, currentY, textPaint)
                currentY += 12f
            } else {
                for (item in p.recentPredictions.take(4)) {
                    ensureSpace(36f)
                    canvas().drawText("• ${item.predictedDisease} (${item.confidencePercentage}% - ${item.confidenceLevel})", MARGIN_HORIZONTAL + 10f, currentY, boldPaint)
                    currentY += 11f
                    canvas().drawText("Date: ${item.predictionDate}  |  Symptoms: ${item.symptoms.joinToString(", ")}", MARGIN_HORIZONTAL + 18f, currentY, textPaint)
                    currentY += 11f
                    item.uncertaintyInterpretation?.let { u ->
                        val uLines = wrapText("Uncertainty: $u", mutedPaint, CONTENT_WIDTH - 25f)
                        for (ul in uLines) {
                            ensureSpace(10f)
                            canvas().drawText(ul, MARGIN_HORIZONTAL + 18f, currentY, mutedPaint)
                            currentY += 10f
                        }
                    }
                    currentY += 4f
                }
            }
            currentY += 6f
        }

        private fun drawMedicationSection() {
            drawSectionTitle("4. Medication Regimen & Adherence (Module 6)")
            val m = report.medications

            ensureSpace(16f)
            val adhText = if (m.adherencePercentage != null) "${m.adherencePercentage}% (${m.adherenceRating})" else "No Adherence Logs"
            canvas().drawText("Active Medications: ${m.activeMedicationsCount}  |  Overall Adherence: $adhText", MARGIN_HORIZONTAL, currentY, boldPaint)
            currentY += 14f

            if (m.totalDosesLogged > 0) {
                ensureSpace(14f)
                canvas().drawText("Dose Logs: ${m.takenDosesCount} Taken, ${m.skippedDosesCount} Skipped, ${m.missedDosesCount} Missed (Total: ${m.totalDosesLogged})", MARGIN_HORIZONTAL, currentY, textPaint)
                currentY += 12f
            }

            if (m.activeMedications.isEmpty()) {
                ensureSpace(14f)
                canvas().drawText("No active medications configured.", MARGIN_HORIZONTAL, currentY, textPaint)
                currentY += 12f
            } else {
                for (med in m.activeMedications) {
                    ensureSpace(22f)
                    canvas().drawText("• ${med.medicineName} (${med.dosage}) — ${med.frequency}", MARGIN_HORIZONTAL + 10f, currentY, boldPaint)
                    currentY += 11f
                    ensureSpace(12f)
                    canvas().drawText("Times: ${med.scheduledTimes.joinToString(", ")}  |  Started: ${med.startDate}", MARGIN_HORIZONTAL + 18f, currentY, textPaint)
                    currentY += 12f
                }
            }
            currentY += 6f
        }

        private fun drawAppointmentSection() {
            drawSectionTitle("5. Doctor Appointments (Module 7)")
            val a = report.appointments

            ensureSpace(16f)
            canvas().drawText("Total Appointments: ${a.totalAppointments}  |  Upcoming: ${a.upcomingAppointments.size}", MARGIN_HORIZONTAL, currentY, boldPaint)
            currentY += 14f

            if (a.upcomingAppointments.isNotEmpty()) {
                for (apt in a.upcomingAppointments) {
                    ensureSpace(22f)
                    canvas().drawText("• [UPCOMING] ${apt.doctorName} — ${apt.clinicName}", MARGIN_HORIZONTAL + 10f, currentY, boldPaint)
                    currentY += 11f
                    ensureSpace(12f)
                    canvas().drawText("Date: ${apt.appointmentDate} at ${apt.appointmentTime} (${apt.appointmentType})", MARGIN_HORIZONTAL + 18f, currentY, textPaint)
                    currentY += 12f
                }
            } else {
                ensureSpace(14f)
                canvas().drawText("No upcoming appointments scheduled.", MARGIN_HORIZONTAL, currentY, textPaint)
                currentY += 12f
            }
            currentY += 6f
        }

        private fun drawTrendSection() {
            drawSectionTitle("6. Longitudinal Health Trends (Module 9B)")
            val t = report.trends

            ensureSpace(16f)
            canvas().drawText("Time Window: ${t.periodLabel}  |  Prediction Activity: ${t.predictionFrequencyTrend}", MARGIN_HORIZONTAL, currentY, boldPaint)
            currentY += 14f

            if (t.topRecurringSymptoms.isNotEmpty()) {
                ensureSpace(14f)
                canvas().drawText("Top Recurring Symptoms: ${t.topRecurringSymptoms.joinToString(", ")}", MARGIN_HORIZONTAL, currentY, textPaint)
                currentY += 12f
            }

            ensureSpace(14f)
            canvas().drawText("Adherence Trend: ${t.adherenceTrend}  |  Appointment Activity: ${t.appointmentActivity}", MARGIN_HORIZONTAL, currentY, textPaint)
            currentY += 12f

            if (t.detectedPatterns.isNotEmpty()) {
                for (p in t.detectedPatterns) {
                    ensureSpace(14f)
                    canvas().drawText("• Pattern: $p", MARGIN_HORIZONTAL + 10f, currentY, mutedPaint)
                    currentY += 11f
                }
            }
            currentY += 6f
        }

        private fun drawContextSection() {
            drawSectionTitle("7. Personal Health Context (Module 9A)")
            val c = report.context

            ensureSpace(14f)
            canvas().drawText("Demographics: ${c.demographicSummary}", MARGIN_HORIZONTAL, currentY, textPaint)
            currentY += 12f

            ensureSpace(14f)
            canvas().drawText("Chronic Conditions: ${c.chronicConditionsSummary}", MARGIN_HORIZONTAL, currentY, textPaint)
            currentY += 12f

            ensureSpace(14f)
            canvas().drawText("Allergies: ${c.allergySummary}", MARGIN_HORIZONTAL, currentY, textPaint)
            currentY += 12f

            ensureSpace(14f)
            canvas().drawText("Medication Context: ${c.activeMedicationSummary}  |  ${c.adherenceContext}", MARGIN_HORIZONTAL, currentY, textPaint)
            currentY += 14f
        }

        private fun drawRchrSection() {
            drawSectionTitle("8. Composite Health Representation (RCHR — Module 10)")
            val r = report.rchr

            ensureSpace(16f)
            val scoreStr = if (r.reconstructionConsistencyScore != null) "${r.reconstructionConsistencyScore}%" else "N/A"
            canvas().drawText("RCHR Model Version: ${r.rchrVersion}  |  Reconstruction Consistency: $scoreStr", MARGIN_HORIZONTAL, currentY, boldPaint)
            currentY += 14f

            ensureSpace(14f)
            canvas().drawText("Encoded Categories: ${r.encodedCategories.joinToString(", ")}", MARGIN_HORIZONTAL, currentY, textPaint)
            currentY += 12f

            val noticeLines = wrapText(r.explanationNotice, mutedPaint, CONTENT_WIDTH)
            for (nl in noticeLines) {
                ensureSpace(11f)
                canvas().drawText(nl, MARGIN_HORIZONTAL, currentY, mutedPaint)
                currentY += 10f
            }
            currentY += 8f
        }

        private fun drawRiskSection() {
            drawSectionTitle("9. Contextual Health Priority (Module 11)")
            val r = report.riskPriority

            ensureSpace(16f)
            val scoreText = if (r.priorityScore != null) "${r.priorityScore}/100" else "N/A"
            val suffText = if (r.hasSufficientData) "SUFFICIENT" else "LIMITED_DATA"
            canvas().drawText("Contextual Priority Level: ${r.priorityLevel.name}  |  Score: $scoreText  |  Data: $suffText", MARGIN_HORIZONTAL, currentY, boldPaint)
            currentY += 14f

            val expLines = wrapText("Explanation: ${r.plainLanguageExplanation}", textPaint, CONTENT_WIDTH)
            for (el in expLines) {
                ensureSpace(12f)
                canvas().drawText(el, MARGIN_HORIZONTAL, currentY, textPaint)
                currentY += 11f
            }

            if (r.contributingFactors.isNotEmpty()) {
                ensureSpace(14f)
                canvas().drawText("Contributing Factors:", MARGIN_HORIZONTAL, currentY, boldPaint)
                currentY += 12f
                for (factor in r.contributingFactors) {
                    ensureSpace(12f)
                    canvas().drawText("• $factor", MARGIN_HORIZONTAL + 10f, currentY, textPaint)
                    currentY += 11f
                }
            }

            val noticeLines = wrapText(r.notice, mutedPaint, CONTENT_WIDTH)
            for (nl in noticeLines) {
                ensureSpace(11f)
                canvas().drawText(nl, MARGIN_HORIZONTAL, currentY, mutedPaint)
                currentY += 10f
            }
            currentY += 8f
        }

        private fun drawGuidanceSection() {
            drawSectionTitle("10. Personalized Health Guidance (Module 12)")
            val g = report.guidance

            if (g.items.isEmpty()) {
                ensureSpace(14f)
                canvas().drawText("No specific guidance recommendations available for the current health context.", MARGIN_HORIZONTAL, currentY, textPaint)
                currentY += 12f
            } else {
                for (item in g.items.take(4)) {
                    ensureSpace(32f)
                    canvas().drawText("• [${item.priority.name}] ${item.title} (${item.category.name})", MARGIN_HORIZONTAL + 10f, currentY, boldPaint)
                    currentY += 11f
                    val actLines = wrapText(item.actionableGuidance, textPaint, CONTENT_WIDTH - 20f)
                    for (al in actLines) {
                        ensureSpace(10f)
                        canvas().drawText(al, MARGIN_HORIZONTAL + 18f, currentY, textPaint)
                        currentY += 10f
                    }
                    val reasonLines = wrapText("Reason: ${item.clinicalReason}", mutedPaint, CONTENT_WIDTH - 20f)
                    for (rl in reasonLines) {
                        ensureSpace(10f)
                        canvas().drawText(rl, MARGIN_HORIZONTAL + 18f, currentY, mutedPaint)
                        currentY += 10f
                    }
                    currentY += 4f
                }
            }
            currentY += 6f
        }

        private fun drawSyncAndFooterSection() {
            drawSectionTitle("11. Cloud Synchronization & Data Governance (Module 15)")
            val s = report.syncStatus

            ensureSpace(16f)
            val lastSyncStr = if (s.lastSyncTimestamp != null) DATE_FORMAT.format(Date(s.lastSyncTimestamp)) else "Never Synced"
            canvas().drawText("Sync Status: ${s.syncStatus}  |  Last Sync: $lastSyncStr  |  Pending Changes: ${s.pendingRecordsCount}", MARGIN_HORIZONTAL, currentY, boldPaint)
            currentY += 14f

            ensureSpace(14f)
            val modeStr = if (s.isOfflineMode) "Operating in Offline-First mode (local Room source of truth)." else "Cloud backup connected with Supabase PostgreSQL."
            canvas().drawText(modeStr, MARGIN_HORIZONTAL, currentY, textPaint)
            currentY += 16f
        }

        private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
            if (maxWidth <= 0f) return listOf(text)
            val words = text.split(" ")
            val lines = mutableListOf<String>()
            var currentLine = StringBuilder()

            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                val measure = paint.measureText(testLine)
                if (measure <= maxWidth) {
                    currentLine = StringBuilder(testLine)
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
            return lines.ifEmpty { listOf("") }
        }
    }
}
