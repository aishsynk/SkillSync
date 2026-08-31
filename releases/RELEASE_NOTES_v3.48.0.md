# SkillEdge Production Release v3.48.0 (Build 134)

- **Release Date**: 2026-08-31
- **Git Commit**: `7100c58` (`feat: genuine weekly/monthly messages + Teams/Viber rewrite engine (v3.48.0, Build 134)`)
- **Version Code**: `134`
- **Version Name**: `3.48.0`
- **APK**: `SkillEdge-v3.48.0.134.apk` (package `com.example.skillsync`, signer SHA-256 `c6868b14bec9982642d908a5d4f535116daaf4e932a1e5ac27ed957671a41808`, installs directly over `v3.47.0.133` without uninstall; user data intact)
- **Deployed to**: GitHub Release `v3.48.0.134` (Android CI/CD) + Render `https://skilledge-backend-fpcl.onrender.com` (auto-deploy on push to `main`)

## Why this release was published

Managers reported two connected failures:
1. **Weekly/monthly coaching text was not genuine.** The same generic behavioural sentences ("pacing & articulation is the primary growth area", "hesitation and slight panic", `Goal → Steps → Verify` guidance for every trainer) appeared for every person, plus bullet-pointed standpoints with `•`/`—` that violate the Teams/Viber house style. Trainer 360 built its 3-part evaluation on-device from the same fiction, so weekly, monthly and 360 never agreed.
2. **No Teams/Viber house-style rewrite of `[User Message]` / `[My Message]`.** The brief requires: at least one of `[User Message: …]` / `[My Message: …]`, Hinglish-tolerant, intent/urgency/firmness/time/assignment context understood, and output always `Greeting` → `Body` → `Closing` ≤1000 chars, no emojis/bullets/hyphens, italics only for names, **bold** only for the key action, __underline__ only for time refs, course codes like `AZ-305` preserved. Managers needed this with NLP + agentic help and offline fallback, not an empty text box.

Publishing now was required because `v3.47.0` left these user-facing messages unverified; a manager could not send a reportee message that was both attributable to RMS data and house-style compliant. `v3.48.0` closes that product gap without touching billing, sales or finance (still stripped at the backend boundary).

## What changed

### 1. Evidence-only weekly and monthly messages (backend + Android)
- **Backend `backend.py`:** Weekly `standpoint_note` stripped of `•`/`—`/`-` and generic filler; now `Standpoint:` / `Learner rating 90 day:` (`_trainer_feedback_detail`, RMS key 244, email-filtered, ≥45-char quotes with session/trainer/content signal) / `Immediate Focus:` lines are evidence-only. `GET /api/data/trainer-360` now ships `manager_evaluation` computed server-side via `_generate_manager_evaluation` (utilisation, learner rating/quotes, named cert gaps from `_exam_policy`/`_CERT_CATALOG`, HR/negative counts, Qubits) so Trainer 360, weekly and monthly agree. A dimension with no evidence says so (`no feedback on record`, `no improvement areas are flagged from evidence this cycle`, `none. Steady, no flags this week`).
- **Android `ui/report/WeeklyMessage.kt`:** Extended `ReporteeSignals` with `learnerRating`/`learnerRatingCount`/`learnerRecentDate`/`hrNegativeCount`; rewrote `composeManagerStandpointNote` to the same bullet-free evidence-only lines, removing `Theoretical baseline active`, `Pacing & Articulation focus active`, and the canned `Goal → Steps → Verify` guidance. `ui/report/WeeklyReportViewModel.kt` now parses `learner_rating`/`learner_rating_count`/`learner_feedback` from the weekly payload.
- **Android `ui/trainer/Trainer360Screen.kt`:** Deleted fabricated `strength`/`improvement`/`verdict` strings. `ManagerEvaluationCard` now renders `data["manager_evaluation"]` (`strength`/`area_of_improvement`/`other_feedback`/`trajectory`/`mock_summary`/`formatted_text`/`learner_feedback`) with an honest `"no evaluation on record"` fallback, threaded via `Trainer360Content` (`identity` + `manager_evaluation`).

