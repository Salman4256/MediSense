package com.medisense.app.domain.emergency

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.medisense.app.domain.model.EmergencyHealthCard
import com.medisense.app.domain.model.HealthReportExportResult
import com.medisense.app.domain.security.SecureLogger
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Native Android [PdfDocument] generator for exporting the [EmergencyHealthCard] as a PDF.
 * Operates 100% offline and locally without third-party dependencies.
 */
object EmergencyHealthCardPdfGenerator {

    private const val TAG = "EmergencyCardPdfGen"

    // Standard A4 dimensions in points (72 points/inch)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    private const val MARGIN_HORIZONTAL = 40f
    private const val MARGIN_TOP = 45f
    private const val MARGIN_BOTTOM = 50f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_HORIZONTAL * 2)

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun generatePdf(context: Context, card: EmergencyHealthCard): HealthReportExportResult {
        var pdfDocument: PdfDocument? = null
        return try {
            val emergencyDir = File(context.cacheDir, "emergency").apply {
                if (!exists()) mkdirs()
            }
            val timestamp = System.currentTimeMillis()
            val outputFile = File(emergencyDir, "medisense_emergency_card_$timestamp.pdf")

            pdfDocument = PdfDocument()
            val renderer = PdfPageRenderer(pdfDocument, card)
            renderer.renderAllSections()

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }

            SecureLogger.d(TAG, "Successfully generated local emergency health card PDF: ${outputFile.name}")
            HealthReportExportResult.Success(
                file = outputFile,
                contentUri = android.net.Uri.EMPTY
            )
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to generate emergency health card PDF", e)
            HealthReportExportResult.Error(e.message ?: "Failed to generate PDF", e)
        } finally {
            try {
                pdfDocument?.close()
            } catch (ignored: Exception) {}
        }
    }

    private class PdfPageRenderer(
        private val document: PdfDocument,
        private val card: EmergencyHealthCard
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
            color = Color.rgb(185, 28, 28) // Crimson / Emergency Red
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
        }

        private val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 58, 138) // Deep Blue
            textSize = 11f
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

            // 1. Emergency Header
            drawEmergencyHeader()

            // 2. Emergency Contact (Prominent High-Priority Box)
            drawEmergencyContactBox()

            // 3. Known Allergies (Alert Box)
            drawAllergiesBox()

            // 4. Blood Group
            drawBloodGroupBox()

            // 5. Active Medications
            drawActiveMedicationsSection()

            // 6. Medical Conditions
            drawMedicalConditionsSection()

            // 7. Personal Baseline Information
            drawPersonalInformationSection()

            // 8. Important Health Notes
            drawHealthNotesSection()

            // 9. Critical Notice & Emergency Disclaimer
            drawEmergencyDisclaimerBanner()

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
                    "MediSense Emergency Health Access Card (Page $currentPageNumber)",
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
                "CRITICAL EMERGENCY SUMMARY — Generated by MediSense Healthcare Assistant",
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

        private fun drawEmergencyHeader() {
            ensureSpace(55f)

            boldPaint.textSize = 18f
            boldPaint.color = Color.rgb(185, 28, 28) // Crimson
            canvas().drawText("🚨 MEDISENSE EMERGENCY HEALTH CARD", MARGIN_HORIZONTAL, currentY, boldPaint)
            currentY += 16f

            subHeaderPaint.textSize = 11.5f
            subHeaderPaint.color = Color.rgb(51, 65, 85)
            canvas().drawText("Critical Medical & Health Access Summary (Offline-Ready)", MARGIN_HORIZONTAL, currentY, subHeaderPaint)
            currentY += 14f

            val dateStr = "Card Completeness: ${card.completeness.scorePercentage}% Ready  |  Last Updated: ${DATE_FORMAT.format(Date(card.lastUpdatedTimestamp))}"
            canvas().drawText(dateStr, MARGIN_HORIZONTAL, currentY, mutedPaint)
            currentY += 8f

            canvas().drawLine(MARGIN_HORIZONTAL, currentY, PAGE_WIDTH - MARGIN_HORIZONTAL, currentY, linePaint)
            currentY += 14f

            boldPaint.textSize = 10f
            subHeaderPaint.textSize = 11f
        }

        private fun drawEmergencyContactBox() {
            ensureSpace(60f)

            fillPaint.color = Color.rgb(254, 242, 242) // Red 50
            val boxHeight = 52f
            val rect = RectF(MARGIN_HORIZONTAL, currentY, PAGE_WIDTH - MARGIN_HORIZONTAL, currentY + boxHeight)
            canvas().drawRoundRect(rect, 6f, 6f, fillPaint)

            strokePaint.color = Color.rgb(239, 68, 68) // Red 500
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = 1.2f
            canvas().drawRoundRect(rect, 6f, 6f, strokePaint)
            strokePaint.style = Paint.Style.FILL

            boldPaint.color = Color.rgb(185, 28, 28)
            boldPaint.textSize = 11f
            canvas().drawText("PRIMARY EMERGENCY CONTACT", MARGIN_HORIZONTAL + 12f, currentY + 16f, boldPaint)

            val name = card.emergencyContact.name.ifBlank { "No emergency contact name recorded" }
            val phone = card.emergencyContact.phone.ifBlank { "No phone number recorded" }

            boldPaint.color = Color.rgb(15, 23, 42)
            boldPaint.textSize = 12f
            canvas().drawText("Name: $name", MARGIN_HORIZONTAL + 12f, currentY + 32f, boldPaint)

            boldPaint.color = Color.rgb(185, 28, 28)
            canvas().drawText("Phone: $phone", MARGIN_HORIZONTAL + 12f, currentY + 46f, boldPaint)

            boldPaint.textSize = 10f
            boldPaint.color = Color.rgb(15, 23, 42)
            currentY += boxHeight + 12f
        }

        private fun drawAllergiesBox() {
            val allergiesList = if (card.allergies.allergies.isNotEmpty()) {
                card.allergies.allergies
            } else if (card.allergies.rawText.isNotBlank()) {
                listOf(card.allergies.rawText)
            } else {
                listOf("No known allergies recorded.")
            }

            val totalLines = allergiesList.size
            val boxHeight = 28f + (totalLines * 14f)

            ensureSpace(boxHeight + 8f)

            fillPaint.color = Color.rgb(254, 243, 199) // Amber 100
            val rect = RectF(MARGIN_HORIZONTAL, currentY, PAGE_WIDTH - MARGIN_HORIZONTAL, currentY + boxHeight)
            canvas().drawRoundRect(rect, 6f, 6f, fillPaint)

            strokePaint.color = Color.rgb(245, 158, 11) // Amber 500
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = 1f
            canvas().drawRoundRect(rect, 6f, 6f, strokePaint)
            strokePaint.style = Paint.Style.FILL

            boldPaint.color = Color.rgb(146, 64, 14) // Amber 800
            boldPaint.textSize = 11f
            canvas().drawText("⚠️ KNOWN ALLERGIES", MARGIN_HORIZONTAL + 12f, currentY + 16f, boldPaint)

            var itemY = currentY + 30f
            boldPaint.color = Color.rgb(15, 23, 42)
            boldPaint.textSize = 10f
            for (allergy in allergiesList) {
                canvas().drawText("• $allergy", MARGIN_HORIZONTAL + 14f, itemY, boldPaint)
                itemY += 14f
            }

            currentY += boxHeight + 12f
        }

        private fun drawBloodGroupBox() {
            ensureSpace(34f)
            drawSectionTitle("🩸 Blood Group")

            val bg = card.bloodGroup.ifBlank { "Not recorded" }
            boldPaint.textSize = 13f
            boldPaint.color = Color.rgb(185, 28, 28) // Red
            canvas().drawText(bg, MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
            currentY += 16f

            boldPaint.textSize = 10f
            boldPaint.color = Color.rgb(15, 23, 42)
        }

        private fun drawActiveMedicationsSection() {
            drawSectionTitle("💊 Active Medications")

            if (card.medications.medications.isNotEmpty()) {
                for (med in card.medications.medications) {
                    ensureSpace(26f)
                    val dosagePart = if (med.dosage.isNotBlank()) " (${med.dosage})" else ""
                    val freqPart = if (med.frequency.isNotBlank()) " — ${med.frequency}" else ""
                    canvas().drawText("• ${med.name}$dosagePart$freqPart", MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
                    currentY += 13f

                    if (med.instructions.isNotBlank()) {
                        canvas().drawText("  Instructions: ${med.instructions}", MARGIN_HORIZONTAL + 14f, currentY, mutedPaint)
                        currentY += 13f
                    }
                }
            } else if (card.medications.rawFallback.isNotBlank()) {
                ensureSpace(14f)
                canvas().drawText(card.medications.rawFallback, MARGIN_HORIZONTAL + 8f, currentY, textPaint)
                currentY += 14f
            } else {
                ensureSpace(14f)
                canvas().drawText("No active medications recorded.", MARGIN_HORIZONTAL + 8f, currentY, textPaint)
                currentY += 14f
            }
            currentY += 6f
        }

        private fun drawMedicalConditionsSection() {
            drawSectionTitle("📋 Medical Conditions")

            if (card.conditions.conditions.isNotEmpty()) {
                for (condition in card.conditions.conditions) {
                    ensureSpace(14f)
                    canvas().drawText("• $condition", MARGIN_HORIZONTAL + 8f, currentY, textPaint)
                    currentY += 13f
                }
            } else if (card.conditions.rawText.isNotBlank()) {
                ensureSpace(14f)
                canvas().drawText(card.conditions.rawText, MARGIN_HORIZONTAL + 8f, currentY, textPaint)
                currentY += 14f
            } else {
                ensureSpace(14f)
                canvas().drawText("No medical conditions recorded.", MARGIN_HORIZONTAL + 8f, currentY, textPaint)
                currentY += 14f
            }
            currentY += 6f
        }

        private fun drawPersonalInformationSection() {
            drawSectionTitle("👤 Personal Information")

            val p = card.personalInfo
            val rows = listOf(
                "Full Name" to p.fullName.ifBlank { "Not recorded" },
                "Date of Birth" to if (p.dateOfBirth.isNotBlank()) "${p.dateOfBirth}${p.age?.let { " (Age $it)" } ?: ""}" else "Not recorded",
                "Gender" to p.gender.ifBlank { "Not recorded" }
            )

            for ((label, value) in rows) {
                ensureSpace(14f)
                canvas().drawText(label, MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
                canvas().drawText(value, MARGIN_HORIZONTAL + 140f, currentY, textPaint)
                currentY += 13f
            }
            currentY += 6f
        }

        private fun drawHealthNotesSection() {
            drawSectionTitle("📝 Important Health Notes")

            val notes = card.notes.notes.ifBlank { "No additional health notes recorded." }
            val noteLines = wrapText(notes, textPaint, CONTENT_WIDTH - 15f)

            for (line in noteLines) {
                ensureSpace(13f)
                canvas().drawText(line, MARGIN_HORIZONTAL + 8f, currentY, textPaint)
                currentY += 13f
            }
            currentY += 10f
        }

        private fun drawEmergencyDisclaimerBanner() {
            val disclaimer = "CRITICAL NOTICE: This emergency health card provides an assistive summary of health records stored in MediSense. It is not an emergency dispatch system and does not replace clinical judgment. In a life-threatening emergency, immediately contact local emergency personnel (e.g., 911 / 112)."
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

        private fun drawSectionTitle(title: String) {
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
