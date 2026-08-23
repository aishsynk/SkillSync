import pytest
from backend import app, _generate_session_token, _verify_session_token, _sessions


def test_hmac_session_token_persistence():
    token = _generate_session_token("manager@koenig-solutions.com", "Delivery Manager")
    assert token and "." in token
    
    # Simulate server restart by clearing in-memory _sessions dict
    _sessions.clear()
    assert token not in _sessions
    
    # Verification should revive the session from HMAC signature
    session = _verify_session_token(token)
    assert session is not None
    assert session["email"] == "manager@koenig-solutions.com"
    assert session["role"] == "Delivery Manager"
    assert token in _sessions


def test_hmac_session_token_tampering_rejected():
    token = _generate_session_token("manager@koenig-solutions.com", "Delivery Manager")
    parts = token.split(".")
    tampered_token = f"{parts[0]}.wrongsig123"
    _sessions.clear()
    
    session = _verify_session_token(tampered_token)
    assert session is None


def test_calendar_v2_requires_auth():
    with app.test_client() as client:
        resp = client.get('/api/v2/team/calendar?email=manager@koenig-solutions.com')
        assert resp.status_code == 401


def test_growth_benchmark_requires_auth():
    with app.test_client() as client:
        resp = client.get('/api/v2/trainer/growth-benchmark?email=trainer@koenig-solutions.com&manager=manager@koenig-solutions.com')
        assert resp.status_code == 401


def test_curriculum_v2_endpoint_validation():
    with app.test_client() as client:
        token = _generate_session_token("manager@koenig-solutions.com", "Delivery Manager")
        headers = {"Authorization": f"Bearer {token}"}
        
        # Missing params
        resp = client.get('/api/v2/course/curriculum', headers=headers)
        assert resp.status_code == 400
        
        # Valid courseName
        resp = client.get('/api/v2/course/curriculum?courseName=AZ-104T00', headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert "course_name" in data
        assert "modules" in data
        assert "content_resources" in data


def test_network_trainers_v2_endpoint():
    with app.test_client() as client:
        token = _generate_session_token("manager@koenig-solutions.com", "Delivery Manager")
        headers = {"Authorization": f"Bearer {token}"}
        
        # Missing course param
        resp = client.get('/api/v2/network/trainers', headers=headers)
        assert resp.status_code == 400
        
        # Valid search
        resp = client.get('/api/v2/network/trainers?course=AZ-104', headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert "course" in data
        assert "trainers" in data
