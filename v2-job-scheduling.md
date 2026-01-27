# v2 Job Scheduling System - Admin Experience Design

## Problem Statement

Without an external system to integrate with, admins must manually schedule every job. This is the critical bottleneck that makes or breaks the v2 automation system.

**Current manual process would require:**
- Entering customer details for each job
- Looking up GPS coordinates for addresses
- Typing job descriptions
- Assigning engineers
- Setting expected times
- Re-entering recurring customer info

**This is too slow and error-prone for a production system.**

## Solution: Multi-Input Smart Scheduling System

Rather than forcing one manual input method, provide multiple easy paths for job creation:

### 1. Quick Job Creation (Primary Method)
### 2. Email-to-Job Parser (Automated)
### 3. Bulk Import from Spreadsheet
### 4. Customer Self-Service Portal
### 5. Recurring Job Templates

---

## 1. Quick Job Creation UI (Portal)

**Goal:** Create a job in under 30 seconds for known customers

### Smart Customer Selection

```
┌─────────────────────────────────────────────────┐
│ Create New Job                                   │
├─────────────────────────────────────────────────┤
│                                                  │
│ Customer: [Acme Corp ▼]  [+ New Customer]      │
│   └─ Quick search/autocomplete                  │
│   └─ Shows recent customers first               │
│   └─ Auto-fills: address, contact, GPS          │
│                                                  │
│ Location: 123 Main St (verified ✓)             │
│   └─ From customer record                       │
│   └─ [Update GPS] if moved                      │
│                                                  │
│ Contact: John Smith (john@acme.com)             │
│   └─ From customer record                       │
│   └─ [Add another contact] if needed            │
│                                                  │
│ Assign To: [Mike Johnson ▼]                    │
│   └─ Shows engineers sorted by:                 │
│       - Availability (not already scheduled)    │
│       - Proximity to job site                   │
│       - Past jobs with this customer            │
│                                                  │
│ Date & Time: [Tomorrow ▼] at [09:00 ▼]         │
│   └─ Smart defaults:                            │
│       - "Tomorrow" / "This afternoon" / Date    │
│       - Common time slots (08:00, 09:00, etc.)  │
│                                                  │
│ Expected Duration: [2 hours ▼]                  │
│   └─ Remember avg for this customer/job type    │
│                                                  │
│ Job Description:                                │
│ ┌─────────────────────────────────────────────┐ │
│ │ [Recently used templates ▼]                 │ │
│ │ - Annual maintenance                        │ │
│ │ - Emergency repair                          │ │
│ │ - Installation                              │ │
│ │ - Follow-up visit                           │ │
│ └─────────────────────────────────────────────┘ │
│ Or type custom description...                   │
│                                                  │
│ [Cancel]              [Create Job & Notify] ✓   │
└─────────────────────────────────────────────────┘
```

**Time-Saving Features:**
- ✅ Customer autocomplete with recent history
- ✅ Auto-populate address and GPS from customer record
- ✅ Engineer suggestions based on availability and proximity
- ✅ Natural language date entry ("tomorrow", "next monday")
- ✅ Job description templates
- ✅ Remember common durations per customer
- ✅ One-click create and notify

**Target:** 20-30 seconds per job for repeat customers

---

## 2. Email-to-Job Parser (AI-Powered)

**Goal:** Admin forwards customer email → job auto-created for approval

### How It Works

**Customer sends email:**
```
From: john@acme.com
Subject: Need service tomorrow

Hi, we need someone to come out tomorrow morning around 9am
for the annual maintenance at our Main Street location.

Thanks,
John
```

**Admin forwards to:** `jobs@easylog.app`

**System automatically:**
1. Extracts customer email (john@acme.com)
2. Looks up customer in database (Acme Corp)
3. Parses: "tomorrow morning around 9am"
4. Identifies: "annual maintenance"
5. Gets address from customer record
6. Creates DRAFT job

