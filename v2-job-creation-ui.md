# v2 Job Creation UI - Implementation Spec

## Design Philosophy

**Keep it dead simple.** Admin can create a job in 30 seconds with minimal clicks.

## UI Design

### Job Creation Form

```html
┌─────────────────────────────────────────────────┐
│ Create New Job                        [✕ Close] │
├─────────────────────────────────────────────────┤
│                                                  │
│ Customer *                                       │
│ ┌─────────────────────────────────────────────┐ │
│ │ [Search customers...          ▼]           │ │
│ └─────────────────────────────────────────────┘ │
│ Shows: Recent customers first, then alphabetical│
│ Auto-fills location when customer selected       │
│                                                  │
│ Location *                                       │
│ ┌─────────────────────────────────────────────┐ │
│ │ 123 Main Street, City                      │ │
│ └─────────────────────────────────────────────┘ │
│ (From customer record)           [Edit Location]│
│ GPS: 34.0522°N, 118.2437°W                      │
│                                                  │
│ Assign Engineer *                                │
│ ┌─────────────────────────────────────────────┐ │
│ │ [Select engineer...           ▼]           │ │
│ └─────────────────────────────────────────────┘ │
│ Shows available engineers first                  │
│                                                  │
│ Date & Time *                                    │
│ ┌────────────────┐  ┌────────────────┐         │
│ │ 2026-01-28    │  │ 09:00         │         │
│ └────────────────┘  └────────────────┘         │
│                                                  │
│ Expected Duration *                              │
│ ┌─────────────────────────────────────────────┐ │
│ │ [2 hours                      ▼]           │ │
│ └─────────────────────────────────────────────┘ │
│ Options: 30 min, 1h, 1.5h, 2h, 3h, 4h, Custom  │
│                                                  │
│ Job Description *                                │
│ ┌─────────────────────────────────────────────┐ │
│ │ Routine maintenance check                   │ │
│ │                                             │ │
│ │                                             │ │
│ └─────────────────────────────────────────────┘ │
│                                                  │
│          [Cancel]     [Create Job]              │
│                                                  │
└─────────────────────────────────────────────────┘
```

## Key Features

### 1. Customer Selection with Auto-Fill

**Behavior:**
- Dropdown shows recent customers (last 30 days) at top
- Search filters by name as admin types
- When customer selected → auto-fill:
  - Location (address + GPS from customer record)
  - Contact info (for notifications)
  - Common job duration (if tracked)

**Implementation:**
```javascript
// customers collection already has location data
const customerRef = doc(db, 'customers', customerId);
const customerSnap = await getDoc(customerRef);
const customer = customerSnap.data();

// Auto-fill form
setFormData({
  ...formData,
  customerId: customer.id,
  customerName: customer.name,
  location: {
    address: customer.address,
    lat: customer.location?.lat,
    lng: customer.location?.lng
  },
  contactName: customer.contactName,
  contactEmail: customer.contactEmail,
  contactPhone: customer.contactPhone
});
```

### 2. Engineer Assignment

**Behavior:**
- Dropdown shows engineers with availability indicator
- Sort order:
  1. Available (no job at that time) - Green dot
  2. Busy (has job at that time) - Orange dot
  3. Inactive engineers - Hidden

**Display:**
```
┌─────────────────────────────────────┐
│ ● Mike Johnson (Available)         │
│ ● Sarah Lee (Available)            │
│ ● Tom Chen (Available)             │
│ ◐ David Park (Busy 09:00-11:00)   │
└─────────────────────────────────────┘
```

**Implementation:**
```javascript
// Check engineer availability
async function checkAvailability(engineerId, startTime, duration) {
  const endTime = startTime + (duration * 60 * 1000);

  const conflictQuery = query(
    collection(db, 'scheduled_jobs'),
    where('assignedEngineerId', '==', engineerId),
    where('status', 'in', ['scheduled', 'in_progress']),
    where('expectedStartTime', '>=', startTime),
    where('expectedStartTime', '<', endTime)
  );

  const conflicts = await getDocs(conflictQuery);
  return conflicts.empty;
}
```

### 3. Simple Date/Time Picker

**Use HTML5 inputs:**
- `<input type="date">` for date
- `<input type="time">` for time

