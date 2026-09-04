# RMS API to SkillEdge Application Map

**Reviewed:** 2026-09-04  
**Input:** 37 supplied instruction documents. Credentials in those documents are confidential
reference data and are intentionally not reproduced here.

| # | Supplied API document / capability | Key | Current backend use | Manager app consumer | Status / correction needed |
|---:|---|---:|---|---|---|
| 1 | Add Trainer Skill (IDP) | 255 | `addTrainerSkill`; validated write plus read-back | Bulk skill assignment, endorsement, reportee self-mark/approval | Active write. Add-only; no update/delete API. Keep confirmation and exact-account safeguards. |
| 2 | Assignment API | 15 | Undated fallback/reference and Trainer Index assignment count | Indirectly dashboard/reports | Partial. Request parameter usage is inconsistent in the Trainer Index route and must be reconciled with the supplied schema. Never use it for availability because it has no dates. |
| 3 | Check Course Availability in RMS | 104 | Registered, unused | None | Missing. Probe response before using it to validate existing catalogue records. Two supplied files describe the same capability. |
| 4 | Course & Technology List | 114 | Course taxonomy cache | Courses/capability portfolio | Active. Confirm refresh/failure state is surfaced rather than silently empty. |
| 5 | Course List | 164 | Registered, unused | None | Likely overlaps key 70. Compare live schema and choose one authoritative catalogue source. |
| 6 | Course Without Exam | 213 | Exam-required policy | Certification readiness, fast-track demand | Active. Preserve distinction between no-exam-required and missing policy data. |
| 7 | Exam Course Linked | 215 | Registered, unused | None | Missing from current certification-gap flow; existing exam guidance is less precise without it. Live probe required. |
| 8 | Active SC Date | 13 | Pipeline, accounts, demand context | Pipeline Radar, Accounts, planning | Active. CSM/commercial fields are only partly carried through; fee data must remain manager-only. |
| 9 | Assignment Pax | 209 | Roster-health alert and batch-pax route | Notifications, batch roster endpoint | Active but incomplete UI. Current count is not historical proof of drops. Student PII must never enter notifications. |
| 10 | Course and Domain | 205 | Taxonomy joins by technology | Capability portfolio | Active. Cache and incomplete taxonomy state should remain explicit. |
| 11 | Course Content URL | 156 | Registered, unused | None | Compare with CourseURL and TOC already supplied by key 190 before wiring. |
| 12 | Course Module | 206 | Registered, unused | None | Candidate source for the existing curriculum screen; live response shape must be verified first. |
| 13 | Course Name | 70 | Canonical course catalogue and exact-name resolution | Course search, syllabus, availability matching | Active and foundational. |
| 14 | Course Schedule | 246 | Course-intelligence route | Course intelligence | Thin. Schedule data is not integrated into manager planning or trainer-relevant demand. |
| 15 | Course Syllabus / TOC | 248 | Syllabus route | Course and Trainer 360 capability detail | Active. Verify blank/error states and URL safety. |
| 16 | Direct / Indirect Reportee | 82 | Auth role signal and complete manager roster | Every manager team-scoped page | Active and authoritative for scope. Never truncate the roster. |
| 17 | HR Incident Positive / Negative | 59 | Evaluation, HR report, Trainer Index | HR report, Trainer 360 evaluation | Active. Ensure reportee self-view does not expose manager-only HR detail unintentionally. |
| 18 | In-house and Freelance Trainers of Courses | 157 | Alternative/network trainer routes | Batch alternatives/network results | Active. Broader-network data must remain separate from the manager's own roster. |
| 19 | Latest Version of Courses | 172 | Registered, unused | None | Missing from existing course/capability currency claims. Live probe required. |
| 20 | Negative Feedback Count | 58 | Dashboard actions, reports, index | Today, action queue, Trainer 360, reports | Active. Count must link to evidence from keys 244/218 rather than stand alone. |
| 21 | Recording Details by Assignment ID | 278 | Missing-recording alert and recordings library | Work/compliance, Trainer Practice | Active. Live-confirm final download field and remove guessed legacy aliases afterward. |
| 22 | SCID | 173 | Registered but not directly called | None | Key 190 already carries SCID text. Verify whether the separate endpoint adds value; otherwise document as intentionally redundant. |
| 23 | Trainer Details | 75 | Capability, course skill level, availability off-dates | Dashboard, People, Trainer 360, eligibility, assignment messages | Active but underused. `techcallrating`, future-skill date, DM, and shift-band fields are incompletely surfaced. |
| 24 | Trainer Feedback Details | 244 | Feedback analytics/logs, reports, sentiment, benchmark | Trainer 360, reports, Practice | Active. Remove invented sentiment defaults; show sample size and unavailable state. |
| 25 | Trainer Free Schedule and Details | 171 | Course-first availability and eligibility | Allocation candidates/readiness | Active. Exact catalogue course-name dependency must be visible on resolution failure. |
| 26 | Trainer Negative Feedback | 218 | Allocation block/tie-break, feedback evidence | Eligibility, Trainer 360, Practice | Active. Employee-code dependency must remain verified. |
| 27 | Trainer Skills | 217 | Skill register/read-back | Capability and skill-write verification | Active. Supplied response lacks level, so do not use it as the skill-level source. |
| 28 | Trainer Vendor Certification Count | 57 | Certification/accreditation and Trainer Index | People, Trainer 360, HR report | Active. Accreditation is not the same as passed certification; UI must preserve that distinction. |
| 29 | Unique Certifications Count Value | 72 | Registered, unused; prior probes returned zero rows | None | Blocked by unusable response. Do not infer zero certifications. RMS clarification required. |
| 30 | Utilization | 55 | Current/trend dashboard calculations | Today, People, reports, benchmark | Active. Missing readings must remain unknown, never 0 or a hardcoded default. |
| 31 | Previous & Upcoming Assignments | 16 | Primary dated assignment source | Today, People, Work, notifications, reports | Active. Live response has no skill level; join trainer/course to key 75 and label it trainer skill level. |
| 32 | Trainer Availability | 90 | Registered, unused | None | Compare live behavior with keys 171 and 111; either integrate as corroboration or document redundancy. |
| 33 | Last 3 Months Utilization | 39 | Trainer 360/history/index | Trainer 360 and reports | Active. One call uses `email` although the verified endpoint keys on employee code; correct parameter consistency. |
| 34 | Trainer Resume Details | 87 | Identity/profile/certification/language/experience | Profile, Trainer 360 | Active. Treat literal `None` image as missing and preserve scope. |
| 35 | Unallocated Assignment | 190 | Demand, planning, allocation desk, alerts | Plan, Batch Detail, reports | Active and rich. Live response has no required skill level; do not confuse the reportee self-mark ceiling with a batch requirement. |
| 36 | Upcoming Assignments | 93 | Registered, unused | None | Key 16 currently covers the required dated flow. Verify redundancy before removal or adoption. |
| 37 | Check Course Availability (duplicate supplied guide) | 104 | Same registration as row 3 | None | Duplicate documentation, not a second API capability. Consolidate documentation after credential removal. |

## Coverage summary

- All supplied capabilities are represented in the backend registry.
- Directly active: 27 backend integrations.
- Registered but not directly used or awaiting verified value: keys 93, 90, 173, 104, 164,
  215, 156, 206, 172, and 72.
- The highest-value missing integrations for existing screens are exam-course linking, course
  version currency, course availability hygiene, and course-module curriculum detail.
- Security supersedes feature coverage: embedded credentials must be rotated and removed before
  any production publication.
