# MediSense — Google Play Store Screenshot Plan & Specifications

## 1. Executive Summary

This document outlines the promotional screenshot strategy, technical requirements, and production assets for **MediSense** (`com.medisense.app`) for submission to the Google Play Console.

All screenshots were captured from the live Android application using a debug-only demonstration state (`Alex Johnson`), clean Android SystemUI demo mode status bars, and framed using Material 3 promotional cards.

### Google Play Console Asset Constraints
* **Format:** PNG or JPEG
* **Max Size:** 8 MB per screenshot (Generated screenshots average ~250 KB)
* **Aspect Ratio:** 9:16 portrait
* **Dimensions:** **1080 × 1920 px** (within the 320 px to 3840 px requirement)
* **Quantity:** 8 phone screenshots (Full recommended set)

---

## 2. Screenshot Inventory & Matrix

| # | Filename | Screen Captured | Purpose | Promotional Headline | Secondary Text | Dimensions | Safe for Store? |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **01** | `01_home_dashboard.png` | Home Dashboard | Main hub, quick actions, health overview, vitals, active medications | **"Your Personal Healthcare Assistant"** | *"Organize your health information and understand personalized health insights."* | 1080 × 1920 | YES |
| **02** | `02_health_records.png` | Personal Health Records | Demographics, blood group, allergies, conditions, and records | **"Keep Your Health Information Organized"** | *"Manage important health information in one place."* | 1080 × 1920 | YES |
| **03** | `03_symptom_prediction.png` | Symptom Selection & Input | Multi-symptom selector and ML model input interface | **"Explore Explainable Health Predictions"** | *"Analyze selected symptoms using an on-device machine-learning model."* | 1080 × 1920 | YES |
| **04** | `04_explainable_ai.png` | Explainable AI Prediction Results | Transparent prediction breakdown, contributing factors, confidence | **"Understand Why the Model Responded"** | *"Explore the factors contributing to a prediction."* | 1080 × 1920 | YES |
| **05** | `05_ai_assistant.png` | AI Health Assistant | Decision-support consultation chat & guidance | **"Ask Your Health Assistant"** | *"Get health-management information through text, voice and supported image input."* | 1080 × 1920 | YES |
| **06** | `06_medication_appointments.png` | Medications & Appointments | Active prescriptions, 7-day adherence chart, reminders, clinic visits | **"Stay Organized With Reminders"** | *"Manage medications, adherence and healthcare appointments."* | 1080 × 1920 | YES |
| **07** | `07_health_trends.png` | Longitudinal Health Trends | 30-day symptom frequency, adherence rates, timeline analysis | **"See Your Health Journey Over Time"** | *"Explore trends and personalized health-management insights."* | 1080 × 1920 | YES |
| **08** | `08_emergency_card.png` | Emergency Health Card | Emergency contact, blood group, allergies, quick access offline card | **"Keep Important Health Information Accessible"** | *"Quickly view selected emergency health information when needed."* | 1080 × 1920 | YES |

---

## 3. Detailed Screenshot Specifications