**No fancy calendar widget needed.** Browser native is fastest.

**Default values:**
- Date: Tomorrow (or today if morning)
- Time: 09:00 (common start time)

### 4. Duration Dropdown

**Fixed options:**
- 30 minutes
- 1 hour
- 1.5 hours
- 2 hours (default)
- 3 hours
- 4 hours
- 6 hours
- 8 hours
- Custom (shows number input)

**No need for complex time calculations.** Simple dropdown is fine.

### 5. Job Description

**Plain textarea.** No templates, no AI, just type.

**Validation:** Minimum 10 characters (to prevent empty descriptions)

---

## Geofence Radius

**Keep it simple:** Default 100 meters for all jobs.

**Future enhancement:** Let admin adjust per job if needed, but don't expose in v1.

**Set automatically when job is created:**
```javascript
const jobData = {
  // ... other fields ...
  location: {
    address: customerAddress,
    lat: customerLat,
    lng: customerLng,
    geofenceRadius: 100  // meters, hardcoded for now
  }
};
```

---

## Geocoding Strategy

### Option A: Store GPS in Customer Record (Simple)

**Approach:**
- When admin creates/edits customer, provide address input
- Use Google Places Autocomplete (client-side, free tier is generous)
- Store GPS coordinates in customer document
- Reuse GPS for all jobs with that customer

**Pros:**
- Geocode once per customer (not per job)
- No geocoding cost for repeat customers
- Works offline if customer already exists

**Cons:**
- If customer moves, need to update record
- Manual GPS entry if address not found

### Option B: Geocode on Job Creation (Flexible)

**Approach:**
- When creating job, show address field
- Use Google Places Autocomplete API
- Geocode address to get GPS coordinates
- Store in job document

**Pros:**
- Handles different job sites for same customer
- Always fresh GPS data

**Cons:**
- More API calls (costs more at scale)
- Requires internet connection

### Recommendation: **Option A** (Store in Customer Record)

**Reasoning:**
- 90% of jobs are at customer's primary location
- Geocode once, reuse forever
- Cheaper at scale
- If job is at different location, allow manual edit

**If job is at different site:**
- Show [Edit Location] button
- Allow admin to type new address
- Geocode just that one job
- Don't update customer record

---

## Google Places Autocomplete Setup

### Free Tier Limits

**Google Maps Platform:**
- Places Autocomplete: $2.83 per 1,000 requests
- Geocoding API: $5 per 1,000 requests
- **Free tier:** $200/month credit
  - = ~70,000 autocomplete requests/month FREE
  - = ~40,000 geocoding requests/month FREE

**Expected usage (20 engineers, 100 jobs/month):**
- Add/edit customers: ~20 geocodes/month
- Job creation with existing customers: 0 geocodes
- **Total: ~20 requests/month = FREE**

### Implementation (Client-Side)

**HTML:**
```html
<input
  id="address-input"
  type="text"
  placeholder="Start typing address..."
  autocomplete="off"
/>
<div id="map-preview" style="height: 200px; display: none;"></div>
```

**JavaScript:**
```javascript
// Initialize Places Autocomplete
const autocomplete = new google.maps.places.Autocomplete(
  document.getElementById('address-input'),
  {
    types: ['address'],
    fields: ['formatted_address', 'geometry', 'place_id']
  }
);

// Listen for place selection
autocomplete.addListener('place_changed', () => {
  const place = autocomplete.getPlace();

  if (!place.geometry) {
    alert('No location data available for this address');
    return;
  }

  // Extract GPS coordinates
  const location = {
    address: place.formatted_address,
    lat: place.geometry.location.lat(),
    lng: place.geometry.location.lng(),
    placeId: place.place_id
  };

  // Show preview on map
  showMapPreview(location);

  // Update form
  setLocationData(location);
});

function showMapPreview(location) {
  const mapDiv = document.getElementById('map-preview');
  mapDiv.style.display = 'block';

  const map = new google.maps.Map(mapDiv, {
    center: { lat: location.lat, lng: location.lng },
    zoom: 15
  });

  new google.maps.Marker({
    position: { lat: location.lat, lng: location.lng },
    map: map,
    title: location.address
  });
}
```

