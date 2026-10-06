# MediSense — Android Permissions Technical Inventory & Audit

## 1. Overview
This technical inventory audits all permissions declared and utilized in **MediSense** (`com.medisense.app`) for Google Play policy compliance and Android 11+ (API 30+) compatibility.

---

## 2. Permission Categorization

### A. Runtime Permissions (Requested via Permission Onboarding / Contextual Prompt)
These permissions require explicit user approval via Android runtime permission dialogs:

| Permission | Declared In Manifest | Android Versions | Feature Purpose | Graceful Fallback if Denied |
| :--- | :--- | :--- | :--- | :--- |
| `android.permission.CAMERA` | Yes | Android 11+ (API 30+) | Capturing images of lab reports or medication labels for AI Assistant analysis | Text input in AI Assistant continues; user can upload existing images via Photo Picker |
| `android.permission.RECORD_AUDIO` | Yes | Android 11+ (API 30+) | Speech recognition / voice query input in the AI Health Assistant | Text typing in AI Assistant remains 100% functional |
| `android.permission.POST_NOTIFICATIONS` | Yes | Android 13+ (API 33+) | Timely notification alerts for scheduled medication doses and clinic appointments | Reminders operate via full-screen ringing screen (`AlarmActivity`) and in-app logs; user is notified of notification limitation |

---

### B. Normal Permissions (Automatically Granted at Install Time)
These permissions do not require runtime user approval:

| Permission | Declared In Manifest | Purpose |
| :--- | :--- | :--- |
| `android.permission.INTERNET` | Yes | Supabase authentication, cloud sync, and AI Assistant API communication |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Yes | Rescheduling active medication and appointment alarms across device reboots |
| `android.permission.WAKE_LOCK` | Yes | Ensuring alarm receiver finishes scheduling and ring activity launches reliably |
| `android.permission.VIBRATE` | Yes | Haptic vibration during ringing medication and appointment alarm alerts |
| `android.permission.DISABLE_KEYGUARD` | Yes | Allowing full-screen alarm screen (`AlarmActivity`) to ring over lock screen |
| `android.permission.USE_FULL_SCREEN_INTENT` | Yes | Launching urgent medication ringing alert over current activity |

---

### C. Special App-Access & Exact Alarm Permissions
Special permissions that cannot be requested via standard runtime dialogs:

| Permission | Declared In Manifest | Android Versions | Purpose & Handling |
| :--- | :--- | :--- | :--- |
| `android.permission.SCHEDULE_EXACT_ALARM` | Yes | Android 12+ (API 31+) | Allows `AlarmManager.setAlarmClock()` for exact-to-the-minute medication alerts. Checked via `alarmManager.canScheduleExactAlarms()`. Directed to system settings if not enabled. |
| `android.permission.USE_EXACT_ALARM` | Yes | Android 13+ (API 33+) | Automatically grants exact alarm privileges for core clock/alarm/calendar applications without requiring manual user toggling. |

---

### D. Unnecessary / Restricted Permissions Avoided
MediSense strictly **does NOT** declare or request the following restricted permissions, preserving Google Play compliance:

| Permission Category | Avoided Permission | Modern Compliant Alternative Used |
| :--- | :--- | :--- |
| **Broad Storage** | `MANAGE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` | **FileProvider** (`androidx.core.content.FileProvider`) for camera captures; app-private internal storage; Android Photo Picker. |
| **Location** | `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` | Clinic locations are manually entered text; zero GPS tracking. |
| **Contacts / Phone** | `READ_CONTACTS`, `CALL_PHONE` | Emergency contacts are entered directly into the local health profile without scanning phone contacts. |
| **Health Connect** | `health.read.*` | Independent offline Room database architecture; no external Health Connect sync required. |
| **Microphone Background** | Background audio capture | Microphone is active **only** while user holds/taps the mic button in the foreground AI chat screen. |

---

## 3. First-Launch Permission Flow
1. **Welcome Screen:** User reads medical disclaimer and checks Terms & Conditions / Privacy Policy consent.
2. **Permission Setup Screen:** Explains why Notifications, Camera, Microphone, and Exact Alarms are used.
3. **Sequential Requests:** Requests each runtime permission sequentially without overwhelming the user.
4. **Non-Blocking Completion:** Denying optional permissions (Camera, Mic) never prevents entry into the main app.
5. **State Persistence:** Local flag `has_completed_permission_onboarding` prevents repeating the flow on subsequent launches.
