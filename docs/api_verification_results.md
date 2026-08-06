# API Verification Results

Verification was performed conservatively from the supplied extract files and current codebase. No dashboard integration was changed.

## Summary

- Verified usable: none yet
- Verified but limited: none yet
- Mismatched extract: several files are clearly polluted with unrelated API documentation
- Not usable yet: APIs missing a reliable endpoint/body/schema from the provided extract
- Needs credentials/details: APIs where the extract is too incomplete to safely call or confirm schema

## Results table

| API Name | Verification Status | Input Used | Actual Request Body | Actual Output Fields | Sample Row Shape without secrets | Can Scope to My Reportees? | Business Use Confirmed? | Integration Priority | Notes / Risk |
|---|---|---|---|---|---|---|---|---|---|
| Previous & Upcoming Assignments | Mismatched extract | Not safely testable from current file | Unknown; file body is unrelated OTP text | Unknown | Unknown | Likely yes, but not confirmed | Yes in intent only | 1 | File content is clearly polluted with OTP docs; cannot trust schema yet |
| Upcoming Assignments | Mismatched extract | Not safely testable from current file | Unknown; file body is unrelated invoice-status text | Unknown | Unknown | Likely yes, but not confirmed | Yes in intent only | 1 | File content is clearly polluted with unrelated API docs |
| Trainer availability | Mismatched extract | Not safely testable from current file | Unknown; file body is unrelated audit/write API text | Unknown | Unknown | Likely yes, but not confirmed | Yes in intent only | 2 | Extract is unrelated; do not integrate |
| Get Trainer Free Schedule and Details | Mismatched extract | Not safely testable from current file | Unknown; file body is unrelated trainee-question text | Unknown | Unknown | Likely yes, but not confirmed | Yes in intent only | 2 | Extract is unrelated; do not integrate |
| Trainer RC Schedule | Not usable yet | No usable API detail in extract | Unknown | Unknown | Unknown | Unknown | Only intent known | 2 | API detail not found; needs endpoint/body/response details |
| Trainer_Last_3_Months_Utilization | Mismatched extract | Not safely testable from current file | Unknown; file body is unrelated invoice-status text | Unknown | Unknown | Likely yes, but not confirmed | Yes in intent only | 3 | Extract is unrelated; do not integrate |
| Get Trainer Feedback Details | Mismatched extract | Not safely testable from current file | Unknown; file body is unrelated trainee-question text | Unknown | Unknown | Likely yes, but not confirmed | Yes in intent only | 4 | Extract is unrelated; do not integrate |
| Course & Technology List | Mismatched extract | Not safely testable from current file | Unknown; file body is unrelated course-availability text | Unknown | Unknown | Likely global | Yes in intent only | 5 | File says one thing, extract body shows another API |
| Get Course and Domain | Not usable yet | No usable API detail in extract | Unknown | Unknown | Unknown | Likely global | Yes in intent only | 5 | API detail not found / unavailable |
| Get Course Name | Not usable yet | No usable API detail in extract | Unknown | Unknown | Unknown | Likely global | Yes in intent only | 5 | API detail not found / unavailable |
| Get Inhouse and FL Trainers Of Courses | Mismatched extract | Not safely testable from current file | Unknown; file body is unrelated freelancer invoice text | Unknown | Unknown | Likely yes if course-scoped | Yes in intent only | 6 | Extract is unrelated; do not integrate |
| Course Without Exam | Mismatched extract | Not safely testable from current file | Unknown; file body is unrelated course-availability text | Unknown | Unknown | Likely global | Yes in intent only | 7 | Extract is unrelated; do not integrate |
| Exam Course Linked API | Not usable yet | No usable API detail in extract | Unknown | Unknown | Unknown | Likely global | Yes in intent only | 7 | API detail not found / unavailable |
| Get Unique Certifications Count Value | Mismatched extract | Not safely testable from current file | Unknown; file body is unrelated negative-feedback text | Unknown | Unknown | Likely yes | Yes in intent only | 7 | Extract is unrelated; do not integrate |

## Python pipeline mapping

Reportee-first remains the base:

1. Get Direct / Indirect Reportee
2. Get Trainer Details
3. Get Trainer Skills
4. Get Utilization
5. Get Trainer Vendor Certification Count
6. Get Negative Feedback Count
7. HR Incident Positive Negative
8. Course List
9. Assignment API

The new APIs are only candidates for later enrichment:

- Availability / capacity: Previous & Upcoming Assignments, Upcoming Assignments, Trainer availability, Get Trainer Free Schedule and Details, Trainer RC Schedule, Trainer_Last_3_Months_Utilization
- Course-first allocation: Get Inhouse and FL Trainers Of Courses, Get Course Schedule, Check Course Availability in RMS, Get Course and Domain, Get Course Name, Course & Technology List
- Certification intelligence: Course Without Exam, Exam Course Linked API, Get Unique Certifications Count Value
- Quality / risk: Get Trainer Feedback Details

## Recommended safe-first integration order

None of the new APIs are safe to integrate yet from the current extracts without endpoint/body confirmation.
