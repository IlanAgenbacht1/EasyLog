# v2 Android Architecture - GPS Automation

## What Changes from v1 to v2

### v1 (Manual Flow)
```
Engineer opens app
  ↓
Selects customer manually
  ↓
Clicks "Start Job"
  ↓
Does work
  ↓
Clicks "End Job"
  ↓
Adds photos and signature
```

### v2 (Automated Flow)
```
Admin creates job in portal (assigns engineer + location)
  ↓
Engineer's app receives job notification
  ↓
App registers geofence at job location (100m radius)
  ↓
Engineer drives to site
  ↓
✨ APP AUTO-DETECTS ARRIVAL via geofence entry
  ↓
Notification: "Start job at Acme Corp?"
  ↓
Engineer taps "Yes" (or auto-start after 2 minutes)
  ↓
Job tracks time automatically
  ↓
Engineer leaves site
  ↓
✨ APP AUTO-DETECTS DEPARTURE via geofence exit
  ↓
Notification: "Are you done at Acme Corp?"
  ↓
Engineer taps "Yes, Complete Job"
  ↓
Prompts for photos and description
  ↓
Sends email to customer for signature
```

---

## New Android Components

### 1. Scheduled Jobs Listener
**Purpose:** Receive jobs assigned by admin

**What it does:**
- Listens to Firestore `scheduled_jobs` collection
- Filters by: `assignedEngineerId == currentUserId`
- Shows notification when new job assigned
- Registers geofence for each job

**Code location:** `data/repository/ScheduledJobRepository.kt` (new)

```kotlin
class ScheduledJobRepository(
    private val firestore: FirebaseFirestore,
    private val geofenceManager: GeofenceManager
) {
    fun observeScheduledJobs(engineerId: String): Flow<List<ScheduledJob>> {
        return callbackFlow {
            val listener = firestore.collection("scheduled_jobs")
                .whereEqualTo("assignedEngineerId", engineerId)
                .whereIn("status", listOf("scheduled", "in_progress"))
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }

                    val jobs = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject<ScheduledJob>()
                    } ?: emptyList()

                    // Register geofences for all scheduled jobs
                    jobs.forEach { job ->
                        if (job.status == "scheduled") {
                            geofenceManager.registerGeofence(job)
                        }
                    }

                    trySend(jobs)
                }

            awaitClose { listener.remove() }
        }
    }
}
```

---

### 2. Geofence Manager
**Purpose:** Register, track, and manage geofences for job sites

**What it does:**
- Registers circular geofence at job location (100m radius)
- Listens for geofence enter/exit events
- Triggers notifications on entry/exit
- Cleans up geofences when jobs complete

**Code location:** `location/GeofenceManager.kt` (new)

**Key Android APIs:**
- `GeofencingClient` (Google Play Services)
- `PendingIntent` for geofence broadcasts
- `BroadcastReceiver` to handle geofence events

**Permissions needed:**
```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" /> <!-- Android 10+ -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" /> <!-- Android 13+ -->
```

**Implementation:**
```kotlin
class GeofenceManager(
    private val context: Context,
    private val geofencingClient: GeofencingClient
) {
    companion object {
        private const val GEOFENCE_RADIUS_METERS = 100f
        private const val GEOFENCE_EXPIRATION_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    fun registerGeofence(job: ScheduledJob) {
        val geofence = Geofence.Builder()
            .setRequestId(job.id)
            .setCircularRegion(
                job.location.lat,
                job.location.lng,
                GEOFENCE_RADIUS_METERS
            )
            .setExpirationDuration(GEOFENCE_EXPIRATION_MS)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                Geofence.GEOFENCE_TRANSITION_EXIT
            )
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            job.id.hashCode(),
            Intent(context, GeofenceBroadcastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        geofencingClient.addGeofences(request, pendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "Geofence registered for job ${job.id}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to register geofence: $e")
            }
    }

    fun removeGeofence(jobId: String) {
        geofencingClient.removeGeofences(listOf(jobId))
    }
}
```

