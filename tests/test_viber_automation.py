import json
import pytest
from backend import app, _viber_queue_build, _viber_dispatch_item


@pytest.fixture
def client():
    app.config["TESTING"] = True
    with app.test_client() as client:
        yield client


def test_viber_queue_matches_reportee_and_composes_house_style_messages(monkeypatch):
    """
    Test that _viber_queue_build extracts unallocated demand, matches against
    reportees, and drafts compliant house-style Viber messages.
    """
    def mock_rms(api, payload):
        if api == "reportees":
            return [
                {
                    "TrainerName": "Subhashish Bhattacharjee",
                    "OffEmail": "subhashish.bhattacharjee@koenig-solutions.com",
                    "Mobile": "+919876543210",
                }
            ]
        elif api == "trainerSkills":
            return [
                {"CourseName": "DP-203T00: Data Engineering on Microsoft Azure"}
            ]
        elif api == "unallocated":
            return [
                {
                    "DemandId": "DEM-8921",
                    "Course": "DP-203T00: Data Engineering on Microsoft Azure",
                    "StartDate": "2026-09-15",
                    "EndDate": "2026-09-18",
                    "Mode": "Virtual",
                    "Pax": "8",
                    "language": "English",
                }
            ]
        elif api == "recordingDetails":
            return []
        return []

    monkeypatch.setattr("backend._rms", mock_rms)

    result = _viber_queue_build("aishwar_c@koenig-solutions.com")
    assert result["total_queued"] >= 2  # At least 1 demand + 1 weekly standpoint
    
    demand_item = next((it for it in result["items"] if it["category"] == "UNALLOCATED_DEMAND"), None)
    assert demand_item is not None
    assert demand_item["recipient_email"] == "subhashish.bhattacharjee@koenig-solutions.com"
    assert "Hello Subhashish" in demand_item["message_text"]
    assert "DP-203T00" in demand_item["message_text"]
    assert "*mark your skill in RMS at level 4 or below*" in demand_item["message_text"]
    assert "_Thank you._" in demand_item["message_text"]


def test_viber_dispatch_endpoint(client):
    """
    Test POST /api/v2/viber/dispatch dispatches items and returns success receipts.
    """
    payload = {
        "items": [
            {
                "id": "viber_test_001",
                "recipient_email": "subhashish.bhattacharjee@koenig-solutions.com",
                "recipient_phone": "+919876543210",
                "message_text": "Hello Subhashish, test message."
            }
        ]
    }
    resp = client.post("/api/v2/viber/dispatch", data=json.dumps(payload), content_type="application/json")
    assert resp.status_code == 200
    data = resp.get_json()
    assert data["status"] == "ok"
    assert data["total_dispatched"] == 1
    assert data["results"][0]["status"] == "SENT"


def test_viber_config_persistence(client):
    """
    Test GET & POST /api/v2/viber/config persists manager automation preferences.
    """
    cfg_data = {
        "email": "manager@koenig-solutions.com",
        "auto_send_demand": True,
        "auto_send_weekly": True,
        "dispatch_mode": "VIBER_BOT_API",
        "viber_bot_token": "mock-token-xyz-123",
        "webhook_url": "https://hooks.koenig.com/viber",
    }
    post_resp = client.post("/api/v2/viber/config", data=json.dumps(cfg_data), content_type="application/json")
    assert post_resp.status_code == 200
    
    get_resp = client.get("/api/v2/viber/config?email=manager@koenig-solutions.com")
    assert get_resp.status_code == 200
    saved = get_resp.get_json()
    assert saved["viber_bot_token"] == "mock-token-xyz-123"
    assert saved["dispatch_mode"] == "VIBER_BOT_API"
