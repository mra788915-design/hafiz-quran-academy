# Firebase Setup Guide — Hafiz Quran Academy

This document outlines the step-by-step setup for connecting **Hafiz Quran Academy** to Google Firebase Cloud Firestore and Firebase Authentication.

---

## 1. Create Firebase Project

1. Navigate to the [Firebase Console](https://console.firebase.google.com/).
2. Click **Add project** and enter `hafiz-quran-academy`.
3. Select your preferred Google Analytics settings and create the project.

---

## 2. Register Android Application

1. In Project Overview, click the **Android** icon to add an Android app.
2. Enter the exact Android package name configured in `app/build.gradle.kts`:
   ```
   com.aistudio.hafizquran.acdm
   ```
3. Set the App Nickname: `Hafiz Quran Academy`.
4. (Optional for Google Sign-In): Generate and paste your SHA-1 and SHA-256 fingerprint from your keystore:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
5. Click **Register app**.

---

## 3. Add `google-services.json`

1. Download `google-services.json` from the Firebase console.
2. Place the file directly in the `app/` folder of the project:
   ```
   /app/google-services.json
   ```
3. In `app/build.gradle.kts`, uncomment the Firestore & Firebase Auth dependencies:
   ```kotlin
   implementation(libs.firebase.firestore)
   implementation(libs.firebase.auth)
   ```

---

## 4. Enable Authentication Providers

1. In Firebase Console, open **Build > Authentication > Sign-in method**.
2. Enable **Email/Password**.
3. (Optional) Enable **Google Sign-In**.

---

## 5. Create Cloud Firestore Database

1. In Firebase Console, go to **Build > Firestore Database**.
2. Click **Create database**.
3. Select your nearest database region (e.g. `asia-south1` or `asia-southeast1`).
4. Choose **Production mode** (security rules will be deployed next).

---

## 6. Deploy Firestore Security Rules

Deploy the included `firestore.rules` file via Firebase CLI:
```bash
firebase deploy --only firestore:rules
```

Or copy the contents of `firestore.rules` directly into **Firestore Database > Rules** tab in the Firebase Console and click **Publish**.

---

## 7. Role-Based Access Control Architecture

The system enforces three distinct role privileges:

| Role | Permissions |
|---|---|
| **ADMIN** | Full administrative control: admissions, classes, teachers, monthly fee generation, FS approvals, attendance override, announcements. |
| **TEACHER** | View assigned classes and students, mark daily attendance, log Quran progress (Sabaq, Sabqi, Manzil/Daur), add remarks. Cannot view fees or other teachers' classes. |
| **PARENT** | View linked child's attendance rate, daily Quran memorization remarks, monthly fee challans, and download/print 3-copy PDF challans. Cannot view other families' records. |

---

## 8. Offline & Local Storage Architecture

The application comes with a built-in high-performance **Room Database & Repository layer** that stores students, teachers, classes, attendance, fee challans, payments, FS records, and Quran progress locally. This allows the app to function with 100% features even in offline environments or before a live cloud backend is connected.