---

### 3. Geofence Broadcast Receiver
**Purpose:** Handle geofence enter/exit events

**What it does:**
- Receives broadcast when engineer enters/exits job site
- Shows notification to start/complete job
- Updates job status in Firestore

**Code location:** `location/GeofenceBroadcastReceiver.kt` (new)

```kotlin
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofence error: ${geofencingEvent.errorCode}")
            return
        }

        val transition = geofencingEvent.geofenceTransition
        val jobId = geofencingEvent.triggeringGeofences?.firstOrNull()?.requestId ?: return

        when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                handleJobSiteArrival(context, jobId)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                handleJobSiteDeparture(context, jobId)
            }
        }
    }

    private fun handleJobSiteArrival(context: Context, jobId: String) {
        // Show notification: "Start job at Acme Corp?"
        showJobStartNotification(context, jobId)

        // Log event in Firestore
        logGeofenceEvent(jobId, "enter")

        // Optional: Auto-start after 2 minutes if no action
        scheduleAutoStart(context, jobId)
    }

    private fun handleJobSiteDeparture(context: Context, jobId: String) {
        // Only handle if job is in progress
        val job = getJobFromFirestore(jobId)
        if (job?.status != "in_progress") return

        // Show notification: "Are you done at Acme Corp?"
        showJobCompletePrompt(context, jobId)

        // Log event
        logGeofenceEvent(jobId, "exit")

        // Optional: Auto-complete after 15 minutes if no return
        scheduleAutoComplete(context, jobId)
    }
}
```

---

### 4. Auto Job Start Logic

**Design Decision to Confirm:**

**Option A: Always Ask for Confirmation (Safer)**
```
Engineer enters geofence
  ↓
Show notification: "Start job at Acme Corp?"
  ↓
[Start Now] [Not Yet]
  ↓
If "Start Now" → Start job immediately
If "Not Yet" → Dismiss (can manually start later)
```

**Option B: Auto-Start with Timer (Smoother)**
```
Engineer enters geofence
  ↓
Show notification: "Starting job at Acme Corp in 2 minutes..."
  ↓
[Start Now] [Cancel Auto-Start]
  ↓
If no action → Auto-start after 2 minutes
If "Cancel" → Dismiss
```

**Option C: Silent Auto-Start (Riskiest)**
```
Engineer enters geofence
  ↓
Auto-start job immediately (no prompt)
  ↓
Show notification: "Job started at Acme Corp"
```

**Recommendation: Option A** (Always ask)
- Safer (prevents false starts)
- Engineer maintains control
- Can upgrade to Option B later if engineers want it

**Implementation:**
```kotlin
private fun showJobStartNotification(context: Context, jobId: String) {
    val startIntent = Intent(context, JobStartActivity::class.java).apply {
        putExtra("JOB_ID", jobId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    val startPendingIntent = PendingIntent.getActivity(
        context,
        jobId.hashCode(),
        startIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_location)
        .setContentTitle("You've arrived at job site")
        .setContentText("Ready to start job at Acme Corp?")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .addAction(R.drawable.ic_play, "Start Now", startPendingIntent)
        .addAction(R.drawable.ic_cancel, "Not Yet", dismissPendingIntent)
        .build()

    notificationManager.notify(jobId.hashCode(), notification)
}
```

---

### 5. Auto Job Complete Logic

**Design Decision to Confirm:**

**Option A: Ask Immediately on Exit (Interrupts)**
```
Engineer exits geofence
  ↓
Show notification: "Did you finish the job?"
  ↓
[Yes, I'm Done] [No, Still Working]
  ↓
If "Yes" → Go to completion screen (photos, description)
If "No" → Continue job
```

**Option B: Exit Timer with Return Window (Smoother)**
```
Engineer exits geofence
  ↓
Start 15-minute timer
  ↓
Show notification: "Job will auto-complete in 15 min..."
  ↓
If engineer returns to geofence → Cancel timer
If 15 min passes → Show completion prompt
```

