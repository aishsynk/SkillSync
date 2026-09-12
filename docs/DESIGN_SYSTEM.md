# SkillEdge Android — Design System Reference

Single source of truth for the SkillEdge Android visual language (Compose). Parallels the
Phase 0 UI/UX Transformation audit (2026-09-11). App code must resolve all values through
these tokens; raw `Color(0x…)` literals, ad-hoc `fontSize`/`RoundedCornerShape` and emoji
in screens are bugs.

Source of truth lives in `SkillEdge_Android/app/src/main/java/com/example/skillsync/theme/`.

## 1. Principles

1. **One identity.** One command-centre scheme, dark in both system modes. Dynamic (wallpaper)
   colour is deliberately OFF (`Theme.kt`). A screen that reads mostly blue means the delivery
   org is healthy — colour is the top-level status signal.
2. **Colour is never the only carrier.** `Severity` drives sort weight + label + tint together;
   urgency survives greyscale and TalkBack.
3. **Real data only.** A figure without a baseline cannot drive a decision; a sparkline/trend is
   drawn only where the payload carries history. Never fabricate numbers, grades or dates.
4. **Honest states.** LOADING / CONTENT / EMPTY / ERROR / OFFLINE are first-class on every major
   page. Absence of data is stated as absence ("RMS returned no feedback records"), never as a
   clean record.
5. **Restrained motion.** Four springs carry the app; one pulsing element per screen at most,
   reserved for `Severity.Critical`.

## 2. Colour (`Color.kt`)

Blue ramp carries structure + brand; cyan = positive performance; amber/rose = exceptions only.

| Token | Value | Use |
|---|---|---|
| `navy` / `DeepNavy` | `#111827` | hero fills, depth |
| `royal` / `RoyalBlue` | `#1D4ED8` | primary container |
| `azure` / `AzureBlue` | `#2563EB` | gradient partner, links |
| `brand` / `BrandBlue` | `#3B82F6` | primary, active elements |
| `sky` / `SkyBlue` | `#60A5FA` | active glow, links, accents |
| `cyan` / `Cyan` | `#22D3EE` | positive performance |
| `aqua` / `StatusGood` | `#34D399` | good / healthy |
| `warn` / `StatusWarn` | `#FBBF24` | warnings |
| `crit` / `StatusCrit` | `#FB7185` | critical / high risk |
| `ice` / `IceBlue` | `#BFDBFE` | info, tertiary |
| `frost` / `FrostWhite` | `#F8FAFC` | primary body text |

Surfaces (elevation steps, not shadows): `pageBg` = `Surface0 #0B0F19` (clean deep slate page),
`surface1` = `Surface1 #111827`, `cardBg`/`surface2` = `Surface2 #162032` (card),
`surface3` = `Surface3 #1E2B42`.
Semantic text: `bodyText = frost`, `subText = heroMuted = #A8B3C5`,
`labelText = #9AA8BF`, `track = cardBorder = glassBorder = #263345`,
`shimmer = #202C3C`.

Access via `MaterialTheme.skill.<name>` (static CompositionLocal). The Material scheme
(`darkColorScheme`) mirrors the ramp — primary = BrandBlue, secondary = Cyan, tertiary = IceBlue.

> **Decision Closed (Wave 1 Redesign):** Legacy plum (`#8A73C4`) and brass (`#D8B26A`) blooms in
> `Surfaces.kt::AuroraBackground` and champagne hairlines are fully retired. Aurora ground now
> features a restrained deep slate base (`#0B0F19`) with calm brand blue (`#3B82F6`, alpha 0.08)
> and subtle cyan (`#22D3EE`, alpha 0.04) sheens. Operational pages favor clean, high-contrast
> surfaces.

## 3. Type (`Type.kt`)

One clean system sans. `Sans` and `Display` are both `FontFamily.SansSerif` (Fraunces serif
retired — names kept so call sites compile).

