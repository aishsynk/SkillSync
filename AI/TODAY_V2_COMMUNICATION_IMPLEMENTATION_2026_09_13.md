# Today V2 — Communication Command Centre: Implementation & Verification (2026-09-13)

Implemented and verified this session. **Not committed, not pushed, no version bump, no release**
— stopping here per instruction for review.

## 1. Engine-boundary audit result

| Function | Context selection | Fact selection | Planning | Generation | Validation | Formatting | Role |
|---|---|---|---|---|---|---|---|
| `composeTeamMessage()` / `composeReporteeMessage()` (`WeeklyMessage.kt`) | Yes (via `TeamSignals`/`ReporteeSignals`) | Yes (severity-branch logic) | Yes (which branch fires) | Yes (hand-written prose per branch) | Yes (`sanitise()`, char limit) | Yes | **Full independent pipeline — duplicates the shared engine end to end** |
| `composeManagerStandpointNote()` (`WeeklyMessage.kt`) | Partial | Yes | Yes | Yes (templated lines) | Partial | Yes | Same duplication, smaller surface; already live as **fallback only** |
| `CommunicationService` (backend) / Android mirror | Yes | Yes | Yes | Yes | Yes | Yes | **The one authoritative pipeline** |
| `CommunicationViewModel`/`CommunicationScreen` | — | — | — | Calls backend `generate`, falls back to local `CommunicationGenerator` only on network failure | Renders validation result | — | UI + fallback wiring, not a second engine |
| `/api/v2/message/compose` (backend) | Yes (warm weekly/HR cache lookup) | Yes (`message_weekly`/`message_monthly` fields, or `_communication_service.generate` when `my_message` given) | Delegates to `CommunicationService` when free text present | Delegates | Delegates | Delegates | A **cadence-shaped entry point into the same authoritative service**, not a parallel pipeline |
| HR Monthly "Rewrite Studio" (`HrMonthlyReportScreen.kt`) | Uses `/api/v2/message/compose` (same as above) + `MessageRewriter.compose` as an offline fallback | — | — | — | — | — | Reuses the authoritative path; `MessageRewriter` is a fallback, not a second generator |

**Decision applied:** `composeTeamMessage()`/`composeReporteeMessage()` are **not** wired to anything new this session — they remain dead code, exactly as found, per your explicit correction not to revive a duplicate pipeline. `composeManagerStandpointNote()` stays exactly where it was (fallback only). Every new entry point built this session routes through `CommunicationScreen`/`CommunicationService`, or (for Weekly/Monthly) to the existing report screens that already call `/api/v2/message/compose`.

## 2. History/status audit result

Read `CommunicationScreen.kt`/`CommunicationViewModel.kt`/`services/communication/service.py` directly. Confirmed real, current behavior:
- Persisted statuses today: **`DRAFT`** and **`COPIED`** only (`viewModel.save(manager, "DRAFT"/"COPIED")`). `status` is a free-text column with no allowlist/CHECK constraint (`service.py:162`, `str(record.get("status", "DRAFT"))`) — so no schema change was needed to add a new status value.
- **No `SENT` status exists anywhere** — already compliant with your rule.
- **`SHARED_EXTERNALLY` did not exist** — a real gap, since Copy was the only action; there was no Share action at all. Added this session (see §3) as a genuinely new but minimal, backward-compatible action — no schema change, just a new string value flowing through the same column.
- `GENERATED` is correctly never persisted as a status — it's the transient `ui.result` state before the manager chooses Save/Share/Discard, which is the right model (a generated-but-unsaved draft isn't a database row).

## 3. Files modified

