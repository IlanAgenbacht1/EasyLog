# v2 Architecture - GPS-Automated Job Tracking

## Overview

EasyLog v2 transforms manual job tracking into automated GPS-based tracking. Admin schedules jobs in portal, engineer's phone auto-detects arrival/departure at job sites.

**Status:** Design locked, ready for implementation
**Branch:** `v2-prototype`
**v1 Branch:** `main` (production, unchanged)

---

## Core Flow

```
1. Admin creates job in portal → assigns engineer + GPS location
2. Engineer's phone registers geofence (100m radius)
3. Engineer arrives → notification "Start job?"
4. Engineer leaves → notification "Job done?"
5. Engineer adds photos/description
6. System sends email to customer for signature
```

---

## Confirmed Design Decisions

### Job Scheduling (Portal)
- **Method:** Simple job creation UI (admin manually creates each job)
- **Customer selection:** Dropdown with auto-fill (address, GPS from record)
- **GPS capture:** Google Places Autocomplete (geocode once, store in customer record)
- **Time to create job:** ~30 seconds

**Rejected alternatives:** Email parser, bulk import, customer self-service (too complex for v1)

### Auto-Start
- **Behavior:** Always ask for confirmation
- **Flow:** Geofence entry → notification → engineer taps "Start Now"
- **Why:** Prevents false starts, engineer maintains control

### Auto-Complete
- **Behavior:** Ask immediately on geofence exit
- **Flow:** Geofence exit → notification → engineer taps "Yes, I'm Done"
- **Why:** Simple, immediate feedback

### Geofencing
- **Radius:** Fixed 100m for all jobs
- **Multiple jobs at location:** Show list, engineer picks
- **Manual override:** Always available as fallback

### Offline Support
- Cache scheduled jobs in Room DB
- Geofencing works offline (cell towers + WiFi)
- Queue Firestore writes until online

---

## Database Schema

### New Collection: scheduled_jobs

```javascript
{
  id: string,
  customerId: string,
  customerName: string,
  customerContact: { name, email, phone },
  location: {
    address: string,
    lat: number,
    lng: number,
    geofenceRadius: 100  // meters, fixed
  },
  assignedEngineerId: string,
  assignedEngineerName: string,
  expectedStartTime: timestamp,
  expectedDuration: number,  // minutes
  jobDescription: string,
  status: "scheduled" | "in_progress" | "completed" | "cancelled",
  autoStarted: boolean,
  autoCompleted: boolean,
  geofenceEvents: [{type: "enter"|"exit", timestamp, location}],
  createdAt: timestamp,
  updatedAt: timestamp
}
```

### Updated Collection: timelogs

Add to existing:
```javascript
{
  // ... existing v1 fields ...
  scheduledJobId: string,        // link to scheduled job
  autoStarted: boolean,          // started via geofence
  autoCompleted: boolean,        // completed via geofence
  signatureMethod: "in_person" | "email",
  geofenceEvents: [...]
}
```

---

## Android Architecture

### New Components

**1. ScheduledJobRepository**
- Listens to Firestore `scheduled_jobs` collection
- Filters by assigned engineer
- Registers geofences for new jobs

**2. GeofenceManager**
- Registers/removes geofences (Google Play Services)
- 100m radius, 24h expiration
- One geofence per scheduled job

**3. GeofenceBroadcastReceiver**
- Handles geofence entry/exit events
- Shows notifications
- Logs events to Firestore

**4. JobStartPromptActivity**
- Full-screen prompt on arrival
- "Start Now" or "Not Yet" buttons

**5. JobCompletePromptActivity**
- Full-screen prompt on exit
- "Yes, I'm Done" or "No, Still Working" buttons

### Permissions Required

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### Dependencies to Add

```kotlin
implementation("com.google.android.gms:play-services-location:21.1.0")
```

### Offline Strategy

- Cache scheduled jobs in Room DB
- Register geofences from cache
- Queue job updates when offline
- Sync when network available

---

## Portal Changes

### New: Job Creation Form

**Fields:**
- Customer (dropdown, auto-fills address/GPS)
- Location (from customer, or manual edit)
- Engineer (dropdown, shows availability)
- Date/Time (HTML5 date/time pickers)
- Duration (dropdown: 30min, 1h, 2h, etc.)
- Description (textarea)

**Integration:**
- Google Places Autocomplete for address input
- Store lat/lng in customer record (geocode once)
- Create document in `scheduled_jobs` collection
- Android app picks up via Firestore listener

### New: Schedule View

**Display:**
- List view grouped by date
- Filter by engineer, date range
- Show: time, customer, location, status
- Actions: edit, cancel, view on map

---

## Implementation Timeline

