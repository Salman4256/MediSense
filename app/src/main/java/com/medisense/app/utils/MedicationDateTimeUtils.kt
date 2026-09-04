package com.medisense.app.utils

import com.medisense.app.data.local.entity.MedicationEntity
import com.medisense.app.data.local.entity.MedicationHistoryEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AdherenceStats(
    val takenCount: Int = 0,
    val skippedCount: Int = 0,
    val missedCount: Int = 0,
    val totalScheduled: Int = 0,
    val percentage: Float = 0f
)

object MedicationDateTimeUtils {
    const val DEFAULT_GRACE_PERIOD_MILLIS = 30 * 60 * 1000L

    private val supportedFormats = listOf(
        SimpleDateFormat("hh:mm a", Locale.US),
        SimpleDateFormat("h:mm a", Locale.US),
        SimpleDateFormat("hh:mm a", Locale.getDefault()),
        SimpleDateFormat("h:mm a", Locale.getDefault()),
        SimpleDateFormat("HH:mm", Locale.US),
        SimpleDateFormat("H:mm", Locale.US),
        SimpleDateFormat("HH:mm", Locale.getDefault()),
        SimpleDateFormat("H:mm", Locale.getDefault())
    )

    fun parseTime(timeStr: String): Date? {
        val trimmed = timeStr.trim()
        for (format in supportedFormats) {
            try {
                return format.parse(trimmed)
            } catch (ignored: Exception) {}
        }
        return null
    }

    fun formatTime12H(date: Date): String {
        return SimpleDateFormat("hh:mm a", Locale.US).format(date)
    }

    fun getStartOfDay(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun getEndOfDay(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    fun getStartOf7DaysAgo(): Long {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun getNextOccurrence(medication: MedicationEntity): Long? {
        if (!medication.active || medication.scheduledTimes.isEmpty()) return null
        val now = System.currentTimeMillis()
        val occurrences = mutableListOf<Long>()

        for (dayOffset in 0..7) {
            val baseCal = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }

            for (timeStr in medication.scheduledTimes) {
                val parsed = parseTime(timeStr) ?: continue
                val timeCal = Calendar.getInstance().apply { time = parsed }

                val targetCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, baseCal.get(Calendar.YEAR))
                    set(Calendar.MONTH, baseCal.get(Calendar.MONTH))
                    set(Calendar.DAY_OF_MONTH, baseCal.get(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                if (targetCal.timeInMillis > now) {
                    if (medication.endDate == null || targetCal.timeInMillis <= medication.endDate) {
                        occurrences.add(targetCal.timeInMillis)
                    }
                }
            }
        }
        return occurrences.minOrNull()
    }

    fun getScheduledSlotsForDate(medication: MedicationEntity, dateMillis: Long): List<Long> {
        val slots = mutableListOf<Long>()
        val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }

        for (timeStr in medication.scheduledTimes) {
            val parsed = parseTime(timeStr) ?: continue
            val timeCal = Calendar.getInstance().apply { time = parsed }
            val slotCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, cal.get(Calendar.YEAR))
                set(Calendar.MONTH, cal.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, cal.get(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            slots.add(slotCal.timeInMillis)
        }
        return slots
    }

    fun calculateAdherence(historyList: List<MedicationHistoryEntity>): AdherenceStats {
        if (historyList.isEmpty()) return AdherenceStats()
        var taken = 0
        var skipped = 0
        var missed = 0

        for (h in historyList) {
            when (h.status.uppercase(Locale.US)) {
                "TAKEN" -> taken++
                "SKIPPED" -> skipped++
                "MISSED" -> missed++
                else -> taken++
            }
        }
        val total = historyList.size
        val percent = if (total > 0) (taken.toFloat() / total) * 100f else 0f
        return AdherenceStats(
            takenCount = taken,
            skippedCount = skipped,
            missedCount = missed,
            totalScheduled = total,
            percentage = percent
        )
    }
}
