# SkillEdge Production Release v3.53.0 (Build 140)

- **Release Date**: 2026-08-31
- **Version Code**: `140`
- **Version Name**: `3.53.0`

## Why this release

The auto-generated manager messages were wrong for a group broadcast and came in
only one cadence. This release makes them correct and gives the manager the four
messages they actually send.

## What changed

### Four message cadences
Every message - team broadcast and per-reportee - now comes in four forms:
- **This week** (Monday): the week ahead - delivery load, open demand, the one ask.
- **Weekend** (Friday): the wrap - what was delivered, learner feedback, thanks, a next-week line.
- **This month** (1st): the month plan.
- **Month end** (last day): the review - utilisation, delivery, quality, wins.

The Weekly and HR Monthly screens have a segmented toggle ("This week / Weekend",
"This month / Month end"); the message block and every reportee card follow it.

### Group messages never single out an individual
A team broadcast now states bench, feedback flags and certification gaps as
aggregate counts only - "2 of us are free", "1 feedback point is being handled
individually". A name appears in a group message only as recognition -
"thanks to Krishna for consistently high learner feedback".

### The message is the headline
On both report screens the composed message moved from a small greyed footnote to
a titled card at readable size with a clear "Copy for Teams" / "Copy for Viber"
button. The [My Message] rewrite studio is now behind the card's expand.

### Fixes
- A trainer actively delivering a batch is no longer flagged "on the bench"
  because of a low RMS utilisation reading - the opportunity push was landing on
  people already in front of a class.
- `_skills()` row key mismatch (callers read `course_name`, rows keyed `course`)
  that was silently breaking every opportunity match.
- The manager email is resolved and propagated through every v2 route, and the
  routes tolerate an empty session.
- "1 batches" -> "1 batch"; the many-certification-gaps line asks to prioritise
  the ones tied to open demand rather than an impossible "book all this week".

## Compatibility

- Android package and signing identity unchanged; build 140 installs over 139.

## Validation

- Backend pytest 208 pass (+5 group-safety / cadence tests).
- Android compileReleaseKotlin + testDebugUnitTest green.
- Verified against aishwar_c@koenig-solutions.com (resolves to aishwar.c@, real
  2-person team): all four cadences render, the weekend team digest is
  backward-looking and names no one negatively.
