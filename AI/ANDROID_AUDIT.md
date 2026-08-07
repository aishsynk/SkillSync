# SkillSync Android — Full Product Audit
**Date:** 2026-08-08 · **Scope:** Android app only (`SkillEdge_Android/`) · **Current version:** v1.23.0
**Method:** Direct source reading of every screen, ViewModel, and the Android-facing backend (`backend.py`). No assumptions from backend field existence — each claim below is traced to a specific Kotlin file and line, or explicitly flagged as unverified where it couldn't be confirmed on-device (no Android SDK/emulator in this environment).

---

## 1. Dashboard Audit

**Current State**
`DashboardTab` (`ui/main/MainScreen.kt`) renders, top to bottom: `ProfileHeader` (name/photo/role) → KPI grid (12 tiles: team size, active/unallocated trainers, active/upcoming batches, training days, avg utilisation, certified, cert gaps, high risk, readiness, skill coverage) → Team Pulse (Delivery Readiness, Feedback Risk, Capacity, Capacity Forecast) → Team Health (5 analytics charts: capacity distribution, deployment, cert coverage, readiness-by-trainer, utilisation trend) → Top Performing (top 5 by utilisation) → Needs Attention (top 5 ranked by risk, since v1.21.0) → "View full team" button.

**30-second test (a Delivery Manager opening this at 8 AM):** Identity ✓ (hero card), team size ✓ (KPI), what's urgent — **partial**. The "Needs Attention" list exists but sits *below* five analytics charts and a "Top Performing" card — a manager scanning top-down hits descriptive charts before the one list that tells them who to act on today. The single highest-value list on the whole screen is buried roughly two-thirds down.

**Gaps**
- No date/time context on the header — a manager can't tell if the data is from this morning or three days ago without opening a batch (the offline/sync banner only appears when relevant, not as a persistent freshness indicator).
- No quick links to Allocation Desk or Actions from the dashboard itself — a manager who spots "3 unallocated trainers" in the KPI has to switch tabs manually; the KPI drill sheet shows names but has no action button.
- Logged-in user's own role/designation is shown, but there's no "last synced" timestamp visible without triggering the offline banner logic.

**UX Issues**
- Ordering: "Needs Attention" (actionable) is positioned after "Team Health" (descriptive/exploratory). A command-center should lead with what needs a decision, not with charts.
- Five stacked analytics cards (`TeamAnalytics`) before reaching the roster preview is a lot of scroll for a "glance" screen — Certification Coverage and Readiness-by-trainer, while accurate, are secondary information for a *daily* open, not a first-thing-in-the-morning one.
- The KPI grid's 12 tiles have no visual grouping — "Team members," "High risk," and "Skill coverage" all read with equal visual weight, so nothing is emphasized as more urgent than anything else.

**Functional Issues**
- None outstanding as of v1.23.0 — the utilization-averaging bug (phantom zeros) was fixed this session, and Team Pulse/Forecast card labeling was clarified in the same release.

