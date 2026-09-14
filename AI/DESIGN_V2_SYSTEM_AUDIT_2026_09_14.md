# Design V2 Recovery — System Audit (2026-09-14)

Audit only, per instruction §45. No UI files modified. Grounded in direct code reads this
session — `theme/` package contents, `Version2Workspaces.kt`, git tag history — not assumption.

## A. V1 vs V2 visual audit

**Correction to my own earlier claim:** I previously flagged `Version2Workspaces.kt` as dead
code. It is not — `TodayWorkspaceSwitch`/`PeopleWorkspaceSwitch` (same file) are live, called
from `MainScreen.kt`'s `DashboardTab`, and are exactly the "Briefing / Action queue" segmented
control visible in the reference screenshots. That control sits *above* where
`ManagerCommandCentre` renders — it survived my rebuild untouched, I just never noticed it was
the same component. Correcting the record rather than repeating a wrong finding.

**The real gap, confirmed by reading `theme/Surfaces.kt`, `Type.kt`, `SkillMotion.kt`:** V1-era
work already built a materially richer design system than what my `ManagerCommandCentre.kt`
rebuild actually *used*:

| Existing primitive (already in `theme/`) | Used in my Today rebuild? |
|---|---|
| `heroSurface()` — Navy→Brand diagonal gradient, the "single loudest fill" | No — hero card used plain `SkillCard`/`glassSurface` |
| `frostedGlass()` — real `RenderEffect` blur on API 31+, graceful fallback below | No |
| `accentGlass()` — directional gradient wash + matching border per severity | No — I used flat `SkillCard(severity=...)` |
| `glowRing()` — focus/active/pressed ring | No |
| `IconSlot()` — square icon chip behind a glyph | No — I used bare emoji text |
| `SkillMotion.snappy/gentle/flow/press` — named springs | No — only the generic `pressable()` scale-tween |
| Full `Typography` ladder (`displayLarge` 44sp → `labelSmall` 11sp, tabular `NumericStyle`) | Partially — mostly `titleSmall`/`labelSmall`, rarely `headlineLarge/Medium` |

**Conclusion:** this is not a "V1 was better, V2 is worse" problem in the sense of missing
capability — the capability already exists in `theme/`. It's that every inner-screen pass so far
(including mine) has been writing screen-local `Row`/`Column`/`SkillCard` composition instead of
reaching for the richer primitives already sitting one import away. That is exactly your
diagnosis: vertical, screen-by-screen work with no horizontal discipline enforcing consistent use
of the system that already exists.

## B. Current design system inventory (real files, real line counts)

```
theme/Color.kt              182 lines — palette, SkillColors data class, Command scheme
theme/Type.kt               108 lines — full Typography ladder + NumericStyle/NumericInline
theme/Surfaces.kt           257 lines — Radii, Space, AuroraBackground, glassSurface,
                                         accentGlass, heroSurface, frostedGlass, glowRing, IconSlot
theme/SkillMotion.kt          35 lines — 4 named springs (snappy/gentle/flow/press)
theme/DesignSystem.kt        330 lines — Severity, Figure, ToneChip, SectionHeading, SkillCard,
                                         StateNote, Layout/ReadableColumn, pressable(), critical pulse
theme/SkillSyncComponents.kt 836 lines — Scaffold/TopBar/PageHeader/Section, Metric, Chips,
                                         Buttons, TextField/SearchBar, Loading/Empty/Error/Offline
                                         banners, ListItem
theme/SkillSyncDesignCatalog.kt 186 lines — (not yet inspected this pass — flag for D1)
theme/Theme.kt               104 lines — MaterialTheme wiring, CommandScheme, StatusBarIcons
```
No graph/heatmap/timeline/chart primitives exist yet anywhere in `theme/` — every chart seen so
far (`CapacityPlanningCard`, `WeekBars`) is a screen-local `Box`-proportional bar, not a shared
component. This is real, new work for D1, not a gap in judgment.

## C. Duplicated / weak components

