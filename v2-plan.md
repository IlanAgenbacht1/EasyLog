# EasyLog v2 - Automated Job Tracking System

## Vision

Transform EasyLog from manual job tracking to a fully automated system using GPS geofencing, external job scheduling integration, and email-based customer signatures.

## Key Differences from v1

| Feature | v1 (Manual) | v2 (Automated) |
|---------|-------------|----------------|
| Job Creation | Engineer creates manually | External system creates with GPS + schedule |
| Job Start | Manual "Start Job" button | Auto-detected via geofencing on arrival |
| Job End | Manual "End Job" + signature | Auto-detected on departure or push notification |
| Customer Signature | In-person on device | Email link with signature form |
| Job Assignment | Engineer selects customer | Pre-assigned from external system |

## Architecture Components

### 1. External Job System Integration

**Requirements:**
- API to receive job data (customer, location, expected start time)
- Job scheduling system (could be simple Firestore collection)
- Webhook/polling to get updates

**Data Model:**
```javascript
jobs {
  customerId: string,
  customerName: string,
  location: {
    lat: number,
    lng: number,
    address: string
  },
  assignedEngineerId: string,
  expectedStartTime: timestamp,
  expectedDuration: number,  // minutes
  status: "scheduled" | "in_progress" | "completed" | "cancelled",
  geofenceRadius: number,    // meters (default 100)
  ...
}
```

### 2. GPS Geofencing (Android)

**Technologies:**
- Android Geofencing API
- Background location tracking (WorkManager)
- Significant motion detection (to save battery)

**Workflow:**
1. App registers geofences for all scheduled jobs (next 24 hours)
2. When engineer enters geofence:
   - Verify it's near expected start time (±30 min buffer?)
   - Show notification: "Start job at [Customer]?"
   - Auto-start if confirmed (or just auto-start)
3. Track time on site via location updates
4. Detect when engineer leaves geofence

**Battery Optimization:**
- Only track location when job is active
- Use significant motion detection for passive tracking
- Geofence radius: 50-100m (balance accuracy vs battery)

### 3. Automated Job Completion

**Option A: Geofence Exit + Timer**
- Detect exit from job geofence
- Start 15-minute timer (configurable)
- If engineer returns → cancel timer, continue job
- If timer expires → auto-complete job
- Send notification: "Job auto-completed. Tap to review."

**Option B: Push Notification Confirmation**
- Detect exit from job geofence
- Send push notification: "Did you finish the job at [Customer]?"
- Buttons: "Yes, I'm done" | "No, still working"
- If "Yes" → prompt for final details (photos, notes)
- If "No" → continue job, ignore exit

**Recommendation:** Start with Option B (more control), add Option A later as "smart mode"

### 4. Email-Based Signature System

**Flow:**
1. Engineer completes job in app (adds photos, notes)
2. System sends email to customer contact with:
   - Job summary (date, time, engineer name, duration)
   - Work description
   - Photos (embedded or linked)
   - "Sign Here" button → opens web form
3. Customer signs on web form (signature canvas)
4. Signature saved to Firebase Storage
5. Job marked as fully completed
6. Engineer gets notification: "Customer signed!"

**Technologies:**
- Email service: Firebase Extensions (Trigger Email) or SendGrid
- Signature page: Simple web form (part of portal)
- Link format: `https://easylog.web.app/sign/{jobId}?token={secureToken}`

**Email Template:**
```
Subject: Job Completion - [Customer Name] - [Date]

Dear [Customer Contact Name],

Your service request has been completed by [Engineer Name].

Job Details:
- Date: [Date]
- Time: [Start] - [End] ([Duration] hours)
- Work Performed: [Description]

Photos: [Gallery or attachments]

Please review and sign to confirm completion:
[Sign Here Button]

Thank you,
EasyLog Team
```

### 5. Offline Support Strategy

**GPS works offline** ✅
- Location services work without network
- Store location events locally
- Sync when online

**Geofencing offline** ⚠️
- Android Geofencing API requires Play Services (network needed initially)
- Fallback: Manual location checks when app is active
- Cache scheduled jobs for offline geofence calculations

**Email signature offline** ❌
- Requires network (customer side)
- Queue email send when online
- Show engineer: "Pending signature request"

### 6. Portal Enhancements

**New Features:**
- Job scheduling interface (create jobs with GPS coordinates)
- Map view of jobs and engineer locations
- Real-time job status updates
- Signature status tracking
- Email delivery status

## Implementation Phases

### Phase 1: Infrastructure (Week 1-2)
- [ ] Design job scheduling data model
- [ ] Create external job API/interface
- [ ] Set up geofencing permissions and testing
- [ ] Research email service (Firebase Extensions vs SendGrid)

### Phase 2: Geofencing Core (Week 3-4)
- [ ] Implement geofence registration for scheduled jobs
- [ ] Auto-detect job start on arrival
- [ ] Track location during active job
- [ ] Detect job site exit

### Phase 3: Automated Completion (Week 5-6)
- [ ] Implement exit notification system
- [ ] Build timer-based auto-completion
- [ ] Add engineer confirmation UI
- [ ] Handle edge cases (poor GPS, multiple jobs nearby)

### Phase 4: Email Signature System (Week 7-8)
- [ ] Set up email service
- [ ] Build signature web form
- [ ] Generate secure signature links
- [ ] Send completion emails with signature request
- [ ] Handle signature submission and storage

