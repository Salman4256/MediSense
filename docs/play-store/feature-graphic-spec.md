# MediSense — Google Play Store Feature Graphic Specification

## 1. Overview & Compliance Summary

* **Asset Name:** `medisense_feature_graphic_1024x500.png`
* **File Locations:**
  * Root workspace: `medisense_feature_graphic_1024x500.png`
  * Documentation directory: `docs/play-store/medisense_feature_graphic_1024x500.png`
* **Resolution:** Exactly **1024 × 500 px**
* **Aspect Ratio:** ~ 2.048 : 1 (Play Store Standard Landscape)
* **Format:** PNG (RGB, 24-bit)
* **File Size:** **~146 KB** (Play Store maximum allowed: 15 MB)
* **Transparency:** No transparency (solid medical-grade gradient canvas)

---

## 2. Design Composition & Visual Hierarchy

The feature graphic follows Google Play Store visual guidelines and Android Material 3 design principles:

### Left Column (Branding & Value Proposition)
1. **Brand Badge:**
   * Pill container in primary teal tone (`#006A6A` at 18% opacity, border `#006A6A`)
   * White cross icon + uppercase text: `MEDISENSE HEALTHCARE` (Segoe UI Bold, 13 pt)
2. **Main Headline:**
   * "Your Personal AI Healthcare Assistant" (Segoe UI Bold, 34 pt, color `#E0F4F4`)
   * Clean, high contrast on dark deep-teal background.
3. **Core Pillars / Value Proposition:**
   * Green checkmark accents (`#4CD7B6`) with two-line structured highlights:
     * **Explainable AI Insights** — Transparent symptom feature importance
     * **Organized Records & Meds** — Personalized reminders & timelines
     * **Intelligent Guidance** — Context-aware consultation preparation
4. **Mandatory Regulatory Disclaimer:**
   * Text: *"Educational health decision support. Not a substitute for clinical diagnosis."* (Segoe UI Regular, 11 pt, subtle teal `#82AAAA` at 80% opacity)
   * Positioned along the bottom-left margin with generous breathing room.

### Center & Right Column (Product Mockup)
* High-fidelity, real application UI preview of the MediSense Home Dashboard.
* Rendered inside a sleek modern smartphone bezel (`400 × 425 px` viewport) with rounded corners (26 px radius).
* Soft drop shadow (`#000F0F` at 47% opacity) and glowing teal bezel highlight (`#6FF7F6` at 63% opacity).
* Shows the live dashboard widgets: Health Overview, Quick Actions, Active Medications, and Next Consultation.

---

## 3. Color Palette (Material 3 Medical Palette)

| Role | Color Name | Hex Code | Usage |
| :--- | :--- | :--- | :--- |
| Background Gradient Start | Deep Medical Navy/Teal | `#031718` | Top-left backdrop anchor |
| Background Gradient End | Rich Forest Teal | `#0B282A` | Bottom-right backdrop |
| Brand Primary | MediSense Teal | `#006A6A` | Badge, accents |
| Brand Primary Bright | Electric Cyan/Mint | `#4CD7B6` / `#6FF7F6` | Glows, icons, highlights |
| Text Primary | Clean White-Cyan | `#E0F4F4` | Main headline |
| Text Secondary | Pale Slate-Cyan | `#B0CCCC` | Sub-points and descriptions |
| Disclaimer Text | Muted Cyan-Gray | `#82AAAA` | Safety notice |

---

## 4. Safety & Policy Compliance Audit

| Requirement | Policy Check | Status |
| :--- | :--- | :--- |
| **No Deceptive Medical Claims** | No claims of "100% accuracy", "diagnoses disease", or "replaces your doctor". | Verified |
| **Clear Positioning** | Stated as "Educational health decision support" and "Personal AI Healthcare Assistant". | Verified |
| **No API Keys or Credentials** | Canvas contains zero developer credentials or sensitive system information. | Verified |
| **No Real Patient Data** | Fictional demo environment only (`Alex Johnson`). | Verified |
| **Google Play Guidelines** | Exact dimensions (1024x500), under 15 MB, no cut-off elements, readable on mobile. | Verified |

---

## 5. Reproduction & Automation

The feature graphic is automatically generated via:
```bash
python generate_all_play_store_assets.py
```
This script uses Python Pillow with vector-grade geometry and dynamic anti-aliasing to render the exact specification above directly from the production repository assets.
