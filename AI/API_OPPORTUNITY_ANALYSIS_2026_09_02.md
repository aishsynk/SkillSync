# RMS API Opportunity Analysis

**Date:** 2026-09-02 · **Source:** `trainer_portal_api_details/` (37 instruction files) cross-referenced
against `backend.py` `_APIS`.
**Caveat:** the instruction files have been wrong on schema before (dates, envelope shapes). Every
item below is a **candidate to probe live** before building — especially the six never-called endpoints.

## Coverage

Every one of the 37 RMS endpoints is already registered in `_APIS`. The gap is **not missing
endpoints** — it is **unused fields and uncombined signals**.

| State | Endpoints |
|---|---|
| **Defined, never called** | `courseAvailability` (104) · `courseContentUrl` (156) · `courseModule` (206) · `examCourseLinked` (215) · `latestCourseVersion` (172) · `uniqueCertsCount` (72) |
| **Called once, thin** | `courseDomain` · `courseSchedule` · `courseSyllabus` · `courseTechnology` · `courseWithoutExam` · `trainerFreeSchedule` · `trainerResume` |
| **Rich fields dropped** | `trainerDetails` (75) is read for capability only; it also carries `techcallrating`, `Future Skill Date`, `DM`, and five shift-band off-date lists (`RoamingOffDates`, `InternationaRoamingOffDates`, `NightILOffDates`, `MorningILOffDates`, `EveningILOffDates`) — all unused |

---

## What a MANAGER could get (new)

Ranked by value.

1. **Shift-band availability** — `trainerDetails` off-date lists. Allocation today knows leave, not
   *shift fit*. "Who can take a US-evening ILO next week" needs `EveningILOffDates` /
   `NightILOffDates`. High impact for international/FMAT coverage.
2. **A second quality axis** — `trainerDetails.techcallrating`, distinct from Qubits. "Knows the
   material" vs "rates well live". Surface both on the PersonCard and Trainer 360.
3. **Courseware-version currency** — `latestCourseVersion` (172) vs the trainer's skill mapping.
   "3 of your team teach AZ-104 on an outdated version" is a real allocatability risk today invisible.
4. **The global bench** — `globalTrainers` (157) returns inhouse *and* freelance trainers of a
   course. When the team can't cover a batch, Allocate should show who in the wider org / FL pool can.
5. **Course market schedule** — `courseSchedule` (246) by country / region / delivery mode is a
   forward public-demand signal that leads signed SCs. A "where the market is heading" board.
6. **Class health** — `assignmentPax` (209) is used only for pax-drop alerts. It also carries every
   `StudentName` / `StudentEmail`: roster size vs expected, and a post-course follow-up list.
7. **Recording library** — `recordingDetails` (278) beyond "missing" alerts: a browsable archive of a
   trainer's delivered sessions for QA, calibration and onboarding new trainers.
8. **Verbatim feedback mining** — `trainerFeedback` (244) carries full `Question` / `TextAnswer`
   pairs. Theme clustering exists; the raw text can drive per-trainer coaching prompts.
9. **Precise cert-gap** — `examCourseLinked` (215): "this exact exam unlocks these 4 courses your
   team is short on", instead of a generic gap count.
10. **CSM staffing patterns** — `activeSCDate` (13) carries `CSM`. "Which CSM's deals you are
    consistently unstaffed for" is a planning conversation the data already supports.
11. **Catalogue hygiene** — `courseAvailability` (104) + `courseWithoutExam` (213): flag team skills
    mapped to courses RMS has marked duplicate or discontinued.

---

## What a REPORTEE (trainer, self-view) could get

The trainer now runs the manager app scoped to a team of one, so anything a manager sees *about* a
trainer, the trainer already sees about themselves. On top of that:

1. **My verbatim feedback** — `trainerFeedback` (244) + `trainerNegFeedback` (218). Every learner
   comment on my sessions, with the question, newest first. The single highest-value thing a trainer
   cannot easily get today.
2. **My recordings** — `recordingDetails` (278) download links to my own delivered sessions, for
   self-review before the next run.
3. **My shift-band profile** — `trainerDetails` off-dates. "You are marked off for evening-IL, so
   batches in that band skip you" — lets a trainer fix stale availability that is costing them work.
4. **My courseware currency** — `latestCourseVersion` (172). "AZ-104 moved to v3.2; you are mapped to
   v3.0 — refresh to stay allocatable."
5. **My exam roadmap** — `examCourseLinked` (215) + `trainerSkills` + held certs: "these 2 exams
   unlock 6 more courses and ~X Trainer-Index points." Concrete, ordered progression.
6. **My class roster** — `assignmentPax` (209) for my upcoming batch: who is in it, how many, so I can prep.
7. **My market demand** — `courseSchedule` (246) filtered to my skills: publicly scheduled courses I
   could teach, so I can raise my hand before they hit the unallocated board.
8. **My schedule** — `trainerRCSchedule` (111): a clean personal from/to calendar view.
9. **My technical-call rating** — `trainerDetails.techcallrating`, the other half of my quality
   picture beside Qubits.
10. **My global standing** — `globalTrainers` (157): am I the inhouse-preferred trainer for my
    courses, or competing with freelancers.

---

## Recommended build order

| Tier | Item | Endpoints | Serves |
|---|---|---|---|
| **Quick** | My / any trainer's verbatim feedback feed | 244, 218 | trainer + manager |
| **Quick** | My / any trainer's recordings library | 278 | trainer + manager |
| **Quick** | Exam roadmap ("what unlocks what") | 215, 217, 72 | trainer + manager |
| **Medium** | Shift-band availability in Allocate + trainer self-view | 75 (dropped fields) | manager + trainer |
| **Medium** | Courseware-version currency flag | 172, 217 | manager + trainer |
| **Medium** | Class roster / class-health panel | 209 | manager + trainer |
| **Larger** | Global bench in Allocate | 157 | manager |
| **Larger** | Course market schedule as a leading-demand board | 246 | manager + trainer |
| **Larger** | `techcallrating` as a second quality axis app-wide | 75 | manager + trainer |

Each tier-1 item is one endpoint, one screen section, self-scoped for a trainer and team-scoped for a
manager via the existing `_reportees()` / `_v2_manager_session` split — so they ship cheaply.
