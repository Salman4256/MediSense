package com.medisense.app.domain.trace

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.medisense.app.domain.model.HealthDecisionTrace
import com.medisense.app.domain.model.HealthReportExportResult
import com.medisense.app.domain.security.SecureLogger
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Native Android [PdfDocument] generator for exporting multi-page [HealthDecisionTrace] audit documents.
 * Operates 100% offline and locally without third-party dependencies.
 */
object HealthDecisionTracePdfGenerator {

    private const val TAG = "DecisionTracePdfGen"

    // Standard A4 dimensions in points (72 points/inch)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    private const val MARGIN_HORIZONTAL = 40f
    private const val MARGIN_TOP = 45f
    private const val MARGIN_BOTTOM = 55f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_HORIZONTAL * 2)

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun generatePdf(context: Context, trace: HealthDecisionTrace): HealthReportExportResult {
        var pdfDocument: PdfDocument? = null
        return try {
            val traceDir = File(context.cacheDir, "traces").apply {
                if (!exists()) mkdirs()
            }
            val timestamp = System.currentTimeMillis()
            val outputFile = File(traceDir, "medisense_decision_trace_${trace.decisionType.name.lowercase()}_$timestamp.pdf")

            pdfDocument = PdfDocument()
            val renderer = PdfPageRenderer(pdfDocument, trace)
            renderer.renderAllSections()

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }

            SecureLogger.d(TAG, "Successfully generated local decision trace PDF: ${outputFile.name}")
            HealthReportExportResult.Success(
                file = outputFile,
                contentUri = android.net.Uri.EMPTY
            )
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to generate decision trace PDF", e)
            HealthReportExportResult.Error(e.message ?: "Failed to generate PDF", e)
        } finally {
            try {
                pdfDocument?.close()
            } catch (ignored: Exception) {}
        }
    }

    private class PdfPageRenderer(
        private val document: PdfDocument,
        private val trace: HealthDecisionTrace
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

            // 1. Trace Header
            drawMainHeader()

            // 2. Safety Limitations Banner
            drawLimitationsBanner()

            // 3. Input Summary
            drawInputSummarySection()

            // 4. Processing Pipeline Steps
            drawProcessingStepsSection()

            // 5. Important Factors & Attribution
            drawFactorsSection()

            // 6. Computational Output
            drawOutputSection()

            // 7. Human-Readable Explanation
            drawExplanationSection()

            // 8. Reproducibility & Audit Metadata
            drawReproducibilitySection()

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
                    "MediSense — Explainable Decision Trace (${trace.decisionType.displayName}) [Page $currentPageNumber]",
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
                "Confidential Explainable AI Audit Trail — Generated by MediSense Healthcare Assistant",
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
            canvas().drawText("MediSense Explainable Decision Trace", MARGIN_HORIZONTAL, currentY, boldPaint)
            currentY += 16f

            subHeaderPaint.textSize = 12f
            subHeaderPaint.color = Color.rgb(30, 58, 138)
            canvas().drawText("${trace.decisionType.displayName} (Engine: ${trace.engineName})", MARGIN_HORIZONTAL, currentY, subHeaderPaint)
            currentY += 14f

            val dateStr = "Generated: ${DATE_FORMAT.format(Date(trace.generatedAt))}  |  Data Quality: ${trace.dataQualityStatus}  |  Version: ${trace.engineVersion}"
            canvas().drawText(dateStr, MARGIN_HORIZONTAL, currentY, mutedPaint)
            currentY += 8f

            canvas().drawLine(MARGIN_HORIZONTAL, currentY, PAGE_WIDTH - MARGIN_HORIZONTAL, currentY, linePaint)
            currentY += 12f

            boldPaint.textSize = 9.5f
            subHeaderPaint.textSize = 10.5f
        }

        private fun drawLimitationsBanner() {
            val lines = wrapText(trace.limitationsText, textPaint, CONTENT_WIDTH - 20f)
            val boxHeight = (lines.size * 11.5f) + 14f

            ensureSpace(boxHeight + 10f)

            fillPaint.color = Color.rgb(254, 243, 199) // Amber 100
            val rect = RectF(MARGIN_HORIZONTAL, currentY, PAGE_WIDTH - MARGIN_HORIZONTAL, currentY + boxHeight)
            canvas().drawRoundRect(rect, 4f, 4f, fillPaint)

            strokePaint.color = Color.rgb(217, 119, 6) // Amber 600
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = 0.8f
            canvas().drawRoundRect(rect, 4f, 4f, strokePaint)
            strokePaint.style = Paint.Style.FILL

            var textY = currentY + 12f
            boldPaint.color = Color.rgb(146, 64, 14) // Amber 800
            boldPaint.textSize = 8.5f
            for (line in lines) {
                canvas().drawText(line, MARGIN_HORIZONTAL + 10f, textY, boldPaint)
                textY += 11.5f
            }
            boldPaint.textSize = 9.5f
            boldPaint.color = Color.rgb(15, 23, 42)

            currentY += boxHeight + 12f
        }

        private fun drawInputSummarySection() {
            drawSectionTitle("1. Input Data Summary")

            val inputLines = wrapText(trace.inputSummary, textPaint, CONTENT_WIDTH - 15f)
            for (line in inputLines) {
                ensureSpace(13f)
                canvas().drawText(line, MARGIN_HORIZONTAL + 8f, currentY, textPaint)
                currentY += 13f
            }
            currentY += 6f
        }

        private fun drawProcessingStepsSection() {
            drawSectionTitle("2. Computational Processing Pipeline")

            for (step in trace.processingSteps) {
                ensureSpace(28f)
                canvas().drawText("Step ${step.stepNumber}: ${step.title} (${step.engineComponent})", MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
                currentY += 13f

                val descLines = wrapText(step.description, textPaint, CONTENT_WIDTH - 25f)
                for (dLine in descLines) {
                    ensureSpace(12f)
                    canvas().drawText("  $dLine", MARGIN_HORIZONTAL + 12f, currentY, textPaint)
                    currentY += 12f
                }
                currentY += 3f
            }
            currentY += 4f
        }

        private fun drawFactorsSection() {
            drawSectionTitle("3. Important Factors & Feature Attribution")

            if (trace.inputFactors.isNotEmpty()) {
                trace.inputFactors.forEachIndexed { idx, factor ->
                    ensureSpace(26f)
                    val weight = factor.weightPercentage?.let { " [Weight: $it%]" } ?: ""
                    canvas().drawText("${idx + 1}. ${factor.name} (${factor.value})$weight — ${factor.influenceDirection.label}", MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
                    currentY += 13f

                    val interpLines = wrapText("   ${factor.interpretation}", textPaint, CONTENT_WIDTH - 20f)
                    for (iLine in interpLines) {
                        ensureSpace(12f)
                        canvas().drawText(iLine, MARGIN_HORIZONTAL + 12f, currentY, textPaint)
                        currentY += 12f
                    }
                    currentY += 3f
                }
            } else {
                ensureSpace(13f)
                canvas().drawText("No specific factors isolated for this computational decision.", MARGIN_HORIZONTAL + 8f, currentY, textPaint)
                currentY += 13f
            }
            currentY += 6f
        }

        private fun drawOutputSection() {
            drawSectionTitle("4. Engine Output & Result")

            ensureSpace(14f)
            canvas().drawText("Primary Output: ${trace.outputResult.primaryOutput}", MARGIN_HORIZONTAL + 8f, currentY, boldPaint)
            currentY += 14f

            trace.outputResult.secondaryOutput?.let {
                ensureSpace(13f)
                canvas().drawText("Secondary Detail: $it", MARGIN_HORIZONTAL + 8f, currentY, textPaint)
                currentY += 13f
            }

            trace.outputResult.confidenceOrStatus?.let {
                ensureSpace(13f)
                canvas().drawText("Status / Confidence: $it", MARGIN_HORIZONTAL + 8f, currentY, subHeaderPaint)
                currentY += 13f
            }
            currentY += 6f
        }

        private fun drawExplanationSection() {
            drawSectionTitle("5. Human-Readable Explanation")

            val expLines = wrapText(trace.explanationText, textPaint, CONTENT_WIDTH - 15f)
            for (line in expLines) {
                ensureSpace(13f)
                canvas().drawText(line, MARGIN_HORIZONTAL + 8f, currentY, textPaint)
                currentY += 13f
            }
            currentY += 6f
        }

        private fun drawReproducibilitySection() {
            drawSectionTitle("6. Reproducibility & Audit Metadata")

            ensureSpace(13f)
            canvas().drawText("Trace ID: ${trace.traceId}  |  Source Modules: ${trace.sourceModules.joinToString(", ")}", MARGIN_HORIZONTAL + 8f, currentY, mutedPaint)
            currentY += 13f

            for ((k, v) in trace.reproducibilityMetadata) {
                ensureSpace(12f)
                canvas().drawText("• $k: $v", MARGIN_HORIZONTAL + 8f, currentY, mutedPaint)
                currentY += 12f
            }
            currentY += 6f
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
