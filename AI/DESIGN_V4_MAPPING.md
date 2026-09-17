# Design V4 UI Rebuild Mapping

## References Actually Inspected
- https://seantheme.com/color-admin/admin/html/index.html (Color Admin V1 Dashboard)
- https://seantheme.com/color-admin/admin/html/index_v2.html (Color Admin V2 Dashboard)
- https://seantheme.com/color-admin/admin/html/index_v3.html (Color Admin V3 Dashboard)
- https://seantheme.com/color-admin/admin/html/widget.html (Widgets)
- https://seantheme.com/color-admin/admin/html/bootstrap_5.html

## 1. Global Manager Shell
* **REFERENCE:** Color Admin Dashboard Header (`#appHeader`)
* **EXACT VISUAL PATTERN:** 
  - Ultra-compact `.app-header` (50px height).
  - Left-aligned `.brand-logo` (no padding abuse).
  - `.menu-item` icons are small (16-20px), often using Duotone icons (like `iconify-icon`).
  - Dropdown badges (`.badge`) overlapping the icons tightly.
  - Avatar is small (30px), bordered, perfectly circular (`.user-img`).
* **WHERE IT IS USED IN SKILLSYNC:** `MainScreen.kt` (Global Top Bar)
* **COMPOSE IMPLEMENTATION:** `TopAppBar` with height bounded to 52dp. Logo forced to 28dp. Secondary contextual line small/semi-bold. `IconButton` bounded to 36dp. Drop shadows removed for a flat enterprise look.

## 2. KPI WIDGETS
* **REFERENCE:** Color Admin `widget-stats` (index.html / widget.html)
* **EXACT VISUAL PATTERN:**
  - Rich colored background (`bg-teal`, `bg-blue`) or stark white with shadow.
  - Title (`.stats-info h4`) is small, uppercase/semibold, low opacity.
  - Value (`.stats-info p`) is very large, bold (`fw-bold`, `fs-24px`).
  - Right-side icon (`.stats-icon`) is large, positioned absolutely, low opacity.
  - Bottom progress/delta (`.stats-link` / `.stats-progress`) adds a distinct footer or 2px tracking bar.
* **WHERE IT IS USED IN SKILLSYNC:** Today Screen (`PulseTile`, `MiniStat`)
* **COMPOSE IMPLEMENTATION:** Re-engineered `PulseTile` into a distinct `KpiWidget` composable. 12dp corner radius (not large cards). Used `Box` to place an oversized, 0.15-alpha vector icon in the `Alignment.CenterEnd`. Added a 2dp height `LinearProgressIndicator` at the exact bottom edge.

## 3. ANALYTICS PANEL
* **REFERENCE:** Color Admin `panel` (chart-apex.html / index_v2.html)
* **EXACT VISUAL PATTERN:**
  - Distinct `.panel-heading` with flat background, flex-between layout, small semibold `.panel-title`.
  - Content `.panel-body` has sharp 0-padding around charts, or rigid `p-15px` padding.
  - High contrast chart legend.
* **WHERE IT IS USED IN SKILLSYNC:** Team Analytics Row (Readiness / Capacity Distribution)
* **COMPOSE IMPLEMENTATION:** Re-engineered `TodayPanel` into `AdminPanel`. Added explicit header bar with `Surface(color = sk.cardHeaderBg)`. Reduced internal padding. Donut charts have a floating legend on the right.

## 4. INBOX / LIST GROUP (Needs You Today)
* **REFERENCE:** Bootstrap 5 `.list-group`, `.list-group-item`, `.d-flex`
* **EXACT VISUAL PATTERN:**
  - Items are separated by 1px solid borders, NOT wrapped in individual floating cards.
  - Left-side indicator (status dot or thick colored left border rail).
  - Dense text (Title + Subtitle + Timestamp).
* **WHERE IT IS USED IN SKILLSYNC:** "Needs You Today" priority inbox.
* **COMPOSE IMPLEMENTATION:** Removed `AttentionCard` wrapping. Items are now `Row`s in a standard Column, separated by `HorizontalDivider(thickness = 1.dp)`. Added a 3dp wide colored `Box` on the start edge for severity.

## 5. TIMELINE (Today's Operations)
* **REFERENCE:** Color Admin `.timeline` (index.html)
* **EXACT VISUAL PATTERN:**
  - Vertical grey track (`.timeline:before`).
  - `.timeline-time` on the left.
  - `.timeline-icon` (circular dot) sitting exactly on the track.
  - `.timeline-content` card on the right.
* **WHERE IT IS USED IN SKILLSYNC:** "Today's Operations" / Delivery Feed.
* **COMPOSE IMPLEMENTATION:** Created `AdminTimelineItem`. Fixed 50dp width for time/status. Drawn vertical line using `drawBehind`.

## 6. ACTION MATRIX (Operations)
* **REFERENCE:** Color Admin `.widget-list` or grid tiles.
* **EXACT VISUAL PATTERN:**
  - Compact square/rectangular tiles with a central icon and a short label.
  - Tightly packed with 8px/10px gaps (`.g-2`).
* **WHERE IT IS USED IN SKILLSYNC:** Operations & Comms sections.
* **COMPOSE IMPLEMENTATION:** 2-column `LazyVerticalGrid` / weighted Rows. Tile padding reduced to 8dp. Icon + Label + Micro context line.
