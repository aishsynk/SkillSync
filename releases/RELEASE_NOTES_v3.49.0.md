# SkillEdge Production Release v3.49.0 (Build 135)

- **Release Date**: 2026-08-31
- **Git Commit**: `c0c4bf4` (pushed as `feat: demand detail share now generic + TOC link, manager-view everywhere (v3.49.0, Build 135)`)
- **Version Code**: `135`
- **Version Name**: `3.49.0`
- **APK**: `SkillEdge-v3.49.0.135.apk` (package `com.example.skillsync`, signer SHA-256 `c6868b14bec9982642d908a5d4f535116daaf4e932a1e5ac27ed957671a41808`, installs directly over `v3.48.0.134` without uninstall; user data intact)
- **Deployed to**: GitHub Release `v3.49.0.135` (Android CI/CD) + Render `https://skilledge-backend-fpcl.onrender.com` (auto-deploy on push to `main`; no backend change required — TOC already in `unallocated` `toc_url`/`course_url` and `v2/course/curriculum` `contentUrl`)

## Why this release was published

`v3.48.0` made weekly/monthly and Trainer 360 messages genuine and house-style, but the **Demand Detail → Share un-allocated batch** path was still incomplete from a manager's chair: the share text was generic and house-style, yet it omitted the **course outline (TOC) URL** so a reportee could not tap through to check whether they can actually cover the content and prerequisites. The brief also requires that *any and everywhere* a message is produced — demand share, weekly team broadcast, per-reportee weekly progress, monthly HR per-reportee — it must be **(a) generic (no hardcoded course), (b) based on real API data, and (c) in the `[User Message]`/`[My Message]` → Teams/Viber house style, from the manager's point of view**. `v3.49.0` closes that last gap.

## What changed

### 1. Demand Detail share is now generic, data-driven and TOC-aware
- **Android `ui/batch/BatchShare.kt`:** `Build` now appends `The course outline is at <tocUrl> . Please review it and confirm whether you can cover the content and prerequisites.` when `Batch.tocUrl` is present. The URL is held verbatim (hyphens/slashes preserved) — the only field allowed to contain them in the house style — and is rendered as plain text (`composeMessage` `*bold*`/`_italic_` plain) and HTML (`<br>` + auto-linkable URL) for clipboard `newHtmlText`, so pasting into Teams/Outlook/Viber keeps the link tappable. No emojis/bullets/dashes as separators; `≤1000` chars.
- **Android `ui/batch/BatchDetailScreen.kt`:** `shareBatch` now derives `effectiveToc` as `operationalContext?.course?.contentUrl` (verified `v2/course/curriculum` `contentUrl`/`latestVersion`) → `batch["toc_url"]` → `batch["course_url"]` → `""`, and `remember`s on `effectiveToc` so the share always carries the best available outline. `BatchShare.Batch(tocUrl = effectiveToc)` therefore never shows a broken placeholder and never omits a real link. The headline share (`Message` action) and per-candidate `Message` (addressed `Hello _First_,` vs `Hello team,`) both flow through the same generic builder.

### 2. Manager-view everywhere — audit and hardening
- **Verified that every message entry point is now evidence-only + prompt-compliant from the manager's chair:**
  - `WeeklyReportScreen` team + per-reportee (`MessageRewriter` + `evidence_context` cert gaps/learner rating/util, `rememberCoroutineScope`, backend `POST /api/v2/message/rewrite` → offline `MessageRewriter.compose` fallback)
  - `HrMonthlyReportScreen` per-reportee (`structured_feedback` `strength`/`area_of_improvement`/`other_feedback` from `GET /api/v2/hr/monthly-report`, same dual-input studio)
  - `Trainer360Screen` `ManagerEvaluationCard` (`data["manager_evaluation"]` from `GET /api/data/trainer-360`, not client-fabricated)
  - `BatchDetailScreen` demand share (this release, `BatchShare` + `effectiveToc`)
- No hardcoded course names, no `Coming Soon`/`N/A`/zero-value placeholders, no `Total Fee`/`Currency` leaking, no silent truncation (`ClipData.newHtmlText` vs old `viber://forward`).

### 3. Versioning
- `app/build.gradle.kts` `versionCode 134 → 135`, `versionName "3.48.0" → "3.49.0"`. Package and signing identity unchanged.

## Why the version number was increased

Patch `3.48.0 → 3.49.0` (and `versionCode 134 → 135`) because this is a user-visible product completion: the last message surface (demand share) now meets the same generic + data + prompt + TOC contract as weekly/monthly. The increment guarantees Android treats it as an upgrade and the deterministic release keystore (`keystore/skillsync-release.jks`, `c6868b14…1808`) lets `SkillEdge-v3.48.0.134.apk → SkillEdge-v3.49.0.135.apk` install directly over without uninstall.

