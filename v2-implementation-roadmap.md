# v2 Implementation Roadmap

## Overview

EasyLog v2 transforms the manual job tracking system into a fully automated GPS-based system. This document provides the implementation roadmap with confirmed design decisions.

---

## ✅ Confirmed Design Decisions

All architectural decisions have been locked in:

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Auto-Start** | Always ask for confirmation | Engineer maintains control, prevents false starts |
| **Auto-Complete** | Ask immediately on exit | Clear immediate feedback, prevents forgotten jobs |
| **Geofence Radius** | Fixed 100m for all jobs | Simple, consistent, good accuracy/false-positive balance |
| **Manual Override** | Always available | Safety net if automation fails |
| **Multiple Jobs** | Let engineer pick from list | Prevents confusion at same location |
| **GPS Accuracy** | Fine + coarse location | Flexibility, coarse sufficient for geofencing |
| **Notifications** | Don't auto-dismiss | Too important, require explicit action |

---

## System Architecture

### Portal (Admin Side)

**Job Creation UI:**
- Customer dropdown with auto-fill (address + GPS)
- Google Places Autocomplete for geocoding
- Engineer assignment with availability check
- Simple date/time/duration pickers
- One-click create and notify

**Key Files:**
- `easylog-portal/create-job.html` (new)
- `easylog-portal/js/job-scheduling.js` (new)
- `easylog-portal/js/google-maps.js` (new)

### Android App (Engineer Side)

**New Components:**
1. `ScheduledJobRepository` - Listen for assigned jobs
2. `GeofenceManager` - Register/manage geofences
3. `GeofenceBroadcastReceiver` - Handle entry/exit events
4. `JobStartPromptActivity` - Arrival notification handler
5. `JobCompletePromptActivity` - Exit notification handler

**Modified Components:**
- Home screen: Show today's scheduled jobs
- Job flow: Support both manual and auto-started jobs
- TimeLog model: Add auto-started flags and scheduled job link

**Key Files:**
- `location/GeofenceManager.kt` (new)
- `location/GeofenceBroadcastReceiver.kt` (new)
- `data/repository/ScheduledJobRepository.kt` (new)
- `ui/scheduled/ScheduledJobsScreen.kt` (new)
- `ui/scheduled/JobStartPromptActivity.kt` (new)
- `ui/scheduled/JobCompletePromptActivity.kt` (new)

### Firestore (Database)

**New Collection:**
```javascript
scheduled_jobs {
  customerId, customerName, customerContact,
  location { address, lat, lng, geofenceRadius: 100 },
  assignedEngineerId, assignedEngineerName,
  expectedStartTime, expectedDuration,
  jobDescription, status,
  autoStarted, autoCompleted, geofenceEvents
}
```

**Updated Collection:**
```javascript
timelogs {
  // ... existing fields ...
  scheduledJobId,        // link to scheduled job
  autoStarted: boolean,
  autoCompleted: boolean,
  signatureMethod: "in_person" | "email",
  geofenceEvents: [...]
}
```

---

## Implementation Timeline

### Week 1: Portal Job Creation + Android Permissions

**Portal:**
- [ ] Add Google Maps API key to project
- [ ] Create customer address autocomplete
- [ ] Add GPS coordinates to customer model
- [ ] Build job creation form
- [ ] Test geocoding with real addresses

**Android:**
- [ ] Add location permissions to manifest
- [ ] Create permission request flow (foreground + background)
- [ ] Add Play Services location dependency
- [ ] Build permission explanation UI
- [ ] Test permission flow on Android 10+

### Week 2: Geofencing Core

**Android:**
- [ ] Create `GeofenceManager` class
- [ ] Create `GeofenceBroadcastReceiver`
- [ ] Implement geofence registration
- [ ] Implement geofence removal
- [ ] Test with mock locations (emulator)
- [ ] Add debug geofence status screen
- [ ] Log all geofence events

### Week 3: Scheduled Jobs Integration