**Admin sees notification:**
```
┌─────────────────────────────────────────────────┐
│ 🤖 New Job Auto-Created from Email              │
├─────────────────────────────────────────────────┤
│                                                  │
│ Customer: Acme Corp ✓                           │
│ Location: 123 Main St ✓                         │
│ Date/Time: Tomorrow (Jan 28) at 09:00 ✓        │
│ Description: Annual maintenance                  │
│                                                  │
│ Assign Engineer: [Select ▼]                    │
│   - Mike Johnson (available, nearby)            │
│   - Sarah Lee (available)                       │
│                                                  │
│ [Edit Details] [Approve & Assign] [Reject]      │
└─────────────────────────────────────────────────┘
```

**Admin just:**
- Reviews auto-filled details (5 seconds)
- Selects engineer (5 seconds)
- Clicks "Approve & Assign" (1 second)

**Time:** ~10 seconds per job

### Email Parsing AI

Use GPT-4 (via OpenAI API) or similar to extract:
- Customer identification (email, name, company)
- Date/time ("tomorrow morning", "next Tuesday at 2pm", "ASAP")
- Location (if new/different address mentioned)
- Job type/description
- Urgency indicators

**Implementation:**
```javascript
// Firebase Function triggered by email webhook
async function parseJobEmail(emailData) {
  const prompt = `
Extract job scheduling details from this customer email:

From: ${emailData.from}
Subject: ${emailData.subject}
Body: ${emailData.body}

Extract:
1. Date and time (convert to ISO format, assume ${currentTimezone})
2. Job description/type
3. Location/address (if mentioned)
4. Urgency (normal/urgent)

Respond in JSON format.
`;

  const aiResponse = await openai.chat.completions.create({
    model: "gpt-4",
    messages: [{role: "user", content: prompt}]
  });

  const parsed = JSON.parse(aiResponse.content);

  // Look up customer by email
  const customer = await findCustomerByEmail(emailData.from);

  // Create draft job
  await createDraftJob({
    customerId: customer.id,
    parsedData: parsed,
    sourceEmail: emailData.id
  });

  // Notify admin
  await notifyAdmin("New job parsed from email - needs engineer assignment");
}
```

**Email Service Options:**
- SendGrid Inbound Parse Webhook
- Mailgun Routes
- Gmail API (if using Google Workspace)

---

## 3. Bulk Import from Spreadsheet

**Goal:** Schedule a week's worth of jobs in one upload

### CSV/Excel Template

```csv
Customer Name,Customer Email,Address,Contact Name,Contact Phone,Date,Time,Duration (hours),Description,Assigned Engineer Email
Acme Corp,john@acme.com,123 Main St,John Smith,555-0100,2026-01-28,09:00,2,Annual maintenance,mike@company.com
Beta Ltd,sarah@beta.com,456 Oak Ave,Sarah Lee,555-0200,2026-01-28,14:00,1.5,Emergency repair,mike@company.com
```

### Import UI

```
┌─────────────────────────────────────────────────┐
│ Bulk Import Jobs                                 │
├─────────────────────────────────────────────────┤
│                                                  │
│ [Download Template] CSV / Excel                  │
│                                                  │
│ Upload file:                                     │
│ ┌─────────────────────────────────────────────┐ │
│ │  Drag & drop or click to browse             │ │
│ └─────────────────────────────────────────────┘ │
│                                                  │
│ [Upload and Preview]                            │
│                                                  │
└─────────────────────────────────────────────────┘
```

**After upload, show preview:**

```
┌─────────────────────────────────────────────────┐
│ Preview: 15 jobs found                           │
├─────────────────────────────────────────────────┤
│                                                  │
│ ✓ 12 jobs ready to import                       │
│ ⚠️ 2 jobs need attention:                        │
│   - Row 5: Customer "XYZ Corp" not found        │
│   - Row 8: Invalid date format                  │
│ ❌ 1 job has errors:                             │
│   - Row 12: Missing required field "Address"    │
│                                                  │
│ [Fix Issues] [Import Valid Jobs Only]           │
└─────────────────────────────────────────────────┘
```

