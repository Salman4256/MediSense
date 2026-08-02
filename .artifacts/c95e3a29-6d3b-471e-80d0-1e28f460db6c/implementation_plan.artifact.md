# Implementation Plan - Fix Logic Errors and Build Configuration

The project recently received external updates to the disease prediction module. While the core functionality is present, there are logic inconsistencies in symptom handling and a critical environment error blocking the build. This plan addresses these issues.

## User Review Required

> [!WARNING]
> The build is failing due to a missing `jlink` executable in a specific RedHat Java extension path. This is an environment issue. I will attempt to resolve it by adjusting the Gradle toolchain configuration, but it may require manual JDK configuration in Android Studio if the issue persists.

## Proposed Changes

### Build Configuration

#### [MODIFY] [gradle-daemon-jvm.properties](file:///E:/MediSense/gradle/gradle-daemon-jvm.properties)
- Change `toolchainVersion` from `21` to `17` to see if it resolves the `jlink` issue by using a more standard JDK version available in most environments.

#### [MODIFY] [gradle.properties](file:///E:/MediSense/gradle.properties)
- Add `org.gradle.java.installations.auto-detect=true` to ensure Gradle finds available JDKs on the system instead of relying on a potentially broken hardcoded path.

---

### Data Layer

#### [MODIFY] [PredictionRepository.kt](file:///E:/MediSense/app/src/main/java/com/medisense/app/data/repository/PredictionRepository.kt)
- Clean up the `getCurrentUserId` implementation to properly use `FirebaseAuthService`.
- Ensure `DISPLAY_SYMPTOMS` matches the expected internal keys if necessary.

---

### UI Layer

#### [MODIFY] [PredictionFragment.kt](file:///E:/MediSense/app/src/main/java/com/medisense/app/ui/prediction/ui/PredictionFragment.kt)
- Fix the logic inconsistency in `updateChipCheckedStates`. Use the chip's `tag` to store the original symptom key for reliable checked state synchronization.
- Remove unused imports.

#### [MODIFY] [PredictionViewModel.kt](file:///E:/MediSense/app/src/main/java/com/medisense/app/ui/prediction/viewmodel/PredictionViewModel.kt)
- Remove unused properties (`searchQuery` LiveData) and functions (`clearPrediction`, `clearSelectedSymptoms`).
- Clean up warnings identified during analysis.

---

### ML Layer

#### [MODIFY] [PredictionResult.kt](file:///E:/MediSense/app/src/main/java/com/medisense/app/ml/predictor/PredictionResult.kt)
- Ensure the data class is properly used across all layers.

## Verification Plan

### Automated Tests
- Run `gradle clean assembleDebug` to verify the build configuration fix.
- Run `analyze_file` on modified files to ensure no new warnings or errors are introduced.

### Manual Verification
- Deploy to device and test the Disease Prediction flow:
    - Search for symptoms.
    - Select/deselect symptoms and verify the counter.
    - Run prediction and verify results are displayed in the list.
