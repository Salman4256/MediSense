package com.medisense.app.domain.guidance

import com.medisense.app.domain.model.PersonalizedGuidance

/**
 * Centralized deterministic safety filter for health guidance.
 * Verifies that all generated recommendations strictly comply with safety policies:
 * - NO disease diagnosis or medical confirmation claims.
 * - NO medication dosage modification or start/stop instructions.
 * - NO clinical treatment plans.
 */
object GuidanceSafetyFilter {

    private val PROHIBITED_PATTERNS = listOf(
        // Diagnostic & certainty claims
        Regex("\\b(you have (a |an )?(flu|cold|covid|disease|infection|illness|condition|diabetes|hypertension|pneumonia|cancer|asthma))\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(diagnosed with|confirmed that you have|disease certainty|clinical diagnosis)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(guaranteed risk|confirm disease|proven condition)\\b", RegexOption.IGNORE_CASE),

        // Prescription & dosage manipulation
        Regex("\\b(increase (your )?dosage|decrease (your )?dosage|double (your )?dose|change (your )?dosage)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(stop taking|start taking|prescribe medication|prescribing medication|take \\d+\\s*(mg|ml|tablets|pills))\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(replace (your )?medication|switch (your )?medication)\\b", RegexOption.IGNORE_CASE),

        // Clinical treatment instructions
        Regex("\\b(treatment plan|cure for|clinical cure|prescribed treatment)\\b", RegexOption.IGNORE_CASE)
    )

    /**
     * Validates a candidate recommendation. Returns true if it passes all safety rules.
     */
    fun isSafe(guidance: PersonalizedGuidance): Boolean {
        val fullContent = "${guidance.title} ${guidance.message} ${guidance.explanation}"

        for (pattern in PROHIBITED_PATTERNS) {
            if (pattern.containsMatchIn(fullContent)) {
                return false
            }
        }

        return true
    }

    /**
     * Filters a list of candidate recommendations, retaining only safe items.
     */
    fun filterSafeGuidance(candidates: List<PersonalizedGuidance>): List<PersonalizedGuidance> {
        return candidates.filter { candidate ->
            val safe = isSafe(candidate)
            safe
        }
    }
}
