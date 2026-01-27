# EasyLog - Engineer Time Logging System

A cloud-based time tracking solution for field engineers, featuring an Android mobile app for time logging and a web portal for management oversight.

## Overview

EasyLog enables field engineers to:
- Log travel time and on-site work hours
- Capture customer signatures for job sign-off
- Work offline with automatic background sync
- View their job history

Managers can:
- View real-time dashboard of engineer activity
- Generate reports on engineer hours and customer jobs
- Export time logs to CSV
- Manage engineers and customers

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    GCP Project: easylog-483511                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐   │
│  │   Android    │────▶│  Cloud Run   │────▶│  Firestore   │   │
│  │     App      │     │   (FastAPI)  │     │  (Database)  │   │
│  └──────────────┘     └──────────────┘     └──────────────┘   │
│         │                    │                                  │
│         │                    ▼                                  │
│         │             ┌──────────────┐                         │
│         └────────────▶│Cloud Storage │                         │
│          (signatures) │ (ezl_bucket) │                         │
│                       └──────────────┘                         │
│                                                                 │
│  ┌──────────────┐     ┌──────────────┐                         │
│  │  Web Portal  │────▶│   Firebase   │                         │
│  │ (Dashboard)  │     │   Hosting    │                         │
│  └──────────────┘     └──────────────┘                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Technology Stack

| Component | Technology |
|-----------|------------|
| **Backend API** | Python 3.11, FastAPI |
| **Database** | Google Firestore |
| **File Storage** | Google Cloud Storage |
| **Mobile App** | Kotlin, Jetpack Compose |
| **Mobile Database** | Room (SQLite) |
| **Mobile DI** | Koin |
| **Mobile Networking** | Retrofit, OkHttp |
| **Background Sync** | WorkManager |
| **Web Portal** | Vanilla JS, Tailwind CSS |
| **Hosting** | Cloud Run (API), Firebase Hosting (Portal) |

---

## Android App

### User Flow

#### 1. Login
- Engineer selects their name from dropdown
- Enters 4-6 digit PIN
- On success, background sync starts automatically

#### 2. Starting a Job
1. Tap "Start New Job" on home screen
2. Select customer (search or browse alphabetically)
3. Enter travel time (hours/minutes)
4. Tap "Start Job" - timer begins

#### 3. During Active Job
- Live timer shows elapsed time
- Can view job details (customer, start time, travel time)
- Option to cancel job if needed (with confirmation)

#### 4. Completing a Job
1. Tap "End Job" from active job screen
2. Review/edit start time if needed
3. Adjust on-site time if different from calculated
4. Enter work description (required)
5. Proceed to signature capture

#### 5. Signature Capture
1. Enter customer signer's name
2. Customer signs on touch canvas
3. Tap "Complete Job"
4. Job saved locally and synced to cloud

#### 6. Job History
- View past jobs filtered by date range
- See job details including sync status
- Jobs grouped by date

#### 7. Settings
- View account info
- Check sync status (unsynced items count)
- Manual "Sync Now" option
- Logout

### Offline Support

The app is designed offline-first:

1. **Local Storage**: All data saved to Room database immediately
2. **Background Sync**: WorkManager syncs every 15 minutes when online
3. **Manual Sync**: Users can trigger sync from Settings
4. **Retry Logic**: Failed syncs retry with exponential backoff

```
User Action → Room DB → UI Update (immediate)
                ↓
           Sync Queue
                ↓
           WorkManager (background)
                ↓
           Cloud API (when online)
                ↓
           Firestore
```

### Project Structure

```
easylog-android/app/src/main/java/com/easylog/android/
├── EasyLogApplication.kt          # App entry, Koin setup
├── MainActivity.kt                # Single activity host
├── data/
│   ├── local/
│   │   ├── dao/                   # Room DAOs
│   │   ├── entity/                # Room entities
│   │   └── EasyLogDatabase.kt     # Room database
│   ├── remote/
│   │   ├── api/EasyLogApi.kt      # Retrofit API interface
│   │   ├── dto/                   # Data transfer objects
│   │   └── interceptor/           # Auth interceptor
│   └── repository/                # Data repositories
├── domain/model/                  # Domain models
├── ui/
│   ├── navigation/NavGraph.kt     # Navigation setup
│   ├── login/                     # Login screen
│   ├── home/                      # Home dashboard
│   ├── customer/                  # Customer selection/creation
│   ├── job/                       # Job flow screens
│   ├── history/                   # Job history
│   ├── settings/                  # Settings screen
│   └── theme/                     # Material theme
├── sync/
│   ├── SyncManager.kt             # Manages sync scheduling
│   └── SyncWorker.kt              # Background sync worker
└── di/                            # Koin modules
```

---

## Backend API

### Base URL
`https://easylog-api-6ukkqliakq-bq.a.run.app`

### Authentication

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/auth/engineer/login` | POST | Engineer PIN login |
| `/auth/portal/login` | POST | Portal admin login |
| `/auth/verify` | GET | Verify token validity |

**Engineer Login Request:**
```json
{
  "engineerId": "abc123",
  "pin": "1234"
}
```

**Response:**
```json
{
  "token": "eyJ...",
  "expiresIn": 604800,
  "engineer": {
    "id": "abc123",
    "name": "John Smith",
    "email": "john@example.com"
  }
}
```

### Engineers

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/engineers/list` | GET | None | List for login dropdown |
| `/engineers` | GET | Admin | Full engineer list |
| `/engineers/{id}` | GET | Admin | Get engineer details |
| `/engineers` | POST | Admin | Create engineer |
| `/engineers/{id}` | PUT | Admin | Update engineer |
| `/engineers/{id}/reset-pin` | POST | Admin | Reset PIN |