- `SkillEdge_Android/app/src/main/java/com/example/skillsync/navigation/NavigationKeys.kt` — `Communication` route gains `initialRecipientType`/`initialRecipientName`/`initialPurpose` (all default `""`, backward compatible).
- `SkillEdge_Android/app/src/main/java/com/example/skillsync/navigation/Navigation.kt` — thread the new fields into `CommunicationScreen`; add `onOpenCommunication` callback on the `Main` case; fix `Communication`'s `onBack` to return to Today (not always Opportunities) when not opened from an Opportunity.
- `SkillEdge_Android/app/src/main/java/com/example/skillsync/feature/communication/ui/CommunicationScreen.kt` — accept the three prefill params, apply once via `LaunchedEffect`; add a **Share** button (`ACTION_SEND`) saving status `SHARED_EXTERNALLY`.
- `SkillEdge_Android/app/src/main/java/com/example/skillsync/feature/communication/ui/CommunicationViewModel.kt` — add `setInitial(recipientType, recipientName, purpose)`.
- `SkillEdge_Android/app/src/main/java/com/example/skillsync/feature/home/ManagerCommandCentre.kt` — the actual Today implementation (§4-6).
- `SkillEdge_Android/app/src/main/java/com/example/skillsync/feature/home/MainScreen.kt` — thread `onOpenCommunication` through `MainScreen` → `DashboardTab` → `ManagerCommandCentre`.
- `SkillEdge_Android/app/src/test/java/com/example/skillsync/ui/ScreenRenderTest.kt` — 5 new tests (§13).

No backend file touched — this slice is entirely a UI/navigation layer over the already-existing, already-tested `/api/v2/message/compose`, `/api/v2/communication/generate|save|history` endpoints.

## 4. Today Communication UI

A new "Communicate" section on Today (`SectionHeading("Communicate")`), one glass card, four compact actions in a row — not a wall of 12 purpose buttons, per instruction:

```
COMMUNICATE
[ Team ] [ Trainer ] [ Weekly ] [ Monthly ]
```

## 5. Team workflow

`Team` → `onOpenCommunication("TEAM", "", "GENERAL_PROFESSIONAL", "", "")` → opens the existing `CommunicationScreen` with recipient type pre-set to `TEAM` and purpose pre-set to `GENERAL_PROFESSIONAL` — the manager picks a different purpose from the existing dropdown if they want one of the other 11, types instruction/own words, generates through the unchanged shared pipeline. No new purpose picker sheet was built (deferred — see §12); the existing screen's dropdown already lists all 12 purposes.

## 6. Trainer workflow

`Trainer` → opens a `ModalBottomSheet` listing the **real** trainer roster (`trainer_operations_df` names, deduplicated) already available on Today — no new network call. Tapping a name → `onOpenCommunication("INDIVIDUAL", trainerName, "GENERAL_PROFESSIONAL", "", "")`, dismisses the sheet, opens the composer pre-filled with that trainer's name and `INDIVIDUAL` recipient type.

## 7. Weekly / Monthly workflow

Per the engine-boundary correction, these do **not** open a new composer or a new pipeline — they call the existing `onOpenWeeklyReport()`/`onOpenHrReport()` navigation callbacks (already present on `ManagerCommandCentre`, previously reachable only from elsewhere), landing the manager on the real, already-working Weekly Report / HR Monthly Report screens that already generate cadence-based messages through `/api/v2/message/compose`. This is a **discoverability fix**, not new generation logic — exactly what the audit recommended and exactly what avoids reviving a duplicate pipeline.

**Deferred, explicitly not built this slice:** the richer "period selector + context-to-be-used preview before generation" UX described in the original instruction's §6/§10/§7 is a genuine new feature (the existing report screens don't expose a pre-generation fact checklist today) — building it responsibly needs its own scoped pass, not a rushed addition here. See §12.

## 8. Contextual Today actions

