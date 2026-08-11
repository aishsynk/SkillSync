# SkillEdge — Full Product & API Audit, and the Version 2 Vision
**Date:** 2026-08-11 · **Status:** audit only, no code · **Basis:** all 37 documents in `trainer_portal_api_details`, `backend.py` (4390 lines), the full Android source, and the shipped v3.6.0 build.

> **The headline.** We have been building screens on roughly **60% of the data estate**, and the 40% we ignore contains almost exactly the fields needed to fix the problems you keep raising. International FMAT/ILT matching is weak *because the fields that decide it are never read*. There is no revenue dimension in the product *because the only endpoint carrying money is dormant*. This is not a design problem that better cards will fix.

---

## 1 · Full Product Audit

### 1.1 The core failure

Every screen answers **"what is true?"**. A manager's job is **"what do I do, and what will it cost me if I don't?"**

| Screen | Answers today | Should answer | Verdict |
|---|---|---|---|
| Dashboard | What our numbers are | What breaks this week if I do nothing | **Rebuild** |
| Team | Who reports to me and their stats | Who is at risk of leaving, burning out, or going stale | **Rebuild** |
| Trainer 360 | 13 facts about a person, now in 4 tabs | Can this person take this batch, and what is their trajectory | **Rebuild** |
| Demand | A list of unallocated batches | Which revenue is at risk and who can save it | **Rebuild** |
| Actions | A queue of items | The consequence of each item ageing | Restructure |
| Skills | A searchable catalogue | A capability portfolio I manage as a portfolio | **Rebuild** |
| Notifications | Events that occurred | Things that changed my plan | Restructure |
| Intelligence/Agent | Answers about state | Answers about consequence and trade-off | Extend |

### 1.2 Why the Dashboard still fails at a glance

I rebuilt it twice and it is still not right, and I can now say why precisely: **it has no unit of consequence.** Every element is a *measurement* — readiness 86, utilisation 76%, 8 unallocated. None of them tells the manager what happens next.

A manager cannot act on "8 unallocated batches". They can act on **"₹X of booked revenue has no owner, and 3 of those batches start in under 14 days"**. We do not show the second sentence because we never fetch the fee (`Get Active SC Date` → `Total Fee`, `Currency`, **dormant**).

The dashboard also violates its own premise: it opens with **readiness**, a composite score nobody outside this app defines, rather than with **money, risk, and time** — the three things a delivery manager is actually measured on.

**The dashboard should be rebuilt around a single question: "What is at risk in the next 14 days, and what is the one thing I should do about it?"**

### 1.3 Why Demand still does not feel international

Not a styling problem. The **matching engine does not model international delivery at all.**

`_rank_batch` scores on: skill match, Qubits score, utilisation, language, skill level, feedback risk. It does **not** consider:

- **Can this person legally and practically travel?** `InternationaRoamingOffDates` — parsed in backend, **never sent to Android, never used in ranking**.
- **Can they cover the client's time zone?** `NightILOffDates`, `MorningILOffDates`, `EveningILOffDates` — same: parsed, unused.
- **Are they suitable for an international audience?** `MTI_Issue` (Mother Tongue Influence) from `Trainer availability` — **the endpoint is dormant; the field appears nowhere in the codebase.**
- **Is this batch worth prioritising?** No fee, no currency, no client name, no CSM.
- **In-house or freelance?** `TrainerType` from `Get Inhouse and FL Trainers Of Courses` — barely used; this is the make-versus-buy decision.

So the globe badge is cosmetic: we mark a batch international, then rank candidates **by a model that does not know what international means.** Adding a bigger globe icon would make the lie louder. **Fix the model first, then the visual treatment has something true to express.**

### 1.4 Why Trainer 360 still reads as sections

Tabs reorganised the scroll; they did not change what is on it. It presents **attributes** (certifications, utilisation, feedback) with no **trajectory** and no **comparison**.

A trainer intelligence centre answers: Are they getting better or worse? How do they compare to the team and to the same role elsewhere? What is their next best assignment? What is the risk of losing them? Are their skills going stale?

We hold data for nearly all of this and use none of it: `Trainer_Last_3_Months_Utilization` (trend, used only as a sparkline), `Get Latest Version Of Courses` (**dormant** — tells you a trainer is certified on a superseded version), `Get HR Incident Positive Negative` (we read only the negative half), `Trainer Resume Details` (`Interest`, `TrainingsDeliveredFor` — career direction and client history, unused).

### 1.5 Why Skill Management feels developer-oriented

Because it is modelled on the API, not the job. `Add Trainer Skill (IDP)` takes **one** `CourseId` + **one** `TrainerEmail`, so the UI became one-skill-one-person-one-search. The manager's actual verb is **"my team needs this capability"**, which is one skill against *many* people. Your proposed flow is correct and I am adopting it wholesale (§9.6).