### Customers

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/customers/list` | GET | Engineer | Minimal list for mobile |
| `/customers` | GET | Any | Full customer list |
| `/customers/{id}` | GET | Any | Get customer details |
| `/customers` | POST | Any | Create customer |
| `/customers/{id}` | PUT | Any | Update customer |

### Time Logs

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/timelogs` | GET | Any | List time logs (filterable) |
| `/timelogs/{id}` | GET | Any | Get time log details |
| `/timelogs` | POST | Engineer | Create/start time log |
| `/timelogs/{id}` | PUT | Engineer | Update time log |
| `/timelogs/{id}/complete` | POST | Engineer | Complete with signature |

**Create Time Log:**
```json
{
  "localId": "uuid-string",
  "engineerId": "eng123",
  "engineerName": "John Smith",
  "customerId": "cust456",
  "customerName": "Acme Corp",
  "travelTimeMinutes": 45,
  "timeStarted": "2024-01-15T09:30:00Z"
}
```

**Complete Time Log:**
```json
{
  "timeEnded": "2024-01-15T14:30:00Z",
  "timeOnSiteMinutes": 270,
  "workDescription": "Replaced faulty motor...",
  "customerSignerName": "Jane Doe"
}
```

### Signatures

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/signatures/upload` | POST | Upload signature image |
| `/signatures/{timelogId}` | GET | Get signature URL |

### Dashboard (Admin Only)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/dashboard/summary` | GET | Today's summary stats |
| `/dashboard/reports/engineer-hours` | GET | Engineer hours report |
| `/dashboard/reports/customer-jobs` | GET | Customer jobs report |
| `/dashboard/export/csv` | GET | Export to CSV |

---

## Database Schema

### Firestore Collections

#### engineers
| Field | Type | Description |
|-------|------|-------------|
| `name` | string | Display name |
| `pinHash` | string | bcrypt hash of PIN |
| `email` | string | Contact email |
| `phone` | string | Contact phone |
| `isActive` | boolean | Can log in |
| `createdAt` | timestamp | Creation date |

#### customers
| Field | Type | Description |
|-------|------|-------------|
| `name` | string | Company name |
| `address` | string | Physical address |
| `contactName` | string | Contact person |
| `contactPhone` | string | Contact phone |
| `createdBy` | string | Engineer ID |
| `isActive` | boolean | Is active |

#### timelogs
| Field | Type | Description |
|-------|------|-------------|
| `localId` | string | Device-generated UUID |
| `engineerId` | string | Engineer reference |
| `engineerName` | string | Denormalized name |
| `customerId` | string | Customer reference |
| `customerName` | string | Denormalized name |
| `travelTimeMinutes` | number | Travel duration |
| `timeStarted` | timestamp | Work start time |
| `timeEnded` | timestamp | Work end time |
| `timeOnSiteMinutes` | number | On-site duration |
| `workDescription` | string | Work performed |
| `customerSignerName` | string | Signer name |
| `signaturePath` | string | Cloud Storage path |
| `status` | string | `in_progress` or `completed` |

#### portal_admins
| Field | Type | Description |
|-------|------|-------------|
| `email` | string | Admin email |
| `name` | string | Display name |
| `passwordHash` | string | bcrypt hash |
| `isActive` | boolean | Can log in |

---

## Setup & Deployment

### Prerequisites
- Google Cloud account with billing enabled
- Android Studio (for mobile development)
- Python 3.11+
- Node.js (for web portal)

### Backend Deployment

```powershell
cd easylog-api
.\deploy.ps1
```

This builds and deploys to Cloud Run.

### Create First Admin

```bash
cd easylog-api
python bootstrap_admin.py
```

### Android Build

1. Open `easylog-android` in Android Studio
2. Sync Gradle
3. Build APK or run on device

### Web Portal Deployment

```bash
cd easylog-portal
firebase deploy
```

---

## Security

### Authentication
- **Engineers**: ID + 4-6 digit PIN, 7-day JWT tokens
- **Portal Admins**: Email + password, 8-hour tokens
- All passwords hashed with bcrypt (cost 12)

### Validation Rules
- PIN: 4-6 digits, no patterns (0000, 1234)
- Travel time: 0-720 minutes (max 12h)
- On-site time: 1-1440 minutes (max 24h)
- Work description: Required

### API Security
- JWT required on all endpoints (except login)
- Role-based access control
- CORS restricted to portal domain

---

## Key Design Decisions

1. **Offline-first**: Room database ensures app works without network
2. **PIN auth**: Simple for field engineers vs complex passwords
3. **7-day tokens**: Balance security with convenience (no refresh tokens)
4. **Work description required**: Ensures proper job documentation
5. **Editable start time**: Allows correction if engineer forgets to start timer
6. **Signature backup**: Saved locally first, then uploaded to cloud
7. **Recent customers**: Only shown when > 5 customers exist

---

## Project Structure

```
EngineerTimeLog/
├── easylog-api/           # Python FastAPI backend
│   ├── app/
│   │   ├── api/           # Route handlers
│   │   ├── models/        # Pydantic models
│   │   ├── services/      # Business logic
│   │   └── utils/         # Helpers
│   ├── requirements.txt
│   └── Dockerfile
├── easylog-android/       # Kotlin Android app
│   └── app/src/main/
│       └── java/com/easylog/android/
├── easylog-portal/        # Web dashboard (TBD)
├── plan.md               # Detailed project plan
└── README.md             # This file
```

---

## Version

**Document Version:** 1.0
**Last Updated:** January 2024
