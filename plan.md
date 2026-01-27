# EasyLog - Engineer Time Logging System

## Overview

A cloud-based system for engineers to log their time on customer jobs, with a web portal for managers to review and manage submissions. Captures travel time, on-site time, work description, photos, and customer sign-off.

**Project Name:** EasyLog
**GCP Project:** easylog-483511
**Region:** africa-south1

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Firebase                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐              ┌──────────────┐                │
│  │   Android    │─────────────▶│  Firestore   │                │
│  │     App      │              │  (Database)  │                │
│  └──────────────┘              └──────────────┘                │
│         │                             ▲                         │
│         │    ┌──────────────┐         │                         │
│         ├───▶│   Firebase   │         │                         │
│         │    │     Auth     │◀────────┤                         │
│         │    └──────────────┘         │                         │
│         │                             │                         │
│         │    ┌──────────────┐         │                         │
│         └───▶│   Firebase   │         │                         │
│              │   Storage    │         │                         │
│              └──────────────┘         │                         │
│                                       │                         │
│  ┌──────────────┐              ┌──────┴───────┐                │
│  │    Portal    │─────────────▶│   Firebase   │                │
│  │    (Web)     │              │   Hosting    │                │
│  └──────────────┘              └──────────────┘                │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Technology Stack

| Layer | Technology |
|-------|------------|
| **Auth** | Firebase Auth (Google Sign-in) |
| **Database** | Firestore |
| **File Storage** | Firebase Storage |
| **Mobile** | Kotlin + Jetpack Compose |
| **Mobile Offline** | Firestore SDK (built-in persistence) |
| **Web Portal** | Vanilla JS + Tailwind + Firebase SDK |
| **Hosting** | Firebase Hosting |

---

## Database Schema (Firestore)

### engineers Collection

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `uid` | string | Yes | Firebase Auth UID (document ID) |
| `email` | string | Yes | Google account email |
| `name` | string | Yes | Display name |
| `phone` | string | No | Contact phone |
| `isActive` | boolean | Yes | Whether engineer can use the app |
| `createdAt` | timestamp | Yes | Creation date |
| `updatedAt` | timestamp | Yes | Last update date |

### customers Collection

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Customer/company name |
| `address` | string | No | Physical address |
| `contactName` | string | No | Contact person |
| `contactPhone` | string | No | Contact phone |
| `contactEmail` | string | No | Contact email |
| `notes` | string | No | General notes |
| `createdBy` | string | Yes | Engineer UID who created |
| `isActive` | boolean | Yes | Whether customer is active |
| `createdAt` | timestamp | Yes | Creation date |
| `updatedAt` | timestamp | Yes | Last update date |

### timelogs Collection

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `engineerId` | string | Yes | Engineer UID |
| `engineerName` | string | Yes | Denormalized for display |
| `customerId` | string | Yes | Customer document ID |
| `customerName` | string | Yes | Denormalized for display |
| `travelTimeMinutes` | number | Yes | Travel time in minutes |
| `timeStarted` | timestamp | Yes | When work started on site |
| `timeEnded` | timestamp | No | When work ended |
| `timeOnSiteMinutes` | number | No | Confirmed on-site duration |
| `workDescription` | string | No | Description of work done |
| `photoPaths` | array | No | Storage paths for photos |
| `customerSignerName` | string | No | Name of person who signed |
| `signaturePath` | string | No | Storage path for signature |
| `signedAt` | timestamp | No | When customer signed |
| `status` | string | Yes | `in_progress` or `completed` |
| `createdAt` | timestamp | Yes | Creation date |
| `updatedAt` | timestamp | Yes | Last update date |

### portal-users Collection

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `uid` | string | Yes | Firebase Auth UID (document ID) |
| `email` | string | Yes | Admin email |
| `name` | string | Yes | Display name |
| `isActive` | boolean | Yes | Whether admin can access portal |
| `createdAt` | timestamp | Yes | Creation date |

---