### Phase 5: Portal Updates (Week 9-10)
- [ ] Job scheduling UI
- [ ] Map view for jobs and engineers
- [ ] Real-time status dashboard
- [ ] Signature tracking

### Phase 6: Testing & Refinement (Week 11-12)
- [ ] End-to-end testing
- [ ] Battery usage optimization
- [ ] GPS accuracy tuning
- [ ] User acceptance testing

## Technical Challenges

### 1. Battery Life
**Problem:** Continuous GPS tracking drains battery quickly
**Solutions:**
- Use geofencing (passive) instead of active tracking
- Only track location when job is active
- Use WorkManager with appropriate intervals
- Leverage device motion detection

### 2. GPS Accuracy
**Problem:** GPS can be inaccurate (±10-50m), especially indoors/urban
**Solutions:**
- Larger geofence radius (100m+)
- Time-based confirmation (must be near expected start time)
- Manual override option always available
- Fused location provider (combines GPS, WiFi, cell towers)

### 3. External Job Integration
**Problem:** You don't have an existing system to integrate with
**Solutions:**
- **Option A:** Build simple job scheduling in portal
- **Option B:** Provide API for future integration
- **Option C:** Import jobs from CSV/spreadsheet initially
- **Recommendation:** Start with Option A, expose API for future

### 4. Email Deliverability
**Problem:** Emails might go to spam
**Solutions:**
- Use reputable service (SendGrid, Firebase Extensions)
- Set up SPF/DKIM records for your domain
- Professional email template
- Test with common email providers

### 5. Offline Job Completion
**Problem:** Email signature requires internet
**Solutions:**
- Queue email for sending when online
- Show clear status to engineer ("Pending signature request")
- Allow signature in-person as fallback
- Grace period for signature (48 hours?)

## Data Model Changes

### New Collections

**scheduled_jobs** (replaces manual job creation)
```javascript
{
  customerId: string,
  customerName: string,
  customerContact: { name, email, phone },
  location: { lat, lng, address },
  assignedEngineerId: string,
  assignedEngineerName: string,
  expectedStartTime: timestamp,
  expectedDuration: number,
  geofenceRadius: number,
  status: "scheduled" | "in_progress" | "completed" | "cancelled",
  autoStarted: boolean,      // true if started via geofence
  autoCompleted: boolean,    // true if completed via geofence exit
  createdAt: timestamp,
  updatedAt: timestamp
}
```

**signature_requests**
```javascript
{
  timeLogId: string,
  customerEmail: string,
  sentAt: timestamp,
  signedAt: timestamp,      // null if pending
  signaturePath: string,    // null if pending
  secureToken: string,      // for signature link
  emailDelivered: boolean,
  emailOpened: boolean,     // if tracking enabled
  linkClicked: boolean,
  reminderSentAt: timestamp  // for follow-up emails
}
```

### Modified Collections

**timelogs** (add automation fields)
```javascript
{
  // ... existing fields ...
  scheduledJobId: string,     // reference to scheduled job
  autoStarted: boolean,       // started via geofence
  autoCompleted: boolean,     // completed via geofence
  signatureMethod: "in_person" | "email",
  signatureRequestId: string, // if email signature
  geofenceEvents: [           // for debugging
    {
      type: "enter" | "exit",
      timestamp: timestamp,
      location: { lat, lng }
    }
  ]
}
```

## Security Considerations

1. **Signature Link Security**
   - Generate unique, unguessable tokens (UUID v4)
   - Token expires after 7 days
   - One-time use (invalidate after signing)
   - Rate limiting on signature endpoint

2. **Location Privacy**
   - Only store location events for active jobs
   - Clear historical location data after job completion
   - Don't expose raw location in portal (just job status)

3. **Email Security**
   - Don't include sensitive info in email body
   - Use HTTPS for signature links
   - Validate email addresses before sending

## Open Questions

1. **Job Scheduling:** Where do jobs come from initially?
   - Build simple scheduling UI in portal?
   - Import from external system later?

2. **Geofence Radius:** How accurate should arrival detection be?
   - 50m (very accurate, might miss) vs 200m (catches all, false positives)?

3. **Auto-Start Confirmation:** Should we always ask, or auto-start silently?
   - Safer: Always ask for confirmation
   - Smoother: Auto-start if within time window

4. **Signature Timeout:** How long should customers have to sign?
   - 48 hours? 7 days? Send reminders?

5. **Multiple Jobs at Same Location:** How to handle?
   - If customer has multiple jobs scheduled at same site

6. **Manual Override:** When should engineers manually control jobs?
   - Always allow manual start/stop as fallback
   - Or only if automation fails?

## Success Metrics

- **Job Start Accuracy:** % of jobs auto-started correctly
- **Job End Accuracy:** % of jobs auto-completed correctly
- **Battery Impact:** Average battery drain per job
- **Signature Completion Rate:** % of emails that get signed
- **Time Savings:** Reduction in engineer app interaction time

## Next Steps

1. Decide on job scheduling approach (portal UI vs external)
2. Set up Android geofencing test environment
3. Research email service options
4. Create detailed database schema
5. Build geofencing prototype
6. Test battery and GPS accuracy

---

**Note:** This is an experimental prototype branch. v1 remains stable on `main` branch for production use.
