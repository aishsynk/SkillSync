# SkillEdge Design North Star — V4

**Date:** 2026-09-02 · **Supersedes:** V2 (2026-08-11 IA) + V3 (2026-09-02 editorial identity).
**Constraint unchanged:** zero business-logic / API / calculation change — V4 is how the app
*moves, feels and anticipates*.
**Published visual version:** https://claude.ai/code/artifact/ff2537b9-13ed-4506-a0ae-a9663a448d37

## North star

> A daily intelligence briefing you operate with your thumb — it tells you what changed,
> what it means, and what to do, before you've finished your coffee.

Not a database with a dark theme. Not a dashboard that reports. A briefing that has already
done the thinking and hands you the decision.

## Principles (each testable)

1. **The screen has a thesis** — every surface opens with one Fraunces sentence that *is* the
   conclusion; the chart is evidence for a claim already absorbed. *Write the sentence first.*
2. **Urgency is physical** — a critical item is bigger, first, stripe-edged, and the only
   thing that moves. *Cover the text — can you still point to what needs you?*
3. **Every number carries its meaning** — value + baseline + delta + direction-is-good.
   *A figure with no baseline is a bug.*
4. **Motion is physics, not decoration** — springs you can catch mid-flight; the tapped thing
   becomes the next screen; nothing you wait out. *Grab any transition halfway.*
5. **The chrome earns its keep or leaves** — nav hides on scroll, actions float at thumb
   height, the title collapses into the bar. *How much of the screen is the answer vs the app?*

## Signature moments (borrowed, re-pointed)

| Moment | Borrows from | What it is |
|---|---|---|
| **The Pulse** | Dynamic Island / Live Activities | A stadium capsule on every screen carrying the one number that matters now; tap → full briefing; breathes during refresh. The app's heartbeat + home button. |
| **The collapsing brief** | iOS large-title | Today opens with a 34pt Fraunces briefing that tracks your finger down to a 17pt masthead line — the conclusion always on screen, sized to the moment. |
| **The decision loop** | Container transform + predictive back | Person card *becomes* the Trainer 360 header *becomes* the allocate candidate row. Dashboard → person → allocate → done never loses place; back-drag previews the destination. |
| **Haptic conscience** | Feedback generators / VibrationEffect | Distinct taps for distinct meanings — incl. a low warning buzz on finger-down when you're about to allocate someone on approved leave, *before* you commit. |
| **Monday 9:04** | Live Activities / anticipatory UI | Open it Monday and Today has assembled the week: "6 batches land, 2 uncovered, Priya clears Thursday." Never interrupts; just ready. |
| **Numbers that arrive** | Material expressive motion | KPIs count up from zero, rings draw, delta chips flip in last; refreshed values cross-fade, never snap. |

## Systems (build foundation-up)

1. **Motion** — 3 springs (snappy .82 / gentle .78 / flow=critically-damped input-tracking).
   Interruptible. One motion per screen. Container transform is the default transition.
   Reduced-motion = a different honest path (120ms cross-fade), not "slower". <400ms,
   transform+opacity only.
2. **Haptics** — 7-signal vocabulary, every one paired with a visual, honours system haptic-off:
   segment-tick (tab), soft-context-click (press), double-tick (skill saved), confirm-pop
   (RMS write), reject-buzz on finger-down (allocate onto leave), firm-tick (refresh
   threshold), double-heavy (critical alert).
3. **Material & depth** — 4 planes: ground (aurora, drawn once) / content (flat, scrolls) /
   raised (real blur — RenderEffect API 31+ / UIVisualEffectView) / modal (scrim + card from
   somewhere). Blur = "above", cap 3 on screen, hero+section shells only. Radii 10/14/16/20,
   decreasing with the element. Pulse = full stadium.
4. **Typography** — Fraunces with `opsz` driven by size, SOFT ~30, WONK off; Inter with
   `cv05` + `tnum` everywhere; Fragment Mono for anything the machine said verbatim. Title
   collapses (iOS). Numerals are a style: Fraunces Light tabular −1sp. Dynamic Type to 200%,
   review at 130%.
5. **Colour** — blue is a *verdict* not a brand (mostly-blue screen = healthy); brass for
   earned moments only; warm ground so signal pops. Semantic scale redundant with shape
   (colour + stripe + icon + sort). Light mode = "the briefing, printed" (warm paper, not an
   inversion). Charts: one series ramp, target corridors, emphasised endpoint, 8% area fill.
6. **Iconography** — one grid-drawn set, stroke weight matched to adjacent text size (SF
   Symbols ↔ SF Pro). Icons are nouns, labels are verbs. One illustrative mark: the 40pt
   globe medallion, international tier only.
7. **Sound** — 3 cues <200ms tuned to the haptics (tick / chime / thud), muted with system,
   off by default.

## Spatial model

5 tabs are *places* — **Today** (what needs me) · **People** (who I have) · **Plan** (what's
coming) · **Work** (live calendar) · **Search** (everything, one field). Depth is the second
axis: every place pushes *into* detail (Today → attention card → person → allocate → done).
The Pulse is orthogonal — always docked, one tap home. No hamburger, no nested tabs, no
"more"; if it doesn't fit 5 places + a depth stack it's in Search.

## Screen theses

- **Today** — *Is my org healthy, and what must I touch today?* Collapsing brief + readiness
  ring → attention strip (≤3, action on card) → 4-KPI pulse row (spark + delta) → capacity
  bar with its interpretation as headline → demand summary (one CTA) → Explore (collapsed).
- **People** — *Who needs me, who's free, who can teach X?* Intelligence bar (search + 3
  saved lenses) → roster ranked by attention, severity edge → compact PersonCard. Capability
  is a lens, not a tab.
- **Trainer 360** — *Can they take this batch, what do I do about them?* Sticky header
  (arrived as the PersonCard) with decision bar + Allocate / Raise action; 4 tabs (Now /
  Capability / Performance / Actions).
- **Plan** — *Where's the high-value work, can I cover it?* Coverage headline first;
  international = a card class (brass ribbon, globe medallion, elevated glass, ambient sheen),
  pinned to Global Priority; pipeline by date; blockers tint the card; recommendation shows
  reason + candidate not a bare score.
- **Allocate** — *Right person for this batch?* Demand row + ranked candidates with evidence,
  the person you came from pre-selected, Confirm bottom-anchored. The on-leave haptic fires here.
- **Trainer view** — the same app, a team of one. Today = their readiness + week; Plan = open
  batches they can raise a hand for; "Mark my skill" the one write (>4 → manager notification).

## Disciplines

- **Adaptive** — one codebase / `WindowSizeClass`. Phone: single column + bottom nav.
  Foldable/tablet: two panes, Pulse top-right, predictive-back = pane collapse. Landscape
  phone: brief starts collapsed.
- **Accessibility** — ordered TalkBack/VoiceOver tour matching thesis→evidence (Pulse
  announces the number first); contrast ≥4.5:1 on the *actual* surface; every status has a
  non-colour carrier; ≥48dp targets; reduce-motion is a designed path.
- **Performance** — Today's first meaningful paint <1s from warm cache (brief + ring before
  the fan-out); no dropped frames on a 3-year-old mid-range phone; blur capped at 3;
  skeletons match the final footprint exactly.

## Roadmap (each shippable)

1. The Pulse + collapsing brief (Today only) — prove the model
2. The decision loop — container transforms + predictive back
3. Haptic vocabulary + motion tokens — app-wide
4. Demand as opportunity — international card class, coverage-first, date pipeline
5. Light mode + adaptive panes
6. Anticipation + literacy — Monday brief, score explainers, saved lenses, memory
