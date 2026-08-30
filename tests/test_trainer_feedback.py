"""`_trainer_feedback_detail` — real learner feedback (RMS key 244).

The RMS endpoint ignores its TrainerEmail filter and returns the whole recent
feedback set, so the helper must filter by email itself, classify by the 1-5
MCQ rating, and never invent text.
"""
from unittest.mock import patch

import backend

_ROWS = [
    {"TrainerEmail": "a@koenig-solutions.com", "FeedBackDate": "2026-08-20T10:00:00",
     "Question": "Overall Rating for Instructor", "MCQAnswer": 5,
     "TextAnswer": "Excellent trainer. Explained Kubernetes networking with clear real-world examples and was very patient."},
    {"TrainerEmail": "a@koenig-solutions.com", "FeedBackDate": "2026-08-10T10:00:00",
     "Question": "How would you rate the trainer's ability to deliver the subject effectively?",
     "MCQAnswer": 2, "TextAnswer": "The pace was too fast and we could not finish the lab exercises in time."},
    {"TrainerEmail": "OTHER@koenig-solutions.com", "FeedBackDate": "2026-08-25T10:00:00",
     "Question": "Overall Rating for Instructor", "MCQAnswer": 1,
     "TextAnswer": "Different trainer entirely — must not appear for a@."},
    {"TrainerEmail": "a@koenig-solutions.com", "FeedBackDate": "2024-01-01T10:00:00",
     "Question": "Overall Rating for Instructor", "MCQAnswer": 4, "TextAnswer": None},
]


def test_filters_by_email_and_classifies_by_rating():
    with patch.object(backend, "_rms", return_value=list(_ROWS)):
        fb = backend._trainer_feedback_detail("A@koenig-solutions.com")

    # "OTHER@" row is excluded despite the endpoint returning it
    joined = " ".join(q["text"] for q in fb["quotes"])
    assert "must not appear" not in joined

    assert fb["response_count"] == 3            # three MCQ rows for a@
    assert fb["avg_rating"] == round((5 + 2 + 4) / 3, 1)
    assert fb["recent_date"] == "2026-08-20"
    assert any("Kubernetes networking" in q["text"] for q in fb["positive_quotes"])
    assert any("pace was too fast" in q["text"] for q in fb["constructive_quotes"])


def test_since_window_excludes_old_rows():
    with patch.object(backend, "_rms", return_value=list(_ROWS)):
        fb = backend._trainer_feedback_detail("a@koenig-solutions.com", days=90)
    # the 2024 row is outside the 90-day window
    assert fb["response_count"] == 2
    assert fb["avg_rating"] == round((5 + 2) / 2, 1)


def test_empty_when_no_rows():
    with patch.object(backend, "_rms", return_value=[]):
        fb = backend._trainer_feedback_detail("a@koenig-solutions.com")
    assert fb["avg_rating"] is None
    assert fb["quotes"] == []
    assert fb["response_count"] == 0