| Style | Size / LH | Weight | Letter-spacing |
|---|---|---|---|
| `displayLarge` | 44 / 46 | Light | −1.6 |
| `displayMedium` | 34 / 36 | Light | −1.2 |
| `displaySmall` | 28 / 32 | Normal | −0.8 |
| `headlineLarge` | 24 / 30 | SemiBold | −0.5 |
| `headlineMedium` | 20 / 26 | SemiBold | −0.3 |
| `headlineSmall` / `titleLarge` | 18 / 24 | SemiBold | −0.2 |
| `titleMedium` | 15 / 20 | SemiBold | 0 |
| `titleSmall` | 13 / 18 | Medium | 0 |
| `bodyLarge` | 15 / 22 | Normal | 0 |
| `bodyMedium` | 14 / 20 | Normal | 0 |
| `bodySmall` | 12 / 17 | Normal | 0 |
| `labelLarge` | 14 / 18 | SemiBold | +0.1 |
| `labelMedium` | 12 / 16 | SemiBold | +0.4 |
| `labelSmall` | 11 / 14 | SemiBold | +0.8 |

Rules: every size is a whole sp; figures use `NumericStyle`/`NumericInline` (`tnum`); **nothing
below 11sp** — screens that used 9–10sp badges must use `labelSmall`/`ToneChip`.

## 4. Radius, spacing, layout (`Surfaces.kt`, `DesignSystem.kt`)

- `Radii`: hero 20 · card 16 · kpi 14 · chip 10 · icon 10 dp. Radius decreases with element size.
- `Space`: xs 4 · sm 8 · md 12 · lg 16 · xl 24 · xxl 32 dp.
- `Layout.gutter = Layout.section = Space.lg`; `Layout.readableMax = 760.dp` (`ReadableColumn`
  caps tablet sprawl).

## 5. Surfaces (`Surfaces.kt`)

"Glass" = translucent gradient fill + light top-edge highlight + 1dp hairline (no blur cost on
mid-range). Real `RenderEffect` frost on API 31+ via `frostedGlass`.

- `glassSurface(shape)` — frosted card.
- `accentGlass(accent, strong = false)` — frosted card carrying a status accent (gradient stripe
  down the left edge + wash). `strong` for `Severity.Critical`.
- `heroSurface()` — Deep Navy → Brand Blue, the single loudest fill.
- `editorialRule()` — 1px hairline section break.
- `glowRing(accent)` — focus/active ring.
- `IconSlot(tint, size)` — the app's icon bed (tinted 16% fill + hairline).

## 6. Components (`DesignSystem.kt`)

- `Severity { Critical, Warning, Watch, Info, Good }` — `.weight` for sort, `.label`, `.tint()`.
- `Figure(value, label, size, tint, delta, deltaTint)` with `FigureSize { Hero, Large, Medium,
  Small }` — **every number in the app**.
- `ToneChip(text, tint, solid)` — every pill: status, filter, tag, code.
- `SectionHeading(title, conclusion, trailing)` — headers state a conclusion, not a topic.
- `SkillCard(modifier, severity, strong, padding)` — the one card.
- `StateNote(message, tint)` — empty / error / offline share one shape.
- `Appear(step)`, `pressable(onClick)`, `rememberCriticalPulse(active)`, `ShimmerBox` (from
  `core/ui/Motion.kt`), `SkillAlertDialog`, `Notify` toasts (`LocalNotify`), `Pulse`/`PulseTone`
  (`core/ui/Pulse.kt`).

## 7. Motion (`SkillMotion.kt`)

- `snappy` (0.82 / Medium) — nav, toggles, tabs.
- `gentle` (0.78 / MediumLow) — sheets, cards, reveals.
- `flow` (1.0 / Medium) — container transforms that track the finger.
- `press` (0.9 / High) — press feedback.

## 8. Status mapping (backend → user language)

Raw snake_case / gate codes must never reach the UI. Map at the screen boundary:

| Raw | User label |
|---|---|
| `capacity_bucket` = On Bench / Stretched | Bench / Stretched / Optimal |
| `feedback_risk` High/Medium | High feedback risk / Feedback alert |
| `current_status` teaching_now | DELIVERING / LIVE |
| `lifecycle_state` open/closed | Open / Closed |
| gate codes (`dnc`, `shift_window`, …) | human names via `gateLabel` |
| `off_bands` keys | Leave dates (Title Case words) |
| `sc_id`, `assignment_id`, `demand_id` | "SC 1234", "Assignment #…", course name |
| `delivery_mode` FMAT / ILT | International (FMAT/ILT) vs domestic |