---

## 2 · Full API Audit

**37 documented APIs. 27 wired into `backend.py`. 4 of those 27 are never called. 10 were never wired at all.**
**Effective usage: 23 of 37 = 62%. Unused capability: 38%.**

### 2.1 Never wired (10)

| Key | API | What it unlocks | Value |
|---|---|---|---|
| **215** | Exam Course Linked | Maps course → required exam. **The missing link in every certification recommendation** — today we say "there is a gap", not "sit *this* exam". | ★★★★★ |
| **172** | Get Latest Version Of Courses | Detects trainers certified on **superseded course versions** — silent capability decay nobody is tracking. | ★★★★★ |
| **114** | Course & Technology List | Technology grouping → portfolio view by technology, not a flat course list. | ★★★★ |
| **205** | Get Course and Domain | Domain grouping → "we are thin in Cloud Security". | ★★★★ |
| **164** | Course List | Vendor + course URL → vendor-level capability strategy. | ★★★ |
| **206** | Get Course Module | Chapter list → precise enablement plan for a gap. | ★★★ |
| **156** | Get Course Content URL | Direct enablement material for closing a gap. | ★★★ |
| **171** | Trainer Free Schedule (by course) | **Course-first availability**: "who is free for X?" — inverse of our trainer-first model, and it is the allocation question. | ★★★★ |
| **111** | Trainer RC Schedule | Per-trainer date-range schedule → a real calendar. | ★★★★ |
| **72** | Unique Certifications Count | Team certification breadth as a single measure. | ★★ |

### 2.2 Wired but dormant (4)

| Key | API | Why it matters |
|---|---|---|
| **13** | Get Active SC Date | **`Total Fee` + `Currency`.** The only money in the entire estate. Without it SkillEdge cannot express business value — everything is headcount and percentages. |
| **90** | Trainer availability | `MTI_Issue`, `Languagee`, `skill`, `TotalAssignment` — **MTI is a direct international-suitability signal.** |
| **93** | Upcoming Assignments | Forward schedule by trainer/date. |
| **278** | Get Recording Details | Session recordings — coaching evidence for a feedback conversation. |

### 2.3 Fields fetched but never surfaced

From `Get Trainer Details` (75), all present in the response, **zero references in Android**:

- `InternationaRoamingOffDates` — international travel blackout
- `RoamingOffDates` — domestic travel blackout
- `NightILOffDates` / `MorningILOffDates` / `EveningILOffDates` — **time-zone shift coverage**
- `QubitsScore` — objective skill measure (used in ranking, never shown to the manager)
- `Is Future Skill` / `Future Skill Date` — declared learning intent, i.e. **the succession pipeline**
- `techcallrating` — **zero references anywhere in the codebase**
- `DM`, `Course Assignment`

From `Get Trainer Negative Feedback` (218): `client_name`, `csm_name`, `assignment_delivery_mode` — we count feedback but never ask **which client** or **which mode** it came from. Mode-specific weakness (fine in ILO, poor in classroom) is invisible.

From `Get Direct Indirect Reportee` (82): `TrainerPlus`, `IsdirectReportee` — direct vs indirect reporting is fetched and flattened away.

From `Trainer Resume Details` (87): `Interest`, `TrainingsDeliveredFor`, `Experience` — career aspiration and client history.

---

## 3 · Current Gaps

1. **No money anywhere.** Not one screen shows revenue, fee, or cost. A delivery manager is measured on revenue delivered and utilisation; we give them only the second.
2. **No time-to-impact.** Nothing says "starts in 9 days". Urgency is asserted by colour, not by a date.
3. **No trajectory.** Everything is a current-state snapshot. No "getting worse", no forecast, no early warning.
4. **International delivery is unmodelled** (§1.3).
5. **No client dimension.** `client_name` and `csm_name` exist; we never group by client. Repeat-client risk and relationship history are invisible.
6. **No skill decay model.** Course versions move; certifications age; nobody is told.
7. **No cost of inaction.** Suggestions say what to do, never what happens if ignored.
8. **Skill management is single-record CRUD**, not portfolio management.
9. **Sync is visible.** "OFFLINE COPY", spinners, and manual refresh push infrastructure into the manager's attention.
10. **No comparison baseline.** No team-vs-team, trainer-vs-peer, or period-vs-period context anywhere.

---

## 4 · Missed Opportunities

These are derivable **today** from data we already have or can wire in days:

| # | Derived metric | Inputs | Manager value |
|---|---|---|---|
| M1 | **Revenue at risk** | `Total Fee` × unallocated + days-to-start | The single number the dashboard should lead with |
| M2 | **International readiness score** | `InternationaRoamingOffDates` + `MTI_Issue` + languages + passport/visa | Makes FMAT/ILT matching real |
| M3 | **Time-zone coverage map** | Night/Morning/Evening IL off-dates | Who can serve US, EMEA, APAC hours |
| M4 | **Capability decay index** | `Get Latest Version Of Courses` vs held certs | Silent risk nobody tracks |
| M5 | **Succession/bench-strength** | `Is Future Skill` + `Future Skill Date` + single-owner analysis | Who is being groomed for what |
| M6 | **Client-specific risk** | `client_name` + feedback + repeat assignments | Protect key accounts |
| M7 | **Mode-specific quality** | feedback × `assignment_delivery_mode` | "Strong in ILO, weak in classroom" |
| M8 | **Make vs buy** | `TrainerType` in-house vs freelance | Sourcing and margin |
| M9 | **Certification ROI** | `Exam Course Linked` + demand by course | Which exam to fund next, ranked by pipeline |
| M10 | **Burnout risk** | Utilisation trend + consecutive weeks + travel days + feedback slope | Retention, before resignation |
| M11 | **Course-first availability** | `Trainer Free Schedule` (171) | Answers the actual allocation question |
| M12 | **Technology portfolio depth** | `Course & Technology List` + `Course and Domain` | Where the team is structurally thin |

---

## 5 · UX Improvements

1. **Lead with consequence, not measurement.** Replace the readiness hero with **revenue at risk + days to impact**.
2. **Everything gets a clock.** Every risk, gap and demand shows days-to-impact. Urgency must be earned by a date.
3. **One decision per screen** — still not honoured; the dashboard currently offers six.
4. **Comparison by default.** No number without a baseline: peer, team, or last period.
5. **Progressive commitment.** Preview → confirm → undo on every write, especially bulk skill assignment.
6. **Course-first entry point.** Add "who can teach X?" as a primary route, not a search filter.
7. **Invisible sync.** Never show "OFFLINE COPY"; show a quiet freshness dot, reconcile silently, queue writes.
8. **Explain every score.** Readiness, relevance and Qubits must be tappable to reveal their inputs.
9. **Bulk everything.** Managers work on groups; every list needs multi-select and a bulk action bar.
10. **Consequence framing in the agent.** "If you do nothing, X" alongside every recommendation.

---

## 6 · Business Improvements

1. **Introduce the revenue lens** (wire API 13). Rank demand by fee, not by date. This changes what the product is *for*.
2. **Margin awareness**: in-house vs freelance (157) on every allocation.
3. **Account protection**: client-level view of delivery quality (218).
4. **Certification investment ranked by pipeline value** (215 + demand): fund the exam that unlocks the most revenue.
5. **Capacity forecasting against booked revenue**, not against headcount.
6. **International premium**: treat FMAT/ILT as a distinct, higher-value business line with its own funnel and its own readiness pool.
7. **Retention economics**: surface burnout risk as cost-of-replacement, not as a wellbeing nicety.

---

## 7 · Design Improvements

1. **A value tier in the type scale** — money needs a treatment distinct from counts.
2. **A time-pressure visual language** — countdown chips, a shared "days remaining" component.
3. **International as a card *class***, not a badge: distinct surface, globe medallion, travel-window strip, time-zone bar, priority ribbon. It earns this only once §1.3 is fixed.
4. **Evidence disclosure pattern** — one consistent "why this number" affordance.
5. **Comparison sparkline standard** — every metric renders against its baseline the same way.
6. **Portfolio matrix** — a reusable capability × people grid for skills, certifications and coverage.
7. **Bulk-selection pattern** — one multi-select behaviour reused across Team, Skills and Actions.
8. **Empty ≠ zero.** A visual distinction between "not measured" and "measured as zero", app-wide.

---

## 8 · Architecture Improvements

1. **Wire the 14 unused/dormant APIs**, in the priority order of §10.
2. **Introduce a derived-metrics layer** in `backend.py` — M1–M12 computed server-side, cached, versioned, so Android stays a rendering client.
3. **Rewrite `_rank_batch` as a transparent, weighted, multi-parameter model** returning per-factor contributions, not one opaque `relevance`.
4. **Move the agent's learning loop server-side** so the model follows the manager and can eventually pool across managers.
5. **Offline-first write queue** with optimistic UI for skill assignment and action state changes.
6. **Bulk endpoint for skill assignment** — the current single-record API forces N calls; the backend should fan out and return a per-row result set.
7. **Field-level provenance**: every value carries source + fetched-at, so "not measured" is always distinguishable from zero.
8. **Complete the security task**: RMS credentials to Render secrets, delete plaintext fallbacks.

---

## 9 · Version 2 Vision

> **SkillEdge V2 is a delivery revenue-protection system, not a team dashboard.**
> It answers: *what revenue is at risk, who can protect it, and what should I do in the next hour.*

### 9.1 Information architecture