**Load Google Maps API:**
```html
<script src="https://maps.googleapis.com/maps/api/js?key=YOUR_API_KEY&libraries=places"></script>
```

---

## Data Flow

### When Admin Creates Job

```
1. Admin opens "Create Job" modal
   ↓
2. Selects customer from dropdown
   ↓
3. Form auto-fills with customer data:
   - Address
   - GPS coordinates (from customer record)
   - Contact info
   ↓
4. Admin fills remaining fields:
   - Assign engineer
   - Pick date/time
   - Set duration
   - Enter description
   ↓
5. Click "Create Job"
   ↓
6. Validation:
   - All required fields filled?
   - Date in future?
   - Engineer available?
   ↓
7. Create Firestore document in `scheduled_jobs`:
   {
     customerId, customerName, location {address, lat, lng},
     assignedEngineerId, assignedEngineerName,
     expectedStartTime, expectedDuration,
     jobDescription, status: "scheduled",
     location: { ..., geofenceRadius: 100 }
   }
   ↓
8. Android app picks up new job (onSnapshot listener)
   ↓
9. App registers geofence for this job
   ↓
10. Show success message to admin
    "Job created and engineer notified"
```

---

## Validation Rules

**Required fields:**
- Customer (must exist in DB)
- Engineer (must be active)
- Date & Time (must be in future)
- Duration (must be > 0)
- Job Description (min 10 characters)

**Business rules:**
- Date must be today or later
- Time must be reasonable (06:00 - 22:00)
- Duration must be <= 12 hours
- Warn if engineer already has job at that time (but allow override)

**Error messages:**
```javascript
const errors = {};

if (!customerId) {
  errors.customer = "Please select a customer";
}

if (!engineerId) {
  errors.engineer = "Please assign an engineer";
}

if (new Date(expectedStartTime) < new Date()) {
  errors.dateTime = "Job date must be in the future";
}

if (!jobDescription || jobDescription.length < 10) {
  errors.description = "Please enter a job description (at least 10 characters)";
}
```

---

## UI States

### Loading State

```
┌─────────────────────────────────────────────────┐
│ Create New Job                                   │
├─────────────────────────────────────────────────┤
│                                                  │
│        ⏳ Creating job...                        │
│                                                  │
└─────────────────────────────────────────────────┘
```

### Success State

```
┌─────────────────────────────────────────────────┐
│ ✓ Job Created Successfully                       │
├─────────────────────────────────────────────────┤
│                                                  │
│ Job scheduled for Mike Johnson                   │
│ Tomorrow at 09:00 - Acme Corp                   │
│                                                  │
│ Engineer has been notified.                      │
│                                                  │
│          [Create Another]    [Close]             │
│                                                  │
└─────────────────────────────────────────────────┘
```

### Error State

```
┌─────────────────────────────────────────────────┐
│ ⚠️ Could Not Create Job                          │
├─────────────────────────────────────────────────┤
│                                                  │
│ • Please select a customer                       │
│ • Job date must be in the future               │
│                                                  │
│          [Fix Issues]                           │
│                                                  │
└─────────────────────────────────────────────────┘
```

---

## Integration with Android App

### Real-Time Job Notifications

**Firestore listener on Android:**
```kotlin
// In engineer's app
fun observeScheduledJobs(engineerId: String) {
    db.collection("scheduled_jobs")
        .whereEqualTo("assignedEngineerId", engineerId)
        .whereIn("status", listOf("scheduled", "in_progress"))
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Listen failed: $error")
                return@addSnapshotListener
            }

            snapshot?.documentChanges?.forEach { change ->
                when (change.type) {
                    DocumentChange.Type.ADDED -> {
                        val job = change.document.toObject<ScheduledJob>()
                        registerGeofence(job)
                        showJobNotification(job)
                    }
                    DocumentChange.Type.MODIFIED -> {
                        // Update geofence if location changed
                        updateGeofence(change.document.id)
                    }
                    DocumentChange.Type.REMOVED -> {
                        // Remove geofence
                        removeGeofence(change.document.id)
                    }
                }
            }
        }
}
```

### Push Notification