**Option C: Smart Detection (Complex)**
```
Engineer exits geofence
  ↓
Check conditions:
- Time on site > expected duration?
- Travel speed > 30 km/h? (driving away vs walking around)
- Time of day (end of work day)?
  ↓
If likely done → Show completion prompt
If likely still working → Wait
```

**Recommendation: Option A** (Ask immediately)
- Simplest to implement
- Clearest to engineer
- Prevents forgotten jobs
- Can upgrade to Option B later

**Implementation:**
```kotlin
private fun showJobCompletePrompt(context: Context, jobId: String) {
    val completeIntent = Intent(context, CompleteJobActivity::class.java).apply {
        putExtra("JOB_ID", jobId)
        putExtra("AUTO_TRIGGERED", true)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    val completePendingIntent = PendingIntent.getActivity(
        context,
        jobId.hashCode(),
        completeIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_location)
        .setContentTitle("You left the job site")
        .setContentText("Are you done at Acme Corp?")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(false)
        .addAction(R.drawable.ic_check, "Yes, Complete", completePendingIntent)
        .addAction(R.drawable.ic_cancel, "No, Still Working", stillWorkingPendingIntent)
        .build()

    notificationManager.notify(jobId.hashCode(), notification)
}
```

---

### 6. Location Permissions Flow

**Android 10+ requires background location permission** (for geofencing)

**Two-step permission request:**
```
Step 1: Request foreground location
  ↓
Step 2: Request background location (separate dialog)
```

**Implementation:**
```kotlin
// Step 1: Foreground location
ActivityCompat.requestPermissions(
    activity,
    arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ),
    REQUEST_FOREGROUND_LOCATION
)

// Step 2: Background location (only after foreground granted)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    ActivityCompat.requestPermissions(
        activity,
        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        REQUEST_BACKGROUND_LOCATION
    )
}
```

**User-friendly explanation:**
```
┌─────────────────────────────────────────────────┐
│ Location Permission Needed                       │
├─────────────────────────────────────────────────┤
│                                                  │
│ EasyLog needs location access to:                │
│                                                  │
│ ✓ Detect when you arrive at job sites          │
│ ✓ Automatically start jobs                      │
│ ✓ Track time on site                            │
│ ✓ Detect when you leave                         │
│                                                  │
│ Your location is only used for scheduled jobs.  │
│                                                  │
│          [Grant Permission]                      │
│                                                  │
└─────────────────────────────────────────────────┘
```

---

### 7. Battery Optimization Strategy

**Challenge:** GPS + geofencing can drain battery

**Optimizations:**

**1. Use Geofencing (Not Continuous Tracking)**
- Geofences are battery-efficient (managed by Android)
- Only trigger when entering/exiting boundaries
- Don't poll GPS continuously

**2. Only Track Location During Active Job**
- Register geofences only for scheduled jobs
- Remove geofences after job completes
- Don't track location when no jobs scheduled

**3. Use Coarse Location When Possible**
- Use `ACCESS_COARSE_LOCATION` for geofence registration
- Only use `ACCESS_FINE_LOCATION` for job site confirmation
- 100m geofence doesn't need GPS precision

**4. Adjust Geofence Responsiveness**
```kotlin
val request = GeofencingRequest.Builder()
    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
    .addGeofence(geofence)
    .build()

// Android automatically balances battery vs responsiveness
// 100m geofence = moderate responsiveness (good balance)
```

**Expected Battery Impact:**
- **Light:** 1-2% per day (no active jobs)
- **Moderate:** 5-10% per day (2-3 jobs)
- **Testing needed** to validate real-world usage

---

### 8. Offline Support

**Good news:** Most of this works offline!

**What works offline:**
- Geofencing (uses cell towers + WiFi, not just GPS)
- Location detection
- Job start/end events (saved locally)
- Firestore writes (queued until online)