**Smart Features:**
- Auto-match customers by name/email (fuzzy matching)
- Auto-geocode addresses
- Validate engineer assignments
- Show conflicts (engineer already scheduled)
- Preview before committing

**Use Case:** Admin gets a batch of appointments from a customer or needs to schedule a week ahead

---

## 4. Customer Self-Service Portal (Phase 2)

**Goal:** Let customers request service themselves

### Public Booking Portal

**URL:** `https://easylog.app/book/{companyCode}`

```
┌─────────────────────────────────────────────────┐
│ 🛠️ Book Service - Your Company Name              │
├─────────────────────────────────────────────────┤
│                                                  │
│ Your Contact Info:                               │
│   Name:  [________________]                      │
│   Email: [________________]                      │
│   Phone: [________________]                      │
│                                                  │
│ Service Location:                                │
│   Address: [_____________________________]       │
│   └─ Auto-suggest from Google Places            │
│                                                  │
│ What do you need?                                │
│   ○ Routine Maintenance                         │
│   ○ Repair/Troubleshooting                      │
│   ○ Installation                                │
│   ○ Other: [___________]                        │
│                                                  │
│ Preferred Date & Time:                           │
│   [📅 Select Date] [⏰ Morning / Afternoon]     │
│                                                  │
│ Description:                                     │
│   ┌───────────────────────────────────────┐     │
│   │ Tell us more about what you need...   │     │
│   └───────────────────────────────────────┘     │
│                                                  │
│ [Submit Request]                                │
│                                                  │
│ ℹ️ We'll confirm your appointment within 2 hours │
└─────────────────────────────────────────────────┘
```

**Admin receives:**
```
┌─────────────────────────────────────────────────┐
│ 🔔 New Service Request from Customer             │
├─────────────────────────────────────────────────┤
│                                                  │
│ John Smith (john@acme.com, 555-0100)            │
│ 123 Main Street                                  │
│                                                  │
│ Requested: Tomorrow morning                      │
│ Service Type: Routine Maintenance                │
│                                                  │
│ "Our annual service is due. Same as last year."  │
│                                                  │
│ Assign Engineer: [Select ▼]                     │
│ Confirm Time: [09:00 ▼]                         │
│                                                  │
│ [Approve & Schedule] [Contact Customer First]    │
└─────────────────────────────────────────────────┘
```

**Benefits:**
- Customer does the data entry
- Reduces phone calls and emails
- Available 24/7
- Auto-geocodes addresses
- Pre-filters spam (simple CAPTCHA)

**Implementation:**
- Simple web form (part of portal)
- Saves to `service_requests` collection
- Admin notification
- One-click to convert to scheduled job

---

## 5. Recurring Job Templates

**Goal:** Set up repeating jobs once, auto-create going forward

### Recurring Job Setup

```
┌─────────────────────────────────────────────────┐
│ Create Recurring Job Schedule                    │
├─────────────────────────────────────────────────┤
│                                                  │
│ Customer: [Acme Corp ▼]                         │
│ Location: 123 Main St ✓                         │
│ Assigned Engineer: [Mike Johnson ▼]             │
│                                                  │
│ Job Description:                                 │
│ ┌─────────────────────────────────────────────┐ │
│ │ Monthly HVAC maintenance                    │ │
│ └─────────────────────────────────────────────┘ │
│                                                  │
│ Recurrence:                                      │
│   ○ Weekly                                      │
│   ● Monthly (on the [1st ▼] [Monday ▼])       │
│   ○ Quarterly                                   │
│   ○ Yearly                                      │
│   ○ Custom...                                   │
│                                                  │
│ Start Date: [Jan 28, 2026 ▼]                   │
│ Time: [09:00 ▼]                                 │
│ Duration: [2 hours ▼]                           │
│                                                  │
│ End Condition:                                   │
│   ○ Never                                       │
│   ● After [12] occurrences                      │
│   ○ On date: [_______]                          │
│                                                  │
│ Auto-confirm jobs: [Yes ▼]                      │
│   └─ Or require admin approval each time         │
│                                                  │
│ [Preview Schedule] [Create Recurring Job]       │
└─────────────────────────────────────────────────┘
```