## What users gain

- **Managers (Demand Detail):** Tapping `Message` on any un-allocated batch now produces a single-tap, manager-voiced, generic share that states the real course, window (`from 12 Sep to 16 Sep`, `09:00 - 17:00` underlined), mode/language/participants/location, **and the tappable course outline URL** — so a reportee can self-check coverage before replying. Editing before copy/share is still possible (`MessagePreviewDialog` 1000-char counter, `Copy` as primary).
- **Managers (everywhere):** Whether the share is a demand batch, a weekly team broadcast, a per-reportee weekly progress note, or an HR monthly evaluation, the text is now consistently: greeting on one line, body with one **bold** action and __underlined__ dates, closing with light emphasis, ≤1000 chars, `AZ-305` hyphens preserved, Hinglish-tolerant, and attributable to RMS data — no more drift between screens.
- **Reportees:** Every message they receive (demand or progress) explains *why* (real data) and *what* (**bold** expectation) and *when* (__when__) and, for demand, *where to check* (TOC link).

## Release history

| Version | Build | Commit | What shipped |
|---|---|---|---|
| v3.49.0 | 135 | `c0c4bf4` | Demand share TOC + manager-view everywhere |
| v3.48.0 | 134 | `7100c58` | Genuine weekly/monthly + Teams/Viber rewrite engine |
| v3.47.0 | 133 | `43334bc` | Data-integrity pass — persistent state, no fake team, honest Trainer Index |
| v3.46.2 | 132 | `d5fa65e` | `gunicorn --timeout 120 / gthread` to end trainer-360 502 |
| v3.46.1 | 131 | `409f69a` | Warm `/api/data/trainer-360` to stop 502 on cold Render |
| v3.46.0 | 130 | `1d2863e` | Partial-first endpoints, offline-first reports, always-on monitoring, genuine feedback |

## Validation (local + CI/CD, post-push)

- Android `:app:compileDebugKotlin` `BUILD SUCCESSFUL` (33s), `:app:testDebugUnitTest` **153 tests passing** (BatchShare TOC path covered via integration, no new failures), `pytest tests/ -q` **169 passed** (unchanged).
- CI/CD (`main` push trigger): `SkillEdge-v3.49.0.135.apk` built and published to GitHub Release `v3.49.0.135` (`c0c4bf4`), signed with `keystore/skillsync-release.jks` (`c6868b14...1808`), installs over `v3.48.0.134`.
- Render auto-deployed on push to `main` — `GET /healthz` returns `ok` (`version 6.1.0`); no backend change required (TOC already served from `unallocated` `toc_url`/`course_url` and `v2/course/curriculum` `contentUrl`).
- Demand share probe (manual): `Batch(courseName="AZ-104 Azure Infrastructure", startDate="12 Sep 2026", endDate="16 Sep 2026", sessionTime="09:00 - 17:00", tocUrl="https://koenig-solutions.com/toc/AZ-104.pdf")` → `composeMessage` contains `The course outline is at https://koenig-solutions.com/toc/AZ-104.pdf` (verbatim, hyphens preserved), `*mark your skill in RMS at level 4 or below*`, `Hello Team,` + `Thank you.` 3 blocks, no `•`/`—`, `≤1000` chars, URL tapable in HTML clipboard.
- Existing weekly/monthly probes still green: `standpoint_note` bullet-free, course codes survive, Hinglish `sir kal ka batch hai` → `Hello _Abhinav_,` house-style.

## Compatibility and upgrade

- `applicationId com.example.skillsync` unchanged.
- Signing: `keystore/skillsync-release.jks` (`storePassword`/`keyPassword` `ZKawzv4nwYFf4OPGeeHe5yz3`, `keyAlias skillsync-release`), `v1`+`v2`+`v3` signing, `debug` and `release` share the same `release` config — guarantees `SkillEdge-v3.48.0.134.apk → SkillEdge-v3.49.0.135.apk` is an in-place update on every Android device. **Any `INSTALL_FAILED_UPDATE_INCOMPATIBLE` is a release blocker.**

## Operator notes

- No backend deploy required for this release (TOC already served); Render disk `skilledge-state` still requires confirmation if `-fpcl` is not a Blueprint (`SKILLEDGE_STATE_DIR=/var/data`) — unchanged.
- Rollback: `git revert <this>` + push rebuilds `134`; no data migration so no manual fix needed.
