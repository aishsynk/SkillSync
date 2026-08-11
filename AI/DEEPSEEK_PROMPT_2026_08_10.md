# DeepSeek Task Prompt — SkillEdge API & Consistency Hardening

> **How to use this file**
> 1. Read this file completely before sending anything to DeepSeek.
> 2. Send the message under "MASTER PROMPT" below to DeepSeek in one fresh session.
> 3. DeepSeek will execute Task 1, stop and report, and wait for your "continue" before starting Task 2.
> 4. Review each task's report, decide go/no-go, then say "continue".
> 5. If a task fails its gates, tell DeepSeek what to fix and let it retry that task before moving on.

---

## MASTER PROMPT (copy everything below this line and paste into DeepSeek)

```
You are taking over the SkillEdge / SkillSync project at repository root
`C:/Users/Aishw/OneDrive - Koenig Solutions Ltd/SkillEdge`.

The current published release is v3.1.1 / code 77 (Plan Continuity), live and
healthy. The README of single truth is `AI/PROGRESS.md`. The full audit you are
acting on lives in `AI/API_AND_VERSION_AUDIT_2026_08_10.md`. Read both files
fully before doing anything else.

## Project rules (non-negotiable)

1. **Read AI/PROGRESS.md first** at the start of every task. Treat it as the
   single source of truth.
2. **Update AI/PROGRESS.md** at the end of every task with: timestamp (ISO 8601
   with +05:30 offset), tool used, files modified, work completed, current
   status, next actions. Add a final dated entry before stopping each task.
3. **Verify, do not assume.** Live-probe the deployed Render backend for any
   claim about production behaviour. Never declare a task "verified" on a
   compile alone.
4. **Ship via GitHub Releases only.** APKs are produced by GitHub Actions on
   tag push, never uploaded as files. The signer is `skillsync-release.jks`
   via CI secrets; the certificate SHA-256 fingerprint must stay
   `c6868b14bec9982642d908a5d4f535116daaf4e932a1e5ac27ed957671a41808` so the
   APK installs over the existing app without uninstall.
5. **No re-dispatch on `cancelled`.** If a CI run cancels, fix the cause and
   push a new commit; never re-dispatch the cancelled run.
6. **No new version bump unless the change ships to production.** Internal
   refactors do not get a version bump.
7. **Backend tests** must pass `python -m unittest discover -s tests -v` from
   the repo root. **Android tests** must pass `./gradlew :app:testReleaseUnitTest`
   inside `SkillEdge_Android/`. **Lint and signed release assembly** must
   pass before any commit claims release-ready.
8. **Do not change `_APIS` plaintext credentials in source.** When you rotate
   or move credentials, read them from environment variables and fail loudly
   on startup if absent. Keep the existing values as defaults during the
   migration so production does not break mid-change.
9. **No fabricated data.** If a contract is unverified, label it
   `available: false` with a `note`. Never invent counts, dates, or names.
10. **One task at a time.** Execute the task I assign, stop, write the dated
    progress entry, and wait for "continue" before starting the next.

## Workflow per task

For each task I assign:

  Step A. Read `AI/PROGRESS.md` and `AI/API_AND_VERSION_AUDIT_2026_08_10.md`
          to confirm scope.
  Step B. Implement the change.
  Step C. Run the full backend test suite and the relevant Android tests.
  Step D. Run lint and signed release assembly for any Android change.
  Step E. Commit and push.
  Step F. For backend changes: trigger Render deploy (or document why you
          could not) and live-probe the deployed URL.
  Step G. For Android changes: monitor the GitHub Actions release, verify
          the published APK (download, SHA-256, package name, version
          code, signer fingerprint), update release notes with
          purpose/changes/commit/version/user-gain/validation/rollback.
  Step H. Update `AI/PROGRESS.md` with the dated entry.
  Step I. Stop and report: what shipped, what evidence you have, what is
          still open.

## Verification environment

- Backend live URL: `https://skilledge-backend-fpcl.onrender.com/`
- Render dashboard is reachable but not authenticated from your session; if
  you need to deploy, document the commit hash and ask the user to trigger
  the Render deploy.
- GitHub CLI (`gh`) is available. The repo is `aishsynk/SkillSync`.
- No ADB-connected Android device is available. Physical install-over and
  on-device visual validation cannot be performed — say so when relevant,
  do not fake it.

## What I am asking you to do, in order