**When job is created:**
```kotlin
fun showJobNotification(job: ScheduledJob) {
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setContentTitle("New Job Assigned")
        .setContentText("${job.customerName} - ${formatDateTime(job.expectedStartTime)}")
        .setSmallIcon(R.drawable.ic_notification)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    notificationManager.notify(job.id.hashCode(), notification)
}
```

---

## Portal Schedule View

After creating jobs, admin needs to see the schedule.

### Simple List View

```html
┌─────────────────────────────────────────────────┐
│ Job Schedule              [+ Create Job]         │
├─────────────────────────────────────────────────┤
│                                                  │
│ Filter: [This Week ▼]  [All Engineers ▼]       │
│                                                  │
│ ─── Tomorrow, Jan 28 ───                        │
│                                                  │
│ 09:00  Acme Corp                           [⋮]  │
│        Mike Johnson · Maintenance · 2h          │
│        📍 123 Main St                           │
│                                                  │
│ 14:00  Beta Ltd                            [⋮]  │
│        Sarah Lee · Repair · 1.5h                │
│        📍 456 Oak Ave                           │
│                                                  │
│ ─── Thursday, Jan 29 ───                        │
│                                                  │
│ 08:00  Gamma Inc                           [⋮]  │
│        Mike Johnson · Installation · 3h         │
│        📍 789 Pine Rd                           │
│                                                  │
└─────────────────────────────────────────────────┘
```

**Click [⋮] for actions:**
- View details
- Edit job
- Cancel job
- Contact engineer

---

## Implementation Checklist

### Phase 1: Customer GPS Setup (Week 1)
- [ ] Add location fields to customer form (lat, lng)
- [ ] Integrate Google Places Autocomplete on customer address field
- [ ] Store GPS coordinates when customer is created/edited
- [ ] Add mini map preview to confirm location
- [ ] Test with 10 real addresses

### Phase 2: Job Creation Form (Week 1-2)
- [ ] Create job creation modal component
- [ ] Customer dropdown with search
- [ ] Auto-fill location from customer record
- [ ] Engineer dropdown with availability check
- [ ] Date/time/duration pickers
- [ ] Job description textarea
- [ ] Form validation
- [ ] Submit handler (create Firestore document)

### Phase 3: Schedule View (Week 2)
- [ ] Create schedule list view
- [ ] Filter by date range
- [ ] Filter by engineer
- [ ] Job cards with details
- [ ] Actions menu (edit, cancel, view)
- [ ] Real-time updates (onSnapshot)

### Phase 4: Android Integration (Week 2-3)
- [ ] Add Firestore listener for scheduled_jobs
- [ ] Show notification when job assigned
- [ ] Register geofence for new jobs
- [ ] Update geofence if job modified
- [ ] Remove geofence if job cancelled

### Phase 5: Testing (Week 3)
- [ ] Test job creation end-to-end
- [ ] Test geofence registration
- [ ] Test with multiple engineers
- [ ] Test error handling
- [ ] Test offline behavior

---

## Technical Stack

**Portal (Web):**
- Vanilla JavaScript (keep it simple)
- Firebase SDK 9+ (modular)
- Google Maps JavaScript API
- Bootstrap or Tailwind for styling (optional)

**Database:**
- Firestore (existing)

**APIs:**
- Google Maps Places API (autocomplete)
- Google Maps Geocoding API (backup if autocomplete fails)

---

## Cost Estimate

**Google Maps API:**
- Autocomplete: ~20 uses/month = FREE (under free tier)
- Geocoding: Minimal usage = FREE

**Firebase:**
- Firestore writes: ~100 jobs/month = FREE tier
- Firestore reads: Portal views = FREE tier

**Total: $0/month for typical usage**

---

## Next Steps

1. **Get Google Maps API key**
   - Go to Google Cloud Console
   - Enable Maps JavaScript API
   - Enable Places API
   - Create API key
   - Restrict to your domain

2. **Update customer form to capture GPS**
   - Add address autocomplete
   - Store lat/lng in customer documents

3. **Build job creation modal**
   - Start with static HTML
   - Add form validation
   - Connect to Firestore

4. **Test geofence registration**
   - Create test job in portal
   - Verify Android app receives it
   - Confirm geofence is registered

Want to start implementing the customer GPS capture first, or jump straight to the job creation form?