### 2. Deterministic Teams/Viber rewrite engine — `[User Message]` / `[My Message]` → house style
- **Spec implemented verbatim:** Inputs are `[User Message: …]` and/or `[My Message: …]` (at least one required). If `user_message` is present it is the primary intent; if empty, `my_message` drives; if both, the writer acknowledges the user context and foregrounds the manager intent, rewriting on *meaning* not literal wording. Hinglish (`kal`/`parso`/`jaldi`/`thoda`/`bhejo`/`plz`/`sir` etc.), informal phrasing, intent, authority, firmness, urgency, time sensitivity, assignment/delivery context and sender–receiver relationship are interpreted deterministically (no LLM required; LLM seam preserved).
- **House style enforced mechanically:** `Greeting` on its own line (`Hello _First_,` italicised in Teams, or `Hello team,`), `Body` on next line (simple professional English, complete sentences, full word forms; no emojis/bullets/hyphens/dashes, at most one **bold** key action and up to two __underlined__ time refs, italics only for names, no combined styling unless unavoidable), `Closing` on its own line with light emphasis (`_Please confirm once done._` / `_Thank you for your continued effort._` etc. chosen by tone `urgent`/`corrective`/`appreciative`/`advisory`/`professional`), total `≤1000` chars with sentence-boundary trimming. Course codes like `AZ-305` are held aside (`\u0001`) so the hyphen survives the prose hyphen strip.
- **Twin engines in lockstep:** Python `backend.py::_compose_rewritten` (+ helpers `_normalize_hinglish`, `_message_sanitise`, `_detect_intent`, `_professional_rephrase`, `_trim_message_to_limit`) and Kotlin `ui/report/MessageRewriter.kt::compose` implement identical rules. New `POST /api/v2/message/rewrite` (`RewriteRequest`/`RewriteResponse` in `SkillEdgeApi.kt`) is the cloud seam; the Android studios try it first with `evidence_context` (cert gaps, learner rating, utilisation as one supporting evidence sentence) and fall back locally to `MessageRewriter.compose` so the studio works offline and the two sides never diverge.
- **Studios wired:** `ui/report/WeeklyReportScreen.kt` — team broadcast (top card) and per-reportee `WeeklyReporteeLiveCard` (expanded) now expose two house-style inputs (`[User Message: …]` Hinglish-tolerant, `[My Message: …]` manager intent) + `Rewrite for Teams` (backend first, offline fallback), live preview (`SelectionContainer`), `Copy Broadcast`/`Copy Rewritten` and `Send`. `ui/report/HrMonthlyReportScreen.kt` — per-reportee expanded now embeds the same dual-input studio (evidence `topCourses`/`utilisationPct`) with preview, `Rewrite for Teams`, `Copy Feedback`/`Copy Rewritten` and `Share Review` (share prefers rewritten when present). Both use `rememberCoroutineScope` + `scope.launch` and degrade gracefully offline.

### 3. Tests and versioning
- `app/src/test/ui/WeeklyMessageTest.kt` updated: standpoint assertions now expect bullet-free evidence lines (`**Standpoint:**`, `**Learner rating 90 day:**`, `AZ-104` + `certification exam`, no `•`/`Mock & Readiness`/`Goal → Steps`), plus `rewriter_handlesHinglishUrgency`, `rewriter_requiresAtLeastOneInput`, `rewriter_isTeamGreeting`, `rewriter_preservesCourseCodesThroughSanitise` (2 hyphens = 2× `AZ-104` occurrences, not prose).
- `app/build.gradle.kts` `versionCode 133 → 134`, `versionName "3.47.0" → "3.48.0"`. Package and signing identity unchanged.

## Why the version number was increased

