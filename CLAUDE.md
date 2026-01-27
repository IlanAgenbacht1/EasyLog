# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

EasyLog is a Firebase-based time tracking system for field engineers. Engineers use an Android app to log time with offline support, while managers use a web portal for oversight and reporting.

**Architecture:** Pure Firebase (no backend API)
- Authentication: Firebase Auth (Google Sign-in)
- Database: Firestore
- File Storage: Firebase Storage
- Hosting: Firebase Hosting

**GCP Project:** easylog-483511
**Region:** africa-south1
**Firebase Console:** https://console.firebase.google.com/project/easylog-483511

## Repository Structure

```
EngineerTimeLog/
├── easylog-android/       # Android app (Kotlin + Compose) - PRODUCTION READY ✅
├── easylog-portal/        # Web portal (Vanilla JS) - MIGRATION IN PROGRESS
├── easylog-api/           # Legacy FastAPI backend - TO BE REMOVED ❌
├── plan.md                # Architecture plan and requirements
└── CLAUDE.md              # This file
```

**Important:** The `easylog-api` directory contains a legacy FastAPI backend that is being phased out. The Android app already uses Firebase SDK directly. The portal is being migrated to use Firebase SDK instead of API calls.

## Commands

### Firebase

**Deploy Firestore security rules:**
```bash
firebase deploy --only firestore:rules
```

**Deploy Storage security rules:**
```bash
firebase deploy --only storage
```

**Deploy web portal:**
```bash
firebase deploy --only hosting
```

**Deploy all:**
```bash
firebase deploy
```

### Android App (easylog-android/)

**Build and run:**
1. Open `easylog-android` in Android Studio
2. Sync Gradle (File > Sync Project with Gradle Files)
3. Run on device: Run > Run 'app' (or Shift+F10)

**Build APK:**
- Build > Build Bundle(s) / APK(s) > Build APK(s)

**Key files:**
- `app/build.gradle.kts` - Dependencies and build config
- `gradle/libs.versions.toml` - Version catalog
- `app/google-services.json` - Firebase configuration (not in git)

### Web Portal (easylog-portal/)

**Local development:**
```bash
cd easylog-portal
# Serve with any static server, e.g.:
python -m http.server 8080
# Or use Firebase emulator:
firebase serve
```

**Deploy:**
```bash
cd easylog-portal
firebase deploy --only hosting
```

## Architecture

### Firebase-Only Stack

**No backend API.** Both Android and Web use Firebase SDKs directly:
- **Firestore SDK** for database operations (with built-in offline persistence)
- **Firebase Auth SDK** for Google Sign-in
- **Firebase Storage SDK** for photos and signatures

### Authentication Flow

**Android & Portal (same flow):**
1. User signs in with Google via Firebase Auth
2. App checks if user exists in `engineers` or `portalAdmins` collection
3. If exists and `isActive: true` → proceed to app
4. If not exists → show "Not authorized" message

**No JWTs, no custom auth logic.** Firebase handles all token management.

### Security Model

Security enforced via **Firestore Security Rules** (in `easylog-portal/firestore.rules`):
- Engineers can only read/write their own time logs
- Portal admins have full read/write access
- All writes validated (e.g., travel time 0-720 minutes)

Rules deployed via: `firebase deploy --only firestore:rules`

## Android App Structure

```
easylog-android/app/src/main/java/com/easylog/android/
├── EasyLogApplication.kt          # Koin DI setup
├── MainActivity.kt                # Single-activity Compose app
├── data/
│   ├── auth/
│   │   └── GoogleAuthClient.kt    # Firebase Auth wrapper
│   ├── repository/
│   │   ├── FirebaseAuthRepository.kt  # Auth + user management
│   │   ├── CustomerRepository.kt      # Firestore customers
│   │   └── TimeLogRepository.kt       # Firestore timelogs
│   └── local/                     # Room (offline cache)
├── ui/
│   ├── navigation/NavGraph.kt     # Compose Navigation
│   ├── login/                     # Google Sign-in
│   ├── home/                      # Dashboard
│   ├── customer/                  # Customer selection/creation
│   ├── job/                       # Job lifecycle screens
│   ├── history/                   # Job history
│   └── settings/                  # Settings & logout
├── storage/
│   └── StorageManager.kt          # Local file management
├── sync/
│   ├── SyncManager.kt             # Sync scheduling
│   └── SyncWorker.kt              # Background sync (WorkManager)
├── util/
│   └── PhotoManager.kt            # Camera & image processing
└── di/
    ├── AppModule.kt               # Koin DI module
    └── DatabaseModule.kt          # Room database module
```

### Key Patterns (Android)