**Week 1:** Portal job creation + Android permissions
**Week 2:** Geofencing core
**Week 3:** Scheduled jobs integration
**Week 4:** Auto job start
**Week 5:** Auto job complete
**Week 6:** Offline support
**Week 7:** Email signatures
**Week 8:** Testing + polish

**Total: 8 weeks**

---

## Key Files to Create/Modify

### Android (v2-prototype branch)

**New:**
- `location/GeofenceManager.kt`
- `location/GeofenceBroadcastReceiver.kt`
- `data/repository/ScheduledJobRepository.kt`
- `data/local/entity/CachedScheduledJob.kt`
- `data/local/dao/ScheduledJobDao.kt`
- `domain/model/ScheduledJob.kt`
- `ui/scheduled/ScheduledJobsScreen.kt`
- `ui/scheduled/JobStartPromptActivity.kt`
- `ui/scheduled/JobCompletePromptActivity.kt`

**Modified:**
- `AndroidManifest.xml` (permissions, receiver)
- `app/build.gradle.kts` (dependencies)
- `domain/model/TimeLog.kt` (add new fields)
- `ui/home/HomeScreen.kt` (show today's schedule)

### Portal (v2-prototype branch)

**New:**
- `create-job.html`
- `schedule.html`
- `js/job-scheduler.js`
- `js/google-maps.js`

**Modified:**
- `index.html` (link to schedule)
- `js/firebase-config.js` (if needed)

---

## Separation from v1

### Code Separation
- **v1:** `main` branch (production)
- **v2:** `v2-prototype` branch (development)
- Switch branches in VSCode (bottom-left indicator)

### Package Name (for testing both on same device)
```kotlin
// In v2-prototype branch only:
// app/build.gradle.kts
android {
    defaultConfig {
        applicationId = "com.easylog.android.v2dev"
    }
}
```

### Data Separation
- Same Firebase project: `easylog-483511`
- v2 adds NEW collections: `scheduled_jobs`
- v2 shares existing: `customers`, `engineers`
- v2 writes to existing: `timelogs` (with new fields)

---

## Testing Strategy

**Phase 1: Mock Testing (Week 1-2)**
- Emulator with simulated GPS
- Manual geofence triggers
- Debug buttons for testing

**Phase 2: Controlled Field Test (Week 3-6)**
- Test with 2-3 real jobs
- Actual GPS locations
- Measure battery usage

**Phase 3: Limited Rollout (Week 7-8)**
- 5 engineers testing
- Monitor accuracy and battery
- Collect feedback
- Fix issues before full rollout

**Metrics:**
- Geofence detection accuracy (target: >90%)
- Battery drain (target: <10% per day with 2-3 jobs)
- False positive rate (target: <5%)

---

## Risk Mitigation

**Poor GPS accuracy:**
- 100m radius provides buffer
- Manual override always available
- Log events for debugging

**Battery drain:**
- Use passive geofencing (not continuous GPS)
- Only track when jobs scheduled
- Real-world testing before rollout

**Permission denial:**
- Clear explanation of why needed
- Fallback to manual job start
- Periodic reminder if disabled

**Network unavailable:**
- Cache jobs locally (Room DB)
- Geofencing works offline
- Queue writes until online

---

## Cost Estimate

**Google Maps API:**
- Autocomplete: ~20 uses/month = FREE (under free tier)
- Geocoding: Minimal = FREE

**Firebase:**
- All within free tier for 100 jobs/month

**Total: $0/month**

---

## Rollback Plan

**v1 remains stable on `main` branch:**
- Can deploy v1 anytime
- Switch branches to work on v1
- v2 doesn't affect v1 data

**Hybrid mode option:**
- Allow engineers to toggle automation on/off
- Default to v1 manual flow
- v2 as opt-in feature

---

## Email Signature System (Week 7)

**Flow:**
1. Engineer completes job in app
2. System sends email to customer with:
   - Job summary (date, time, engineer, duration)
   - Work description
   - Photos (embedded/linked)
   - "Sign Here" button → web form
3. Customer signs on web
4. Signature saved to Firebase Storage
5. Engineer notified

**Service:** SendGrid or Firebase Extension (free tier)

**Security:**
- Unique token per job (UUID)
- Token expires after 7 days
- One-time use

---

## Success Criteria

**Must Have (v2.0 Release):**
- Job scheduling from portal works
- Geofence auto-detects arrival/departure
- Notifications prompt engineer
- Manual override available
- Works offline (cached jobs)
- Battery drain <10% per day

**Nice to Have (v2.1+):**
- Email signatures working
- Bulk job import
- Recurring job templates
- Real-time admin dashboard

---

## Next Steps

1. **Set up Google Maps API key** (30 min)
2. **Add location permissions to Android** (1 hour)
3. **Create GeofenceManager class** (Day 1-2)
4. **Build job creation form in portal** (Day 3-4)
5. **Test end-to-end with mock location** (Day 5)

All planning complete. Ready to implement.
