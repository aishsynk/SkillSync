# Koenig Manager OS — Enterprise Product Architecture

> Status note: This is an architecture/design document. For the current runtime surface after auth, service extraction, `/proxy` removal, and deprecation cleanup, use `MANAGER_OS_PRODUCTION_STATUS.md` and `DEPRECATED_PAGES.md` as the source of truth.

*Consulting document. API-first. No Excel / CSV / notebook / snapshot / local-file runtime dependency — ever. Where an API does not exist, the gap is named, never simulated.*

---

## 1. Executive Summary

SkillEdge has outgrown "manager dashboard." The right product is a **Manager Operating System (Manager OS)** whose job is to convert live RMS APIs into **decisions**: who delivers, who certifies, who grows, who is at risk, and where the business is capability-thin. The codebase already proves the hardest part — a reportee-scoped, cached, explainable server pipeline that fans ~20 APIs into one unified per-manager payload.

The strategic finding: **we expose ~40% of the intelligence the current APIs already permit.** `trainerDetails` (per-course vendor + Qubit + skill-level + future-skill) and `courseWithoutExam` (10,445-row course→vendor→exam-required master) are barely used. The single highest-value, zero-new-API move is a **Vendor Strength Index + Growth Intelligence** layer (Modules "Capability" and "Growth").

The honest boundary: **exam-level certification results (Pass/Pending/Cleared) have no working API.** `examCourseLinked` is a broken write endpoint. So Manager OS ships certification intelligence at **accreditation + requirement-gap** granularity now, and exam-result granularity only after RMS provides an API — flagged, never faked.

The original critical security debts have been closed in the current runtime: browser credentials were removed, `/proxy` was removed, protected routes require a session, and manager scoping is enforced server-side. Remaining enterprise readiness work is production credential rotation, RBAC beyond manager scope, scale hardening, and continued documentation cleanup.

---

## 2. Current Product Assessment

**Strengths (keep):** reportee-first scoping; server-side unified payload with explainability (`evidence`, `confidence`, `missing_signals`); per-manager 4h disk cache; per-trainer fault isolation; token pre-warming; a working availability engine and rule-based recommendations.

**Weaknesses:** some orchestration still lives in `build_unified`; several deeper enterprise concerns remain, including production credential rotation, RBAC for personas beyond the manager, scale posture, repo/documentation cleanup, and trend history. Legacy browser credential exposure and `/proxy` are no longer current runtime risks.

**Maturity verdict:** strong intelligence spine with the core security posture now hardened in the current runtime. Remaining gains are product architecture, scale posture, credential rotation, RBAC, and deeper enterprise packaging.

---

## 3. Business Understanding

Koenig sells **instructor-led training delivery**. The scarce resource is a **certified, available, high-quality trainer for a given course/OEM**. Managers win or lose on four recurring decisions:

- **Allocation** — put the right trainer on the right course now.
- **Capability building** — grow trainers into where demand is heading (AI, Fabric, cloud, security).
- **Certification** — keep OEM accreditations current so delivery is compliant and premium.
- **Risk & continuity** — spot decline, over/under-load, single-points-of-failure, and flight risk.

Cadence: **daily** (allocation, at-risk), **weekly** (upskilling, certification nominations, backup coverage), **monthly/quarterly** (OEM capability posture, growth plans, succession, workforce planning). Manager OS must serve all four cadences from one live payload.

---

## 4. User Personas

1. **Delivery Manager (primary).** Owns a reportee team. Daily allocation + readiness + risk; weekly growth/certification.
2. **Practice / OEM Lead.** Owns capability in an OEM (Microsoft, AWS, RedHat…). Wants OEM strength/gaps, backup depth, who to certify.
3. **Operations / Resourcing.** Fills course demand from the trainer pool; needs "can we build N trainers for X."
4. **Training / L&D Manager.** Runs upskilling & certification programs; needs nomination lists and skill-gap targets.
5. **Leadership / Executive.** Portfolio capability, risk concentration, workforce planning; needs rollups + trends.

Today only Persona 1 is served. Personas 2–5 need **team- and org-level rollups** that don't yet exist.

---

## 5. Manager Workflows (answerable now vs blocked)