**1. Repository Pattern**
- All Firestore operations in repository classes
- Repositories use Firebase SDK directly (no API calls)
- Example: `CustomerRepository` wraps Firestore collection("customers")

**2. Offline-First Architecture**
- Firestore SDK has built-in offline persistence (enabled in config)
- All reads return cached data when offline
- All writes queue locally and sync when online
- No manual sync logic needed for Firestore data

**3. Dependency Injection (Koin)**
- Single module in `di/AppModule.kt`
- ViewModels, repositories, and managers injected
- DataStore for local preferences (token, user state)

**4. MVVM + Compose**
- Each screen has a ViewModel
- State hoisted to composables
- Navigation via Compose Navigation

**5. File Upload Strategy** (Improved Architecture - No Device Paths in Firestore)
- Photos/signatures saved to device storage immediately (`StorageManager`)
- Pending upload tracking stored in **local Room DB** (`pending_uploads` table)
- Firestore gets `uploadStatus: "pending"` (NOT device-local file paths)
- `SyncWorker` reads Room DB to know which files to upload
- After successful upload: cloud paths saved to Firestore, local entry deleted from Room
- **Critical**: Device-local paths NEVER written to shared Firestore database

**6. Firebase Auth Integration**
- `GoogleAuthClient` handles Google Sign-in via Credential Manager
- `FirebaseAuthRepository` checks user in `engineers` collection
- Auth state managed via DataStore
- Token refresh handled automatically by Firebase SDK

### Android Dependencies

Managed via Gradle Version Catalog (`gradle/libs.versions.toml`):
- **Compose** - UI toolkit
- **Koin** - Dependency injection
- **Room** - Local database (offline cache)
- **Firebase** - Auth, Firestore, Storage
- **WorkManager** - Background sync
- **Coil** - Image loading
- **Navigation Compose** - Screen navigation
- **DataStore** - Preferences storage

## Web Portal Structure

```
easylog-portal/
├── index.html              # Dashboard
├── login.html              # Admin login
├── engineers.html          # Engineer management
├── customers.html          # Customer management
├── timelogs.html           # Time log viewing
├── reports.html            # Reports & export
├── js/
│   ├── config.js           # Firebase config
│   ├── auth.js             # Firebase Auth wrapper
│   ├── api.js              # LEGACY (being replaced with Firebase SDK)
│   └── utils.js            # Utilities
├── firestore.rules         # Firestore security rules
├── storage.rules           # Storage security rules
└── firebase.json           # Firebase hosting config
```

### Portal Migration Strategy

**Current state:** Portal uses legacy API (`easylog-api`)
**Target state:** Portal uses Firebase SDK directly (like Android app)

**When working on portal:**
1. Replace API calls in `js/api.js` with Firestore SDK calls
2. Use Firebase Auth SDK for authentication (already in place)
3. Use Firebase Storage SDK for file access
4. Follow Android repository pattern for consistency

**Example transformation:**
```javascript
// OLD (API-based)
const response = await fetch(`${API_URL}/customers`, {
  headers: { Authorization: `Bearer ${token}` }
});
const customers = await response.json();

// NEW (Firebase SDK)
const customersRef = collection(db, 'customers');
const snapshot = await getDocs(customersRef);
const customers = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
```

## Database Schema (Firestore)

### Collections

**engineers** (document ID = Firebase Auth UID)
```javascript
{
  uid: string,            // Firebase Auth UID (redundant with document ID)
  name: string,           // Display name
  email: string,          // From Google account
  phone: string,          // Optional contact
  isActive: boolean,      // Access control
  createdAt: timestamp,
  updatedAt: timestamp
}
```

**customers**
```javascript
{
  name: string,           // Customer/company name
  address: string,        // Physical address
  contactName: string,    // Contact person name
  contactPhone: string,   // Contact phone number
  contactEmail: string,   // Contact email
  notes: string,          // General notes
  createdBy: string,      // Engineer UID who created
  isActive: boolean,      // Whether customer is active
  createdAt: timestamp,
  updatedAt: timestamp
}
```

**timelogs**
```javascript
{
  localId: string,              // Device-generated UUID
  engineerId: string,           // Firebase Auth UID
  engineerName: string,         // Denormalized for display
  customerId: string,           // Customer doc ID
  customerName: string,         // Denormalized for display
  travelTimeMinutes: number,    // 0-720
  timeStarted: timestamp,       // Work start
  timeEnded: timestamp,         // Work end (null if in_progress)
  timeOnSiteMinutes: number,    // Confirmed duration (null if in_progress)
  workDescription: string,      // Required on completion
  customerSignerName: string,   // Signer name
  signaturePath: string,        // Cloud Storage path after upload (single signature)
  signatureUrl: string,         // Download URL (generated on demand)
  photoPaths: array,            // Cloud Storage paths after upload ["photos/{id}/1.jpg"]
  signedAt: timestamp,          // When customer signed
  status: string,               // "in_progress" | "completed"
  uploadStatus: string,         // "pending" | "completed" (file upload status)
  createdAt: timestamp,
  updatedAt: timestamp
}
```

