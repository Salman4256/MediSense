package com.medisense.app.domain.model

/**
 * Standard trend direction indicators for longitudinal health metrics.
 */
enum class TrendDirection(val label: String) {
    INCREASING("Increasing"),
    DECREASING("Decreasing"),
    STABLE("Stable"),
    IMPROVING("Improving"),
    DECLINING("Declining"),
    INSUFFICIENT_DATA("Insufficient Data")
}