Exceptions counter-example: "× and N more" overflow text, Trainer360 EmptyNotes.

## 9. Accessibility contract

- Every interactive affordance ≥ 48dp tap target (the 62dp AppNavBar is the model).
- `contentDescription` present on every meaningful icon (`null` only for decorative).
- Nothing below 11sp. Colour is never the sole differentiator (stripe + label + weight).
- Text scaling: whole-sp sizes so the system font setting scales predictably.

## 10. Unified Shared Component System (`SkillSyncComponents.kt`)

Wave 1 establishes standard, production-ready primitives:
- `SkillSyncScreen` / `SkillSyncScaffold`: Standardized container with background, insets, and offline banner.
- `SkillSyncTopBar`: Professional application chrome with title, subtitle, back handler, and action buttons.
- `SkillSyncPageHeader`: Screen header with title, context subtitle, and optional primary action.
- `SkillSyncSection`: Section wrapper with header, optional conclusion text, and trailing affordance.
- `SkillSyncCard`: Glass/solid elevated card with optional severity stripe and tactile `.pressable` feedback.
- `SkillSyncMetric`: Tabular numeric figure with label and optional delta badge.
- `SkillSyncStatusChip` / `SkillSyncChip`: Compact status and filter chips with ≥ 44dp touch bounds.
- `SkillSyncPrimaryButton` / `SkillSyncSecondaryButton`: High-contrast corporate buttons with press scaling.
- `SkillSyncTextField` / `SkillSyncSearchBar`: Clean input fields with clear icons and focus borders.
- `SkillSyncEmptyState`: Branded empty state illustration/icon, title, description, and action button.
- `SkillSyncErrorState`: Error state with retry action button.
- `SkillSyncLoadingState`: Standardized skeleton shimmer block.
- `SkillSyncInfoBanner`: Severity-driven alert/notice banner.
- `SkillSyncListItem`: Scannable list row with leading icon, title, subtitle, trailing value, and chevron.

## 11. Global Screen States

The application enforces consistent visual treatments across all screens:
- **LOADING**: Standardized `SkillSyncLoadingState` skeleton with smooth shimmer animation (no bare progress spinners).
- **EMPTY**: Actionable `SkillSyncEmptyState` card with icon, title, honest absence reason, and recovery button.
- **ERROR**: Elevated `SkillSyncErrorState` with `Severity.Critical` accent and retry button.
- **OFFLINE**: Persistent `SkillSyncOfflineBanner` across top indicating cached data status.
- **ALERTS / BANNERS**: `SkillSyncInfoBanner` driven by `Severity` (`Critical`, `Warning`, `Watch`, `Info`, `Good`).

## 12. Communication Intelligence Context Policy

**Architectural Rule**: `AVAILABLE DATA ≠ MESSAGE CONTENT`

Available SkillSync data is evidence from which relevant context may be selected. It must NOT automatically be inserted into messages.

Pipeline:
`Purpose → Recipient Relationship → User Message → Entity → Context Selector → ONLY verified facts → Policy → Review → Manual Send`

### Sensitivity & Disclosure Rules (`CommunicationContextFilter`)
1. **`TRAINER_SUMMARY_EXTERNAL`**:
   - *Allowed*: trainer name, primary skills, certifications, approved readiness, public bio, experience.
   - *Denied*: private manager notes, raw negative feedback, internal utilization margins, retention risks.
2. **`BATCH_INVITATION`**:
   - *Allowed*: course code, course title, schedule dates, delivery mode, timing, timezone, location.
   - *Denied*: client billing codes, financial IDs, client rate cards, gross profit margins.
3. **`EXTERNAL_STAFFING_REQUEST`**:
   - *Allowed*: required technology, course, dates, mode, location, candidate criteria.
   - *Denied*: target margins, internal rate calculations, client confidential revenue numbers.