| Manager question | Live-answerable? | Basis / blocker |
|---|---|---|
| Who should deliver this course? | ✅ | allocation engine (skills + readiness + availability) |
| Who is under/over-utilized? | ✅ | utilization trend |
| Who is at risk (quality/HR)? | ✅ | feedback + HR engines |
| Who is the strongest Microsoft/AWS trainer? | ✅ *(new)* | **Vendor Strength Index** (courses×Qubit×deliveries per OEM) |
| Who should move into AI / is ready for Fabric? | ✅ *(new)* | skills + future-skill flags + vendor adjacency |
| Which OEM is weak / has no backup trainer? | ✅ *(new)* | team OEM capability heatmap + coverage depth |
| Who should be certified next? | ◑ | accreditation gap on exam-required courses ✅; **exact exam ladder** ❌ (no exam API) |
| Who is becoming outdated / stagnant? | ◑ | future-skill ratio + recency proxy ✅; true trend ❌ (no time-series API) |
| If a trainer resigns, what capability gap appears? | ✅ *(new)* | remove trainer → recompute OEM/course coverage |
| If a client needs 20 Fabric trainers, can we build the team? | ✅ *(new)* | count ready + upskillable within the OEM |
| Who may leave (flight risk)? | ❌ | needs HR tenure/engagement signals — no API |
| Who has the best delivery quality? | ✅ | feedback + Qubit + HR |

The bolded "new" rows are the untapped value — all derivable from **existing** APIs.

---

## 6. Complete API Catalog (live, verified)

| API (id) | Input | Output (key fields) | Reliability |
|---|---|---|---|
| reportees (82) | manager email | TrainerName, TrainerId, EmpId, OffEmail, TrainerPlus, IsdirectReportee, Designation | High |
| trainerSkills (217) | employee_id | course_id, course_name, is_duplicate, is_discontinue | High |
| trainerDetails (75) | email | CourseName, **VendorName, QubitsScore, SkillLevel, Is Future Skill, techcallrating**, Course Assignment | High |
| vendorCerts (57) | email | Certificate Count + per-**OEM-accreditation-body** booleans (MCT, RHCI, (ISC)2…) | High |
| resumeDetails (87) | email | Certifications (free-text), Skill, Summary, Experience, Languages, TrainingsDeliveredFor | Med (unstructured) |
| assignments (15) | email, page | AssignmentID, Course, TrainerName, OffEmailId | High |
| utilization (55) | email | monthly "load / util" columns → current, 3-mo, trend | High |
| negativeFeedback (58) | email | Total | High |
| hrIncidents (59) | email | Positive Count, Negative Count | High |
| courseList (164) | — | Course, Courseid, vendor_name, course_url | High |
| courseWithoutExam (213) | — | Courseid, CName, **Exam Required or Not, Vendor, CourseStatus** (10,445 rows) | High, **under-used** |
| trainerAvail (90) | cname,dates | schedule rows | Low (often empty) |
| freeSchedule (171) | course | rows | Low (often empty) |
| prevUpcoming (16) | dates,email | assignment rows | Low (often empty) |
| trainerRCSchedule (111) | email,dates | rows | Low (often empty) |
| trainerLast3 (39) | EmpCode | monthly util | Low (fetched, unused) |
| trainerFeedback (244) | email,assignmentId | feedback detail | Low (often empty) |
| uniqueCertCount (72) | email | count | Broken-ish (returns 0) |
| examCourseLinked (215) | — | — | **Dead** (write/log endpoint, errors on read) |

---

## 7. API Capability Matrix (usage vs potential vs verdict)

| API | Used now | Untapped potential | Verdict |
|---|---|---|---|
| reportees | scope | org-shape analytics | keep |
| trainerSkills | skills_count | capability spine, coverage depth | **exploit more** |
| trainerDetails | Qubit avg | **per-OEM strength, future-skill orientation, skill-level mix** | **exploit — biggest lever** |
| vendorCerts | certified_vendors | accreditation gap vs exam-required courses | exploit |
| resumeDetails | resume fields | cross-OEM skill hints (keyword) | exploit (careful) |
| assignments | count | delivery proof per OEM, backup depth | exploit |
| utilization | current/trend | capacity planning rollups | exploit |
| feedback/HR | risk | quality trend | keep |
| courseList | vendor map | course universe for growth targets | exploit |
| courseWithoutExam | one status string | **certification-requirement master (10k rows)** | **exploit — under-used** |
| 6 schedule/feedback APIs | mostly empty | calendar truth *if* they populate | monitor; don't depend |
| uniqueCertCount | — | — | verify params or deprecate |
| examCourseLinked | dead call | — | **stop calling; request read API** |

---

## 8. Existing Intelligence (already built)

Readiness scoring (weighted, explainable) · Growth/Risk-taker classification · Delivery Availability engine (calendar/capacity/workload/final status/batch-fit) · Course Allocation scoring · Manager Action recommendations · Timeline events · Data Health / Trust normalization. All rule-based, all carry evidence + confidence + missing-signals.

## 9. Intelligence Added Since Original Architecture Draft

