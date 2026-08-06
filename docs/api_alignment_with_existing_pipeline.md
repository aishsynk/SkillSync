# API Alignment With Existing Pipeline

## Base pipeline

Reportee API -> scoped trainers -> child APIs per reportee -> normalize -> aggregate -> scoring/recommendation -> dashboard

The existing 9-API pipeline remains the source of truth until the new APIs are verified.

## Core APIs already in use

| API | Role |
|---|---|
| Get Direct / Indirect Reportee | Root scoped trainer list and identity |
| Get Trainer Details | Trainer course/performance facts |
| Get Trainer Skills | Skill inventory per trainer |
| Get Utilization | Utilization signal per trainer |
| Get Trainer Vendor Certification Count | Certification count per trainer |
| Get Negative Feedback Count | Risk signal per trainer |
| HR Incident Positive Negative | HR risk signal per trainer |
| Course List | Global course master |
| Assignment API | Trainer workload and assignment history |

## New API verification table

| New API | Business Problem Solved | Existing API It Enhances/Replaces | Input Needed | Output Fields Verified | Can Scope to My Reportees? | Pipeline Stage | Dashboard Impact | Trainer 360 Impact | Risk / Limitation | Integration Priority | Verification Status |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Get Trainer Feedback Details | Adds evidence-based quality context instead of count-only feedback | Negative Feedback Count | Trainer email or trainer identifier, per doc to verify | Not verified yet; doc extract appears mismatched and may not match title | Unknown until endpoint is checked | Quality / risk enrichment | Show reasons behind quality risk | Stronger narrative on trainer quality | Extract body appears unrelated in some files | 4 | Mismatched / not usable yet |
| Get Inhouse and FL Trainers Of Courses | Course -> trainer mapping for allocation | Course List + Trainer Skills + Assignment API | Course identifier / course name, to verify | Not verified yet; file body needs endpoint confirmation | Likely yes, if course-scoped | Course-first allocation | Best trainer for a course | Allocation desk / trainer matching | Needs exact request/response check | 6 | Needs verification |
| Get Course Schedule | Course schedule and timing context | Course List + Trainer availability | Course identifier / course name, to verify | Not verified yet; file body appears mismatched | Possibly, if course-scoped | Scheduling / allocation | Course readiness and timing | Better allocation against delivery windows | File content is noisy and may be wrong | 2 | Needs verification |
| Check Course Availability in RMS | Validate course exists and is schedulable | Course List | Exact course name, to verify | Not verified yet; file body appears mismatched | Likely global course check | Course validation | Prevent unavailable course recommendations | Reduces false course gaps | File content is noisy and may be wrong | 2 | Needs verification |
| Get Course and Domain | Domain/category mapping for course intelligence | Course List | Course id / course name, to verify | Not verified yet | Likely global | Course intelligence | Better grouping and filters | Domain-aware recommendations | Must confirm real endpoint | 5 | Needs verification |
| Get Course Name | Readable course name resolution | Course List | Course id / code, to verify | Not verified yet | Likely global | Normalization / display | Clean labels in UI | Better reporting clarity | Likely helper API only | 5 | Needs verification |
| Course & Technology List | Map courses to technology domains | Trainer Skills + Course List | Likely none or course/technology filters, to verify | Not verified yet; file extract is incomplete/mismatched | Likely global | Course intelligence | Skill-to-course matching | Better recommendation clustering | Needs endpoint/body confirmation | 5 | Needs verification |
| Course Without Exam | Avoid false certification gaps for courses without exams | Exam / certification mapping | Course id / course name, to verify | Not verified yet | Likely global | Certification normalization | Fewer false red flags | More accurate cert status | Must verify carefully | 7 | Needs verification |
| Exam Course Linked API | Map exams to courses | Course List + Certification Count | Course / exam reference, to verify | Not verified yet | Likely global | Certification mapping | Certification readiness panels | Exam-linked planning | Document extract appears sparse | 7 | Needs verification |
| Get Unique Certifications Count Value | Deduplicate certification counts | Trainer Vendor Certification Count | Trainer email / id, to verify | Not verified yet | Likely yes | Certification normalization | More accurate KPI counts | Better trainer readiness score | Must avoid double counting | 7 | Needs verification |
| Previous & Upcoming Assignments | Past delivery evidence plus future workload | Assignment API | Trainer email or trainer id, to verify | Not verified yet; file body appears unrelated | Likely yes | Workload / allocation | Better assignment trend panels | Over-allocation checks | Extract body is polluted | 1 | Needs verification |
| Upcoming Assignments | Future workload only | Assignment API | Trainer email or trainer id, to verify | Not verified yet | Likely yes | Workload / allocation | Prevent over-allocation | Future planning | Must confirm endpoint | 1 | Needs verification |
| Trainer_Last_3_Months_Utilization | Utilization trend instead of single-point utilization | Get Utilization | Trainer email or id, to verify | Not verified yet; file body appears unrelated | Likely yes | Utilization / trend | Better utilization charting | Trend-based readiness | Extract is noisy | 3 | Needs verification |
| Get Trainer Free Schedule and Details | Real free/busy window and schedule details | Get Utilization + Trainer availability | Trainer email or id, to verify | Not verified yet; file body appears unrelated | Likely yes | Availability | Better availability panels | Free-slot planning | Must confirm request schema | 2 | Needs verification |
| Trainer availability | Actual availability before assignment | Get Utilization | Trainer email or id, to verify | Not verified yet; file body appears unrelated | Likely yes | Availability | Avoid wrong availability assumptions | Allocation accuracy | Extract is noisy | 2 | Needs verification |
| Trainer RC Schedule | Calendar / blocked-time availability logic | Trainer availability | Trainer email or id, to verify | Not verified yet | Likely yes | Availability | Better calendar blocks | Better scheduling confidence | Must verify endpoint | 2 | Needs verification |

## Verification rules

For each new API:

1. Verify the actual endpoint.
2. Verify the request body.
3. Verify the response schema.
4. Run one safe test if credentials exist.
5. Mark as Verified, Mismatched, or Not usable yet.

Do not integrate APIs marked Mismatched.

## Python code alignment

config.py
- add API configs only after verification

api_client.py
- reuse the same token/common call pattern

normalize.py
- add normalizers for:
  - normalize_upcoming_assignments()
  - normalize_availability()
  - normalize_utilization_trend()
  - normalize_feedback_details()
  - normalize_course_technology()
  - normalize_exam_course_mapping()

pipeline.py
- call new APIs only after reportee_master exists
- call per trainer only for scoped reportees
- call course master APIs globally only once

scoring.py
- use availability/utilization trend only after verified
- do not replace scoring blindly

recommendation.py
- enrich course matching with course technology/domain/exam mapping

executive/dashboard builder
- use richer fields only if verified

## Integration order

1. Previous & Upcoming Assignments / Upcoming Assignments
2. Trainer availability / Free Schedule / RC Schedule
3. Trainer_Last_3_Months_Utilization
4. Get Trainer Feedback Details
5. Course & Technology List / Course Domain / Course Name
6. Get Inhouse and FL Trainers Of Courses
7. Exam Course Linked / Course Without Exam / Unique Certification Count

