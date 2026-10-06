# MediSense — Google Play Release Readiness & Compliance Checklist

## 1. Overview
This document outlines the release-readiness verification for **MediSense** (`com.medisense.app`) in accordance with Google Play Console Developer Program Policies and Play Console Requirements.

---

## 2. Developer Account Compliance (Play Console Requirements)
Under the updated Google Play policy (effective August 31, 2024), health apps declared with medical features and categorized under Medical must register using an Organization developer account.

- [ ] **Developer Account Type:** Organization Account (Required for Medical category / Clinical decision support)
- [ ] **Legal Entity Verification:** Legal entity name and registered physical address matching official incorporation documents
- [ ] **D-U-N-S Number:** Verifiable Dun & Bradstreet profile and active D-U-N-S number
- [ ] **Contact Email:** Verified developer email address visible on Google Play
- [ ] **Phone Number:** Verified developer contact phone number with country code
- [ ] **Google Payment Profile:** Active and linked merchant profile (where applicable)

> *Note: Developer credentials and corporate registrations must be provided directly in Google Play Console by the account owner. No fabricated business details are recorded in source control.*

---

## 3. App Identity & Category Specifications
- **Application ID:** `com.medisense.app`
- **Application Name:** `MediSense`
- **Current Version Code:** `4`
- **Current Version Name:** `1.1.0`
- **Minimum SDK:** `30` (Android 11+)
- **Target SDK:** `36` (Android 16 / Modern Android APIs)
- **Compile SDK:** `36`
- **Primary Store Category:** `Medical` *(or Health & Fitness if submitted under personal wellness track)*
- **Primary Relevant Health Feature Declarations:**
  1. *Diseases and conditions management* (Symptom evaluation, on-device disease risk assessment, educational explanations)
  2. *Clinical decision support* (Explainable AI decision traces, risk factor contributions, counterfactual preventive scenarios)
  3. *Healthcare services and management* (Doctor consultation preparation, emergency health card, health data portability)
  4. *Medication and treatment management* (Daily prescription scheduling, dose logging, adherence tracking, timely exact alarms)

---

## 4. Store Listing Assets & Support Presence
- [x] **Application Title:** MediSense (max 30 characters)
- [x] **App Icon:** High-resolution 512 × 512 px 32-bit PNG with alpha
- [x] **Feature Graphic:** 1024 × 500 px banner located at `docs/play-store/medisense_feature_graphic_1024x500.png`
- [x] **Phone Screenshots:** 8 curated 1080 × 1920 px Material 3 screenshots located in `docs/play-store/screenshots/`
- [ ] **Tablet Screenshots:** 7-inch & 10-inch screenshots (optional / applicable for tablet distribution)
- [x] **Official Support Email:** `support4medisense@gmail.com`
- [x] **Official Website:** `https://medisense-policy-website.vercel.app/`
- [x] **Privacy Policy URL:** `https://medisense-policy-website.vercel.app/`

---

## 5. Medical Safety & Disclaimer Compliance
- [x] **No Unsupported Claims:** MediSense does not claim definitive clinical diagnosis, prescription authority, or emergency life-saving services.
- [x] **Mandatory Medical Disclaimer:** Prominently displayed across first launch, on-device prediction result screens, explainable AI views, and health report exports.
- [x] **Educational & Decision Support Framing:** Clearly positioned as an educational health-management and decision-support companion intended to facilitate consultations with licensed medical professionals.