**What requires network:**
- Receiving new scheduled jobs (need to sync first)
- Uploading photos/signatures
- Sending completion emails

**Strategy:**
```
1. Sync scheduled jobs when online
2. Cache jobs locally (Room DB)
3. Register geofences from cached jobs
4. Handle geofence events offline
5. Queue Firestore writes
6. Sync when back online
```

**Implementation:**
```kotlin
// Cache scheduled jobs in Room
@Entity(tableName = "cached_scheduled_jobs")
data class CachedScheduledJob(
    @PrimaryKey val id: String,
    val customerId: String,
    val customerName: String,
    val locationLat: Double,
    val locationLng: Double,
    val expectedStartTime: Long,
    val status: String,
    // ... other fields
)

// Sync from Firestore to Room
fun syncScheduledJobs(engineerId: String) {
    firestore.collection("scheduled_jobs")
        .whereEqualTo("assignedEngineerId", engineerId)
        .get()
        .addOnSuccessListener { snapshot ->
            val jobs = snapshot.documents.mapNotNull { doc ->
                doc.toObject<ScheduledJob>()?.toCachedJob()
            }

            // Save to Room
            cachedJobDao.insertAll(jobs)

            // Register geofences
            jobs.forEach { registerGeofence(it) }
        }
}
```

---

## New Data Models

### ScheduledJob (Firestore + Room)
```kotlin
data class ScheduledJob(
    val id: String = "",

    // Customer info
    val customerId: String = "",
    val customerName: String = "",
    val customerContact: CustomerContact = CustomerContact(),

    // Location
    val location: JobLocation = JobLocation(),

    // Assignment
    val assignedEngineerId: String = "",
    val assignedEngineerName: String = "",

    // Scheduling
    val expectedStartTime: Timestamp = Timestamp.now(),
    val expectedDuration: Int = 0, // minutes
    val status: JobStatus = JobStatus.SCHEDULED,

    // Job details
    val jobDescription: String = "",
    val jobType: String = "",
    val priority: JobPriority = JobPriority.NORMAL,

    // Automation tracking
    val autoStarted: Boolean = false,
    val autoCompleted: Boolean = false,
    val geofenceEvents: List<GeofenceEvent> = emptyList(),

    // Timestamps
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
)

data class JobLocation(
    val address: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val placeId: String = "",
    val geofenceRadius: Float = 100f // meters
)

data class GeofenceEvent(
    val type: String = "", // "enter" or "exit"
    val timestamp: Timestamp = Timestamp.now(),
    val location: GeoPoint? = null
)

enum class JobStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

enum class JobPriority {
    NORMAL,
    URGENT
}
```

### Updated TimeLog Model
```kotlin
// Add fields to existing TimeLog
data class TimeLog(
    // ... existing fields ...

    // NEW: Link to scheduled job
    val scheduledJobId: String? = null,

    // NEW: Automation flags
    val autoStarted: Boolean = false,
    val autoCompleted: Boolean = false,

    // NEW: Signature method
    val signatureMethod: SignatureMethod = SignatureMethod.IN_PERSON,
    val signatureRequestId: String? = null,

    // NEW: Geofence tracking
    val geofenceEvents: List<GeofenceEvent> = emptyList()
)

enum class SignatureMethod {
    IN_PERSON,
    EMAIL
}
```

---

## Architecture Changes

### New Packages

```
com.easylog.android/
├── location/                    # NEW
│   ├── GeofenceManager.kt
│   ├── GeofenceBroadcastReceiver.kt
│   └── LocationPermissionHelper.kt
│
├── data/repository/
│   ├── ScheduledJobRepository.kt   # NEW
│   └── ... (existing)
│
├── data/local/
│   ├── dao/
│   │   └── ScheduledJobDao.kt      # NEW
│   └── entity/
│       └── CachedScheduledJob.kt   # NEW
│
├── ui/scheduled/                # NEW
│   ├── ScheduledJobsScreen.kt
│   ├── JobStartPromptActivity.kt
│   └── JobCompletePromptActivity.kt
│
└── ... (existing packages)
```

