# MediSense — Google Play Data Safety Notes & Technical Audit

## 1. Overview
This document records the actual data collection, storage, and transmission practices implemented in **MediSense** (`com.medisense.app`). Use these technical facts when completing the **Data safety** questionnaire in Google Play Console.

---

## 2. Actual Data Categories & Processing Details

### A. Personal Info
| Data Field | Collected / Transmitted | Storage Location | Ephemeral / Stored | Purpose | Optional or Required |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Email Address** | Yes | Supabase Auth & Local Encrypted Prefs | Stored | Account management & Authentication | Required for account |
| **Name / Display Name** | Yes | Room Database & Supabase Database | Stored | User profile personalization | Optional |
| **User Identifier (UUID)** | Yes | Supabase Auth & Room Database | Stored | Account identification & Row Level Security | Required |

---

### B. Health & Fitness Info
| Data Field | Collected / Transmitted | Storage Location | Ephemeral / Stored | Purpose | Optional or Required |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Health Profile** (DOB, Gender, Blood Group, Height, Weight, Allergies, Chronic Conditions) | Yes (when entered by user) | Local Room DB; Synced to Supabase DB via HTTPS | Stored | App functionality (Health Records & Emergency Card) | Optional |
| **Medication Records** (Drug name, dosage, reminder timings, adherence logs) | Yes | Local Room DB; Synced to Supabase DB via HTTPS | Stored | App functionality (Medication Reminders) | Optional |
| **Appointment Details** (Doctor name, specialization, clinic location, date & time) | Yes | Local Room DB; Synced to Supabase DB via HTTPS | Stored | App functionality (Appointment Scheduler) | Optional |
| **Symptom & Prediction History** (Selected symptoms, predicted condition, confidence score) | Yes | Local Room DB; Synced to Supabase DB via HTTPS | Stored | App functionality (On-Device Prediction Tracking & Longitudinal Trends) | Optional |

> **Critical Note on AI Prediction Engine:** Disease predictions and explainable AI inferences are executed **100% locally on-device** using TensorFlow Lite (LiteRT). Symptom inputs and prediction inferences are **never transmitted** to external AI endpoints for classification.

---

### C. Messages & Conversational AI Content
| Data Field | Collected / Transmitted | Storage Location | Ephemeral / Stored | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **AI Assistant Chats** | Transmitted over HTTPS to configured provider (Groq / Gemini) | Transmitted for generation; conversation history stored locally in Room DB | Ephemeral transmission; local history stored | AI Health Assistant conversational assistance |

---

### D. Photos and Videos
| Data Field | Collected / Transmitted | Storage Location | Ephemeral / Stored | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **User Camera Captures** | Locally captured via `FileProvider` | App-private cache directory | Ephemeral (used only for user-initiated assistant analysis) | Optional user attachment for AI assistance |

---

### E. Device or Other Identifiers
* **Advertising ID (AAID):** **NOT COLLECTED**. MediSense contains no advertisement SDKs.
* **Device Hardware ID:** **NOT COLLECTED**.

---

## 3. Third-Party SDK & Library Audit
* **Supabase (`io.github.jan-tennert.supabase`):** Handles user authentication and encrypted PostgreSQL database synchronization. Enforces Row-Level Security (RLS) isolating each user's records.
* **LiteRT / TensorFlow Lite (`com.google.ai.edge.litert`):** Embedded offline inference engine. Transmits zero telemetry.
* **Google Generative AI / Groq API:** Invoked strictly on-demand for user-initiated assistant queries.
* **WorkManager (`androidx.work`):** Local Android OS background scheduler for reliable reminder checks. Transmits no data.
* **Analytics / Ads / Tracking SDKs:** **NONE**. No Firebase Analytics, no AdMob, no tracking trackers.

---

## 4. Security & Privacy Safeguards
* **Encryption in Transit:** All network traffic to Supabase and AI APIs uses HTTPS with TLS 1.3 encryption.
* **Data Deletion Mechanism:** The app features a dedicated **Clear Local Health Data** function in the *Privacy & Security* screen, permitting users to wipe all local records, predictions, medications, and logs instantly.
* **Offline-First Resilience:** All primary features (symptom prediction, explainable AI, medication alarms, emergency card) operate fully offline without network access.