**Data Utilization Issues**
- `manager_kpis.deployable_pct` is computed by the backend but **not surfaced anywhere in the KPI grid** — a ready-made "share of team you can currently vouch for" metric goes unused.
- `unknown_status` (count of trainers whose current status RMS couldn't determine) is backend-computed but never shown — a manager has no way to know "N trainers' status is simply unknown" as distinct from "N trainers are free."

**Proposed Redesign**
1. Reorder to: Header → **Needs Attention** (promoted directly under the KPI grid) → Team Pulse → Team Health charts (collapsed/summarized by default, expand for full analytics) → Top Performing.
2. Add a persistent "Synced Xm ago" caption in the header, always visible (not only during an offline fallback).
3. Group the KPI grid into two visual bands: "Team & Delivery" (size, active, batches) and "Risk & Readiness" (unallocated, high risk, readiness, cert gaps) so a manager's eye lands on risk figures as a set, not scattered among neutral counts.
4. Surface `deployable_pct` as a 13th KPI tile ("Vouched-for %") since the backend already computes it specifically to answer "how much of my team can I actually rely on right now."

---

## 2. Team Screen Audit

**Current State**
`TeamTab.kt` — a single roster with real search (name/designation/course), sort (Utilisation/Readiness/Name/Status/Cert gaps), and a filter sheet (Status, Utilisation band, Readiness band, Skill, Certification, Gaps-only). Each row is a full `TrainerCard`: status badge, delivery-readiness badge, capacity badge, feedback-risk badge, cert count, utilisation bar, current/next batch banner.

**Does it answer the four questions without multiple clicks?**
- **Who is available?** Yes — Status filter chip "Available" + Utilisation sort, one tap.
- **Who is risky?** Partially — feedback-risk badge is visible per-card, but there's **no risk filter or risk sort** in `TeamFilters`/`TeamSort` at all. A manager must scroll the entire roster reading badges to find at-risk trainers; there's no "show me only High risk" toggle.
- **Who is ready?** Yes — Readiness band filter + Readiness sort exist.
- **Who needs development?** Partially — "Cert gaps" sort exists, but there's no dedicated filter for "has gaps" the way there's a "Gaps only" toggle already built for exactly this (it exists — `gapsOnly` — so this one is actually covered).

**Gaps**
- **No risk-based filter or sort** — the single biggest gap on this screen. Feedback risk is a first-class signal everywhere else in the app (Dashboard's Team Pulse, `TrainerCard` badges) but the Team screen — the one screen whose whole job is roster triage — can't filter or sort by it.
- Utilization shown per-card has no time-window caption (same clarity issue fixed on the Dashboard this session — `TrainerCard`'s bar just says "X%" with no "3-mo avg" context).

**UX Issues**
- The filter sheet (`TeamFilterSheet`) mixes fast, always-available filters (Status, Utilisation) with capability-gated ones (Readiness, Skill, Certification — disabled with an explanation until `team-capability` loads) in one flat list; a first-time user opening filters before capability loads sees several disabled-looking options with no visual separation from the working ones.

**Functional Issues**
- None found. Filtering/sorting logic (`shown` computation in `TeamTab.kt`) was read in full and is internally consistent.

**Data Utilization Issues**
- `trainer_operations_df.recommended_action` (backend computes a specific next-step string per trainer: "Urgent: Review feedback incidents," "Check availability," etc.) is **never displayed on the Team screen** — it's shown nowhere in `TrainerCard` either. This is a ready-made "what should I do about this person" hint the backend already writes and the UI never surfaces.

**Proposed Redesign**
1. Add "Risk" to both `TeamSort` and `TeamFilters` (band: High/Medium/Low), mirroring the existing Utilisation/Readiness pattern exactly — this is a small, contained change since the filter infrastructure already exists.
2. Add a one-line `recommended_action` caption to `TrainerCard` when present, so triage doesn't require opening Trainer 360.
3. Visually separate "always available" filters from "needs capability" filters in the filter sheet with a divider + label, rather than one flat list.

---

## 3. Trainer 360 Audit

**Current State** — current section order (`Trainer360Content` in `ui/trainer/Trainer360Screen.kt`):
Identity → Personal Details → Utilisation (+ trend + forecast line) → Capability Metrics → Delivery Readiness (gauge, strengths, constraints, recommendations) → Risk (gauge, HR flags) → Certifications (held/missing/recommended/accreditations) → Capability (course list) → Delivery (assignment history + participant roster for current/next) → Feedback (negative incidents + per-question responses) → SPOF & Strategic Impact (critical-course ownership, succession-planning prompts) → Availability (off-dates).

This is already the richest screen in the app — 11 sections, genuinely close to what was asked for (Profile/Skills/Certifications/Delivery Readiness/Utilization/Risk/Growth/Roadmap/Recommended Actions), and covers Profile, Skills, Certifications, Delivery Readiness, Utilization, Risk, and Recommended Actions already.

**What's Missing**
- **Growth / career trajectory** — genuinely absent. There's a "Recommended certifications" list (adjacent tracks based on what they already teach) inside the Certifications section, but nothing framed as "where is this person headed" — no trend-over-time on readiness score, no comparison to peers beyond the one-time `team_rank` figure shown in Capability Metrics.
- **Future Skill Roadmap** — the backend field for this (`future_skill_roadmap_df`) is **always an empty list** (`backend.py` line 1634 literally returns `[]`, unconditionally) — so there is no backend data to build this section from yet. This is not an Android gap; it's an unbuilt backend feature. Flagging per the audit's own instruction not to assume something exists because a field is named — this field exists in the payload shape but carries no data.
- **Peer comparison** is thin: `team_rank`/`team_size` (a single number, "3rd of 12") is the only comparative signal; there's no "how does this trainer's readiness/risk compare to the team average" visual.
- **Mock ratings / tech-call attribution** — per the AutoTall rule audit this session (`AI/CONTEXT.md`), no RMS API exposes either; Trainer 360 correctly has nothing here because nothing exists to show.

**UX Issues**
- 11 sections in one long vertical scroll with no in-page navigation (no section jump/tab strip) — reaching "SPOF & Strategic Impact" or "Availability" requires scrolling past everything else every time.
- Risk section and Delivery Readiness section are adjacent but visually similar (both use a gauge + colored label pattern) — could be mistaken for duplicated information on a quick glance.

**Functional Issues**
- `feedback.responses` (per-question feedback detail added this session) is parsed defensively but its real RMS field names are still unverified — flagged already in `AI/PROGRESS.md`, repeating here since it's directly relevant to this screen's accuracy.

**Data Utilization Issues**
- None found beyond the above — this screen is the best-utilized surface in the app relative to what the backend provides.

**Proposed Recommendation**
1. Add a lightweight sticky section-jump strip (Profile / Skills / Delivery / Risk / History) at the top, since this is by far the longest screen in the app.
2. When `future_skill_roadmap_df` gets real backend data (out of scope to build now, per the Android-only directive — noting as a dependency), add a "Growth & Roadmap" section between Certifications and Feedback.
3. Add a small peer-average marker on the Readiness and Risk gauges (e.g., a tick mark at the team's average score) so the single number gets context without a separate screen.

---

## 4. Courses Screen Audit

**Current State**
`CoursesTab.kt` — a searchable, sortable (Coverage/Qubits/Delivered/Name), filterable (Single owner/Nobody certified/Future skill/Vendor) catalogue. Each `CourseCard` shows: exam code + vendor + future-skill/single-owner tags, a Qubits badge, a certification-mapping row (course→exam, "$certified/$owners certified"), owner avatars, and an expandable per-owner breakdown (skill level, delivered count, approved flag, certified/not-certified tag, Qubits).

**Does it look modern?** Yes — card-based, expandable rows, consistent with the rest of the app's visual language (tinted tags, colored left-rail risk indicator for single-owner courses). No dated UI patterns found.

**Is certification mapping visible?** Yes, and well done — the course→exam→certified-count row is exactly the right level of detail, and it's honest about courses with no exam mapping at all (the row simply doesn't render rather than showing a false "0 certified").

**Is trainer suitability visible?** Partially. Per-owner Qubits and skill level are shown, but there's no way to sort or filter the *owner list within a course* by suitability — for a single-owner course you see one name; for a 6-owner course you see 6 names in whatever order the backend returned them, with no "best fit first" ordering inside the expanded card.

**Does it support allocation decisions?** Indirectly — tapping an owner navigates to Trainer 360, but there's no direct "suggest this trainer for a batch" action from here, and no link *from* a course *to* matching unallocated demand (the connection between "who can teach this" and "what needs teaching" lives only in Allocation Desk, in the opposite direction).

**Gaps**
- No cross-link from a course to unallocated batches needing that exact course (Allocation Desk already computes this match server-side per batch; Courses has no equivalent "batches needing this course" view).
- Owner list inside an expanded course card isn't sorted by any suitability signal (Qubits, certified-first, etc.) — currently just backend order.

**Proposed Redesign**
1. Sort owners-within-a-course by certified-first, then Qubits descending, so the expanded view already answers "who's the best pick for this course" without cross-referencing Allocation Desk.
2. Add a small "N open batches need this course" indicator when `course_name` matches something in `unallocated_demand_df` (would need the course code passed alongside capability data — a moderate integration, not just UI).

---

## 5. Allocation Desk Audit — verified against the specific Phase 3 checklist

Checked directly in `AllocationDeskScreen.kt` and `BatchDetailScreen.kt`, not assumed:

| Requirement | Status | Where |
|---|---|---|
| Best Match / Alternate Match / Risky Assignment | ✅ Shown | `category` field, colored by `relevanceColor`, on both the list card and detail screen |
| Match Percentage | ✅ Shown | `${match}%` on both list and detail |
| Primary Trainer / Secondary Trainer / Emergency Backup | ✅ Shown | `backup_role`, on `BatchDetailScreen` per-candidate row (not on the list card, only in detail) |
| Priority Indicators | ✅ Shown | `customer_priority` as a tinted tag on the list card |
| Revenue Indicators | ✅ Shown | `revenue_impact` as a tinted tag on the list card |
| Skill Match Explanations | ⚠️ Partial | `via_course` (which of the trainer's courses matched) is shown on the detail screen; the list card shows only "Missing: X" or "Upskilling Required," not the specific matched course |
| Negative-feedback block / clean-record signal (added this session, v1.22.0) | ✅ Shown | Blocked candidates get a distinct red "🚫 Not auto-allocated until [date]" row |

**Verified working, not assumed.** The one real gap: `backup_role` (Primary/Secondary/Emergency Backup) only appears after tapping into a batch's detail screen — the list view's compact candidate preview (`BatchCard`, top 3 candidates) shows category + match% but not the backup-role label, so a manager scanning the list can't tell at a glance which candidate is the actual Primary pick vs. an Alternate without opening detail.

**Gaps**
- `customer_priority` and `revenue_impact` are both driven by naive backend heuristics (`vendor in [microsoft/aws/cisco/google] → High priority`; `participants > 10 → High revenue`) — Android displays them correctly, but the underlying values are coarse. Not an Android issue; noting because the audit asked to verify, not assume, correctness end-to-end.

**Proposed Redesign**
1. Add the `backup_role` label to the compact list-card candidate rows (small text under the trainer name), not just the detail screen — one line, already-available data.

---

## 6. Skill Management Audit — full workflow traced, not assumed

**Flow, verified end-to-end:**
1. `BatchDetailScreen` → "My skill" or "Reportee" action opens `MarkSkillDialog` (date picker + 1-10 level slider, reportee picker if applicable).
2. Confirm → `AllocationViewModel.markSkill()` → `POST /api/action/mark-skill` via Retrofit, returning a raw `Response<MarkSkillResponse>` (not a plain suspend call) specifically so a 409-rejected write's body can be read (`AllocationViewModel.kt:94-143`).
3. Backend (`backend.py::mark_skill` route) writes to RMS (`addTrainerSkill`, key 255), then **re-reads the trainer's skill register to verify the write actually landed** before reporting success — this is a real, deliberate verify-after-write pattern, not a fire-and-forget POST.
4. Response maps to one of five `MarkState`s: `Working` → `Done` (verified + changed), `Done` (verified, no-op re-assert of an already-held skill), `Unconfirmed` (RMS accepted but re-read failed), `Failed` (RMS rejected, with the actual RMS-provided reason).
5. `BatchDetailScreen` shows a `Snackbar` keyed to the exact state: short snackbar for confirmed success, long snackbar for unconfirmed or failed, and calls `onClearMark()` after each so the dialog can reopen cleanly.
6. On confirmed success (`changed == true`), `onSaved()` fires: `mainViewModel.refreshCapability(email)` (re-fetches `team-capability`, since a skill write changes course ownership and cert coverage) **and** `allocationViewModel.refresh(email, context)` (re-fetches the Allocation Desk, since ownership changes candidate matching for every batch).

**Verdict: this workflow is solid.** Save → API → RMS write → verify-by-read-back → typed UI feedback → targeted cache refresh (not a blanket full-app reload) → dependent screens invalidated correctly. No bug found here — I looked for one specifically per the audit's instruction and didn't find one.

**One real gap:** the confirmation UI is a `Snackbar`, which auto-dismisses and isn't visible if the manager has already navigated away (e.g., backed out of `BatchDetailScreen` immediately after tapping Confirm, before the async write resolves) — a slow RMS write's outcome could be missed entirely if the screen closes first. No persistent record of "you recorded a skill for X" exists anywhere in the app after the snackbar disappears (not in Trainer 360, not in a notification).

**Proposed fix**
Fire a local notification (the same `LocalNotificationService` infrastructure built this session for allocation/feedback events) on `MarkState.Done`/`Failed` if the write resolves after the screen has already been backed out of, so a slow or delayed write's outcome is never silently lost.

---

## 7. Session & Authentication Audit

**Login flow:** Email-only (`LoginScreen.kt` — no password field exists in this app at all; auth is domain + RMS-role verification server-side). Clear, minimal, fast.

**Session persistence:** `SessionManager` (SharedPreferences) stores email + `session_id` indefinitely. `Navigation.kt` checks `SessionManager.isLoggedIn()` on cold start and routes straight to `Main` if present — **so yes, login once and just use the app** is already true; there's no re-login-on-every-launch friction.

**Auto-login:** Effectively yes, via the persisted session check above.

**Logout:** Explicit only — `SessionManager.clearSession()` is called from exactly two places (`MainScreen.kt:137` and `:423`, both user-initiated logout taps). Confirmed by direct grep — no other code path clears it.

**Session recovery / expiry:** **This is the one real gap.** There is no interceptor, no 401 handler, and no session-expiry check anywhere in the app. Cross-checked against the backend: `backend.py` writes `_sessions[sid] = {...}` on login but **never reads that dict again anywhere** — every data endpoint trusts the `email` query parameter directly, with no session-token validation. Practically, this means the Android app's session literally cannot expire server-side, so there is nothing for the app to recover *from* today — but it also means if server-side session enforcement is ever added later, the Android app has zero handling for a resulting 401 and would need one built before that backend change could ship safely.

**Offline startup:** Covered by this session's own work (`LocalCache`, v1.17.0) — a cold start with no network now serves the last successfully cached dashboard instead of failing to an error screen, with an honest "Offline — showing data from X ago" banner.

**Verdict:** Login-once-and-use works today. The only genuine gap is a hypothetical one (no 401/expiry handling), not a currently-experienced problem, but worth flagging since it's a real gap.

---

## 8. Notification & Refresh Audit

Built substantially this session; verified state as of v1.19.0:

- **Foreground polling:** every 60s while a screen is open (`MainScreenViewModel.startPolling`), bound to `ON_RESUME`/`ON_PAUSE` lifecycle events in `Navigation.kt`.
- **Background polling:** every 15 minutes via `SkillSyncNotificationWorker` (WorkManager), which runs even with the app closed.
- **Shared dedupe:** both paths use one `NotificationStateStore` (SharedPreferences seen-set) so an event fires exactly once regardless of which path notices it first.
- **Refresh on navigation:** `RefreshOnResume` re-fetches on `ON_RESUME` for both Dashboard and Trainer 360.
- **Refresh after save:** confirmed above (Section 6) — targeted, not blanket.
- **Notification visibility:** real Android system notifications (`NotificationCompat`, `POST_NOTIFICATIONS` permission requested at runtime as of this session — previously would have silently never shown on Android 13+).
- **User awareness:** three real triggers exist — new batch allocation, batch-completed (feedback mandatory), new unallocated demand.

**Will a manager realistically notice important changes?** Yes, for the three implemented triggers. **Gap:** no notification exists for a *skill-management* or *high-risk-trainer-appeared* event — the notification system currently only watches `batch_engagement_df` and `unallocated_demand_df`; it does not watch `trainer_operations_df` for a trainer's `feedback_risk` flipping to "High" between polls, which is arguably just as time-sensitive as a new unallocated batch.

**Proposed addition:** extend `NotificationEngine.detect()` with a fourth bucket watching for `feedback_risk` transitions to "High" per trainer (same seen-set pattern already built) — this is a small, contained addition to existing, working infrastructure.

---

## 9. Android API Utilization Review

Cross-checked `SkillEdgeApi.kt` (every Retrofit call Android makes) against what each response actually contains vs. what's displayed.

**APIs consumed:** `login`, `getTrainerIntelligence` (unified dashboard), `getManagerProfile`, `getTrainer360`, `getTeamCapability`, `getAllocationDesk`, `getTrainerSkills`, `markSkill`.

**Fields present in responses but not displayed anywhere in Android:**
1. `manager_kpis.deployable_pct` and `manager_kpis.unknown_status` — computed, never shown (Section 1).
2. `trainer_operations_df.recommended_action` — computed per-trainer, never shown on Team screen or `TrainerCard` (Section 2).
3. `feedback.responses_raw_sample` on Trainer 360 — deliberately temporary scaffolding from this session, correctly never meant for display, will be removed once RMS field names are confirmed live.
4. `delivery.assignments[].participants` (added this session) — displayed only for the current+next assignment on Trainer 360's delivery list; historical assignments' participant data is never fetched (by design, to bound RMS calls) but this means the feature is invisible for anything but the two nearest batches.

**Screens underutilizing available data:**
- **Team screen** — has risk data in every row's source (`feedback_risk`) but no way to filter/sort by it (Section 2's main finding).
- **Dashboard** — has `deployable_pct`/`unknown_status` available in the same `manager_kpis` object already being read for the other 12 tiles, at zero extra API cost.

**No screen was found calling an API and discarding a large portion of its response** — the underutilization here is narrow and specific (a few named fields), not systemic.

---

## 10. Priority Order & Release Roadmap

### P0 — Critical Fixes
*(None outstanding.)* The one bug found and fixed this session (utilization phantom-zero averaging) is already shipped in v1.23.0. No other functional break was found in this audit — Skill Management, Allocation Desk's Phase 3 checklist, and the Session/Auth flow were all verified working as designed.

### P1 — Core Improvements — ✅ ALL SHIPPED in v1.24.0 (2026-08-08)
1. ✅ Add Risk filter + sort to the Team screen (Section 2) — the single clearest gap in the whole audit relative to how central feedback-risk is everywhere else in the app.
2. ✅ Reorder the Dashboard so "Needs Attention" sits directly under the KPI grid, ahead of the descriptive analytics charts (Section 1).
3. ✅ Surface `recommended_action` on `TrainerCard` (Section 2) and `deployable_pct`/`unknown_status` as Dashboard KPI tiles (Section 1/9) — all zero-new-API-call, data already in hand.
4. ✅ Add `backup_role` to the Allocation Desk's compact list-card view, not just the detail screen (Section 5).

See `AI/PROGRESS.md` v1.24.0 entry for exact file-level changes. Not yet
visually verified on-device (no Android SDK/emulator in this environment).

### P2 — Experience Enhancements
1. Persistent "Synced Xm ago" header caption on Dashboard (Section 1).
2. Section-jump navigation on Trainer 360, given its length (Section 3).
3. Sort course-owners by suitability (certified-first, Qubits) inside `CoursesTab`'s expanded card (Section 4).
4. Persist skill-write outcomes via notification if the screen closes before the async result lands (Section 6).
5. Visually separate "always-on" vs. "capability-gated" filters in the Team filter sheet (Section 2).

### P3 — Intelligence & Advanced Features
1. Extend the notification engine to watch for `feedback_risk` transitioning to High between polls (Section 8).
2. Cross-link Courses ↔ Allocation Desk ("N open batches need this course") (Section 4) — needs a small data-shape addition, not just UI.
3. Peer-average marker on Trainer 360's Readiness/Risk gauges (Section 3).
4. Growth & Roadmap section on Trainer 360 — blocked on backend `future_skill_roadmap_df` actually being populated (currently always empty); flagged as a dependency, not an Android task.

### Recommended Release Sequence from v1.23.0
- **v1.24.0** — P1 items 1-3 (Team risk filter/sort, Dashboard reorder + new KPI tiles, `recommended_action` surfacing). These are independent, low-risk, and address the audit's single biggest finding.
- **v1.25.0** — P1 item 4 + P2 items 1, 4, 5 (Allocation Desk backup-role visibility, sync-time header, skill-write notification persistence, filter-sheet grouping).
- **v1.26.0** — P2 items 2-3 (Trainer 360 navigation, Courses owner sorting).
- **v1.27.0+** — P3 items, sequenced after confirming the `future_skill_roadmap_df` backend dependency status.

---

*This audit is Android-scoped throughout, per instruction. Backend items are referenced only where they gate or explain an Android-visible behavior (e.g., the empty `future_skill_roadmap_df`, the unused `_sessions` dict, the naive `revenue_impact`/`customer_priority` heuristics) — none of those were investigated or changed as part of this pass.*
