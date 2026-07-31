# Firebase Setup Guide

Follow these steps to configure Firebase for the MediSense app.

## 1. Go to Firebase Console
Navigate to the [Firebase Console](https://console.firebase.google.com).

## 2. Create Project
Click on **Add project** and name it `MediSense`. Follow the on-screen instructions to complete the project creation.

## 3. Register Android App
1. Click the Android icon (or **Add app** -> **Android**) to register your app.
2. In the **Android package name** field, enter:
   `com.medisense.app`
3. Click **Register app**.

## 4. Download google-services.json
1. Download the `google-services.json` file provided by Firebase.
2. If you don't download it now, you can always find it in the Project Settings > General > Your apps section.

## 5. Place it inside app/ directory
Move the downloaded `google-services.json` file into the `app/` folder of your MediSense Android Studio project (i.e. `MediSense/app/google-services.json`).

## 6. Enable Authentication (Email/Password)
1. In the Firebase console, go to **Build** > **Authentication**.
2. Click **Get Started**.
3. Go to the **Sign-in method** tab.
4. Click on **Email/Password**.
5. Toggle **Enable** for Email/Password and click **Save**.

## 7. Create Firestore Database
1. Go to **Build** > **Firestore Database**.
2. Click **Create database**.
3. Choose **Start in Test Mode** (for initial development so you can read/write data easily).
4. Choose the region nearest to you.
5. Click **Enable**.

## 8. Create users Collection
The `users` collection will be created automatically when the first user registers, as the app will write a document to it. You don't need to create it manually.

## 9. Obtain SHA-1 Certificate Fingerprint
To obtain the SHA-1 certificate for Firebase (needed if using Google Sign-In or phone auth, but good to have):
1. Open the **Gradle** panel on the right side of Android Studio.
2. Navigate to **MediSense > app > Tasks > android > signingReport**.
3. Double-click **signingReport**.
4. The SHA-1 and SHA-256 keys will be printed in the **Run** tool window at the bottom.
5. Copy the SHA-1 and add it in your Firebase project settings (Project Settings > General > Your apps > Add fingerprint).

## 10. Verify Login Works
1. Run the app on an emulator or physical device.
2. Navigate to the **Register** screen.
3. Fill in the Full Name, Email, Password, and Confirm Password.
4. Tap **Register**. 
5. The app should navigate back to Login. Log in using the new credentials, which should navigate you to the **Dashboard Placeholder**.
6. Check the Firebase Console under **Authentication** to see the new user, and under **Firestore Database** to see their document in the `users` collection.