Minor version `3.47.0 → 3.48.0` (and `versionCode 133 → 134`) because this is a new user-facing capability (evidence-only coaching + first-class rewrite studio) and a net-new API (`POST /api/v2/message/rewrite` + `GET /api/data/trainer-360` `manager_evaluation`), not a patch. The increment guarantees the Play/binary Store and Android identify it as an upgrade and the deterministic release keystore (`keystore/skillsync-release.jks`, `c6868b14…1808`) lets the new APK install directly over `133` — no uninstall, no data loss.

## What users gain

- **Managers:** Weekly standpoints, weekly per-reportee messages, monthly 3-part evaluations and Trainer 360 verdicts now read as attributable facts (learner `4.2/5` + dated quote + named cert gap) not fiction, and all three views agree. For any reportee or the whole team, a manager can paste a Hinglish/informal `[User Message]` and/or a rough `[My Message]` and get a single-tap, house-style Teams/Viber message that is ready to copy or send — with correct greeting, one **bold** action, __underlined__ dates, and offline fallback.
- **Reportees:** Messages they receive are shorter, respectful, and explain *why* (evidence) and *what* (**bold** expectation + __when__), in proper English without emojis or bullets.
- **Operators:** `trainer-360` 502 risk is unchanged from `v3.46.2` (`gthread --timeout 120`), and the rewrite is deterministic and test-covered, so support load from "mystery prose" drops.

## Release history

| Version | Build | Commit | What shipped |
|---|---|---|---|
| v3.48.0 | 134 | `7100c58` | Genuine weekly/monthly messages + Teams/Viber rewrite engine |
| v3.47.0 | 133 | `43334bc` | Data-integrity pass — persistent state, no fake team, honest Trainer Index |
| v3.46.2 | 132 | `d5fa65e` | `gunicorn --timeout 120 / gthread` to end trainer-360 502 |
| v3.46.1 | 131 | `409f69a` | Warm `/api/data/trainer-360` to stop 502 on cold Render |
| v3.46.0 | 130 | `1d2863e` | Partial-first endpoints, offline-first reports, always-on monitoring, genuine feedback |

## Validation (local, pre-push)

- Android `:app:compileDebugKotlin` `BUILD SUCCESSFUL` (2m02s), `:app:testDebugUnitTest` **153 tests passing** (previously 2 failing, now green with 4 new rewriter tests), `:app:lintDebug` clean.
- Backend `python -m pytest tests/ -q` **169 passed** (no regression; new endpoint additive; weekly/monthly structure checks still pass).
- Weekly `standpoint_note` verified bullet/hyphen/em-dash free; course codes (`AZ-305`, `AZ-104`) survive sanitise; Hinglish samples (`sir kal ka batch hai, thoda help chahiye, please jaldi bhejo material` + `Please review and share the AZ-305 material by tomorrow` → `Hello _Abhinav_,` … `**Please review…**` + `__tomorrow__`) produce exactly one greeting, one bold, ≤1000 chars.

## Compatibility and upgrade

- `applicationId` `com.example.skillsync` unchanged.
- Signing: `keystore/skillsync-release.jks` (`storePassword`/`keyPassword` `ZKawzv4nwYFf4OPGeeHe5yz3`, `keyAlias` `skillsync-release`), `v1`+`v2`+`v3` signing, `debug` and `release` share the same `release` config — guarantees `SkillEdge-v3.47.0.133.apk` → `SkillEdge-v3.48.0.134.apk` is an in-place update on every Android device. **Any `INSTALL_FAILED_UPDATE_INCOMPATIBLE` is a release blocker.**

## Operator notes

- Render persistent disk for `skilledge-state` still requires confirmation if the service is a manually-created `-fpcl` instance (Settings → Disks → Add Disk, name `skilledge-state`, mount `/var/data`, 1 GB, `SKILLEDGE_STATE_DIR=/var/data`) — unchanged from `v3.47.0`.
- Rollback: `git revert 7100c58` + push rebuilds `133`; no data migration occurred so no manual data fix is needed.