- **Not duplicated — underused.** No evidence of two competing card/button systems; `SkillCard`
  (`DesignSystem.kt`) and the newer `theme/SkillSyncComponents.kt` `SkillSyncCard` appear to be
  two names for a similar idea (needs a D1 decision: consolidate to one, per §6's own instruction
  not to create duplicates).
- **Weak:** `ManagerCommandCentre.kt`'s own `ComparisonStat`/`PulseTile`/`MiniStat`/`OperationTile`
  (added this session) are exactly the "same rounded rectangle, different label" pattern named in
  §5 — candidates to fold into a shared `MetricCell`/`MetricCard`/`ActionRow` family rather than
  live as screen-private composables.
- **Chart language:** zero shared chart primitives. `Box`-height bars work but aren't reusable,
  don't support tap-to-drill, and have no sparkline/donut/radial-progress sibling.

## D. Proposed V2 design tokens (extend, don't replace, what exists)

`Color.kt`/`Type.kt`/`Surfaces.kt`/`SkillMotion.kt` are kept as-is — they're sound. Additions:

- **Surface levels** (§7): `base`, `surface`, `surfaceRaised`, `surfaceInteractive`,
  `surfaceSelected`, `surfaceCritical` as named tokens on `SkillColors`, distinct from the current
  single `glassSurface()` treatment — currently every card is visually the same depth.
- **Shape ladder** (§8): `Radii` already has `hero/card/kpi/chip/icon` (5 steps) — extend with a
  `pill` and a `compact` (smaller than `chip`, for dense data rows) rather than inventing a
  parallel system.
- **Semantic accent families** (§10): `Color.kt`'s `SkillColors` already has `good/warn/crit` — add
  `info`, `intelligence` (AI/Copilot identity, currently unnamed — Copilot uses ad hoc color),
  `planning`, `communication` as named semantic slots so a given meaning uses one color everywhere,
  not per-screen judgment calls.

## E. Proposed component library (additions only; existing names kept where equivalent)

```
Already exist, promote to consistent use (no new code):
  SkillCard / accentGlass / heroSurface / frostedGlass / glowRing / IconSlot
  Figure / ToneChip / SectionHeading / StateNote / pressable()
  SkillSyncTopBar / SkillSyncPageHeader / SkillSyncListItem / SkillSyncEmptyState /
  SkillSyncErrorState / SkillSyncLoadingState / SkillSyncOfflineBanner

New, D1 scope:
  MetricCell        — compact single-value cell (replaces ComparisonStat/PulseTile/MiniStat)
  HeroMetric        — the one big number per screen, built on heroSurface() + ReadinessRing-style
                       Canvas ring (already prototyped in ManagerCommandCentre — promote to theme/)
  TrendMetric       — metric + delta, delta omitted (not zeroed) when backend has no trend
  MiniSparkline     — Canvas line, only rendered when real time-series data exists (§39/§12)
  ChartCard         — frame + title + legend wrapper around a chart body (bar/donut/radial/sparkline)
  ActionRow         — the "This Week" card pattern (severity stripe, title, detail, due, action chip),
                       generalized from PrioritiesScreen.PriorityCard
  DataRow / ExpandableDataRow — the responsive-table row pattern (§19/§20)
  TimelineItem      — icon/status, title, description, actor, timestamp, optional action (§21)
  FilterBar / FilterChipGroup — search + chips + date range, for Priorities/Runway/Pipeline/Accounts
  SegmentedSelector — promote TodayWorkspaceSwitch's pattern out of feature/home into theme/
  SkeletonState / PartialDataState — distinct from the existing ErrorState (§34)
  ActionBottomSheet / ConfirmationDialog / SmartSnackbar — feedback vocabulary (§16)
```
Deliberately not proposed yet: `ProfileRow`, `CalendarBlock`, message-bubble composer surfaces —
those belong to D3/D4/D5 screens specifically and should be designed against real data shapes at
that time, not speculatively now.

## F. Screen → design pattern matrix (adopting your §29 mapping, unchanged — it's already correct)