**Preview before creation:**
```
┌─────────────────────────────────────────────────┐
│ Upcoming Jobs (Next 3 months)                    │
├─────────────────────────────────────────────────┤
│                                                  │
│ ✓ Feb 1, 2026 09:00 - Acme Corp - Mike Johnson │
│ ✓ Mar 1, 2026 09:00 - Acme Corp - Mike Johnson │
│ ✓ Apr 1, 2026 09:00 - Acme Corp - Mike Johnson │
│                                                  │
│ ... (9 more)                                    │
│                                                  │
│ [Confirm] [Adjust Schedule]                     │
└─────────────────────────────────────────────────┘
```

**System behavior:**
- Creates jobs automatically (e.g., 1 week before due date)
- Checks for conflicts (engineer already booked)
- Notifies admin if conflicts found
- Allows admin to skip/reschedule individual instances

**Use Cases:**
- Maintenance contracts (monthly/quarterly service)
- Regular customers with standing appointments
- Seasonal work (e.g., HVAC twice yearly)

---

## Recommended Implementation Priority

### Phase 1 (MVP) - Week 1-2
1. ✅ **Quick Job Creation UI** (80% of use cases)
   - Customer selection with autocomplete
   - Auto-fill address and GPS
   - Engineer assignment with suggestions
   - Job templates

2. ✅ **Bulk Import** (batch scheduling)
   - CSV/Excel upload
   - Validation and preview
   - Auto-geocoding

### Phase 2 (Automation) - Week 3-4
3. ✅ **Email-to-Job Parser** (reduce manual entry)
   - Email webhook setup
   - AI parsing (OpenAI GPT-4)
   - Draft job creation
   - Admin approval flow

### Phase 3 (Self-Service) - Week 5+
4. ✅ **Customer Self-Service Portal** (reduce admin load)
   - Public booking form
   - Address autocomplete
   - Service request approval

5. ✅ **Recurring Job Templates** (maintenance contracts)
   - Template creation
   - Auto-scheduling
   - Conflict detection

---

## Portal Dashboard Enhancement

### Admin Job Management View