I will hand you one task at a time. You will not start the next task until
I say "continue". The full ranked list is in the audit document §"What to
ship first". The order is:

  Task 1  Move every V1 read route behind the V2 session+scope helper.
  Task 2  Move `mark_skill` behind session auth.
  Task 3  Rotate RMS credentials out of `_APIS` into env/secret store.
  Task 4  Fix `AllocationViewModel.loadCourseIntelligence` synthesised payload.
  Task 5  Standardise error envelope and codes across the backend.
  Task 6  Unify the V1/V2 split into a real contract (V2 is canonical).
  Task 7  Auto-generate the `/` endpoint list from the route table.
  Task 8  Remove the dead 503 branch in `login()` and align the
          `_verify_role` docstring.
  Task 9  Drop or wire up the 3 dead Retrofit declarations
          (`/api/auth/session`, `/api/data/trainer-skills`,
          `/api/v2/actions/{id}/audit`).
  Task 10 Introduce typed `ActionRow`, `CourseIntelligence`, `CapacityPlan`
          models on the Android side.
  Task 11 Adopt a `UiState<T>` interface across ViewModels; replace the four
          bespoke sealed classes.
  Task 12 Move all hardcoded `Color(...)` and `RoundedCornerShape(N.dp)`
          literals into theme tokens.
  Task 13 Version the cache swap in `adoptBackgroundSync` (MainScreen and
          Allocation) so a background sync cannot overwrite a fresh
          foreground fetch.
  Task 14 Wire `/api/v2/actions/{id}/audit` into `ActionsInbox`.
  Task 15 Add a shared `Throwable.userMessage(verb)` helper; replace the 8
          ad-hoc error mappers.
  Task 16 (Upstream — requires RMS team engagement, defer.) Negotiate SSO,
          GET reads, versioned responses, deduplicated assignments.

## When I send "Task N" you will:

1. Re-read `AI/PROGRESS.md` (top of file only) and confirm scope.
2. State in one paragraph what Task N will change.
3. Implement.
4. Run the relevant test/lint/release gates.
5. Commit, push, deploy (or document the deploy dependency).
6. Update `AI/PROGRESS.md` with the dated entry.
7. Stop and report. Do not start the next task.

## When I send "continue" you will:

1. Move to the next task in the list above.
2. Follow the same workflow.

## When I send "skip N" you will:

1. Note in `AI/PROGRESS.md` that Task N was skipped and why.
2. Move on when I say "continue".

Begin by reading `AI/PROGRESS.md` and `AI/API_AND_VERSION_AUDIT_2026_08_10.md`
in full. Then wait for my first task assignment.
```

---

## Operating instructions (for you, not for DeepSeek)

- **One fresh DeepSeek session per major phase** is safer than one long
  session — long sessions tend to drift from the rules above.
- **Always say "continue" between tasks.** DeepSeek should stop and report;
  if it tries to start the next task without your sign-off, interrupt it.
- **Have the audit open in another window.** When DeepSeek reports a task,
  cross-check that it actually hit the audit's recommendation and did not
  invent a different change.
- **For Tasks 1, 2, 3, 5, 6**: these are backend-only. The deliverable is a
  pushed commit, a successful Render deploy (or documented dependency), and
  live-probe evidence on the deployed URL. No APK bump.
- **For Tasks 4, 9, 10, 11, 12, 13, 14, 15**: Android-only. Decide up
  front whether the change warrants a version bump. Per rule 6, an internal
  refactor that does not change behaviour does **not** get a bump — only ship
  the next APK when there is a user-visible or contract-visible change.
  Multiple Android tasks can batch into a single release.
- **For Task 7 and 8**: trivial cleanup; can be folded into the next release
  that touches `backend.py` rather than shipped alone.
- **For Task 16**: defer. It is a negotiation with the RMS team, not a code
  task. The audit document is the deliverable.

**What to watch for — three common traps:**

- DeepSeek reading the inventory's "silent rejection" concern about
  `MarkSkillRequest` and re-introducing that fear. The audit document
  explicitly corrects it: Gson serialises snake_case Kotlin names verbatim,
  so the wire keys are correct. If DeepSeek adds `@SerializedName` to fields
  that don't need it, point it at `AI/API_AND_VERSION_AUDIT_2026_08_10.md`
  §3.4 and §4.1.
- DeepSeek bumping the version on internal refactors. Per rule 6, do not
  allow it. Refactors ride the next behaviour-changing release.
- DeepSeek declaring a task verified on a green test alone. Per rule 3, it
  must also live-probe Render and (for Android) verify the published APK.
- DeepSeek rewriting `_verify_role` to enforce a real role check. The audit
  says the docstring lies, but the simplest fix is to update the docstring,
  not to add scope logic that breaks every Koenig email. Wait for explicit
  Entra/identity configuration before tightening role enforcement.
