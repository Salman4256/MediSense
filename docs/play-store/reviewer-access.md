# MediSense — Google Play Reviewer Access Guide

## 1. Overview
Google Play app review guidelines require providing working demo/test credentials in the **App access** section of Google Play Console so the Google review team can evaluate all app features.

> **CRITICAL SECURITY NOTICE:**  
> Never commit actual passwords, reviewer account credentials, or API secret keys to Git.  
> Enter the active credentials securely within the Google Play Console under:  
> **Policy and programs &rarr; App content &rarr; App access**.

---

## 2. Reviewer Account Placeholder
* **Login URL / Screen:** App initial screen &rarr; First Launch / Welcome &rarr; Login
* **Reviewer Email:**  
  `<TO BE PROVIDED IN PLAY CONSOLE>`
* **Reviewer Password:**  
  `<TO BE PROVIDED SECURELY IN PLAY CONSOLE>`
* **Additional Access Instructions:**  
  *"Check the Terms & Conditions and Privacy Policy consent checkbox on the Welcome screen, tap 'Get Started', then sign in using the provided test account credentials. All app modules (Health Records, Disease Prediction, Explainable AI, Medications, Emergency Card, and Support) will be immediately accessible."*

---

## 3. Recommended Review Walkthrough Steps for Google Review Team
1. **Welcome & Consent:**
   - Launch app on device / emulator.
   - On the Welcome screen, view the medical disclaimer and check *"I agree to the Terms & Conditions and Privacy Policy."*
   - Tap the enabled **Get Started** button.
2. **Authentication:**
   - On the Login screen, enter the provided reviewer test email and password.
   - Tap **Login**.
3. **Home Dashboard:**
   - Inspect the personalized health summary, upcoming medication schedules, and quick action cards.
4. **Disease Prediction & Explainable AI (Offline Core):**
   - Navigate to **Disease Prediction**.
   - Select 2–3 symptoms (e.g. *Fever*, *Cough*, *Fatigue*) and tap **Predict Disease**.
   - Review the on-device LiteRT neural network confidence distribution and learned feature contribution explanations.
5. **Medications & Appointments:**
   - Verify medication alarms and doctor appointment scheduling.
6. **Support & Privacy:**
   - Open toolbar menu &rarr; **Privacy & Security** (or Profile &rarr; **Support & Official Website**).
   - Verify official support email (`support4medisense@gmail.com`) and policy website (`https://medisense-policy-website.vercel.app/`).
