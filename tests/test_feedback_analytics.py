"""`_feedback_analytics` — deterministic trend + theme analysis over learner
feedback rows (RMS key 244). Trend is per-month, direction compares the last 3
available months to the prior 3 (+/- 0.2), themes are keyword clusters.
"""
from unittest.mock import patch

import backend

E = "t@koenig-solutions.com"


def _row(date, mcq, text):
    return {"TrainerEmail": E, "FeedBackDate": date + "T09:00:00",
            "Question": "Overall Rating for Instructor", "MCQAnswer": mcq,
            "TextAnswer": text}


# 6 months, ratings climbing ~3.0 -> ~4.7 => "improving"
_ROWS = [
    _row("2026-03-04", 3, "The pace was far too fast and rushed through the material."),
    _row("2026-03-18", 3, "Wanted more depth, the content stayed quite basic."),
    _row("2026-04-07", 3, "Labs were rushed and we skipped several hands-on exercises."),
    _row("2026-04-20", 4, "Explanations got clearer this time and easier to understand."),
    _row("2026-05-06", 4, "Good practical lab exercises and demos throughout the session."),
    _row("2026-05-19", 4, "Clear communication and the trainer explained concepts well."),
    _row("2026-06-03", 5, "Excellent depth and detail, went well beyond the basics."),
    _row("2026-06-16", 5, "Very engaging and interactive, patient with every question."),
    _row("2026-07-08", 5, "Clear, articulate explanations; nothing was confusing at all."),
    _row("2026-07-22", 4, "Great hands-on practice and practical demos."),
    _row("2026-08-05", 5, "Deep subject matter knowledge, a real expert in the field."),
    _row("2026-08-21", 5, "Engaging, responsive and genuinely helpful throughout."),
    # noise: another trainer must be ignored
    {"TrainerEmail": "other@koenig-solutions.com", "FeedBackDate": "2026-08-25T09:00:00",
     "Question": "Overall Rating for Instructor", "MCQAnswer": 1, "TextAnswer": "slow and confusing"},
]


def test_trend_series_and_direction():
    with patch.object(backend, "_rms", return_value=_ROWS):
        out = backend._feedback_analytics(E)

    months = [t["month"] for t in out["trend"]]
    assert months == ["Mar 2026", "Apr 2026", "May 2026", "Jun 2026", "Jul 2026", "Aug 2026"]
    assert all(t["count"] == 2 for t in out["trend"])
    assert out["trend"][0]["avg_rating"] == 3.0
    assert out["trend"][-1]["avg_rating"] == 5.0
    assert out["trend_direction"] == "improving"


def test_themes_detected_with_sentiment():
    with patch.object(backend, "_rms", return_value=_ROWS):
        out = backend._feedback_analytics(E)

    by = {t["theme"]: t for t in out["themes"]}
    assert "pace" in by
    assert by["pace"]["sentiment"] == "constructive"   # early low-rated rows
    assert "labs/hands-on" in by
    assert by["labs/hands-on"]["sentiment"] == "positive"
    assert all(t["mentions"] >= 1 and t["sample"] for t in out["themes"])
    assert len(out["themes"]) <= 5


def test_surfaced_on_detail_helper():
    with patch.object(backend, "_rms", return_value=_ROWS):
        d = backend._trainer_feedback_detail(E)
    assert "trend" in d and "trend_direction" in d and "themes" in d
    assert d["avg_rating"] is not None


def test_declining_direction():
    rows = [
        _row("2026-03-04", 5, "clear and engaging"), _row("2026-03-18", 5, "clear and engaging"),
        _row("2026-04-04", 5, "clear and engaging"), _row("2026-04-18", 5, "clear and engaging"),
        _row("2026-05-04", 3, "pace too fast"), _row("2026-05-18", 3, "pace too fast"),
        _row("2026-06-04", 3, "pace too fast"), _row("2026-06-18", 2, "rushed labs"),
    ]
    with patch.object(backend, "_rms", return_value=rows):
        out = backend._feedback_analytics(E)
    assert out["trend_direction"] == "declining"
