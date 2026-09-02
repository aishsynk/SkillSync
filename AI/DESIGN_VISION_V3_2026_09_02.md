# SkillEdge V3 — Editorial Reinvention

**Date:** 2026-09-02 · **Supersedes:** `DESIGN_VISION_V2_2026_08_11.md` (its IA and
decision-surface principles still hold; V3 is the visual/type/motion layer on top).
**Constraint:** zero business-logic / API / calculation change.

## Direction (operator decision)

- **Editorial premium.** A serif display face carries every screen title, section
  conclusion and hero numeral; a workhorse sans carries everything else. Magazine
  hierarchy: big conclusion → hairline rule → evidence. Generous whitespace.
- **Bold reinvention**, not a re-skin — new layout language per screen, distinctive motion.
- Keep the dark identity, the aurora ground, and blue-means-healthy colour logic.

## Type system

| Family | Files (`res/font/`) | Role |
|---|---|---|
| **Fraunces** (`Display`) | `fraunces_light/regular/medium` | `display*`, `headline*` — screen titles, section headers, hero figures. Light weight + negative tracking so a large size still reads as considered. |
| **Inter** (`Sans`) | `inter_light/regular/medium/semibold/bold` | `title*`, `body*`, `label*` — row leads, prose, controls, tracked caps labels. |

`NumericStyle` = Fraunces Light + `tnum` for hero numerals; `NumericInline` = Inter Medium
+ `tnum` for figures inside rows. Both bundled (SIL OFL, latin subset, ≈0.5 MB total).
Defined in `theme/Type.kt`.

## Motion (`ui/components/Motion.kt`)

- `Motion.Expo` — `CubicBezier(0.16,1,0.3,1)`, the V3 signature reveal curve.
- `Modifier.pressable { }` — 0.97 scale dip + haptic tick on press; no-ops the scale when
  the system animator scale is 0 (reduced motion). Use on every tappable surface.
- `Motion.press()` — tight no-overshoot spring for the above.
- `stagger` retuned to 40 ms step / 240 ms cap.

## Surfaces (`theme/Surfaces.kt`, `theme/Color.kt`)

- `Modifier.frostedGlass()` — real `RenderEffect.createBlurEffect` backdrop sample on API
  31+, falls back to the layered-gradient `glassSurface` below.
- `Modifier.editorialRule(top=…)` — a true 1-px hairline, the section break.
- `Surface0…3` lifted off near-black (`#0A0D14`…) — OLED smear guard.

## Primitives (`ui/components/Editorial.kt`)

- `Figure(value, label, delta, size, tone)` — the one figure primitive. Fraunces numeral,
  Inter caps label, optional ▲/▼ delta.
- `SectionHeader(title, conclusion, trailing)` — Fraunces title, Inter conclusion sentence,
  hairline rule.
- `MicroStat(label, value)` — dense key/value for inside a card.

## Shipped in v3.60.0 (Build 148)

- Foundation: fonts, type scale, motion, surfaces — **applies to every screen** (all copy
  now renders Fraunces/Inter).
- **Trainer (reportee) app** rebuilt editorial end to end — Today (Fraunces greeting + hero
  utilisation figure), Demand, Updates, dock, dialogs.
- **App shell** — Fraunces tab titles + tracked eyebrow, haptic nav.
- **Login** — Fraunces "SkillEdge" wordmark, larger heading.

## Next (follow-up releases, one area each)

Bespoke layout passes — the type system is already in place, these are structure/rhythm:
Today (BriefingHero + bento pulse), People (intelligence bar + compact PersonCard),
Trainer 360 (editorial decision bar), Demand/Allocate (single international tier),
the report screens. Migrate the 8 duplicate figure components and 8 chip components onto
`Figure` / a future `SkillChip`. Add `SkillChartTheme` so the 10 Canvas charts share one style.
