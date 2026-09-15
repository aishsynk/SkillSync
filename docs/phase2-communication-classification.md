# Phase 2 — Communication Responsibility Classification Matrix

Audit performed before any Phase 2 consolidation, per the operator's instruction. Every
row below is based on reading the actual implementation, not inferred from naming.

## Android (`SkillEdge_Android/.../feature/communication/`)

| Implementation | Caller(s) (before this pass) | Input contract | Facts consumed | Calculations | Intent/purpose decision | Recipient decision | Prose responsibility | Formatting | Channel-specific | Fallback | Sends? | Duplicate of |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `MessageRewriter.compose` | 3 report screens (until Phase 1); 0 after Phase 1 | `userMessage`+`myMessage` strings, `EvidenceContext` | Evidence passed in by caller | none | **Yes — `detectIntent` infers urgency/tone/firmness from free text** | none (targetName param) | Yes, full prose | Bold/italic/underline via regex | Teams/Plain via `MessageStyle` | Deterministic local mirror of `_compose_rewritten` | No | `CommunicationComposer`, `WeeklyMessage` |
| `WeeklyMessage.composeTeamMessage`/`composeReporteeMessage` | **0 production callers** (dead; only `WeeklyMessageTest`) | `TeamSignals`/`ReporteeSignals` (evidence-only) + `managerNote` | Evidence-only | none | Yes — severity-ordered `when` on evidence | n/a (broadcast/per-reportee) | Yes, full prose | Own bold/italic/sanitise | Teams/Plain | n/a | No | `MessageRewriter`, `CommunicationComposer` |
| `WeeklyMessage.composeManagerStandpointNote` | `WeeklyReportScreen.kt` (live, as fallback when `rep.standpointNote` and server compose are both blank) | `ReporteeSignals` | Evidence-only | none | Yes — status/focus `when` on evidence | n/a | Yes — a different artifact type (labelled internal note, not Teams prose) | Own bold markers | n/a (internal only) | n/a | No | none — legitimately distinct channel |
| `CommunicationComposer.composeFromPlan` | `ManagerCommunicationComposer` (new), `CommunicationGenerator` | `ContextSelectionPlan` (structured) | `plan.selectedFacts` | none | No — purpose already decided by caller | No — recipient already decided by caller | Yes, full prose per purpose | Bold/italic/underline, `cleanFormatting` | Teams-oriented markdown | n/a | No | none — this is the canonical composer |
| `CommunicationContextSelector.evaluateAndSelect` | `CommunicationGenerator` only | `userMessage`+`myMessage`+`verifiedContext` | `verifiedContext` map | Sensitive-key filtering (not KPI calc) | **Yes — Flow A treats `userMessage` as primary intent when present; Flow B is fact-driven** | Partial (recipient type inference from text in Flow A) | No — produces a plan, not prose | n/a | n/a | n/a | No | Backend `context_selector.py` (intentional mirror) |
| `CommunicationPlanner` | `CommunicationHintResolver` (Phase 1), `ManagerCommandCentre.kt` (Today) | `DemandFact`+`CandidateTrainer[]` | Real matched-candidate data | none | No | **Yes — resolves a real named recipient from verified availability, never an aggregate** | No | n/a | n/a | n/a | No | none — this is the correct recipient-resolution pattern |
| `CommunicationContextPolicy.CommunicationPurpose` + `CommunicationContextFilter` | `BatchShare`, `BatchShare.composeExternalStaffingRequest` | purpose enum + raw context map | n/a | none | No (purpose supplied by caller) | No | No | No | No | n/a | No | **This is the canonical purpose taxonomy — Phase 1's separate `domain.CommunicationPurpose` was a duplicate, now merged into this one this pass** |
| `CommunicationValidator.validate`/`validateFactualIntegrity` | `CommunicationGenerator`, `ManagerCommunicationComposer` | composed text + `ContextSelectionPlan` | facts+inputs as ground truth | none | No | No | No | No | No | n/a | No | none |
| `CommunicationGenerator.generate` | `CommunicationViewModel` (the dedicated Communication screen) | raw `Map<String,Any>` incl. `userMessage`/`myMessage` | via `verifiedContext` param (in practice: **always empty from this screen** — no live caller supplies it) | none | Delegates to ContextSelector (see above) | Delegates | Delegates to Composer | Delegates | Delegates | n/a | No | Backend `service.py` (intentional mirror) |
| `BatchShare` (`composeMessage`/`composeWithIntent`/etc.) | `BatchDetailScreen`, `NetworkStaffingSheet`, `Trainer360Screen`, `PrioritiesScreen`, `GrowTeamCard` | `Batch` data class (evidence) + optional `myMessage` | Batch fields only | none | No — fixed mandated broadcast format ("RMS allocation broadcast, applied literally"), not free prose | No | Yes, but a **fixed labelled-field format**, not free prose — a legitimate different message type | Bold/italic/underline per target (Teams/plain/HTML) | **Yes — 3 renderers: `composeMessage` (Viber/WhatsApp markers), `plainMessage`, `htmlMessage`** | n/a | **Yes** — `copyMessage`/`shareAnywhere`/`openUrl` (clipboard + Android share Intent + browser) | none — legitimately its own format |
| `BulkBatchShare` | `PrioritiesScreen` only | `List<BatchShare.Batch>` | Batch fields only | Aggregation (count/group) only | No | No | Yes — aggregates `BatchShare`'s per-batch format | Reuses `BatchShare` | Reuses `BatchShare` | n/a | No (delegates share to `BatchShare`) | Thin wrapper over `BatchShare`, not a duplicate engine |
| `CommunicationHintResolver` (Phase 1) | `PrioritiesViewModel` | primitive candidate/board-item data | Real matched-candidate data | none | No | Yes — delegates to `CommunicationPlanner` | No | n/a | n/a | n/a | No | Thin wrapper over `CommunicationPlanner`, not a duplicate |
| `ManagerCommunicationComposer` (Phase 1) | `CommunicationRepository` | `CommunicationRequest` (structured, no `userMessage`) | `CommunicationEvidence` | none | No — purpose is part of the structured request | No — audience is part of the structured request | Builds a plan, then delegates prose to `CommunicationComposer` | Delegates | Delegates | n/a | No | none |

