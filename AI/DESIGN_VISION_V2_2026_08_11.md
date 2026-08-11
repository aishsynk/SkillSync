# SkillEdge V2 — Design Vision
**Date:** 2026-08-11 · **Scope:** whole Android experience · **Constraint:** zero business-logic change

Grounded in a read of the shipped Compose source (`theme/Color.kt`, `theme/Surfaces.kt`, `theme/Type.kt`,
`ui/main/MainScreen.kt`, `DashboardSections.kt`, `TeamTab.kt`, `CoursesTab.kt`, `ActionsInbox.kt`,
`ui/trainer/Trainer360Screen.kt`, `ui/batch/AllocationDeskScreen.kt`, `ui/components/Charts.kt`, `Motion.kt`)
and the Command Centre proposal artifact. The artifact is treated as inspiration, not as the target.

---

## 1 · UX Audit

**The core problem is not visual. It is that every screen is a *report*, and a manager needs a *decision surface*.**

| # | Finding | Evidence in code | Consequence |
|---|---|---|---|
| U1 | **No primary job per screen.** Dashboard renders header → command centre → KPI grid → readiness → risk → capacity → analytics → forecast → needs-you → top performers → cert-gap cards. Eleven peers, none dominant. | `DashboardTab` item list; `DashboardSections.kt` has 6 near-equal summary cards | Manager scans instead of decides. Time-to-first-decision is a scroll, not a glance. |
| U2 | **Actions are scattered across four surfaces.** Actions inbox, `NeedsYouTodayCard`, `CertGapActionCard`, `ManagerActionsSection` in Trainer 360, plus Allocation Desk CTAs. | 4 separate action renderers | No single truth for "what is on my plate". Items get double-handled or missed. |
| U3 | **Everything is equally urgent.** Severity is carried by chip colour only; card weight, size and order are identical for a critical cert expiry and an informational note. | `StatePill`, `Chip`, `stateTint` | Triage collapses. The eye has nothing to land on. |
| U4 | **Trainer 360 is a 13-section vertical dump** with 5 group headers — Profile, Performance, Credentials, Delivery record, Manager decisions. Roughly 8–10 screens of scroll. | `Trainer360Content` items 0–12 | The two questions a manager actually asks — *can they take this batch?* and *what do I do about them?* — are at the top and the bottom. |
| U5 | **Demand is presented as a list of batches, not as opportunity vs. coverage.** International signal exists (`InternationalBadge`, `GlobalPriorityRibbon`) but competes with everything else at the same visual weight. | `AllocationDeskScreen.kt` | High-value FMAT/ILT work does not announce itself. |
| U6 | **Filtering is heavyweight and hidden.** Team has a bottom-sheet with 8 filter groups + a sort menu; Demand has its own separate sheet with a different interaction model. | `TeamFilterSheet`, `FilterBottomSheet` | Two filter paradigms in one app; managers won't learn either. |
| U7 | **No narrative or comparison.** Numbers appear without baseline, target, or delta ("76% utilisation" — vs. what?). | `KpiCard`, `SummaryFigure`, `StatFigure`, `MiniStat`, `Figure`, `HeroFigure`, `CatalogueFigure`, `OverviewMetric` | Manager cannot tell good from bad without prior knowledge. |
| U8 | **Empty/loading/error states are afterthoughts.** One skeleton (`DashboardSkeleton`), one `EmptyStateCard(message)`, one `DashErrorView`. | — | First-run and offline feel broken rather than designed. |
| U9 | **No onboarding, no personalisation, no memory.** No first-run explanation of what readiness or risk scores mean; no saved views; no "my team" default lens. | — | Every session starts from zero. |
| U10 | **Navigation is 5 flat tabs with no cross-linking narrative.** A risk seen on Dashboard cannot be pursued to the trainer and back without losing place. | `SkillSyncNavBar`, `Navigation.kt` | Broken decision loop. |

## 2 · UI Audit

