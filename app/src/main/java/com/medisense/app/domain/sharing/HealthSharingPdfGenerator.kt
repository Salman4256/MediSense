package com.medisense.app.domain.sharing

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.medisense.app.domain.model.HealthReportExportResult
import com.medisense.app.domain.model.HealthSharingPackage
import com.medisense.app.domain.security.SecureLogger
import java.io.File
import java.io.FileOutputStream

/**
 * Native Android [PdfDocument] generator for exporting the [HealthSharingPackage] as a PDF summary report.
 * Operates 100% offline and locally without third-party PDF dependencies.
 */
object HealthSharingPdfGenerator {

    private const val TAG = "HealthSharingPdfGen"

    // Standard A4 dimensions in points (72 points/inch)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    private const val MARGIN_HORIZONTAL = 40f
    private const val MARGIN_TOP = 45f
    private const val MARGIN_BOTTOM = 50f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_HORIZONTAL * 2)

    fun generatePdf(context: Context, pkg: HealthSharingPackage): HealthReportExportResult {
        var pdfDocument: PdfDocument? = null
        return try {
            val sharesDir = File(context.cacheDir, "shares").apply {
                if (!exists()) mkdirs()
            }
            val timestamp = System.currentTimeMillis()
            val outputFile = File(sharesDir, "medisense_sharing_report_${pkg.packageMetadata.packageId}_$timestamp.pdf")

            pdfDocument = PdfDocument()
            val renderer = PdfSharingPageRenderer(pdfDocument, pkg)
            renderer.renderAllSections()

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }

            SecureLogger.d(TAG, "Successfully generated local health sharing PDF: ${outputFile.name}")
            HealthReportExportResult.Success(
                file = outputFile,
                contentUri = android.net.Uri.EMPTY
            )
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to generate health sharing PDF", e)
            HealthReportExportResult.Error(e.message ?: "Failed to generate PDF", e)
        } finally {
            try {
                pdfDocument?.close()
            } catch (ignored: Exception) {}
        }
    }

    private class PdfSharingPageRenderer(
        private val document: PdfDocument,
        private val pkg: HealthSharingPackage
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

        private val headerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(13, 148, 136) // Teal 600
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }

        private val sectionHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 118, 110) // Teal 700
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }

        private val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 58, 138) // Deep Blue 800
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
        }

        private val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139) // Slate 500
            textSize = 8.5f
            typeface = Typeface.DEFAULT
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 232, 240) // Slate 200
            strokeWidth = 0.8f
        }

        private fun canvas(): Canvas = currentCanvas
            ?: throw IllegalStateException("No active PDF page canvas")

        fun renderAllSections() {
            startNewPage()

            // 1. Header Banner
            drawHeaderBanner()

            // 2. Consent & Recipient Info Box
            drawConsentInfoBox()

            // 3. Personal Profile
            pkg.personalProfile?.let { drawProfileSection(it) }

            // 4. Conditions & Allergies
            pkg.conditionsAndAllergies?.let { drawConditionsSection(it) }

            // 5. Emergency Summary
            pkg.emergencyAccessCard?.let { drawEmergencySection(it) }

            // 6. Medications & Adherence
            pkg.medicationsAndAdherence?.let { drawMedicationsSection(it) }

            // 7. Prediction Assessments
            pkg.predictionHistory?.let { drawPredictionsSection(it) }

            // 8. Appointments
            pkg.doctorAppointments?.let { drawAppointmentsSection(it) }

            // 9. Decision Traces
            pkg.decisionTraces?.let { drawDecisionTracesSection(it) }

            // 10. Longitudinal Summary
            pkg.longitudinalSummary?.let { drawLongitudinalSection(it) }

            // 11. Data Quality Notice
            pkg.dataQualityNotice?.let { drawDataQualitySection(it) }

            // 12. Non-Diagnostic Disclaimer Banner
            drawDisclaimerBanner()

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

            if (currentPageNumber > 1) {
                currentCanvas?.drawText(
                    "MediSense Secure Health Data Share (${pkg.packageMetadata.packageId}) — Page $currentPageNumber",
                    MARGIN_HORIZONTAL,
                    30f,
                    mutedPaint
                )
                currentCanvas?.drawLine(MARGIN_HORIZONTAL, 35f, PAGE_WIDTH - MARGIN_HORIZONTAL, 35f, linePaint)
            }
        }

        private fun finishCurrentPage() {
            val page = currentPage ?: return

            val footerY = PAGE_HEIGHT - 25f
            currentCanvas?.drawLine(MARGIN_HORIZONTAL, footerY - 10f, PAGE_WIDTH - MARGIN_HORIZONTAL, footerY - 10f, linePaint)
            currentCanvas?.drawText(
                "NON-DIAGNOSTIC HEALTH RECORD — Generated for ${pkg.consentDeclaration.recipientLabel}",
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

        private fun drawHeaderBanner() {
            ensureSpace(55f)

            canvas().drawText("🛡️ MEDISENSE SECURE HEALTH SHARE", MARGIN_HORIZONTAL, currentY, headerTitlePaint)
            currentY += 15f

            subHeaderPaint.textSize = 10.5f
            subHeaderPaint.color = Color.rgb(51, 65, 85)
            canvas().drawText("Consent-Controlled Health Data Package", MARGIN_HORIZONTAL, currentY, subHeaderPaint)
            currentY += 13f

            val metaStr = "Package ID: ${pkg.packageMetadata.packageId}  |  Generated: ${pkg.packageMetadata.generatedAtIso}  |  SHA-256: ${pkg.packageMetadata.sha256Fingerprint.take(12)}..."
            canvas().drawText(metaStr, MARGIN_HORIZONTAL, currentY, mutedPaint)
            currentY += 8f

            canvas().drawLine(MARGIN_HORIZONTAL, currentY, PAGE_WIDTH - MARGIN_HORIZONTAL, currentY, linePaint)
            currentY += 12f
        }

        private fun drawConsentInfoBox() {
            ensureSpace(56f)

            fillPaint.color = Color.rgb(240, 253, 250) // Teal 50
            val boxHeight = 48f
            val rect = RectF(MARGIN_HORIZONTAL, currentY, PAGE_WIDTH - MARGIN_HORIZONTAL, currentY + boxHeight)
            canvas().drawRoundRect(rect, 5f, 5f, fillPaint)

            strokePaint.color = Color.rgb(20, 184, 166) // Teal 500
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = 1f
            canvas().drawRoundRect(rect, 5f, 5f, strokePaint)
            strokePaint.style = Paint.Style.FILL

            boldPaint.color = Color.rgb(15, 118, 110)
            boldPaint.textSize = 10f
            canvas().drawText("🎯 PURPOSE: ${pkg.consentDeclaration.purpose.uppercase()}", MARGIN_HORIZONTAL + 10f, currentY + 15f, boldPaint)

            boldPaint.color = Color.rgb(15, 23, 42)
            canvas().drawText("Recipient: ${pkg.consentDeclaration.recipientLabel}", MARGIN_HORIZONTAL + 10f, currentY + 29f, boldPaint)

            val grantedStr = "Granted Sections: ${pkg.consentDeclaration.grantedCategories.joinToString(", ")}"
            val truncatedGranted = if (grantedStr.length > 85) grantedStr.take(82) + "..." else grantedStr
            canvas().drawText(truncatedGranted, MARGIN_HORIZONTAL + 10f, currentY + 41f, mutedPaint)

            currentY += boxHeight + 12f
            boldPaint.color = Color.rgb(15, 23, 42)
        }

        private fun drawProfileSection(p: com.medisense.app.domain.model.SharedProfileSection) {
            drawSectionHeader("👤 Personal Profile & Demographics")

            val rows = listOf(
                "Full Name" to p.fullName.ifBlank { "Not provided" },
                "Age / DOB" to "${p.age?.let { "$it years" } ?: "Age not recorded"}${p.dateOfBirth?.let { " ($it)" } ?: ""}",
                "Biological Sex" to (p.gender?.ifBlank { "Not recorded" } ?: "Not recorded"),
                "Blood Group" to (p.bloodGroup?.ifBlank { "Not recorded" } ?: "Not recorded"),
                "Emergency Contact" to "${p.emergencyContactName ?: "None"} (${p.emergencyContactPhone ?: "No phone"})"
            )

            for ((label, value) in rows) {
                ensureSpace(13f)
                canvas().drawText(label, MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
                canvas().drawText(value, MARGIN_HORIZONTAL + 140f, currentY, textPaint)
                currentY += 12f
            }
            currentY += 6f
        }

        private fun drawConditionsSection(c: com.medisense.app.domain.model.SharedConditionsAllergiesSection) {
            drawSectionHeader("📋 Medical Conditions & Known Allergies")

            val condStr = if (c.conditions.isNotEmpty()) c.conditions.joinToString(", ") else "None recorded"
            ensureSpace(14f)
            canvas().drawText("Chronic Conditions:", MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
            canvas().drawText(condStr, MARGIN_HORIZONTAL + 140f, currentY, textPaint)
            currentY += 13f

            val allergyStr = if (c.allergies.isNotEmpty()) c.allergies.joinToString(", ") else "None recorded"
            ensureSpace(14f)
            canvas().drawText("Known Allergies:", MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
            boldPaint.color = Color.rgb(185, 28, 28)
            canvas().drawText(allergyStr, MARGIN_HORIZONTAL + 140f, currentY, boldPaint)
            boldPaint.color = Color.rgb(15, 23, 42)
            currentY += 13f

            c.notes?.let { note ->
                ensureSpace(14f)
                canvas().drawText("Clinical Notes:", MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
                canvas().drawText(note, MARGIN_HORIZONTAL + 140f, currentY, textPaint)
                currentY += 13f
            }
            currentY += 6f
        }

        private fun drawEmergencySection(e: com.medisense.app.domain.model.SharedEmergencySection) {
            drawSectionHeader("🚨 Emergency Access Card Summary")

            ensureSpace(13f)
            canvas().drawText("Emergency Contact:", MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
            canvas().drawText("${e.emergencyContactName} — ${e.emergencyContactPhone}", MARGIN_HORIZONTAL + 140f, currentY, boldPaint)
            currentY += 12f

            ensureSpace(13f)
            canvas().drawText("Blood Group:", MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
            canvas().drawText(e.bloodGroup, MARGIN_HORIZONTAL + 140f, currentY, textPaint)
            currentY += 12f

            ensureSpace(13f)
            canvas().drawText("Critical Allergies:", MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
            val allergyList = if (e.criticalAllergies.isNotEmpty()) e.criticalAllergies.joinToString(", ") else "None reported"
            canvas().drawText(allergyList, MARGIN_HORIZONTAL + 140f, currentY, textPaint)
            currentY += 12f

            ensureSpace(13f)
            canvas().drawText("Emergency Notes:", MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
            canvas().drawText(e.emergencyInstructions, MARGIN_HORIZONTAL + 140f, currentY, textPaint)
            currentY += 14f
        }

        private fun drawMedicationsSection(m: com.medisense.app.domain.model.SharedMedicationsSection) {
            drawSectionHeader("💊 Active Medications & Adherence")

            ensureSpace(14f)
            val adherenceText = m.overallAdherencePercentage?.let { "$it% adherence rate" } ?: "Adherence not evaluated"
            canvas().drawText("Active Prescriptions (${m.totalActiveMedications})  |  Adherence: $adherenceText", MARGIN_HORIZONTAL + 8f, currentY, subHeaderPaint)
            currentY += 14f

            if (m.activeMedications.isNotEmpty()) {
                for (med in m.activeMedications) {
                    ensureSpace(24f)
                    canvas().drawText("• ${med.medicineName} (${med.dosage}) — ${med.frequency}", MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
                    currentY += 12f
                    if (med.instructions.isNotBlank()) {
                        canvas().drawText("  Instructions: ${med.instructions}", MARGIN_HORIZONTAL + 14f, currentY, mutedPaint)
                        currentY += 12f
                    }
                }
            } else {
                ensureSpace(13f)
                canvas().drawText("No active medications recorded.", MARGIN_HORIZONTAL + 8f, currentY, textPaint)
                currentY += 13f
            }
            currentY += 6f
        }

        private fun drawPredictionsSection(p: com.medisense.app.domain.model.SharedPredictionsSection) {
            drawSectionHeader("🩺 Symptom & Assessment History")

            if (p.assessments.isNotEmpty()) {
                for (item in p.assessments) {
                    ensureSpace(28f)
                    val title = "${item.dateIso}: ${item.primaryAssessment} (${item.confidencePercentage}% confidence, Risk: ${item.riskLevel})"
                    canvas().drawText(title, MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
                    currentY += 12f

                    val symptoms = "Reported Symptoms: ${item.reportedSymptoms.joinToString(", ")}"
                    canvas().drawText(symptoms, MARGIN_HORIZONTAL + 14f, currentY, textPaint)
                    currentY += 12f

                    if (item.recommendedPrecautions.isNotEmpty()) {
                        val prec = "Precautions: ${item.recommendedPrecautions.joinToString(", ")}"
                        canvas().drawText(prec, MARGIN_HORIZONTAL + 14f, currentY, mutedPaint)
                        currentY += 12f
                    }
                }
            } else {
                ensureSpace(13f)
                canvas().drawText("No assessment history records found.", MARGIN_HORIZONTAL + 8f, currentY, textPaint)
                currentY += 13f
            }
            currentY += 6f
        }

        private fun drawAppointmentsSection(a: com.medisense.app.domain.model.SharedAppointmentsSection) {
            drawSectionHeader("📅 Clinical Appointments")

            if (a.appointments.isNotEmpty()) {
                for (appt in a.appointments) {
                    ensureSpace(22f)
                    val line = "• ${appt.appointmentDate} at ${appt.appointmentTime}: ${appt.doctorName} (${appt.clinicName}) [${appt.status}]"
                    canvas().drawText(line, MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
                    currentY += 12f
                    appt.notes?.let { n ->
                        canvas().drawText("  Notes: $n", MARGIN_HORIZONTAL + 14f, currentY, mutedPaint)
                        currentY += 12f
                    }
                }
            } else {
                ensureSpace(13f)
                canvas().drawText("No clinical appointments recorded.", MARGIN_HORIZONTAL + 8f, currentY, textPaint)
                currentY += 13f
            }
            currentY += 6f
        }

        private fun drawDecisionTracesSection(t: com.medisense.app.domain.model.SharedDecisionTracesSection) {
            drawSectionHeader("🔍 Explainable Decision Traces")

            if (t.traces.isNotEmpty()) {
                for (trace in t.traces) {
                    ensureSpace(30f)
                    val header = "• [${trace.decisionType}] ${trace.title} (${trace.traceId})"
                    canvas().drawText(header, MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
                    currentY += 12f

                    canvas().drawText(trace.summary, MARGIN_HORIZONTAL + 14f, currentY, textPaint)
                    currentY += 12f

                    if (trace.contributingFactors.isNotEmpty()) {
                        val factors = "Factors: " + trace.contributingFactors.joinToString("; ") { "${it.factorName} (${it.weightDescription})" }
                        val truncatedFactors = if (factors.length > 90) factors.take(87) + "..." else factors
                        canvas().drawText(truncatedFactors, MARGIN_HORIZONTAL + 14f, currentY, mutedPaint)
                        currentY += 12f
                    }
                }
            } else {
                ensureSpace(13f)
                canvas().drawText("No explainable decision traces included.", MARGIN_HORIZONTAL + 8f, currentY, textPaint)
                currentY += 13f
            }
            currentY += 6f
        }

        private fun drawLongitudinalSection(l: com.medisense.app.domain.model.SharedLongitudinalSection) {
            drawSectionHeader("📈 Longitudinal Trends & Patterns")

            ensureSpace(13f)
            canvas().drawText("Period: ${l.analysisPeriod}  |  ${l.stabilitySummary}", MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
            currentY += 13f

            if (l.recurringSymptoms.isNotEmpty()) {
                ensureSpace(13f)
                canvas().drawText("Recurring Symptoms: ${l.recurringSymptoms.joinToString(", ")}", MARGIN_HORIZONTAL + 8f, currentY, textPaint)
                currentY += 12f
            }

            if (l.detectedPatterns.isNotEmpty()) {
                ensureSpace(13f)
                canvas().drawText("Detected Dynamics: ${l.detectedPatterns.joinToString("; ")}", MARGIN_HORIZONTAL + 8f, currentY, mutedPaint)
                currentY += 12f
            }
            currentY += 6f
        }

        private fun drawDataQualitySection(q: com.medisense.app.domain.model.SharedDataQualitySection) {
            drawSectionHeader("✅ Data Quality & Completeness Notice")

            ensureSpace(14f)
            val scoreText = q.completenessScore?.let { "$it%" } ?: "N/A"
            canvas().drawText("Validation Status: ${q.overallQualityStatus} (Completeness: $scoreText, Passed: ${q.checksPassed}/${q.totalChecks})", MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
            currentY += 13f

            ensureSpace(13f)
            canvas().drawText(q.noticeText, MARGIN_HORIZONTAL + 8f, currentY, mutedPaint)
            currentY += 14f
        }

        private fun drawDisclaimerBanner() {
            val disclaimer = pkg.safetyDisclaimer
            val lines = wrapText(disclaimer, textPaint, CONTENT_WIDTH - 20f)
            val boxHeight = (lines.size * 11f) + 14f

            ensureSpace(boxHeight + 8f)

            fillPaint.color = Color.rgb(241, 245, 249) // Slate 100
            val rect = RectF(MARGIN_HORIZONTAL, currentY, PAGE_WIDTH - MARGIN_HORIZONTAL, currentY + boxHeight)
            canvas().drawRoundRect(rect, 4f, 4f, fillPaint)

            strokePaint.color = Color.rgb(203, 213, 225) // Slate 300
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = 0.8f
            canvas().drawRoundRect(rect, 4f, 4f, strokePaint)
            strokePaint.style = Paint.Style.FILL

            var textY = currentY + 12f
            mutedPaint.textSize = 8f
            mutedPaint.color = Color.rgb(71, 85, 105) // Slate 600
            for (line in lines) {
                canvas().drawText(line, MARGIN_HORIZONTAL + 10f, textY, mutedPaint)
                textY += 11f
            }
            mutedPaint.textSize = 8.5f
            mutedPaint.color = Color.rgb(100, 116, 139)

            currentY += boxHeight + 8f
        }

        private fun drawSectionHeader(title: String) {
            ensureSpace(24f)
            canvas().drawText(title, MARGIN_HORIZONTAL, currentY, sectionHeaderPaint)
            currentY += 4f
            canvas().drawLine(MARGIN_HORIZONTAL, currentY, PAGE_WIDTH - MARGIN_HORIZONTAL, currentY, linePaint)
            currentY += 10f
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
