# SkillEdge Production Release v3.50.0 (Build 136)

- **Release Date**: 2026-08-31
- **Version Code**: `136`
- **Version Name**: `3.50.0`

## Why this release

The weekly and monthly report text read like a machine wrote it — a stack of
labelled facts ("Standpoint: Active Delivery on AB-100T00 (34.5% util)",
"Immediate Focus: delivering ... this week. No action needed"). The v3.48.0
rewrite engine only rewrote a message the manager typed by hand; the
auto-generated report text was never routed through it.

## What changed

### Messages are composed, not listed
Every message the app produces for the team now goes through a new deterministic
composer (`_compose_manager_message`) that turns the analysed data into prose a
manager would actually send on Teams or Viber: a greeting line, a short body,
and a closing line, following the house style — no emojis, bullets or hyphens,
one bold key action, one underlined time reference, at most 1000 characters,
tone that shifts between appreciative, advisory, corrective and urgent based on
what the data says.

Four message types:
- **Reportee, weekly** — replaces `standpoint_note`
- **Reportee, monthly** — added to the HR monthly `structured_feedback`
- **Team, weekly** — replaces `team_digest`
- **Team, monthly** — added to the HR monthly report

Each message is built from: current and upcoming delivery, utilisation, Qubits
knowledge score, learner rating and a dated learner quote, certification gaps,
quality flags, and — new — **opportunity cost**: open unallocated demand whose
course the trainer (or someone on the team) already teaches but is not assigned
to. A bench week with matching open demand now reads "There are 2 open batches
that match your work on Azure Administrator. Please confirm your availability so
I can put you forward."

### [My Message] overlay
New endpoint `GET /api/v2/message/compose` returns the composed message for a
reportee or the team, with an optional `my_message` from the manager woven in as
the lead. The Weekly and HR Monthly screens' compose buttons now call this
instead of the raw-text rewrite, so the manager's note is added on top of the
real analysis rather than replacing it.

## Compatibility

- Android package and signing identity unchanged; build 136 installs over 135.

## Validation

- Backend pytest suite green (175, including the new `tests/test_manager_messages.py`).
- Android unit tests, lint-release and signed release assembly green.
- Live check: the composed team digest and per-reportee messages read as natural
  manager prose and stay within the house-style rules.
