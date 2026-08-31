# MediSense

MediSense is an AI-powered personal healthcare assistant designed for explainable disease prediction and personalized health management. Built with clean MVVM architecture, it utilizes a local Room database cache and integrates securely with Supabase for cloud database storage and authentication.

---

## 🚀 Accomplished Modules

### Module 1: User Authentication (Completed)
- **Supabase Auth:** Replaced standard Firebase Auth with Supabase Email/Password signup, sign-in, and sign-out.
- **Robust Session Check:** Implemented a `SplashFragment` that reads session persistence and directs the user to either the login fragment or the dashboard accordingly.
- **Resilient Logout:** Robust local session clear ensures that user tokens are safely wiped from disk even during offline mode, correctly navigating back to the login screen.
- **Custom ViewModels:** Handles UI states cleanly (Idle, Loading, Success, Error) using Kotlin Flow and Lifecycle architecture.

### Module 2: Personal Health Records (Completed)
- **Offline-First Storage:** Form details are saved locally to a Room database and marked with a `pendingSync` flag.
- **Supabase Postgrest Sync:** Triggers an automatic backend sync to write records to the `health_profiles` PostgreSQL table.
- **M3 Questionnaire Form:** Created a Material Design 3 form grouped into collapsible sections:
  - **Personal Info:** Name, Age/DOB (via `MaterialDatePicker`), Gender (Dropdown), Blood Group (Dropdown), Height, and Weight.
  - **Medical Info:** Allergies, Existing Diseases, Current Medications, and Family Medical History.
  - **Emergency Contact:** Contact Name and Phone Number (including input validation).
  - **Additional Info:** Custom notes.
- **Input Validation:** Restricts future dates for birth, enforces positive height/weight bounds, and matches emergency numbers to phone pattern formatting.

### Module 3: Symptom-Based Disease Prediction (Completed)
- **Local TFLite Inference:** Integrates `DiseasePredictionModel.tflite` for completely offline machine learning inference. Uses Google's modern LiteRT SDK framework.
- **Dynamic Symptoms Catalog:** Loads the symptom catalog (`symptoms.json`) and label mappings (`labels.json`) directly from assets.
- **Symptom Search & Checklists:** Designed a searchable checkboxes list to select symptoms, dynamically tracking selection count with a "Clear All" action.
- **Ranked Predictions Screen:** Renders the primary suspected condition in an accented card, details secondary conditions descending by probability, maps selected symptoms as Chips, and incorporates a prominent medical disclaimer card for safety.

### Module 4: Explainable AI (XAI) (Completed)
- **On-Device Clinical Reasoning:** Evaluates learned feature importances (`feature_importance.json`) and clinical rules (`disease_rules.json`) 100% offline to explain *why* the model produced its prediction.
- **Ranked Feature Contributions:** Visualizes contributing symptoms with Material Design 3 linear progress indicators and support badges (e.g., "Key Indicator", "Supports").
- **Extensible SHAP Schema:** Supports future model versioning and external SHAP metadata ingestion via `xai_feature_metadata.json`.
- **Honest Fallback Handling:** Gracefully displays informational notices without fabricating weights when condition-specific metadata is not present.
- **Clinical Safety:** Features prominent medical disclaimers framing all outputs as educational AI associations rather than confirmed medical diagnoses.

---

## 🛠️ Setup Instructions

To build and run this application successfully on a new machine:

### 1. Configure Supabase Credentials
Create a `local.properties` file in the root directory (if it does not exist) and add your Supabase credentials:
```properties
SUPABASE_URL="https://your-project-id.supabase.co"
SUPABASE_ANON_KEY="your-anonymous-key-here"
```

### 2. Set Up Database Schema & Row Level Security (RLS)
Execute the SQL script located in the root folder inside the **SQL Editor** on your Supabase Console:
👉 [`schema.sql`](file:///schema.sql)

This script will:
- Create the `health_profiles` table referencing the Supabase Auth UUID.
- Enable **Row Level Security (RLS)**.
- Apply security policies allowing authenticated users to create, read, update, or delete only their *own* health profiles.

---

## 🏗️ Architecture & Package Patterns
- **Language:** Kotlin
- **Architecture:** Model-View-ViewModel (MVVM)
- **Dependency Injection:** Hilt
- **Local DB:** Room
- **Cloud Database:** Supabase PostgreSQL
- **On-Device Inference:** TensorFlow Lite / Google LiteRT
- **Layouts:** View Binding (XML)