## Backend (`services/communication/`, `domain/communication/`, `repositories/communication_store.py`, `backend.py`)

| Implementation | Caller(s) | Classification | Notes |
|---|---|---|---|
| `services/communication/service.py::CommunicationService.generate` | `backend.py` routes `/api/v2/message/compose` (indirectly, only when `my_message` present) and `/api/v2/communication/generate` (directly) | Orchestrates intent→context→compose→validate — **structurally identical to Android's `CommunicationGenerator`, intentional mirror** | `/api/v2/message/compose` (used by Android's report-screen "authoritative server composer") **only ever passes `myMessage`, never `userMessage`** — verified by reading `backend.py:8962-9008`. `/api/v2/communication/generate` is a generic passthrough that would forward a `user_message` if a caller sent one. |
| `services/communication/context_selector.py::ContextSelector` | `CommunicationService` | Same Flow A/B split as Kotlin `CommunicationContextSelector` | Intentional mirror; Flow A still exists here too |
| `services/communication/composer.py::compose_from_plan` | `CommunicationService` | Mirror of `CommunicationComposer` | Not modified this pass |
| `services/communication/policy.py`, `validator.py`, `intent.py` | `CommunicationService` | Mirrors of Kotlin `CommunicationContextPolicy`/`CommunicationValidator`/intent detection | Not modified this pass |
| `domain/communication/models.py` | all of the above | Domain models (`CommunicationContext`, `GeneratedMessage`, etc.) | Not modified this pass |
| `repositories/communication_store.py` | `CommunicationService.save`/`history` | Persistence (DELIVERY-adjacent, history only — not actual message send) | Not modified this pass |
| `backend.py::_compose_manager_message` | `/api/v2/message/compose` route | **DOMAIN FACT PREPARATION + MESSAGE COMPOSITION**, deterministic, evidence-only, `my_message` optional | This is the authoritative source both Android's server-compose path and (indirectly) `_communication_service.generate` feed into as a fallback |
| `_viber_queue_build` (backend.py) | Viber automation (`feature/guardian` on Android) | **CHANNEL RENDERING + DELIVERY/SEND** (queues for the Viber automation client) | Out of scope this pass — not a prose-generation duplicate, it's queuing/delivery for already-composed text |