**pending_uploads** (Room DB - Local only, NOT in Firestore)
```javascript
{
  timeLogId: string,            // Firestore document ID (primary key)
  localSignaturePath: string,   // Local device file path (nullable)
  localPhotoPaths: array,       // Local device file paths
  createdAt: number             // Timestamp when upload was queued
}
```
Note: This table exists ONLY on the device in the local Room database. It tracks which files need to be uploaded to Firebase Storage. Device-local file paths are NEVER written to the shared Firestore database.

**portal-users** (document ID = Firebase Auth UID)
```javascript
{
  email: string,
  name: string,
  isActive: boolean,
  createdAt: timestamp
}
```

### Denormalization Strategy

Engineer and customer names are copied into time log documents:
- **Why:** Enables display without additional queries
- **Trade-off:** Names don't update if engineer/customer renamed
- **Decision:** Acceptable because time logs are historical records

### File Storage Strategy

**Local-first upload approach (Improved Architecture):**
1. Photos/signatures saved to device storage immediately (in app's private files directory)
2. Local file paths stored in **local Room DB** (`pending_uploads` table) - NOT Firestore
3. Firestore document gets `uploadStatus: "pending"` (no device-local paths!)
4. Background worker (`SyncWorker`) reads Room DB to find pending uploads
5. Once uploaded, cloud paths stored in Firestore (`signaturePath`, `photoPaths`)
6. Firestore `uploadStatus` set to `"completed"`
7. Pending upload entry deleted from local Room DB

**Cloud Storage paths:**
- **Photos:** `photos/{timelogId}/{filename}.jpg`
- **Signatures:** `signatures/{timelogId}/{filename}.png`

**Why this approach:**
- Firebase Storage SDK doesn't queue uploads when offline (unlike Firestore)
- Local-first ensures user can complete jobs without network
- **Critical improvement:** Device-local file paths NEVER stored in shared Firestore database
- Prevents data corruption if device is lost or job viewed from different device
- Each device manages its own upload queue independently via Room DB

## Security Rules

### Firestore (`firestore.rules`)

**Key functions:**
- `isEngineer()` - Checks user in `engineers` collection with `isActive: true`
- `isPortalAdmin()` - Checks user in `portalAdmins` collection with `isActive: true`
- `isOwner(engineerId)` - Checks if current user matches engineerId

**Access patterns:**
- Engineers: CRUD on own time logs, read-only customers, read own profile
- Portal admins: Full access to everything except `portalAdmins` (managed via Console)
- Time log creation: Validates engineerId matches auth.uid and travel time in range

**Deploy:** `firebase deploy --only firestore:rules`

### Storage (`storage.rules`)

**Photos:** `photos/{timelogId}/{fileName}`
- Read: Any authenticated user
- Write: Any authenticated user, max 10MB, must be image

**Signatures:** `signatures/{timelogId}/{fileName}`
- Read: Any authenticated user
- Write: Any authenticated user, max 5MB, must be image

**Deploy:** `firebase deploy --only storage`

## Key Implementation Details

### Offline Support (Android)

**Firestore Offline Persistence:**
- Explicitly enabled in `EasyLogApplication.kt` with unlimited cache size
- Configured before Koin initialization
- Automatic caching of all reads
- Automatic queueing of writes
- No manual sync code needed for Firestore operations

**File Upload Retry:**
- Photos/signatures saved to local device storage immediately
- Local paths tracked in **local Room DB** (`pending_uploads` table) - NOT Firestore
- Firestore gets `uploadStatus: "pending"` to indicate uploads in progress
- `SyncWorker` runs every 15 minutes, reads Room DB for pending uploads
- On successful upload:
  - Cloud paths saved to Firestore (`photoPaths`, `signaturePath`)
  - Firestore `uploadStatus` set to `"completed"`
  - Entry deleted from local Room DB
  - Local files deleted from device storage
- WorkManager retries failed uploads with exponential backoff
- **Architecture benefit:** No device-local paths in shared Firestore = no data corruption across devices

### Validation Rules

| Field | Rule |
|-------|------|
| Travel time | 0-720 minutes (max 12 hours) |
| On-site time | 1-1440 minutes (max 24 hours) |
| Photo size | Max 10MB per file |
| Signature size | Max 5MB |
| Work description | Required on job completion |
| Customer signer name | Required on job completion |

Enforced in:
- Android UI (prevents invalid input)
- Firestore Security Rules (server-side validation)
- Portal UI (when migration complete)

### Common Development Tasks

**Add new Firestore collection:**
1. Define schema in `plan.md` (documentation)
2. Add security rules in `easylog-portal/firestore.rules`
3. Deploy rules: `firebase deploy --only firestore:rules`
4. Create repository class in Android (`data/repository/`)
5. Add to Koin DI module (`di/AppModule.kt`)

**Add new screen to Android app:**
1. Create composable in `ui/{feature}/`
2. Create ViewModel in same directory
3. Add ViewModel to Koin module
4. Add route to `ui/navigation/NavGraph.kt`

**Add field to existing collection:**
1. Update schema in `plan.md`
2. Update security rules if needed
3. Update Android entity/model
4. Update repository methods
5. Update UI to display/edit field
6. Deploy rules if changed

**Debug offline sync issues:**
1. Check Logcat for "Firestore" tag (shows sync events)
2. Check WorkManager logs for `SyncWorker` execution
3. Verify network connectivity in emulator/device
4. Check `StorageManager` for local file paths
5. Verify Firebase Storage rules allow upload

## Migration Notes

### Legacy API Removal

The `easylog-api/` directory contains a FastAPI backend that is **no longer used**:
- Android app migrated to Firebase SDK (complete)
- Portal migration in progress
- **Do not add features to the API**
- **Do not fix API bugs** (except blocking issues)
- Focus on Firebase SDK implementation

### Portal Migration Checklist

When migrating portal pages to Firebase SDK:
- [ ] Replace `fetch()` calls with Firestore SDK
- [ ] Use `onSnapshot()` for real-time updates (optional)
- [ ] Handle loading states (Firestore is async)
- [ ] Handle offline state (show cached data)
- [ ] Remove `js/api.js` dependencies
- [ ] Test with Firebase Auth (not API tokens)
- [ ] Update error handling (Firebase exceptions vs HTTP errors)

## Cost Optimization

**All services use Firebase free tier:**
- Firebase Auth: 50k MAU (expected <100)
- Firestore reads: 50k/day (expected <5k)
- Firestore writes: 20k/day (expected <1k)
- Firebase Storage: 5 GB (expected <1 GB)
- Firebase Hosting: 10 GB/month (expected <100 MB)

**Estimated monthly cost:** $0 for typical usage (20 engineers, 100 jobs/day)

**Optimization tips:**
- Use Firestore offline persistence (reduces reads)
- Avoid unnecessary real-time listeners (use `getDocs` for one-time reads)
- Compress images before upload (`PhotoManager` handles this)
- Limit photo count per job (UI allows max 10)

## Troubleshooting

### Android Build Issues

**"google-services.json not found":**
- Download from Firebase Console > Project Settings > Your apps
- Place in `easylog-android/app/`
- File is in `.gitignore` (don't commit)

**"Room schema export location not found":**
- Create directory: `mkdir easylog-android/app/schemas`
- Or disable in `app/build.gradle.kts`

**Koin dependency injection errors:**
- Check `di/AppModule.kt` for missing dependencies
- Ensure all ViewModels registered in module
- Verify `startKoin` called in `EasyLogApplication.kt`

### Firestore Issues

**"Permission denied" errors:**
- Check security rules: `firebase deploy --only firestore:rules`
- Verify user exists in `engineers` or `portalAdmins` collection
- Ensure `isActive: true` in user document
- Check Firebase Console > Authentication for user

**Offline sync not working:**
- Verify offline persistence enabled (default in SDK)
- Check device network connectivity
- Review Logcat for Firestore sync errors
- Try signing out and back in (refreshes cache)

### Storage Upload Issues

**Upload fails silently:**
- Check storage rules: `firebase deploy --only storage`
- Verify file size within limits (10MB photos, 5MB signatures)
- Check file MIME type is `image/*`
- Review Logcat for Storage SDK errors

**Signature/photo not displaying:**
- Verify path saved in Firestore document
- Check Firebase Console > Storage for file existence
- Ensure user authenticated when fetching download URL
- Use storage rules to debug access issues

## Important Conventions

**Never:**
- Add features to `easylog-api/` (it's being deprecated)
- Commit `google-services.json` (contains Firebase config)
- Commit service account keys
- Hard-code Firebase config in source files (use `google-services.json`)

**Always:**
- Use Firebase SDK directly (no API intermediary)
- Enforce validation in both UI and security rules
- Test offline functionality (airplane mode)
- Use Koin for dependency injection (Android)
- Follow repository pattern for data access
- Deploy security rules after schema changes
- Document schema changes in `plan.md`