```
COMMAND      what is at risk now, and the one action that changes it
PEOPLE       capability, capacity, trajectory, retention
DEMAND       revenue pipeline, coverage, international funnel
CAPABILITY   skills and certifications managed as a portfolio
AGENT        ask, decide, act — with consequence framing
```

### 9.2 Command Centre (rebuilt)

**Hero: revenue at risk.** "₹48L across 8 unallocated batches. ₹12L starts inside 14 days." Then a **14-day risk timeline** — batches, expiries and blackout windows on one horizontal axis. Then **the one action** that most reduces risk, with its projected effect. Then a compact health strip (utilisation, readiness, at-risk) as *supporting* evidence, not headline.

### 9.3 People Intelligence

Cards carry **trajectory and risk**, not stats: utilisation slope, burnout index, capability decay, international readiness, retention signal. Grouped by *what the manager must do*: Protect, Develop, Deploy, Recognise.

### 9.4 Demand & Allocation

Ranked by **value at risk**. International/FMAT/ILT as a first-class funnel with travel windows, time-zone fit, MTI and visa state. Allocation shows a **transparent match breakdown** — every factor and its contribution, so a manager can disagree with the model on a specific axis.

### 9.5 Trainer Intelligence Centre

Four questions, in this order: **Can they take this?** · **Where are they heading?** · **What is the risk of losing them?** · **What is my next move?** Peer comparison and trajectory throughout; assignment history by client and mode; recordings as coaching evidence.

### 9.6 Capability Management (your specified flow — adopted)

**Skill → Select Team Members → Assign.**

- Tap a skill from the capability portfolio (never a blank search).
- A Select2-style multi-select lists **all reportees**, each annotated with current level, Qubits, cert status and availability, so the choice is informed.
- Single select · multi-select · **Select All** · filter by "does not have this skill".
- Skill level per person or applied to all.
- **Assignment preview** — exactly what will change, per person, before anything is written.
- **Confirmation**, then optimistic apply with per-row success/failure and **undo**.
- Same surface offers **Remove Skill**, **Edit Skill Level**, and **Bulk Skill Management**.
- Backed by a new bulk endpoint; the single-record RMS call is fanned out server-side.

### 9.7 The agent

Extends from "what is true" to **"what will happen, and what is the trade-off"**: consequence framing, scenario comparison ("if I move Priya to Dubai, what breaks?"), and server-side pooled learning. The LLM seam stays at `Agent.ask`.

---

## 10 · Prioritised Roadmap

Ordered by **manager value ÷ effort**, one shippable release per step, per the one-page-per-release rule.

### Phase 1 — Unlock the data (foundation; no new screens)
| # | Work | Why first |
|---|---|---|
| 1.1 | Wire **API 13** (fee/currency) and expose fee on demand | Unlocks the entire revenue lens; everything downstream depends on it |
| 1.2 | Expose the **roaming/IL off-date fields** end to end | Unblocks real international matching |
| 1.3 | Wire **API 90** (`MTI_Issue`) and **215** (exam mapping) | Completes matching and makes cert advice actionable |
| 1.4 | Derived-metrics layer + provenance | Prevents V2 metrics being recomputed in three places |

### Phase 2 — The two screens that carry the product
| 2.1 | **Command Centre rebuild** around revenue at risk + 14-day timeline |
| 2.2 | **Demand rebuild**: value-ranked, international funnel, transparent match breakdown |

### Phase 3 — Capability management
| 3.1 | Bulk skill endpoint |
| 3.2 | **Skill → Select Members → Assign** flow, with remove/edit/bulk |
| 3.3 | Capability portfolio matrix (114/205/164/172) incl. decay index |

### Phase 4 — People
| 4.1 | People Intelligence rebuild (trajectory, burnout, retention) |
| 4.2 | Trainer Intelligence Centre rebuild |

### Phase 5 — Operations & polish
| 5.1 | Actions with consequence + bulk |
| 5.2 | Invisible sync and offline write queue |
| 5.3 | Agent consequence framing + server-side learning |
| 5.4 | Notification relevance model |

---

## Decisions required before Phase 1

1. **Is revenue in scope?** Wiring fee/currency changes SkillEdge from a capacity tool into a revenue tool. It is the single highest-value change available, and it needs your approval on exposing commercial figures in a mobile app.
2. **Confirm the 14 unused APIs are usable** — the credentials in the portal docs exist, but I have not probed them live. Some may be restricted or return empty for this account.
3. **Is a bulk skill-write endpoint acceptable to the RMS team?** We would fan out N single-record calls server-side. Volume and rate limits need their sign-off.
4. **International policy source of truth**: are visa and passport status anywhere in RMS, or is `InternationaRoamingOffDates` the only signal we get?
5. **Screen order** for Phases 2–4 if you disagree with value ÷ effort.
