# SkillEdge Production Release v3.51.0 (Build 137)

- **Release Date**: 2026-08-31
- **Version Code**: `137`
- **Version Name**: `3.51.0`

## Why this release

First wave of the manager-view enhancement roadmap: move the tool from "here is
what is happening" toward "here is what to do about it." Three enhancements,
built from data that already exists, all deterministic.

## What changed

### Demand left on the table (dashboard KPI)
The dashboard gains a headline figure: of all open unallocated batches, how many
the team already has the skills to teach, and roughly how many trainer-days of
delivery capacity that represents. `manager_kpis.opportunity_cost` carries
`open_batches_coverable / open_batches_total`, `trainer_days_at_stake`, a
`by_cause` breakdown (skill gap in the team's own vendor space vs the rest) and
example course names. The card taps through to the list.

### "Your Week" priorities (API)
New endpoint `GET /api/v2/manager/priorities?manager=<email>` returns one ranked
worklist: unstaffed batches by deadline, trainers who need a one to one,
overloaded trainers, certification gaps, and overdue manager actions — each with
a severity, a due date, a deep-link target and a rank score. The dedicated
"This Week" screen that consumes it ships in v3.52.0; the endpoint is live and
tested now.

### Feedback trend and themes (Trainer 360)
Trainer 360's feedback section now shows whether a trainer's learner rating is
**improving, declining or steady** (last three months versus the prior three),
a month-by-month rating series, and the **themes learners keep raising** —
pace, depth, labs, clarity, knowledge, engagement — each tagged positive or
constructive with a mention count and a sample comment. All from the RMS key 244
history the app already fetches, clustered deterministically by keyword.

## Compatibility

- Android package and signing identity unchanged; build 137 installs over 136.

## Validation

- Backend pytest suite green (187, +12 new across `test_opportunity_cost.py`,
  `test_manager_priorities.py`, `test_feedback_analytics.py`).
- Android unit tests, lint-release and signed release assembly green.
- Live check: dashboard `opportunity_cost` present, `/api/v2/manager/priorities`
  returns a ranked list, Trainer 360 `feedback` block carries `feedback_trend`
  / `feedback_trend_direction` / `feedback_themes`.