**Portal:**
- [ ] Create schedule list view
- [ ] Add date/engineer filters
- [ ] Implement real-time updates
- [ ] Add edit/cancel actions

**Android:**
- [ ] Create `ScheduledJob` data model
- [ ] Create `ScheduledJobRepository`
- [ ] Add Firestore listener
- [ ] Register geofences when jobs received
- [ ] Add "Today's Schedule" section to home screen
- [ ] Show job details screen

### Week 4: Auto Job Start

**Android:**
- [ ] Create `JobStartPromptActivity`
- [ ] Show notification on geofence entry
- [ ] Handle "Start Now" action
- [ ] Create TimeLog from ScheduledJob
- [ ] Update job status to "in_progress"
- [ ] Test with real locations
- [ ] Handle edge cases (early arrival, passing by)

### Week 5: Auto Job Complete

**Android:**
- [ ] Detect geofence exit during active job
- [ ] Create `JobCompletePromptActivity`
- [ ] Show notification on exit
- [ ] Handle "Yes, Complete" action
- [ ] Handle "No, Still Working" action
- [ ] Prompt for photos and description
- [ ] Test departure detection accuracy

### Week 6: Offline Support

**Android:**
- [ ] Add `CachedScheduledJob` entity (Room)
- [ ] Sync scheduled jobs to local DB
- [ ] Register geofences from cached jobs
- [ ] Queue job updates when offline
- [ ] Sync when network restored
- [ ] Test full offline workflow

### Week 7: Email Signature System

**Portal/Backend:**
- [ ] Set up email service (SendGrid or Firebase Extension)
- [ ] Create signature web form
- [ ] Generate secure signature links
- [ ] Send completion emails
- [ ] Handle signature submission

**Android:**
- [ ] Add "Email Signature" option to complete flow
- [ ] Queue email send when online
- [ ] Show signature status in history

### Week 8: Testing & Polish

**Real-World Testing:**
- [ ] Battery life testing (measure actual drain)
- [ ] GPS accuracy testing (various locations)
- [ ] Multi-engineer testing
- [ ] Edge cases:
  - Poor GPS (parking garages, tunnels)
  - Multiple jobs at same location
  - Engineer passes by job site
  - Network interruptions
  - Battery optimization modes

**Polish:**
- [ ] Optimize battery usage
- [ ] Improve geofence responsiveness
- [ ] Better error messages
- [ ] Loading states
- [ ] Notification improvements

---

## Cost Estimate

| Service | Usage | Cost |
|---------|-------|------|
| **Google Maps API** | ~20 geocodes/month | **FREE** (within free tier) |
| **Firebase Firestore** | 100 jobs/month | **FREE** (within free tier) |
| **Firebase Storage** | Photos/signatures | **FREE** (within free tier) |
| **Email Service** | 100 emails/month | **FREE** (SendGrid/Mailgun free tier) |
| **OpenAI API** | (not used in simple version) | **$0** |

**Total: $0/month** for typical usage (20 engineers, 100 jobs/month)

---

## Testing Strategy

### Phase 1: Emulator Testing
- Mock GPS locations
- Test geofence triggers
- Verify notification flow
- Test permission dialogs

### Phase 2: Controlled Field Testing
- Create real test jobs
- Visit actual job sites
- Measure GPS accuracy
- Test with 2-3 engineers

### Phase 3: Production Testing
- Limited rollout (5 engineers)
- Monitor battery usage
- Collect feedback
- Fix issues
- Full rollout

### Metrics to Track
- Geofence detection accuracy (% of correct triggers)
- Battery drain (% per day with active jobs)
- False positive rate (unwanted triggers)
- Engineer adoption rate (using automation vs manual)
- Time savings (avg time per job vs v1)

---

## Risk Mitigation

### Risk 1: Poor GPS Accuracy
**Impact:** False geofence triggers or missed arrivals
**Mitigation:**
- 100m radius provides buffer
- Manual override always available
- Log all geofence events for debugging
- Adjust radius if needed based on testing