- **Vendor / OEM Strength Index** per trainer is now represented through capability/growth datasets.
- **Certification posture** is now represented through certification intelligence, gaps, and summary datasets.
- **Growth vectors** are now represented through growth intelligence and risk-taker surfaces.
- **Team OEM capability heatmap** and **coverage depth / single-point-of-failure** detection are now represented through organization and executive datasets.
- **Delivery, Allocation, Organization/Succession, and Executive Intelligence** are now additive unified-payload datasets.
- Remaining future opportunity: demand-weighted capacity simulation and true trend history.

---

## 10. Product Vision

**Manager OS**: a decision engine, not a viewer. One live payload → layered engines → explained recommendations → thin module pages → (future) an AI copilot that answers workforce questions in natural language. Every number is traceable to an API and a rule; every recommendation carries why + confidence + what's missing.

---

## 11. Product Modules (scope + feasibility)

Legend: ✅ now · ◑ partial · ⛔ blocked (needs API) · 🔮 future AI.

| Module | Scope | Feasibility |
|---|---|---|
| Trainer Intelligence | 360 profile: skills, accreditations, experience, feedback, utilization, future skills | ✅ |
| Capability Intelligence | skills → OEM strength → certifiable courses → readiness | ✅ (adjacency ◑ — needs taxonomy) |
| Certification Intelligence | accreditation map + requirement gap | ✅ ; exam Pass/Pending ⛔ |
| **Growth Intelligence** | next OEM / next courses / nomination | ✅ at OEM+course; graded exam ladder ⛔ |
| Delivery Intelligence | readiness, allocation, utilization, actions | ✅ |
| Business Intelligence | OEM heatmap, gaps, stagnation, nominations | ✅ ; demand-weighting ⛔ |
| Manager Action Center | prioritized decisions | ✅ |
| Trust & Data Health | signal completeness, redaction | ✅ |
| Organization Insights | org shape, span, direct/indirect | ✅ |
| OEM Intelligence | per-OEM strength/depth/risk | ✅ |
| Future-Skills Intelligence | future-skill flags → readiness | ✅ (flag-based) |
| Learning Recommendations | upskill/certify targets | ✅ at course level |
| Succession Planning | resignation-impact, backup depth | ✅ |
| Capacity Planning | build-a-team simulation, utilization headroom | ✅ |
| Risk Intelligence | quality/HR/overload/coverage risk | ✅ |
| Executive Dashboard | portfolio rollups + trends | ◑ (trends need history) |
| Predictive Intelligence | decline/success/demand prediction | 🔮 |
| AI Copilot | NL Q&A over payload | 🔮 |
| Knowledge Graph | entity relationships | ◑ (implicit joins now; true graph 🔮) |
| Recommendation / Explainability / Confidence engines | cross-cutting | ✅ (exist, to be centralized) |
| Notification / Audit engines | alerts, change log | ◑ (build) |
| Governance / Security / Admin / Settings | auth, config, future RBAC | Partial: auth/session implemented; RBAC beyond manager scope remains future |

## 12. User Journey