- **U-a — Eight different figure components** (`SummaryFigure`, `OverviewMetric`, `HeroFigure`, `Figure`, `StatFigure`, `MiniStat`, `CatalogueFigure`, `CapacityStat`) render the same thing: a number and a label. Eight sets of sizes, weights, and colour rules.
- **U-b — Three chip components** (`Chip`, `SelectChip`, `Tag`, `CodeChip`, `StatePill`, `DismissChip`, `StateChip`, `CategoryChip`). Same visual job, drifting radii and paddings.
- **U-c — Card surfaces are inconsistent.** `glassSurface`, `accentGlass`, `heroSurface` exist and are good, but screens still hand-roll `Card`/`Column` + background in places, and `SectionCard`/`AnalyticsCard`/`AttentionCard`/`ActionCard`/`BatchCard`/`TrainerCard` each define their own internal padding rhythm.
- **U-d — Type scale is dense but flat.** `titleMedium` 15sp / `titleSmall` 13.5sp / `bodyMedium` 13sp / `labelLarge` 13.5sp all sit within 1.5sp. Four "levels" that read as one. No display-weight numeral style, so hero figures compete with body text.
- **U-e — Fractional sp values** (13.5, 11.5, 10.5) don't scale predictably with system font size and pin the layout to a hairline.
- **U-f — Charts are structurally solid but stylistically unpooled.** `DonutChart`, `GaugeChart`, `TrendChart`, `BarChart`, `StackedBar`, `Sparkline`, `ReadinessRing`, `CorridorBars`, `DistributionBar`, `MeterRow` — 10 chart types, each choosing its own stroke width, corner rounding, label placement and legend treatment.
- **U-g — Spacing is nearly right but not enforced.** `Space` exists (4/8/12/16/24/32) yet screens use `spacedBy(14.dp)` (Dashboard), `spacedBy(8.dp)` (Trainer 360), `PaddingValues(12.dp)`, `PaddingValues(16/4/12)`. Three different page gutters in three screens.
- **U-h — Elevation is decorative, not semantic.** Surface0–3 exist but do not map to a consistent "scaffold / section / card / raised" rule.
- **U-i — Icon language is mixed** — emoji in the proposal, Material icons in code, glyph characters in the nav.
- **U-j — Numerals are not tabular.** Values jitter horizontally on refresh.

## 3 · Design System Review

**What is already right and must be kept:** the single-identity dark scheme, the blue-ramp-as-health idea (`Color.kt` doc comment), status hues held apart from brand, `Radii`/`Space` ladders, `AuroraBackground` drawn once, `glassSurface`/`accentGlass`/`heroSurface`, and `Appear`/`AnimatedCount`/`ShimmerBox` in `Motion.kt`.

**What is missing to call it a system:**