### Risk 2: Battery Drain
**Impact:** Engineers disable location, automation fails
**Mitigation:**
- Use passive geofencing (not continuous tracking)
- Only track when jobs scheduled
- Remove geofences after completion
- Real-world battery testing before rollout
- Clear communication about battery impact

### Risk 3: Android Permission Denial
**Impact:** Users deny background location, geofencing fails
**Mitigation:**
- Clear explanation of why permission needed
- Graceful fallback to manual job start
- Periodic reminder to enable if disabled
- Show benefits in onboarding

### Risk 4: Network Unavailable
**Impact:** Can't receive new jobs, can't upload completion
**Mitigation:**
- Cache scheduled jobs locally (Room DB)
- Queue all writes to Firestore
- Geofencing works offline (cell + WiFi)
- Show clear offline status indicators

### Risk 5: Multiple Jobs at Same Location
**Impact:** Wrong job auto-started
**Mitigation:**
- Show list of all jobs at location
- Engineer picks which to start
- Check expected time window
- Log selection for tracking

---

## Rollback Plan

If v2 automation proves problematic:

**Option 1: Hybrid Mode**
- Keep v2 geofencing as optional feature
- Allow engineers to toggle automation on/off
- Default to v1 manual flow

**Option 2: Full Rollback**
- v1 remains on `main` branch (unchanged)
- Can deploy v1 immediately if needed
- All v2 changes isolated to `v2-prototype` branch

**Data Safety:**
- v2 doesn't delete v1 data
- Manual jobs still work in v2
- Can switch between versions without data loss

---

## Success Criteria

### Must Have (v2.0 Release)
- ✅ Job scheduling from portal works
- ✅ Geofence auto-detects arrival/departure
- ✅ Notifications prompt engineer to start/complete
- ✅ Manual override always available
- ✅ Works offline (with cached jobs)
- ✅ Battery drain < 10% per day (2-3 jobs)

### Should Have (v2.1)
- Email signature system working
- Real-time admin dashboard
- Map view of engineer locations
- Job analytics and reports

### Nice to Have (v2.2+)
- Auto-start with timer (upgrade from always-ask)
- Exit timer with return window (upgrade from immediate-ask)
- Smart job suggestions based on history
- Recurring job templates
- Bulk import from spreadsheet

---

## Documentation Needed

**For Engineers:**
- [ ] How location permissions work
- [ ] What happens when you arrive at job site
- [ ] How to manually start job if automation fails
- [ ] Battery optimization tips
- [ ] Offline mode explanation

**For Admins:**
- [ ] How to create scheduled jobs
- [ ] How to add GPS to customer records
- [ ] How to monitor job status
- [ ] What to do if engineer doesn't start job
- [ ] Troubleshooting guide

**For Developers:**
- [ ] v2 architecture overview (done ✅)
- [ ] Android setup instructions
- [ ] Portal setup instructions
- [ ] Google Maps API setup
- [ ] Testing procedures

---

## Next Steps

1. **Set up Google Maps API key** (30 min)
   - Create/enable in Google Cloud Console
   - Add to portal project
   - Test autocomplete

2. **Start Week 1 implementation** (Portal + Permissions)
   - Begin with customer GPS capture
   - Parallel: Android permission flow

3. **Daily standups to track progress**
   - Review completed tasks
   - Identify blockers
   - Adjust timeline if needed

4. **Weekly demo of working features**
   - Show progress to stakeholders
   - Gather early feedback
   - Adjust priorities

---

**All design documents:**
- [v2-plan.md](v2-plan.md) - Overall v2 vision and architecture
- [v2-job-scheduling.md](v2-job-scheduling.md) - Admin job scheduling options (chose simple UI)
- [v2-job-creation-ui.md](v2-job-creation-ui.md) - Portal UI implementation details
- [v2-android-architecture.md](v2-android-architecture.md) - Android GPS automation details
- [v2-implementation-roadmap.md](v2-implementation-roadmap.md) - This document (the plan)

**Ready to build!** 🚀