Today: Dashboard V1 richness + V3 hierarchy + Widgets. This Week: Inbox + Scrum + data-management
filtering (structurally already close — `PrioritiesScreen` already has severity + kind grouping;
needs `FilterBar` and `ActionRow` promotion, not a rebuild). Capacity Runway: V3 analytics + charts
+ expandable rows. Remaining screens per your table — not re-litigated here, your mapping already
matches what I found reading the actual screens (Pipeline Radar/Capacity Runway are real,
backend-fed, chart-shaped data already; Communication genuinely needs the AI-Chat/Email-Compose
treatment, confirmed by reading `CommunicationScreen.kt` — it is currently a plain form).

## G. Dependency review

**No new dependency justified yet.** Checked against §2/§13/§36:
- Radial ring, sparkline, bar, donut: all achievable with `Canvas`/`drawArc`/`drawPath` — already
  proven this session (`ReadinessRing` in `ManagerCommandCentre.kt` uses `Canvas`/`Stroke`/
  `StrokeCap.Round`, zero dependency). Promote that pattern into `theme/`, don't replace it with a
  charting library.
- Blur: `RenderEffect` (API 31+) already implemented in `frostedGlass()` with a correct fallback —
  no blur library needed.
- Motion: `SkillMotion`'s named springs cover `animateContentSize`/`AnimatedVisibility`/
  `AnimatedContent` use cases natively — no motion library needed.
- Material 3 Expressive components (flexible toolbars, button groups) are in the stable
  `androidx.compose.material3` artifact the project already depends on (need to confirm exact
  version pin in `libs.versions.toml` before D1 — flagged, not yet checked this pass).
- **If D1 finds a genuine gap** (e.g. a production-quality calendar month-grid), the evaluation
  criteria in §2 apply then — not speculatively now.

## H. D1 foundation file plan (proposed, not yet built)

```
theme/MetricPrimitives.kt   — MetricCell, HeroMetric, TrendMetric, MiniSparkline
theme/ChartPrimitives.kt    — ChartCard, bar/donut/radial Canvas drawers, tap-to-select
theme/ActionRow.kt          — ActionRow, DataRow, ExpandableDataRow
theme/Timeline.kt           — TimelineItem
theme/FilterBar.kt          — FilterBar, FilterChipGroup, SegmentedSelector (promoted from
                               Version2Workspaces.kt's WorkspaceSelector — consolidate, don't
                               duplicate, per your explicit instruction)
theme/Feedback.kt           — SmartSnackbar, ActionBottomSheet, ConfirmationDialog,
                               SkeletonState, PartialDataState
```
Each compiled and unit-render-tested in isolation before touching a real screen — matches §40's
"compile and test" gate for D1.

## I. Pilot plan — Today, This Week, Capacity Runway

1. **Today:** replace `ManagerCommandCentre`'s local `ComparisonStat`/`PulseTile`/`MiniStat`/
   `ReadinessRing`/`OperationTile` with `HeroMetric`/`MetricCell`/`TrendMetric`/`ActionRow` from the
   new `theme/` files — same data, same navigation callbacks, no functional change, per §38.
2. **This Week:** `PriorityCard` becomes `ActionRow` with a `FilterBar` added above the list
   (severity/kind chips already exist as the `SummaryStrip`; promote to `FilterChipGroup` and make
   them actually filter, which `SummaryStrip` currently does not — it's decorative today).
3. **Capacity Runway:** `WeekBars` becomes a `ChartCard` with tap-to-drill into the filtered Needs
   Allocation list (already scoped in the earlier Plan V2 design, §8 of that document) — same real
   backend data (`_capacity_runway_build`), new shared chart component instead of the screen-local
   `Box` bars.

Screenshot review after all three, per §41, before proceeding to D3 — will request that checkpoint
explicitly once D1+pilot compile and pass tests, rather than assume they look right.

---

**Stopping here per §45. No implementation started.** Ready to proceed to D1 (foundation files)
on your go-ahead — the file list in §H is the concrete next unit of work, each one compiled and
tested before the next, per §40.