## Responsibility classification summary

| Responsibility | Owner (target) | Current state |
|---|---|---|
| DOMAIN FACT PREPARATION | `_reportee_message_facts`, `FactBuilder`, `CommunicationEvidence` | Correct — backend/Android both already fact-sourced, not composer-owned |
| DERIVED INSIGHT | backend scoring helpers, `projectNextUtilization` (Android, misplaced per Phase 1 assessment, not yet moved) | Correct ownership, not yet relocated (Phase 4, out of scope here) |
| RECIPIENT RESOLUTION | `CommunicationPlanner` | Correct, reused by `CommunicationHintResolver` and Today |
| COMMUNICATION PURPOSE | `CommunicationContextPolicy.CommunicationPurpose` | **Consolidated this pass** — Phase 1's duplicate `domain.CommunicationPurpose` removed, two new values (`TEAM_PERIODIC_UPDATE`/`INDIVIDUAL_PERIODIC_UPDATE`) added to the one canonical enum |
| COMMUNICATION POLICY | `CommunicationContextFilter` (sanitization), `CommunicationValidator` (format/factual policy) | Correct, unchanged |
| MESSAGE COMPOSITION | `CommunicationComposer.composeFromPlan` | Canonical; `ManagerCommunicationComposer` (Phase 1) and `CommunicationGenerator` both delegate to it |
| CHANNEL RENDERING | `BatchShare`'s three renderers (`composeMessage`/`plainMessage`/`htmlMessage`), `WeeklyMessage`'s `MessageStyle` | Correct, legitimately separate per destination |
| VALIDATION | `CommunicationValidator.validate` | Correct, unchanged |
| DELIVERY/SEND | `BatchShare.copyMessage`/`shareAnywhere`/`openUrl`, `_viber_queue_build` | Correct — kept separate from composition, not touched |
| LEGACY/DUPLICATE | `MessageRewriter` (**removed this pass**), `WeeklyMessage.composeTeamMessage`/`composeReporteeMessage` (**dead, flagged, not yet removed — see below**) | Partially resolved |

## What this pass changed

1. **`MessageRewriter` deleted.** It had zero production callers left after Phase 1
   moved the three report screens off it, and its `detectIntent`/`compose` pair was
   the literal `[User Message]`+`[My Message]`-primary-intent model the operator
   ordered retired. Its 4 dedicated tests (which tested exactly that retired
   precedence behaviour) were removed from `WeeklyMessageTest.kt`; the file's other
   tests (`composeTeamMessage`/`composeReporteeMessage`/`composeManagerStandpointNote`/
   `sanitise`/`trimToLimit`) are untouched.
