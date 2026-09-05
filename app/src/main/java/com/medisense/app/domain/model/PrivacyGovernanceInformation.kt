package com.medisense.app.domain.model

/**
 * Factual, non-inflated governance details describing MediSense's actual storage and AI architecture.
 */
object PrivacyGovernanceInformation {

    const val LOCAL_STORAGE_EXPLANATION =
        "Health information used by MediSense is stored locally in an offline-first Room database on your device to support reliable offline access and swift processing."

    const val CLOUD_STORAGE_EXPLANATION =
        "Account authentication and synchronized health records are managed securely via Supabase Auth and PostgreSQL cloud database using authenticated user UUID isolation."

    const val AI_DATA_EXPLANATION =
        "When using the interactive AI Assistant, health context is transmitted to the configured AI API provider (Groq/Gemini) solely to generate conversational assistance. The disease prediction engine runs entirely offline on-device via TensorFlow Lite."

    const val HEALTHCARE_DISCLAIMER =
        "MediSense is an educational health-management and decision-support tool. It does not provide definitive medical diagnoses, prescriptions, or replace clinical consultation with qualified physicians."

    const val LOCAL_DATA_CLEARING_NOTICE =
        "Clearing local data removes all local health profiles, predictions, medication schedules, appointments, and conversation logs from this device. Cloud-stored records (if any) require backend synchronization to update."

    const val ACCOUNT_DELETION_NOTICE =
        "Complete account deletion requires initiating the authenticated account-removal process. MediSense does not embed administrative master keys in the application client."
}