1. **No component contract.** Tokens exist; components are not defined in terms of them. Every screen re-derives its own composition.
2. **No density tiers.** Cards are either "full detail" or "figure row" — nothing between, so information density is set per-card by whoever wrote it.
3. **No status *scale*, only status *colours*.** There is no `Severity` type (Critical / Warning / Watch / Info / Good) driving stripe, order, tint, icon and label together.
4. **No chart theme object.** Stroke widths, grid, axis type, empty-series behaviour are per-chart.
5. **No motion tokens.** Durations and easings are inline.
6. **No state kit.** Loading / empty / error / offline / stale are ad hoc.
7. **No a11y contract.** Contrast on `labelText` (#7F8CA3 on Surface0) is ~4.0:1 — under AA for small text. Colour is often the sole carrier of status. Touch targets on chips fall under 48dp.

## 4 · Information Architecture Review

Current: **Dashboard · Team · Demand · Skills · Actions** — five flat, coequal tabs. This mirrors the API surface (`manager_kpis`, `trainer_operations_df`, `batch_engagement_df`, capability, actions), not the manager's mental model.

The manager's actual model is three-layered:

```
TODAY        →  what needs me now                 (triage)
PEOPLE       →  who I have and what they can do   (supply)
DEMAND       →  what work is coming               (demand)
                     ↕
               ALLOCATE — the join between supply and demand
```

Skills/Courses is not a destination; it is a **lens on People** (capability portfolio) and a **filter on Demand** (who can teach this). Actions is not a destination either; it is the **operating queue of Today**.

**Proposed IA (4 tabs):**

| Tab | Owns | Absorbs |
|---|---|---|
| **Today** | Triage + org pulse + the action queue | Dashboard + Actions |
| **People** | Roster, capability, readiness, Trainer 360 | Team + Skills/Courses |
| **Demand** | Pipeline, international opportunity, coverage gaps | Allocation Desk |
| **Allocate** | The matching workspace: demand row ⟷ candidate trainers | (new; assembled from existing allocation-desk data) |

Note: `Version2Workspaces.kt` already contains `PeopleWorkspaceSwitch`, `TodayWorkspaceSwitch`, `DeliveryOperationsWorkspace`, and `UniversalCommandSearch` — the IA above is the completion of a direction already started in the codebase, not a reversal of it.

## 5 · Current Experience Problems (summarised for stakeholders)

1. The dashboard reports; it does not brief.
2. There is no single place where work lives.
3. Nothing is loud enough — including the things that should be.
4. Trainer 360 buries the two decisions it exists to support.
5. International/FMAT value is invisible at a glance.
6. Screens don't share a component vocabulary, so the app reads as five products.
7. Numbers without baselines can't drive decisions.
8. Nothing is remembered between sessions.

## 6 · Proposed Design Direction

> **"A briefing, not a report."**

Five principles, each testable:

- **P1 · One decision per screen.** Every surface has one job stated in one sentence, and one dominant element that serves it. Everything else is supporting evidence, visually subordinate.
- **P2 · Severity is structural.** Urgency is carried by *order + size + stripe + tint + icon + label* — five redundant channels, never colour alone. A critical item is physically bigger and always first.
- **P3 · Every number carries its meaning.** Value + baseline + delta + direction-is-good/bad. No naked figure ships.
- **P4 · Progressive disclosure.** Summary → expand → detail sheet → full screen. Trainer 360 becomes a tabbed profile, not a scroll.
- **P5 · One vocabulary.** ~20 components, one figure primitive, one chip primitive, one card primitive, one chart theme. If a screen needs something new, it goes in the library first.

**Visual direction:** keep the dark command-centre identity and aurora ground; increase *contrast between tiers* rather than adding decoration. Fewer, larger, quieter cards. More white (dark) space. Restrained glass — glass on the hero and section shells only, flat surfaces for rows, so blur stops being noise. Numerals get their own display treatment (light weight, tight tracking, tabular).

## 7 · Screen-by-Screen Redesign Strategy

### 7.1 Today (Dashboard) — Redesign Vision

**Job:** *"In ten seconds, tell me whether my delivery org is healthy and what I must act on today."*

Structure, top to bottom, and nothing else above the fold:

1. **Briefing line** (new hero, replaces `ProfileHeader` + `CommandHero` + `ManagerCommandCentre` stack).
   One sentence generated from existing data: *"7 of 10 deployed · utilisation 76% and rising · 1 trainer at risk · 8 demands unallocated."* Under it, a single **Readiness ring** (reuse `ReadinessRing`) with delta vs. last month. This is the only large element on screen.
2. **Attention strip** — a horizontally-scrolling row of at most 3 *critical* items, each a large tappable card with the recommended action as the primary button. Sourced from the existing attention ranking (`rankByAttention`).
3. **Pulse row** — 4 KPI tiles maximum (Strength, Utilisation, Cert coverage, At risk), each with sparkline + delta. The current 8-tile grid moves behind "All metrics".
4. **Capacity balance** — one `DistributionBar` (bench / optimal / stretched) with the *interpretation* as the headline: *"The gap is coverage, not headcount."*
5. **Demand at a glance** — count of unallocated, of which international, with a single CTA into Demand.
6. **Everything else** (analytics, forecast, top performers, cert gaps) moves into a collapsed **"Explore"** section or into People/Demand where it belongs.

Storytelling rule: each section headline states a *conclusion*, and the chart is the evidence. Not "Utilisation trend" but "Utilisation is inside the target corridor for the third month".

### 7.2 People (Team) — Redesign Vision

**Job:** *"Who needs me, who is free, and who can teach X."*

- **Header intelligence bar** replaces the current filter/sort cluster: search + three saved lenses (**Needs attention · Available now · By capability**). Lenses are one tap; the 8-group filter sheet becomes "Advanced", not the primary path.
- **Roster ranked by attention by default**, severity stripe on the leading edge (already available via `accentGlass`).
- **New trainer card** — compact, 96–112dp, one row:
  `avatar · name / primary skill · location flag` on the left; `util • certs • risk` as three micro-figures; a 60×22 sparkline on the right; severity stripe on the edge; status pill top-right.
  Everything else currently on `TrainerCard` (333 lines) drops to the profile.
- **Group headers that count**: "Needs attention (3) · Available (2) · Deployed (5)".
- **Capability lens** absorbs `CoursesTab`'s `CapabilityPortfolio` and key-person risk: a coverage matrix of course × owners, with single-owner courses flagged.

### 7.3 Trainer 360 — Redesign Vision

**Job:** *"Can this person take this batch, and what do I do about them?"*

Replace the 13-section scroll with a **sticky identity header + 4 tabs**:

- **Header (always visible):** avatar, name, designation, current status, and a **decision bar** — `Readiness ▸ 82 · Utilisation ▸ 76% · Risk ▸ Low · Next free ▸ 22 Aug` — plus two persistent buttons: **Allocate** and **Raise action**.
- **Tab 1 · Now** — current + next batch, availability window, capacity forecast. (from `UtilisationSection`, `AvailabilitySection`)
- **Tab 2 · Capability** — certifications held/gaps/expiring, teachable courses, syllabus depth. (from `CertificationSection`, `CapabilitySection`)
- **Tab 3 · Performance** — readiness, risk, feedback, delivery history. (from `DeliveryReadinessSection`, `RiskSection`, `FeedbackSection`, `DeliverySection`)
- **Tab 4 · Actions** — open manager actions on this trainer with inline resolution. (from `ManagerActionsSection`)

Result: ~8 screens of scroll become ~1.5 per tab, and both decisions are answerable without scrolling at all.

### 7.4 Demand — Redesign Vision

**Job:** *"Where is the high-value work, and can I cover it?"*

- **Coverage headline first** — a fit-band `DistributionBar` (strong / partial / no cover) with the sentence that interprets it.
- **International & FMAT get a distinct visual class**, not a badge:
  - full-bleed **priority ribbon** across the card top with a gradient (Azure → Cyan) and the value framing (*"International ILT · 24 pax"*),
  - a **globe/flag medallion** at 40dp in the leading slot instead of a generic icon,
  - **elevated tier**: taller card, stronger glass, glow ring, and a slow 4s ambient shimmer on the ribbon only,
  - pinned to a **"Global priority"** section that always sorts first with its own header (the existing `globalPrioritySection` / `GlobalPriorityRibbon` / `InternationalOpportunityBanner` become one coherent treatment instead of three competing ones).
- **Pipeline by date**, not by mode — managers plan in weeks. Mode becomes a chip on the card.
- **Blockers surface on the card**: visa clearance, accreditation, local language — as amber chips with the blocked card tinted.
- **Recommendation presentation**: instead of a bare fit score, show `95 fit` *plus* the reason and the candidate — *"Subhash Verma — certified, free from 17 Aug, taught 6×"* — with **Allocate** as the primary action.

### 7.5 Actions — Redesign Vision

**Job:** *"Clear my plate."* Actions becomes the operating queue of **Today**, not a separate destination.

- **Queue, not list.** Ordered by severity then age. Three swimlanes: **Now · This week · Watching**.
- **Every row carries its resolution inline** — swipe-to-resolve, snooze, delegate; the primary action is a button on the card, not a navigation.
- **Grouped by subject** (trainer / batch / course) so five actions about one person collapse into one expandable block.
- **Bulk operations** on selection: certify, allocate, escalate, snooze.
- **Escalation path is explicit** — an action that has aged past threshold gets a visual promotion and an "Escalate" affordance.
- Keep the existing category/state chips but reduce to one chip row using the shared chip primitive.

## 8 · Component Library Recommendations

**Foundation (extend `theme/`)**

- `Severity` enum → drives stripe, tint, icon, pill label, sort weight. One source of urgency.
- `SkillMotion` object → durations + easings as tokens.
- `SkillChartTheme` → stroke widths, grid, axis, corner radius, empty-series behaviour, series ramp.
- Type scale rework: integer sp, wider steps (`Display 40/300 tabular`, `H1 24/600`, `H2 18/600`, `Title 15/600`, `Body 14/400`, `Caption 12/400`, `Label 11/650 +0.10em caps`), plus a dedicated `numeric` style with `TextStyle(fontFeatureSettings = "tnum")`.
- Contrast fix: raise `labelText` to ≥ 4.5:1 on Surface0.

**Primitives (replace the duplicates)**

| New | Replaces |
|---|---|
| `Figure(value, label, delta, size)` | `SummaryFigure`, `OverviewMetric`, `HeroFigure`, `Figure`, `StatFigure`, `MiniStat`, `CatalogueFigure`, `CapacityStat` |
| `SkillChip(text, tone, size, onClick?)` | `Chip`, `Tag`, `CodeChip`, `StatePill`, `SelectChip`, `DismissChip`, `StateChip`, `CategoryChip` |
| `SkillCard(severity, density, header, body, actions)` | `SectionCard`, `AnalyticsCard`, `AttentionCard`, ad-hoc `Card` usage |
| `SectionHeader(title, conclusion, trailing)` | `DashSectionHeader`, `ProfileGroupHeader`, `SectionHeader`, `ModeSectionHeader` |
| `KpiTile(value, label, delta, spark, severity)` | `KpiCard` + inline tiles |
| `PersonRow` / `PersonCard` | `TrainerCard` (compact form) |
| `DemandCard(tier)` | `BatchCard` + `GlobalPriorityRibbon` + `InternationalBadge` + `InternationalOpportunityBanner` |
| `ActionRow(severity, subject, resolution)` | `ActionCard`, `CertGapActionCard`, `NeedsYouTodayCard`, `AttentionCard` |
| `FilterBar(lenses, advanced)` | `TeamFilterSheet` + `FilterBottomSheet` + `SortMenu` (one model) |
| `StateView(loading/empty/error/offline/stale)` | `DashboardSkeleton`, `EmptyStateCard`, `DashErrorView` |

**Target:** ~20 components total. A screen file should contain layout and data mapping — no bespoke visuals.

## 9 · Motion & Animation Recommendations

Purposeful only; every animation answers "what changed, and where did it come from".

| Token | Value | Use |
|---|---|---|
| `enter` | 280ms `FastOutSlowIn`, 12dp rise + fade | Card/section entry (`Appear` already does this — tokenise it) |
| `stagger` | 40ms, capped at 6 items | List entry; never cascade a 40-row list |
| `countUp` | 600ms ease-out | All KPI figures (`AnimatedCount` already exists) |
| `shimmer` | 1200ms loop | Skeletons only |
| `expand` | 240ms, shared-bounds | Card → detail sheet; the tapped card *becomes* the sheet |
| `severityPulse` | 2s, 0.6→1.0 alpha, critical only | At most 1 element on screen |
| `ambientRibbon` | 4s linear sheen | International demand ribbon only |
| `refreshSettle` | 180ms cross-fade | Value updates on auto-refresh, so numbers don't snap |
| `press` | 100ms scale 0.98 | All tappable cards |

Rules: honour `Settings.Global.ANIMATOR_DURATION_SCALE` / reduced-motion (skip to end state, keep opacity). Never animate more than two properties at once. Use shared-element transitions for Team card → Trainer 360 and Demand card → Allocate, so the decision loop stays spatially coherent.

## 10 · Dashboard Redesign Vision (expanded)

The dashboard must answer, in order and in this visual weight:

1. **Are we healthy?** → readiness ring + one sentence. (largest element on screen)
2. **What is on fire?** → up to 3 critical cards with actions.
3. **What is moving?** → 4 KPI tiles with deltas and sparklines.
4. **Where is the slack?** → capacity balance with its interpretation.
5. **What is coming?** → demand summary + CTA.
6. **What else?** → collapsed Explore.

Storytelling devices: conclusion-first headlines; target corridors drawn on charts (`CorridorBars` already supports the idea) so "good" is visible without knowing the number; deltas always against a stated baseline; and a single "as of" timestamp so managers trust freshness.

## 11 · Design System Maturity Target

| Level | Definition | Now | V2 |
|---|---|---|---|
| 1 | Colours named | ✅ | ✅ |
| 2 | Spacing/radius scales | ✅ (defined, not enforced) | ✅ enforced |
| 3 | Type scale with clear steps | ⚠️ flat | ✅ |
| 4 | Component library with contracts | ❌ | ✅ ~20 components |
| 5 | Semantic states (severity, density, tone) | ❌ | ✅ |
| 6 | Motion tokens | ❌ | ✅ |
| 7 | Chart theme | ❌ | ✅ |
| 8 | A11y contract (contrast, targets, non-colour status, TalkBack) | ❌ | ✅ |
| 9 | State kit (loading/empty/error/offline) | ⚠️ partial | ✅ |
| 10 | Documented + lint-guarded (no raw hex, no raw dp) | ❌ | ✅ |

## 12 · Phasing (proposed, for approval — no code written yet)

- **Phase 0 — Foundation.** Type scale, `Severity`, motion tokens, chart theme, contrast fixes. No screen changes. Low risk, unlocks everything.
- **Phase 1 — Primitives.** `Figure`, `SkillChip`, `SkillCard`, `SectionHeader`, `StateView`; mechanically migrate call sites. Pure substitution, business logic untouched.
- **Phase 2 — Today.** Rebuild the dashboard around the briefing hero + attention strip.
- **Phase 3 — People + Trainer 360.** New person card; tabbed profile.
- **Phase 4 — Demand + Allocate.** International tier treatment; recommendation presentation; the allocate workspace.
- **Phase 5 — Actions.** Queue model, inline resolution, bulk ops, escalation.
- **Phase 6 — Polish.** Shared-element transitions, onboarding for score literacy, saved lenses.

Every phase is independently shippable and reversible, and none of them touches the repository, API layer, or any calculation.

---

### The test, applied

*"Would a senior product designer be proud to ship this?"* — Today: no, because the screens report rather than brief, and five surfaces don't share a vocabulary. After V2: the app has one job per screen, one component language, urgency you can feel before you read, and a decision loop that closes — dashboard → person → allocate → done.