### Screenshot 1: Home Dashboard (`01_home_dashboard.png`)
* **Screen Target:** [DashboardFragment](file:///e:/MediSense/app/src/main/java/com/medisense/app/ui/dashboard/DashboardFragment.kt)
* **Required Demo State:**
  * User profile: "Alex Johnson"
  * Vitals: Height 172 cm, Weight 68 kg, Blood Group O+
  * Active widgets: Today's Medications (Amoxicillin, Cetirizine), Upcoming Appointment (Dr. Sarah Patel, MD), Quick Actions bar.
* **Safety & Compliance:** Contains no real patient records; no clinical claims; clean Material 3 cards.

### Screenshot 2: Personal Health Records (`02_health_records.png`)
* **Screen Target:** [HealthRecordsFragment](file:///e:/MediSense/app/src/main/java/com/medisense/app/ui/records/HealthRecordsFragment.kt)
* **Required Demo State:**
  * Profile overview with DOB: 15 June 1998, Blood Group O+.
  * Allergies chip: "Peanuts".
  * Existing conditions: "None reported".
  * Emergency contact: "+91 90000 00000".
* **Safety & Compliance:** Purely fictional identity adhering to privacy standards.

### Screenshot 3: Symptom Prediction (`03_symptom_prediction.png`)
* **Screen Target:** [DiseasePredictionFragment](file:///e:/MediSense/app/src/main/java/com/medisense/app/ui/prediction/DiseasePredictionFragment.kt)
* **Required Demo State:**
  * Selected demo symptoms: Fever, Cough, Fatigue, Headache.
  * Live filter search interface with selected symptom chips and "Predict Condition" action button.
* **Safety & Compliance:** Headline states *"Explore Explainable Health Predictions"*. Copy explicitly avoids "Diagnose" or "Guaranteed prediction".

### Screenshot 4: Explainable AI Prediction Results (`04_explainable_ai.png`)
* **Screen Target:** [PredictionResultFragment](file:///e:/MediSense/app/src/main/java/com/medisense/app/ui/prediction/PredictionResultFragment.kt)
* **Required Demo State:**
  * Real on-device ML model execution output for demo symptoms.
  * Predicted condition (e.g. Common Cold / Viral Pharyngitis) with confidence indicator.
  * Contributing symptom breakdown with transparency weights and educational context.
* **Safety & Compliance:** Model output clearly labeled as educational decision support; explicit medical disclaimer displayed on screen.

### Screenshot 5: AI Health Assistant (`05_ai_assistant.png`)
* **Screen Target:** [ChatFragment](file:///e:/MediSense/app/src/main/java/com/medisense/app/ui/chat/ChatFragment.kt)
* **Required Demo State:**
  * Query: *"What information should I keep ready for my next doctor visit?"*
  * Safe, evidence-based preparation checklist: current symptoms log, list of medications, history of allergies, specific questions for the doctor.
* **Safety & Compliance:** No API keys exposed; no diagnostic prescriptions or dosage instructions; compliant with medical chatbot guidelines.

### Screenshot 6: Medications & Appointments (`06_medication_appointments.png`)
* **Screen Target:** [MedicationsFragment](file:///e:/MediSense/app/src/main/java/com/medisense/app/ui/medications/MedicationsFragment.kt)
* **Required Demo State:**
  * Active schedule: Amoxicillin (500mg, Twice daily), Cetirizine (10mg, Nightly), Multivitamin (Once daily).
  * 7-day adherence chip active displaying >90% adherence with visual progress bars.
  * Next appointment badge: Dr. Sarah Patel, General Physician.
* **Safety & Compliance:** Demonstrates compliance and organization without offering drug dispensing advice.

### Screenshot 7: Health Trends & Personalized Insights (`07_health_trends.png`)
* **Screen Target:** [HealthTrendsFragment](file:///e:/MediSense/app/src/main/java/com/medisense/app/ui/trends/HealthTrendsFragment.kt)
* **Required Demo State:**
  * 30-day longitudinal data: recurring symptom trends, monthly medication adherence (93%), completed consultations.
  * Personalized lifestyle insights card based on recorded history.
* **Safety & Compliance:** Highlights long-term health tracking; does not offer prognostic medical guarantees.

### Screenshot 8: Emergency Health Card (`08_emergency_card.png`)
* **Screen Target:** [EmergencyCardFragment](file:///e:/MediSense/app/src/main/java/com/medisense/app/ui/emergency/EmergencyCardFragment.kt)
* **Required Demo State:**
  * High-visibility emergency view: Blood Group O+, Critical Allergy (Peanuts), Emergency Contact (+91 90000 00000).
  * Current vital medications listed for first responders.
* **Safety & Compliance:** Strictly informational; does not purport to detect emergencies or provide trauma care.

---

## 4. Tablet Compatibility Audit & Recommendations (Step 7)

### Current Architecture Assessment
* **Layout Structure:** The application uses responsive `ConstraintLayout`, `CoordinatorLayout`, and `NestedScrollView` architectures.
* **Orientation & Multi-Window:** Adapts properly without clipping, overlapping, or component truncation on Android tablets.
* **Layout Qualification:** The codebase currently uses single-column phone-first layouts (`layout/` default) without dedicated tablet-specific two-pane master-detail resource folders (`layout-sw600dp` or `layout-sw720dp`).

### Tablet Recommendations for Play Store Submission
1. **7-inch Tablets (e.g., Nexus 7, Galaxy Tab A7):**
   * Can safely reuse the standard responsive phone layouts.
   * Elements expand cleanly within scrollable cards.
   * If tablet screenshots are submitted, capture using a 7-inch emulator (1200 × 1920 px or 800 × 1280 px).
2. **10-inch Tablets (e.g., Pixel Tablet, Galaxy Tab S9):**
   * The app is fully functional and does not crash or distort on 10-inch devices.
   * However, wide single-column cards on large 10-inch screens have wider white margins.
   * **Recommendation:** Focus primary Play Store marketing on the 8 high-resolution phone screenshots (`1080 × 1920 px`), and only submit 10-inch tablet screenshots after introducing a dual-pane master-detail navigation rail (recommended for a future feature release).

---

## 5. Security & Isolation Safeguards

1. **Debug-Only Boundary (`BuildConfig.DEBUG`):**
   * The demo database seeder [PlayStoreDemoHelper.kt](file:///e:/MediSense/app/src/main/java/com/medisense/app/demo/PlayStoreDemoHelper.kt) is guarded by `BuildConfig.DEBUG` checks and cannot be invoked in release builds.
2. **Database Isolation:**
   * Demo data uses the isolated user ID `demo_alex_johnson` in Room DB.
3. **Cloud Sync Exemption:**
   * [SyncEngine.kt](file:///e:/MediSense/app/src/main/java/com/medisense/app/data/sync/SyncEngine.kt) specifically bypasses any user beginning with `demo_` (`if (userId.startsWith("demo_")) return`). Fictional records are never uploaded to Supabase or any remote server.
4. **Credential Protection:**
   * No API keys (Supabase, Groq, Gemini) or session tokens are rendered anywhere in the UI or assets.

---

## 6. Directory Structure of Generated Assets

```
docs/play-store/
├── feature-graphic-spec.md                # Feature graphic design specification
├── screenshot-plan.md                     # This plan and technical audit
├── medisense_feature_graphic_1024x500.png # Ready-to-upload feature graphic (1024x500 px)
├── raw/                                   # Direct 1080x1920 device screencaps
│   ├── 01_home_dashboard.png
│   ├── 02_health_records.png
│   ├── 03_symptom_prediction.png
│   ├── 04_explainable_ai.png
│   ├── 05_ai_assistant.png
│   ├── 06_medication_appointments.png
│   ├── 07_health_trends.png
│   └── 08_emergency_card.png
└── screenshots/                           # Finished Play Store promotional screenshots (1080x1920 px)
    ├── 01_home_dashboard.png
    ├── 02_health_records.png
    ├── 03_symptom_prediction.png
    ├── 04_explainable_ai.png
    ├── 05_ai_assistant.png
    ├── 06_medication_appointments.png
    ├── 07_health_trends.png
    └── 08_emergency_card.png
```