Login (email → reportee validation) → **Command Home** (today's actions, at-risk, OEM posture) → drill to **Trainer 360** or **Capability & Growth** → act (allocate / nominate / coach) via **Action Center** → verify trust in **Data Health**. Persona 2–5 enter at **OEM Intelligence** / **Business Intelligence** rollups.

## 13. Page Architecture (module-aligned, consolidated)

Collapse ~20 pages to a coherent set: **Command Home** (BI+Delivery), **Trainer 360** (Trainer Intel), **Capability & Growth**, **Certification Intelligence** (`certifications.html`), **Allocation Desk**, **My Team** (+OEM heatmap), **Growth/Risk-Takers**, **Action Center**, **Data Health**, and **Settings/Admin**. Current active/deprecated page truth is maintained in `DEPRECATED_PAGES.md`; deprecated legacy URLs remain compatibility redirects only.

## 14. Backend Architecture (target)

Layered, one data path:
`api.client (token+cache) → normalizers → entity model → derivation engines (real modules, not stubs) → recommendation+explainability+confidence → aggregation (unified payload) → HTTP`. One server-side credential store; **no browser credentials**; `/proxy` removed or authenticated. Per-manager cache retained.

## 15. Intelligence Architecture

L0 Ingestion · L1 Normalization · L2 Entity model (Trainer, Course, OEM, Skill, Certification[accreditation], Assignment, Feedback, Utilization) · L3 Engines (VendorStrength, Readiness, Availability, Certification, Growth, Risk, Capacity, Succession, OEM) · L4 Recommendation + Explainability + Confidence · L5 Aggregation · L6 thin pages · L7 AI (future). The empty `intelligence_engines/*` become the real homes for today's inline logic.

## 16. AI Opportunities (future, on top of the rule engine)

Skill/course **embeddings** for real adjacency (replaces missing taxonomy) · **certification-path model** (after exam+demand APIs) · **demand forecast** · **feedback NLP** · **flight-risk model** (needs HR signals) · **Manager Copilot** NL Q&A over the payload. All augment, never override, the auditable rules.

## 17. Security Review

Current runtime status: browser credentials have been removed, `/proxy` has been removed, unified intelligence and `/rms/<api>` are session-protected, manager scoping is enforced server-side, and static path traversal is blocked. Remaining security work is production credential rotation, removing local source fallback secrets after environment variables are deployed, and adding RBAC for Persona 2-5.

## 18. Scalability Review

`http.server` threading + synchronous per-trainer fan-out is fine for tens of reportees, not hundreds of managers concurrently. Cache is per-manager disk JSON (no eviction policy). For scale: async/queued builds, shared course-master cache (one global fetch, not per-build), background refresh, and a real cache store. Not urgent for pilot; plan for it.

## 19. Technical Debt

Duplicated & shadowed health logic (intelligence.py vs shared) · empty `intelligence_engines/*` + `knowledge/*` stubs · two orphan services · two data paths · dead `examCourseLinked` + unused `trainerLast3`/`uniqueCertCount` fetches · `server._upgrade_payload` = second availability authority · dual payload naming (`*_df` + aliases) · repo cruft (rar, png, 10+ docs).

## 20. Missing APIs (the RMS ask list)

1. **Trainer exam-result API** (exam, Result, ApprovalStatus, Cleared/Pending) — unblocks exam-level Certification + verified Growth. (examCourseLinked is broken.)
2. **Course → canonical exam-code** map.
3. **Course → technology/domain taxonomy** — unblocks real skill adjacency & cross-OEM growth.
4. **Course popularity / market-demand API** — unblocks demand-weighted growth & BI.
5. **Reliable availability/schedule read APIs** — unblocks calendar-true delivery availability.
6. **HR tenure/engagement signals** — unblocks flight-risk.
7. `uniqueCertCount` contract clarification.

## 21. Business Opportunities

OEM capability posture as a **sales enabler** ("can we staff this deal") · certification-compliance as a **premium-pricing lever** · growth plans as **retention** · succession/coverage as **risk reduction** · executive workforce planning as a **leadership product**. Manager OS becomes cross-persona, not single-manager.

## 22. Product Roadmap (business-value phases, additive & reversible)

- **A — Capability & Growth Intelligence** (Vendor Strength Index + growth recommendations). Zero new API. Highest value. Additive payload.
- **B — Certification Intelligence** (accreditation map + requirement gap; exam-status flagged "unavailable via API").
- **C — Manager OS consolidation** (elevate Capability&Growth; repurpose Certifications; OEM heatmap on Team/Home; deprecate legacy pages).
- **D — Security hardening** (server-only secrets, auth/session, close `/proxy`, remove browser fan-out).
- **E — Org/Exec + RBAC** (Persona 2–5 rollups, succession, capacity simulation).
- **F — AI layer** (embeddings, copilot) once RMS APIs (§20) land.

## 23. Refactoring Roadmap (only where it enables the above)

Dedup health (delete shadowed copies) → remove orphan stubs & dead API calls → unify to one data path (delete `js/api.js` client fan-out) → extract inline engines into `intelligence_engines/*` → fold `_upgrade_payload` into the availability engine → repo hygiene. Each independently verifiable via `smoke_test.py`.

## 24. Implementation Roadmap (planning only; per task)

Each task carries: Objective · Business value · Files affected · Risk · Dependencies · Expected outcome · Verification (`smoke_test.py` + payload contract diff) · Rollback (additive keys / feature-flag / revert). **First task = Phase A backend derivation** (Vendor Strength + Growth), additive only, no page change, no new API.

## 25. Risks

Browser-credential exposure (security) · exam-data expectation gap (manage stakeholder expectation: accreditation ≠ exam pass) · schedule-API unreliability (don't build hard dependencies) · scope creep across 28 modules (sequence strictly) · payload-contract breakage (additive-only discipline) · doc sprawl (this becomes the single source of truth).

## 26. Final Recommendation

Adopt **Manager OS** as the product. Build **Capability & Growth Intelligence first (Phase A)** — it is the highest-value, zero-new-API, additive move and directly answers "who is strong in which OEM and what should they pursue next." Run **security hardening (Phase D)** in parallel as a non-negotiable enterprise gate. Treat exam-level certification, technology taxonomy, and demand as **explicit RMS API asks (§20)** — never simulate them. Refactor only in service of these phases. The platform is mature enough that the next gains come from **product architecture and API expansion, not another service extraction.**