---

## UI Changes

### Home Screen - Show Scheduled Jobs

**Add section for today's scheduled jobs:**
```
┌─────────────────────────────────────────────────┐
│ EasyLog                                          │
├─────────────────────────────────────────────────┤
│                                                  │
│ Today's Schedule                                 │
│ ─────────────────────────────────────────────   │
│                                                  │
│ 09:00  Acme Corp                                │
│        123 Main St · Maintenance               📍│
│        Expected: 2 hours                         │
│        [View Details]                            │
│                                                  │
│ 14:00  Beta Ltd                                 │
│        456 Oak Ave · Repair                    📍│
│        Expected: 1.5 hours                       │
│        [View Details]                            │
│                                                  │
│ ─────────────────────────────────────────────   │
│                                                  │
│ [Start Manual Job]                              │
│                                                  │
└─────────────────────────────────────────────────┘
```

**Tap 📍 → Opens map with job location**

### Job Start Prompt (from geofence)

**Full-screen activity (triggered by notification):**
```
┌─────────────────────────────────────────────────┐
│                                                  │
│              You've Arrived!                     │
│                                                  │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │
│                                                  │
│ 🏢 Acme Corp                                     │
│ 📍 123 Main Street                               │
│                                                  │
│ Expected Duration: 2 hours                       │
│ Scheduled: 09:00 (You're on time!)             │
│                                                  │
│ Maintenance check                                │
│                                                  │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │
│                                                  │
│        [Start Job Now]    [Not Yet]             │
│                                                  │
└─────────────────────────────────────────────────┘
```

### Job Complete Prompt (from geofence exit)

```
┌─────────────────────────────────────────────────┐
│                                                  │
│          You Left the Job Site                   │
│                                                  │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │
│                                                  │
│ 🏢 Acme Corp                                     │
│                                                  │
│ Time on site: 2 hours 15 minutes                │
│ (09:05 - 11:20)                                 │
│                                                  │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │
│                                                  │
│ Are you done with this job?                      │
│                                                  │
│   [Yes, Complete Job]   [No, Still Working]     │
│                                                  │
└─────────────────────────────────────────────────┘
```

---

## Testing Strategy

### Phase 1: Mock Geofencing (No GPS)
- Create test scheduled jobs in Firestore
- Trigger geofence events manually (debug button)
- Test notification flow
- Test job start/complete logic

### Phase 2: Simulated GPS (Emulator)
- Use Android Emulator with GPS simulation
- Set test location near geofence
- Walk through entry/exit events
- Verify responsiveness

### Phase 3: Real-World Testing
- Create real jobs at actual addresses
- Drive to job sites
- Test with multiple engineers
- Measure battery impact
- Test edge cases (poor GPS, tunnels, parking garages)

### Debug Tools Needed
- Geofence status screen (list active geofences)
- Manual geofence trigger buttons
- Location accuracy display
- Geofence event log

---

## Implementation Checklist

### Phase 1: Permissions & Setup (Week 1)
- [ ] Add location permissions to manifest
- [ ] Create permission request flow (foreground + background)
- [ ] Add Google Play Services location dependency
- [ ] Test permission dialogs

### Phase 2: Geofencing Core (Week 2)
- [ ] Create GeofenceManager class
- [ ] Create GeofenceBroadcastReceiver
- [ ] Register test geofence
- [ ] Verify geofence events trigger
- [ ] Add geofence event logging

### Phase 3: Scheduled Jobs Integration (Week 2-3)
- [ ] Create ScheduledJob data model
- [ ] Create ScheduledJobRepository
- [ ] Add Firestore listener for scheduled jobs
- [ ] Register geofences when jobs received
- [ ] Add scheduled jobs UI (home screen section)