## Security Rules

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // Helper functions
    function isSignedIn() {
      return request.auth != null;
    }

    function isEngineer() {
      return isSignedIn() &&
        exists(/databases/$(database)/documents/engineers/$(request.auth.uid)) &&
        get(/databases/$(database)/documents/engineers/$(request.auth.uid)).data.isActive == true;
    }

    function isPortalAdmin() {
      return isSignedIn() &&
        exists(/databases/$(database)/documents/portalAdmins/$(request.auth.uid)) &&
        get(/databases/$(database)/documents/portalAdmins/$(request.auth.uid)).data.isActive == true;
    }

    function isOwner(engineerId) {
      return request.auth.uid == engineerId;
    }

    // Engineers collection
    match /engineers/{engineerId} {
      allow read: if isSignedIn() && (isOwner(engineerId) || isPortalAdmin());
      allow write: if isPortalAdmin();
    }

    // Customers collection
    match /customers/{customerId} {
      allow read: if isEngineer() || isPortalAdmin();
      allow create: if isEngineer();
      allow update, delete: if isPortalAdmin();
    }

    // Timelogs collection
    match /timelogs/{timelogId} {
      allow read: if isSignedIn() &&
        (isOwner(resource.data.engineerId) || isPortalAdmin());
      allow create: if isEngineer() &&
        request.resource.data.engineerId == request.auth.uid &&
        request.resource.data.travelTimeMinutes >= 0 &&
        request.resource.data.travelTimeMinutes <= 720;
      allow update: if isEngineer() &&
        isOwner(resource.data.engineerId) &&
        request.resource.data.engineerId == resource.data.engineerId;
      allow delete: if isPortalAdmin();
    }

    // Portal admins collection
    match /portalAdmins/{adminId} {
      allow read: if isSignedIn() && isOwner(adminId);
      allow write: if false; // Managed via Firebase Console only
    }
  }
}
```

---

## Storage Rules

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {

    function isSignedIn() {
      return request.auth != null;
    }

    // Photos: engineers can upload, anyone authenticated can read
    match /photos/{timelogId}/{fileName} {
      allow read: if isSignedIn();
      allow write: if isSignedIn() &&
        request.resource.size < 10 * 1024 * 1024 &&
        request.resource.contentType.matches('image/.*');
    }

    // Signatures: engineers can upload, anyone authenticated can read
    match /signatures/{timelogId}/{fileName} {
      allow read: if isSignedIn();
      allow write: if isSignedIn() &&
        request.resource.size < 5 * 1024 * 1024 &&
        request.resource.contentType.matches('image/.*');
    }
  }
}
```

---

## Android App

### Authentication Flow

1. User taps "Sign in with Google"
2. Firebase Auth handles OAuth flow
3. App checks if user exists in `engineers` collection
4. If exists and active → proceed to Home
5. If not exists → show "Not registered" error

### Features

| Feature | Description |
|---------|-------------|
| **Google Sign-in** | One-tap authentication |
| **Home Dashboard** | Today's stats, active job, quick actions |
| **Customer Selection** | Search, filter, create new |
| **Start Job** | Select customer, enter travel time |
| **Active Job** | Live timer, end job button |
| **Complete Job** | Edit times, add description, attach photos |
| **Signature Capture** | Canvas signature with customer name |
| **Job History** | Date-filtered list of completed jobs |
| **Offline Support** | Full functionality without network |

### Offline Behavior

- Firestore SDK handles offline persistence automatically
- Reads return cached data when offline
- Writes queue locally and sync when online
- Real-time listeners resume on reconnection
- Photos/signatures upload when network available

### Screens

1. **Login** - Google Sign-in button
2. **Home** - Summary cards, active job, navigation
3. **Customer Selection** - Search + list
4. **Add Customer** - Form for new customer
5. **Start Job** - Travel time input
6. **Active Job** - Timer + end button
7. **Complete Job** - Edit times, description, photos
8. **Signature** - Canvas + customer name
9. **History** - Date filter + job list
10. **Settings** - Account info, sign out

---

## Web Portal

### Authentication

- Google Sign-in (same as Android)
- User must exist in `portalAdmins` collection

### Pages

| Page | Features |
|------|----------|
| **Dashboard** | Today's stats, recent completions, charts |
| **Time Logs** | Filterable table, search, view details |
| **Time Log Detail** | Full info, photos, signature, print |
| **Engineers** | List, add, edit, activate/deactivate |
| **Customers** | List, add, edit, view history |
| **Reports** | Engineer hours, customer jobs, date range |
| **CSV Export** | Download filtered time logs |

---

## Implementation Status

### Completed

- [x] Firestore database schema
- [x] Firebase Storage structure
- [x] Android app - all screens
- [x] Android app - offline support
- [x] Android app - photo capture
- [x] Android app - signature capture
- [x] Web portal - dashboard
- [x] Web portal - time logs
- [x] Web portal - engineers management
- [x] Web portal - customers management
- [x] Firebase Auth integration (Google Sign-in) - Android
- [x] Firestore Security Rules deployment
- [x] Storage Rules deployment
- [x] Replace Retrofit with Firestore SDK (Android)

### Pending

- [ ] Replace API calls with Firestore SDK (Portal)
- [ ] Firebase Hosting deployment
- [ ] Remove FastAPI backend

---

## Validation Rules

| Field | Rule |
|-------|------|
| Travel time | 0-720 minutes (max 12 hours) |
| On-site time | 1-1440 minutes (max 24 hours) |
| Photo size | Max 10MB each |
| Signature size | Max 5MB |
| Work description | Required on completion |

---

## Cost Estimate (Free Tier)

| Service | Free Limit | Expected Usage |
|---------|------------|----------------|
| Firebase Auth | 50k MAU | <100 |
| Firestore reads | 50k/day | <5k |
| Firestore writes | 20k/day | <1k |
| Firebase Storage | 5 GB | <1 GB |
| Firebase Hosting | 10 GB/month | <100 MB |

**Estimated cost:** $0/month for typical usage (20 engineers, 100 jobs/day)

---

## Quick Reference

### Firebase Console
```
https://console.firebase.google.com/project/easylog-483511
```

### Deploy Rules
```bash
firebase deploy --only firestore:rules
firebase deploy --only storage
```

### Deploy Portal
```bash
firebase deploy --only hosting
```

---

**Document Version:** 5.1
