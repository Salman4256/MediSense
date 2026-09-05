package com.medisense.app.domain.model

/**
 * Application-defined priority ranking for guidance cards.
 *
 * NOTE: Priority reflects how strongly an application rule was triggered.
 * It is NOT a clinical urgency or emergency rating.
 */
enum class GuidancePriority(val label: String, val level: Int) {
    LOW("Low Priority", 1),
    MEDIUM("Medium Priority", 2),
    HIGH("High Priority", 3)
}