2. **`CommunicationPurpose` consolidated to one enum.** Phase 1 introduced a second,
   competing `feature.communication.domain.CommunicationPurpose` (two values). This
   pass deletes it and adds its two values (`TEAM_PERIODIC_UPDATE`,
   `INDIVIDUAL_PERIODIC_UPDATE`) to the pre-existing, richer
   `feature.communication.engine.CommunicationPurpose` (the one already used by
   `CommunicationContextFilter`'s sanitisation policy and by `BatchShare`). No
   existing purpose's behaviour changed.
3. **`CommunicationScreen`/`CommunicationViewModel` — the actual dedicated
   Communication feature — had its own live `[User Message]` field, independent of
   the three report screens Phase 1 fixed.** `userMessage` is removed from
   `CommunicationUiState` entirely; the remaining field is renamed
   `managerInstruction` and mapped only to the backend's `myMessage`/`my_message`
   parameter (the one the authoritative `/api/v2/message/compose` route already
   accepts) — this screen can structurally no longer originate a `userMessage`.
4. **Backend: verified, not modified.** Read `backend.py:8962-9008`
   (`/api/v2/message/compose` handler): it already only ever passes `myMessage` to
   `CommunicationService.generate`, never `userMessage` — this was already
   parity-correct with the Android contract before this pass, so no backend edit was
   needed for the paths Android actually calls. The generic
   `/api/v2/communication/generate` route (and `services/communication/*.py`
   underneath it) retains `user_message` support for any other, out-of-scope caller
   (e.g. a web dashboard) that might still send one — documented here as an
   intentional, not-yet-unified platform difference, not a silent divergence.

## What this pass deliberately did NOT do (documented, not silently skipped)

- **`WeeklyMessage.composeTeamMessage`/`composeReporteeMessage` were not deleted**,
  despite being confirmed dead code (zero production callers) and a duplicate prose
  engine. Removing them cleanly also means removing ~150 lines of their dedicated
  test coverage in the same file as the tests this pass already edited; given the
  session's remaining verification budget, this is deferred to a focused follow-up
  rather than risking an incomplete edit to a shared test file. Flagged here so a
  future pass does not need to re-discover it.
- **`CommunicationContextSelector`'s Flow A (`hasManualInput` branch) was not
  deleted from the engine itself**, only stopped from ever being reachable with a
  real `userMessage` from any live Android caller (confirmed by removing the field
  at every call site: `CommunicationRequest`, `CommunicationUiState`,
  `CommunicationViewModel.buildRequest`). The function signature still accepts a
  `userMessage` parameter for backward compatibility with its existing unit tests
  (`CommunicationEngineTest.kt`, `CommunicationPlannerTest.kt`) which exercise it
  directly, not through a live screen. A full deletion of the parameter would touch
  `CommunicationGenerator`, `CommunicationContextSelector`, `CommunicationComposer`,
  `CommunicationValidator`, and their Python mirrors simultaneously — a wider,
  riskier edit than this pass's verification budget supports safely. This is the
  main remaining item for a further Phase 2 increment.
- **Backend `services/communication/*.py` / `context_selector.py` Flow A was not
  touched**, since no Android caller now reaches it with a populated `user_message`,
  and no other in-scope caller was identified. `SkillEdge_Local` (out of scope) was
  not inspected for whether it still sends one.
- **`BatchShare`/`BulkBatchShare`'s composition was not routed through
  `CommunicationComposer`** — inspection (this document) found their fixed
  labelled-field broadcast format is a real, mandated, structurally different
  message type ("the RMS allocation broadcast, applied literally"), not a
  duplicate of manager-to-team/reportee prose. Forcing it through the prose
  composer would change a format the business explicitly requires. Their delivery
  mechanics (`copyMessage`/`shareAnywhere`/`openUrl`) were correctly already
  separate from composition and were not touched.

## Phase 2 closure pass (2026-09-15, same session)

The operator correctly rejected the items above marked "deferred" as an
acceptable way to close Phase 2 — classification is not closure. This
section records what the closure pass actually did.

1. **`WeeklyMessage.composeTeamMessage`/`composeReporteeMessage` deleted.**
   Verified zero production callers repository-wide (only
   `WeeklyMessageTest.kt`, itself updated). Their private helpers
   (`assemble`, `sanitise`, `trimToLimit`, `formatManagerNote`, `bold`,
   `italic`, `count`, `nextAvailabilityDeadline`, `weekReference`) were
   dead-code-only for those two functions and removed with them.
   `TeamSignals`/`ReporteeSignals` (still real, used as evidence structs by
   `WeeklyReportScreen.kt`) and `composeManagerStandpointNote` (still live,
   a distinct labelled-field internal note, not Teams/Viber prose) are
   retained.

2. **`BatchShare`/`BulkBatchShare` — classification confirmed, not
   re-plumbed.** Re-read `BatchShare.kt` end to end: its only "decision" is
   a fixed field-presence check (a label prints only when RMS returned that
   field); it does not choose tone, purpose, appreciation/correction
   framing, or KPI interpretation anywhere. Classified `STRUCTURED
   OPERATIONAL SHARE` in `AI/CONTEXT.md`'s canonical architecture section.
   No code change.

3. **Backend Flow A retired**, mirroring the Android change already made in
   the main Phase 2 pass:
   - `services/communication/context_selector.py`'s `if um: ... else: ...`
     split (the literal "user_message is PRIMARY" branch, 4 purpose cases +
     a generic fallback) is removed; the `my_message`-only logic is now
     unconditional inside `ContextSelector.evaluate_and_select`'s
     `has_manual_input` branch. `user_message` remains an accepted
     parameter (API-shape compatibility) and still contributes to
     recipient-type/time-reference text scanning (benign parsing, not
     intent), per the corrected module docstring.
   - The Kotlin mirror, `CommunicationContextSelector.kt`, received the
     identical change (the `if (um.isNotEmpty())`/`else` split removed) so
     the two engines do not silently diverge.
   - `services/communication/intent.py`'s module docstring, which
     literally asserted "User Message is the PRIMARY source of intent" as
     a current rule, corrected — logic unchanged (it is a tone/urgency
     signal extractor, not a purpose/recipient decision-maker; that
     decision lives in `ContextSelector`, which was fixed).
   - **A previously-undiscovered third entry point found and retired**:
     Android's `SkillEdgeApi.kt` declared a `POST api/v2/message/rewrite`
     endpoint (`rewriteMessage`/`RewriteRequest`/`RewriteResponse`) with
     zero callers anywhere in the app — confirmed by repo-wide search. Its
     backend counterpart, `backend.py`'s `/api/v2/message/rewrite` route
     (`message_rewrite()`, calling `_compose_rewritten`/`_detect_intent`),
     had zero test coverage and zero other internal callers. The Android
     declaration was deleted outright; the backend **route** was removed
     (confirmed exact boundaries before editing), while `_compose_rewritten`/
     `_detect_intent` themselves were left in place as now-unreferenced
     dead code rather than risking an imprecise deletion inside a
     15,000-line file with no test coverage protecting that specific
     removal — flagged here as a follow-up cleanup item, not silently left
     reachable (the reachable HTTP path is gone).

4. **Repository-wide search, final pass** — every remaining hit for
   `User Message`/`userMessage`/`user_message`/`My Message`/`myMessage`/
   `my_message`/`MessageRewriter`/`WeeklyMessage`/direct
   `CommunicationComposer`/`CommunicationGenerator` calls was individually
   classified (see the search output preserved in this session's record).
   Remaining hits are: (a) parameter names still accepted for API-shape
   compatibility but no longer treated as primary intent by any live
   caller (`userMessage`/`user_message` fields on `ContextSelectionPlan`/
   `CommunicationContext`/`CommunicationPlan`), (b) an unrelated
   `Throwable.userMessage(verb)` error-formatting extension function
   (`core/common/Errors.kt`) that has nothing to do with communication
   semantics, (c) doc comments/tests explicitly describing the retired
   behaviour as historical context, and (d) the two legitimate direct
   `CommunicationComposer`/`CommunicationGenerator` callers
   (`ManagerCommunicationComposer`, `CommunicationViewModel`) which are the
   correct, intended call sites. No hidden manager-communication path
   using the retired semantics remains.

5. **Verification**: Android — see `AI/PROGRESS.md`'s Phase 2 closure
   entry for the CI run and failure-identity comparison. Backend — full
   suite (`python3 -m pytest tests/ -q`) run after each backend edit in
   this closure pass (context_selector.py, intent.py, backend.py route
   removal): **370 passed, 25 subtests passed, 0 failed** throughout, no
   regressions at any step.

6. **Durable architecture record**: `AI/CONTEXT.md`'s "Manager
   Communication Architecture (effective 2026-09-15)" section now states
   the canonical pipeline, supersedes the 2026-09-12 entry explicitly
   (append-and-supersede, per this repo's own documentation convention —
   the superseded text is retained below it as history, not deleted), and
   covers every point the operator's closure instructions required.