### Phase 4: Auto Job Start (Week 3-4)
- [ ] Create job start notification
- [ ] Create JobStartPromptActivity
- [ ] Handle "Start Now" action
- [ ] Create TimeLog from ScheduledJob
- [ ] Update job status to "in_progress"
- [ ] Start time tracking

### Phase 5: Auto Job Complete (Week 4-5)
- [ ] Detect geofence exit during active job
- [ ] Create job complete notification
- [ ] Create JobCompletePromptActivity
- [ ] Handle "Yes, Complete" action
- [ ] Prompt for photos and description
- [ ] Email signature flow (or in-person fallback)

### Phase 6: Offline Support (Week 5)
- [ ] Add CachedScheduledJob entity (Room)
- [ ] Sync scheduled jobs to local DB
- [ ] Register geofences from cached jobs
- [ ] Queue job updates when offline
- [ ] Sync when back online

### Phase 7: Testing & Polish (Week 6)
- [ ] Build debug tools (geofence status screen)
- [ ] Real-world testing
- [ ] Battery optimization
- [ ] Error handling
- [ ] Edge case testing

---

## Key Technical Decisions to Confirm

### 1. Auto-Start Behavior
- **Proposal:** Always ask for confirmation (Option A)
- **Alternative:** Auto-start with 2-min timer (Option B)
- **Your choice?**

### 2. Auto-Complete Behavior
- **Proposal:** Ask immediately on exit (Option A)
- **Alternative:** 15-min timer with return window (Option B)
- **Your choice?**

### 3. Geofence Radius
- **Proposal:** Fixed 100m for all jobs
- **Alternative:** Allow admin to set per job (50m-200m)
- **Your choice?**

### 4. Manual Override
- **Proposal:** Always allow manual start/stop (fallback if automation fails)
- **Your choice?**

### 5. GPS Accuracy Requirements
- **Proposal:** Use both fine and coarse location (flexibility)
- **Alternative:** Require fine location only (more accurate)
- **Your choice?**

### 6. Notification Persistence
- **Proposal:** Job start/complete notifications don't auto-dismiss (require action)
- **Alternative:** Auto-dismiss after 5 minutes
- **Your choice?**

### 7. Multiple Jobs at Same Location
- **Proposal:** Show all jobs at location, let engineer pick
- **Alternative:** Start most recent scheduled job
- **Your choice?**

---

## Dependencies to Add

**build.gradle.kts (app module):**
```kotlin
dependencies {
    // Existing dependencies...

    // Google Play Services - Location & Geofencing
    implementation("com.google.android.gms:play-services-location:21.1.0")
}
```

**Permissions (AndroidManifest.xml):**
```xml
<!-- Location permissions -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- Notification permission (Android 13+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Register geofence receiver -->
<receiver
    android:name=".location.GeofenceBroadcastReceiver"
    android:enabled="true"
    android:exported="false" />
```

---

## Estimated Complexity

**Easy (1-2 days):**
- ScheduledJob data model
- Firestore listener for scheduled jobs
- UI to display scheduled jobs

**Medium (3-5 days):**
- Geofence registration and management
- Notification handling
- Permission flow
- Job start/complete prompts

**Hard (5-7 days):**
- Geofence event handling (entry/exit logic)
- Offline sync with Room DB
- Battery optimization
- Edge case handling (poor GPS, conflicts)

**Testing (5+ days):**
- Real-world GPS testing
- Battery life testing
- Multi-engineer testing
- Edge cases

**Total estimate: 3-4 weeks** for complete implementation + testing

---

## What do you think?

**Questions for you:**
1. Which auto-start option? (Always ask / Timer / Silent)
2. Which auto-complete option? (Ask immediately / Timer / Smart detect)
3. Fixed 100m geofence or allow customization?
4. Should we always allow manual override?
5. Any concerns about battery life?
6. Ready to start with permissions + basic geofencing?

Let me know what to change and I can update the plan!