```
┌─────────────────────────────────────────────────────────────┐
│ 📋 Job Schedule                           [+ New Job ▼]      │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│ Filters: [📅 This Week ▼] [👤 All Engineers ▼] [✓ Scheduled] │
│                                                               │
│ ┌───────────────────────────────────────────────────────┐   │
│ │ TODAY - Tuesday, Jan 27                                │   │
│ ├───────────────────────────────────────────────────────┤   │
│ │ 09:00  ●  Acme Corp - Mike Johnson                   │   │
│ │            123 Main St · Maintenance · 2h            [≡]  │
│ │                                                           │
│ │ 14:00  ●  Beta Ltd - Sarah Lee                        │   │
│ │            456 Oak Ave · Repair · 1.5h               [≡]  │
│ └───────────────────────────────────────────────────────┘   │
│                                                               │
│ ┌───────────────────────────────────────────────────────┐   │
│ │ TOMORROW - Wednesday, Jan 28                           │   │
│ ├───────────────────────────────────────────────────────┤   │
│ │ 08:00  ⏱️  Gamma Inc - Mike Johnson                    │   │
│ │            789 Pine Rd · Installation · 3h           [≡]  │
│ │                                                           │
│ │ 13:00  📝  Delta Co - [UNASSIGNED]                     │   │
│ │            321 Elm St · Follow-up · 1h     [Assign]   │   │
│ └───────────────────────────────────────────────────────┘   │
│                                                               │
│ 🔔 3 pending service requests need assignment                 │
│ 📧 2 jobs auto-created from emails awaiting approval          │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

**Status Indicators:**
- ● Green - Scheduled, engineer assigned
- ⏱️ Orange - In progress (auto-started via GPS)
- ✅ Blue - Completed, awaiting signature
- 📝 Gray - Draft/unassigned

**Quick Actions (click [≡]):**
- View details
- Edit job
- Reassign engineer
- Cancel job
- Contact customer
- View on map

---

## Address Geocoding Strategy

Critical for GPS automation: every job needs accurate coordinates.

### Automatic Geocoding

When admin enters address:
1. Use **Google Maps Geocoding API** or **Mapbox Geocoding**
2. Get lat/lng automatically
3. Show preview on mini-map for confirmation
4. Store in customer record for reuse

```javascript
async function geocodeAddress(address) {
  const response = await fetch(
    `https://maps.googleapis.com/maps/api/geocode/json?address=${encodeURIComponent(address)}&key=${API_KEY}`
  );
  const data = await response.json();

  if (data.results.length > 0) {
    const location = data.results[0].geometry.location;
    return {
      lat: location.lat,
      lng: location.lng,
      formattedAddress: data.results[0].formatted_address,
      placeId: data.results[0].place_id
    };
  }

  throw new Error("Address not found");
}
```

**UI Flow:**
```
Admin types: "123 Main St, Cityville"
↓
System auto-searches as they type (autocomplete)
↓
Admin selects from suggestions
↓
System geocodes and shows map preview
↓
Admin confirms: "Yes, that's the right location"
↓
GPS coordinates stored with job
```

### Geocoding Budget

**Cost:** ~$5 per 1000 requests (Google Maps)
**Optimization:**
- Cache coordinates in customer record (geocode once per unique address)
- Use free tier first (2,500/month free with Google Maps)
- Fallback to manual pin drop if geocoding fails

---

## Data Models for Job Scheduling

### scheduled_jobs Collection

```javascript
{
  id: string,                    // Auto-generated

  // Customer info
  customerId: string,
  customerName: string,          // Denormalized
  customerContact: {
    name: string,
    email: string,
    phone: string
  },

  // Location
  location: {
    address: string,
    lat: number,
    lng: number,
    placeId: string,             // Google Maps Place ID
    geofenceRadius: number       // meters (default 100)
  },

  // Assignment
  assignedEngineerId: string,
  assignedEngineerName: string,  // Denormalized

  // Scheduling
  expectedStartTime: timestamp,
  expectedDuration: number,      // minutes
  status: "draft" | "scheduled" | "in_progress" | "completed" | "cancelled",

  // Job details
  jobDescription: string,
  jobType: string,               // "maintenance", "repair", "installation"
  priority: "normal" | "urgent",

  // Automation
  autoStarted: boolean,
  autoCompleted: boolean,
  geofenceRegistered: boolean,

  // Recurring
  recurringJobId: string,        // null if one-time
  instanceNumber: number,        // which occurrence (1, 2, 3...)

  // Source tracking
  createdBy: string,             // admin userId
  createdVia: "manual" | "email" | "import" | "customer_portal" | "recurring",
  sourceEmailId: string,         // if created from email

  // Timestamps
  createdAt: timestamp,
  updatedAt: timestamp
}
```

### recurring_jobs Collection

```javascript
{
  id: string,

  customerId: string,
  assignedEngineerId: string,

  jobDescription: string,
  jobType: string,
  expectedDuration: number,

  recurrenceRule: {
    frequency: "daily" | "weekly" | "monthly" | "yearly",
    interval: number,            // every N days/weeks/months
    dayOfWeek: number,           // 0-6 (for weekly)
    dayOfMonth: number,          // 1-31 (for monthly)
    monthOfYear: number,         // 1-12 (for yearly)
  },

  startDate: timestamp,
  endDate: timestamp,            // null if no end
  maxOccurrences: number,        // null if unlimited

  nextScheduledDate: timestamp,  // when to create next job
  jobsCreated: number,           // counter

  autoConfirm: boolean,          // create without admin approval
  isActive: boolean,             // can be paused

  createdAt: timestamp,
  updatedAt: timestamp
}
```

### service_requests Collection (Customer Portal)

```javascript
{
  id: string,

  // Customer info (from form)
  customerName: string,
  customerEmail: string,
  customerPhone: string,

  // Job details
  address: string,
  jobType: string,
  description: string,
  preferredDate: timestamp,
  preferredTimeOfDay: "morning" | "afternoon" | "anytime",

  // Status
  status: "pending" | "approved" | "rejected" | "converted",
  reviewedBy: string,            // admin userId
  scheduledJobId: string,        // after conversion

  createdAt: timestamp,
  reviewedAt: timestamp
}
```

---

## Cost & Feasibility Analysis

### Geocoding (Google Maps API)

**Free Tier:** 2,500 requests/month
**Paid:** $5 per 1,000 requests after free tier

**Estimate:**
- 20 new customers/month × 1 geocode = 20 requests
- 100 jobs/month using existing customers = 0 requests (cached)
- **Total: ~20-50 requests/month = FREE**

### Email Parsing (OpenAI GPT-4)

**Cost:** ~$0.01 per email parsed

**Estimate:**
- 20 email-based jobs/month × $0.01 = $0.20/month

### Email Service (SendGrid/Mailgun)

**Free Tier:** 100 emails/day (SendGrid), 5,000/month (Mailgun)
**Estimate:** Well within free tier for job notifications

### Total Added Cost for v2 Scheduling

**~$0-5/month** for typical usage (20 engineers, 100 jobs/month)

---

## Implementation Checklist

### Phase 1: Quick Job Creation (Week 1)
- [ ] Customer autocomplete component
- [ ] Address input with geocoding
- [ ] Engineer assignment with smart suggestions
- [ ] Job templates system
- [ ] Mini map preview for location confirmation
- [ ] Create job and send notification

### Phase 2: Bulk Import (Week 1-2)
- [ ] CSV/Excel template design
- [ ] File upload handler
- [ ] Data validation
- [ ] Preview and fix errors
- [ ] Batch geocoding
- [ ] Batch job creation

### Phase 3: Email Parser (Week 2-3)
- [ ] Set up email webhook (SendGrid/Mailgun)
- [ ] OpenAI API integration
- [ ] Email parsing prompt engineering
- [ ] Draft job creation
- [ ] Admin approval UI
- [ ] Test with sample emails

### Phase 4: Portal Enhancements (Ongoing)
- [ ] Dashboard with job calendar view
- [ ] Job status indicators
- [ ] Quick actions menu
- [ ] Map view of scheduled jobs
- [ ] Engineer availability view

### Phase 5: Customer Portal (Week 4+)
- [ ] Public booking form
- [ ] Address autocomplete (Google Places)
- [ ] Service request submission
- [ ] Admin approval workflow
- [ ] Customer notification system

### Phase 6: Recurring Jobs (Week 5+)
- [ ] Recurring job template UI
- [ ] Schedule preview
- [ ] Auto-job creation (Cloud Function)
- [ ] Conflict detection
- [ ] Instance management (skip/edit)

---

## Success Metrics

**Admin Efficiency:**
- Job creation time: < 30 seconds (vs 2-3 minutes manual)
- Jobs per admin per day: 50+ (vs 20 manual)
- Error rate: < 5% (vs 15-20% manual entry errors)

**Automation Rate:**
- Email-to-job conversion: 60%+ of jobs
- Customer portal requests: 20%+ of jobs
- Fully manual entry: < 20% of jobs

**Data Quality:**
- Address geocoding success: > 95%
- GPS accuracy: ± 50m
- Customer info completeness: > 90%

---

## Next Steps

1. **Decision Point:** Which methods to implement first?
   - Recommend: Quick Job Creation + Bulk Import (covers 90% of needs)

2. **Setup Requirements:**
   - Google Maps API key (geocoding + autocomplete)
   - Email service account (if doing email parser)
   - OpenAI API key (if doing AI parsing)

3. **Portal Development:**
   - Build job scheduling UI
   - Add calendar/schedule view
   - Integrate geocoding

4. **Testing Strategy:**
   - Test geocoding with real addresses
   - Measure job creation time
   - Get admin feedback early

Would you like to start with the Quick Job Creation UI, or focus on the Email Parser for maximum automation?
