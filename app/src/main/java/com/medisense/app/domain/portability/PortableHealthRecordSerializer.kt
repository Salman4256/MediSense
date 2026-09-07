package com.medisense.app.domain.portability

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.medisense.app.domain.model.PortableHealthRecordBundle
import com.medisense.app.domain.security.SecureLogger
import java.io.File
import java.io.FileOutputStream

/**
 * Handles deterministic formatting, file writing, and preview string generation
 * for [PortableHealthRecordBundle] instances.
 */
object PortableHealthRecordSerializer {

    private const val TAG = "PortableSerializer"

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    /**
     * Serializes the [PortableHealthRecordBundle] to human-readable pretty-printed JSON and writes it
     * into app-private cacheDir/exports/.
     */
    fun writeBundleToFile(context: Context, bundle: PortableHealthRecordBundle): File {
        val exportsDir = File(context.cacheDir, "exports").apply {
            if (!exists()) mkdirs()
        }
        val timestamp = System.currentTimeMillis()
        val file = File(exportsDir, "MediSense_Health_Record_${bundle.meta.packageId}_$timestamp.json")

        val jsonString = gson.toJson(bundle)
        FileOutputStream(file).use { out ->
            out.write(jsonString.toByteArray(Charsets.UTF_8))
        }

        SecureLogger.d(TAG, "Successfully serialized portable health record to: ${file.name} (${file.length()} bytes)")
        return file
    }

    /**
     * Generates a truncated pretty JSON sample snippet for the preview modal.
     */
    fun generatePreviewJsonSample(bundle: PortableHealthRecordBundle): String {
        val fullJson = gson.toJson(bundle)
        val lines = fullJson.lines()
        return if (lines.size > 25) {
            lines.take(22).joinToString("\n") + "\n  ...\n  [+ ${lines.size - 22} more lines in full export]\n}"
        } else {
            fullJson
        }
    }

    /**
     * Generates a human-readable text summary for sharing intent text and dialog displays.
     */
    fun generatePreviewSummary(bundle: PortableHealthRecordBundle): String {
        val sb = StringBuilder()
        sb.appendLine("📦 Portable Health Record (${bundle.meta.packageId})")
        sb.appendLine("📄 Format: FHIR-Inspired JSON Bundle (Collection)")
        sb.appendLine("📅 Exported: ${bundle.meta.generatedAtIso}")
        sb.appendLine("🔒 SHA-256 Checksum: ${bundle.meta.sha256Fingerprint.take(16)}...")
        sb.appendLine("📊 Total Resources Included: ${bundle.meta.totalResourceCount}")
        sb.appendLine()
        sb.appendLine("Included Categories (${bundle.meta.selectedCategories.size}):")
        bundle.meta.selectedCategories.forEach { cat ->
            sb.appendLine(" • $cat")
        }
        return sb.toString().trim()
    }
}
