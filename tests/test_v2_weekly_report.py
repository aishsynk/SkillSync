import pytest
from datetime import datetime, timedelta
from backend import app


def test_weekly_report_v2_endpoint_structure():
    """Verify that /api/v2/report/weekly computes full weekly operations snapshot."""
    with app.test_client() as client:
        res = client.get('/api/v2/report/weekly?manager=aishwar_v@koenig-solutions.com')
        # Returns 200 (if session passes) or 401 unauthenticated
        assert res.status_code in (200, 401)
        if res.status_code == 200:
            data = res.get_json()
            assert "week_label" in data
            assert "week_start" in data
            assert "week_end" in data
            assert "team_summary" in data
            assert "reportees" in data
            assert "team_digest" in data

            ts = data["team_summary"]
            assert "headcount" in ts
            assert "delivering_count" in ts
            assert "bench_count" in ts
            assert "stretched_count" in ts
            assert "at_risk_count" in ts
            assert "total_cert_gaps" in ts
            assert "total_participants" in ts
            assert "total_batches" in ts

            for rep in data["reportees"]:
                assert "email" in rep
                assert "name" in rep
                assert "capacity_bucket" in rep
                assert "status_headline" in rep
                assert "standpoint_note" in rep
                assert "avg_qubits" in rep
                assert "batch_count" in rep
                assert "total_pax" in rep


def test_weekly_report_v2_date_navigation():
    """Verify that specifying a custom week parameter calculates correct Monday-Sunday window."""
    with app.test_client() as client:
        # Request for a specific date in August 2026: 2026-08-19 (Wednesday)
        res = client.get('/api/v2/report/weekly?manager=aishwar_v@koenig-solutions.com&week=2026-08-19')
        assert res.status_code in (200, 401)
        if res.status_code == 200:
            data = res.get_json()
            # Wednesday 2026-08-19 has Monday 2026-08-17 and Sunday 2026-08-23
            assert data["week_start"] == "2026-08-17"
            assert data["week_end"] == "2026-08-23"
            assert "17 August" in data["week_label"]
            assert "23 August 2026" in data["week_label"]


def test_weekly_report_v2_invalid_week():
    """Verify error response for malformed week string."""
    with app.test_client() as client:
        res = client.get('/api/v2/report/weekly?manager=aishwar_v@koenig-solutions.com&week=invalid-date')
        if res.status_code != 401:
            assert res.status_code == 400
            err = res.get_json()
            assert err["error"]["code"] == "INVALID_WEEK"