Implemented: unallocated-demand rows in "Needs you today" now show an **"Ask availability"** chip alongside the severity chip. Tapping it calls `onOpenCommunication("TEAM", "", "AVAILABILITY_REQUEST", "demand", <real demand_id>)` — the `demand_id` is the actual field already flowing through `unallocated_demand_df`, never invented. The chip only appears for demand-sourced attention items; the pending-skill-requests row gets no chip (there's no natural recipient/purpose for it yet — not faked).

**Deferred:** the other contextual actions named in the instruction (delivery-readiness reminder, capacity-pressure check-in, appreciation for a completed delivery) — see §12 for why, scoped honestly rather than added as decorative no-ops.

## 9. Trainer 360 / People integration

**Not done this slice.** The instruction's own priority order (§15: "complete the manager Communication layer first because it is part of the Today experience") scoped this pass to Today; extending "Message Trainer" to Trainer 360 and People rows is the same one-line pattern already proven here (`onOpenCommunication("INDIVIDUAL", name, "GENERAL_PROFESSIONAL", "", "")`) and is the natural next slice, not attempted here to keep this change reviewable as one coherent unit.

## 10. Shared CommunicationService reuse

Confirmed by construction: every new entry point calls the same `onOpenCommunication` → `Communication` nav route → the same `CommunicationScreen`/`CommunicationViewModel`/`CommunicationService` that already served Opportunity Detail. Zero new generation code was written anywhere in this slice — only navigation, a picker sheet, and prefill plumbing.

## 11. Multiple trainers (instruction §9)

**Correctly not implemented**, per your own instruction. `RECIPIENT_TYPES` has no `MULTIPLE_TRAINERS`/group-of-individuals value, and building one would mean either (a) a real domain change (a list-of-recipients model, sensitive-fact filtering per recipient, distinct provenance per person) or (b) faking it by mislabeling a partial selection as `TEAM` — explicitly forbidden. Documented here as the next real capability gap, not attempted.

## 12. Deliberately deferred (with reasons, not omissions)

- **Weekly/Monthly context-preview UI** ("3 relevant deliveries ✓ / unrelated historical courses –") — genuine new feature, needs backend work to expose what facts would feed a message *before* generation (today the compose endpoint only returns the finished text); scoping this properly is bigger than this slice.
- **Delivery-readiness / capacity-check-in / appreciation contextual actions** — same reasoning as demand→availability, but each needs its own real grounding check (e.g. "positive completed delivery" needs a defined, non-fabricated signal for what counts as recognition-worthy) that wasn't part of this pass's scope to define safely.
- **Trainer 360 / People "Message Trainer" entry points** — next slice, same pattern, deliberately sequenced after Today per your own priority.
- **Multiple-trainer messaging** — needs a domain decision, not a workaround (§11).
- **WhatsApp/Email channel formatting** — explicitly out of scope per instruction §11.

## 13. Tests / results

5 new tests added to `ScreenRenderTest.kt`:
- `today_communicateSectionRendersFourActions`
- `today_teamActionOpensSharedComposerWithTeamRecipient` — asserts the exact tuple passed to `onOpenCommunication`
- `today_trainerActionOpensPickerThenSharedComposerWithSelectedTrainer` — asserts the real fixture trainer name flows through
- `today_weeklyAndMonthlyRouteToExistingReportScreensNotANewPipeline` — asserts `onOpenWeeklyReport`/`onOpenHrReport` fire, proving no new pipeline was created
- `today_unallocatedDemandOffersAskAvailabilityWithRealDemandId` — asserts the real fixture `demand_id` ("264455"), never an invented one

**Results:**
- `:app:compileDebugKotlin` — clean.
- `:app:testDebugUnitTest` — **203 run (198 baseline + 5 new), 12 pre-existing `ScreenRenderTest` failures (unrelated Dashboard-spec gap, unchanged in count/location from every prior session), zero new failures.**
- Backend: `python -m pytest tests/` — **358 passed** (this slice touched no backend file; run to confirm no incidental breakage).
- Existing Opportunity-communication flow: not independently re-tested with a new test (none existed to regress against beyond compile-level verification), but the only changes to shared files (`Communication` route, `CommunicationScreen`, `CommunicationViewModel`) are additive/backward-compatible — new optional parameters with defaults, a new button, a new ViewModel method — nothing existing was removed or renamed, and the fix to `onBack`'s fallback target only changes behavior for a `relatedEntityType` value ("") that the Opportunity flow never passes (it always passes `"OPPORTUNITY"`), so that path is provably unaffected.
- HR Monthly existing flow: untouched file, not modified this session; the new "Monthly" Today action navigates *to* it, doesn't change it.

## 14. Screenshots/render verification

Not available in this environment (no emulator/device attached to this session) — verification is the Robolectric-rendered Compose tests in §13, which exercise the real composable tree (semantics assertions on actual rendered text/click targets), not a mock.

## 15. Discovered fabrication/data-integrity issues

None new found in this slice. The `_pipeline_build` fabrication (`certified`/`skill_level` default) found during the Plan V2 audit remains open and untouched — out of scope for this Communication-focused pass.

---

**Status: implemented and verified, stopped for review — not committed, not pushed, no version
bump, no release, per instruction.**
