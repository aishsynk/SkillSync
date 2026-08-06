"""
SkillSync Backend API
Runs on Vercel - 24/7 serverless Python backend for Android app
"""

from flask import Flask, jsonify, request
from flask_cors import CORS
from datetime import datetime
import json

app = Flask(__name__)
CORS(app)

# Mock data for Phase 1
MOCK_MANAGER = {
    "id": "mgr_001",
    "name": "John Manager",
    "email": "john.manager@company.com",
    "role": "Delivery Manager"
}

MOCK_TRAINERS = [
    {
        "id": "trainer_001",
        "name": "Alice Smith",
        "skills": ["Python", "Django", "SQL"],
        "utilization": 85,
        "status": "Active"
    },
    {
        "id": "trainer_002",
        "name": "Bob Johnson",
        "skills": ["Java", "Spring", "AWS"],
        "utilization": 72,
        "status": "Active"
    },
    {
        "id": "trainer_003",
        "name": "Carol Davis",
        "skills": ["React", "TypeScript", "Node.js"],
        "utilization": 90,
        "status": "Active"
    }
]

MOCK_KPIS = {
    "total_trainers": len(MOCK_TRAINERS),
    "active_trainers": 3,
    "avg_utilization": 82,
    "pending_actions": 5,
    "completion_rate": 94
}

MOCK_ACTIONS = [
    {
        "id": "action_001",
        "type": "allocation",
        "description": "Allocate trainer to Project X",
        "priority": "high",
        "status": "pending",
        "trainer": "Alice Smith"
    },
    {
        "id": "action_002",
        "type": "training",
        "description": "Update skill: AWS Certification",
        "priority": "medium",
        "status": "pending",
        "trainer": "Bob Johnson"
    },
    {
        "id": "action_003",
        "type": "review",
        "description": "Performance review due",
        "priority": "medium",
        "status": "pending",
        "trainer": "Carol Davis"
    }
]

# ============================================================================
# Health & Status Endpoints
# ============================================================================

@app.route('/healthz', methods=['GET'])
def healthz():
    """Health check endpoint"""
    return jsonify({
        "status": "ok",
        "service": "SkillSync Backend",
        "version": "1.0.0",
        "environment": "production",
        "timestamp": datetime.utcnow().isoformat()
    }), 200

@app.route('/api/status', methods=['GET'])
def status():
    """Service status"""
    return jsonify({
        "status": "running",
        "uptime": "24/7",
        "database": "connected",
        "cache": "ready"
    }), 200

# ============================================================================
# Authentication Endpoints
# ============================================================================

@app.route('/api/auth/login', methods=['POST'])
def login():
    """
    Login endpoint
    Body: {"email": "user@company.com"}
    """
    try:
        data = request.get_json()
        email = data.get('email', '')

        if not email:
            return jsonify({"error": "Email required"}), 400

        if '@company.com' not in email:
            return jsonify({"error": "Invalid email domain"}), 401

        # Mock session
        return jsonify({
            "success": True,
            "session_id": "sess_" + email.replace("@", "_").replace(".", "_"),
            "user": MOCK_MANAGER,
            "message": "Login successful"
        }), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/api/auth/logout', methods=['POST'])
def logout():
    """Logout endpoint"""
    return jsonify({
        "success": True,
        "message": "Logged out successfully"
    }), 200

# ============================================================================
# Dashboard Endpoints
# ============================================================================

@app.route('/api/dashboard/kpis', methods=['GET'])
def get_kpis():
    """Get KPI cards data"""
    return jsonify({
        "kpis": [
            {
                "title": "Active Trainers",
                "value": MOCK_KPIS["active_trainers"],
                "change": "+2",
                "trend": "up"
            },
            {
                "title": "Avg Utilization",
                "value": f"{MOCK_KPIS['avg_utilization']}%",
                "change": "+5%",
                "trend": "up"
            },
            {
                "title": "Pending Actions",
                "value": MOCK_KPIS["pending_actions"],
                "change": "3 new",
                "trend": "neutral"
            },
            {
                "title": "Completion Rate",
                "value": f"{MOCK_KPIS['completion_rate']}%",
                "change": "+2%",
                "trend": "up"
            }
        ],
        "last_updated": datetime.utcnow().isoformat()
    }), 200

@app.route('/api/dashboard/summary', methods=['GET'])
def get_dashboard_summary():
    """Get dashboard summary (unified intelligence payload)"""
    return jsonify({
        "manager": MOCK_MANAGER,
        "kpis": MOCK_KPIS,
        "trainers_count": len(MOCK_TRAINERS),
        "actions_pending": len(MOCK_ACTIONS),
        "last_sync": datetime.utcnow().isoformat()
    }), 200

# ============================================================================
# Trainer Endpoints
# ============================================================================

@app.route('/api/trainers', methods=['GET'])
def get_trainers():
    """Get all trainers"""
    return jsonify({
        "trainers": MOCK_TRAINERS,
        "total": len(MOCK_TRAINERS),
        "last_updated": datetime.utcnow().isoformat()
    }), 200

@app.route('/api/trainers/<trainer_id>', methods=['GET'])
def get_trainer(trainer_id):
    """Get specific trainer details"""
    trainer = next((t for t in MOCK_TRAINERS if t['id'] == trainer_id), None)

    if not trainer:
        return jsonify({"error": "Trainer not found"}), 404

    return jsonify({
        "trainer": trainer,
        "details": {
            "assignments": [
                {"project": "Project X", "role": "Lead", "status": "active"},
                {"project": "Project Y", "role": "Contributor", "status": "active"}
            ],
            "performance": {
                "rating": 4.5,
                "reviews": 12,
                "last_review": "2026-07-15"
            },
            "certifications": [
                {"name": "AWS Solutions Architect", "date": "2025-06-20", "status": "active"}
            ]
        }
    }), 200

# ============================================================================
# Actions Endpoints
# ============================================================================

@app.route('/api/actions', methods=['GET'])
def get_actions():
    """Get all pending actions"""
    return jsonify({
        "actions": MOCK_ACTIONS,
        "total": len(MOCK_ACTIONS),
        "pending": len([a for a in MOCK_ACTIONS if a['status'] == 'pending'])
    }), 200

@app.route('/api/actions/<action_id>', methods=['GET'])
def get_action(action_id):
    """Get specific action"""
    action = next((a for a in MOCK_ACTIONS if a['id'] == action_id), None)

    if not action:
        return jsonify({"error": "Action not found"}), 404

    return jsonify({"action": action}), 200

@app.route('/api/actions/<action_id>/update', methods=['POST'])
def update_action(action_id):
    """Update action status"""
    try:
        data = request.get_json()
        action = next((a for a in MOCK_ACTIONS if a['id'] == action_id), None)

        if not action:
            return jsonify({"error": "Action not found"}), 404

        # Update status
        new_status = data.get('status', action['status'])
        action['status'] = new_status

        return jsonify({
            "success": True,
            "action": action,
            "message": f"Action updated to {new_status}"
        }), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 500

# ============================================================================
# Data Endpoint (Unified Intelligence Payload)
# ============================================================================

@app.route('/api/data/unified-manager-intelligence', methods=['GET'])
def unified_intelligence():
    """
    Single endpoint providing all data the Android app needs
    This is the main data endpoint for the dashboard
    """
    return jsonify({
        "manager": MOCK_MANAGER,
        "kpis": MOCK_KPIS,
        "trainers": MOCK_TRAINERS,
        "actions": MOCK_ACTIONS,
        "summary": {
            "total_trainers": len(MOCK_TRAINERS),
            "active_trainers": len([t for t in MOCK_TRAINERS if t['status'] == 'Active']),
            "avg_utilization": MOCK_KPIS['avg_utilization'],
            "pending_actions": len([a for a in MOCK_ACTIONS if a['status'] == 'pending'])
        },
        "cache": {
            "age": 0,
            "ttl": 3600,
            "source": "live"
        },
        "timestamp": datetime.utcnow().isoformat()
    }), 200

# ============================================================================
# Error Handlers
# ============================================================================

@app.errorhandler(404)
def not_found(error):
    """Handle 404 errors"""
    return jsonify({
        "error": "Endpoint not found",
        "path": request.path,
        "method": request.method
    }), 404

@app.errorhandler(500)
def internal_error(error):
    """Handle 500 errors"""
    return jsonify({
        "error": "Internal server error",
        "message": str(error)
    }), 500

# ============================================================================
# Root Endpoint
# ============================================================================

@app.route('/', methods=['GET'])
def root():
    """API documentation"""
    return jsonify({
        "name": "SkillSync Backend API",
        "version": "1.0.0",
        "documentation": {
            "health": "GET /healthz",
            "status": "GET /api/status",
            "login": "POST /api/auth/login",
            "logout": "POST /api/auth/logout",
            "kpis": "GET /api/dashboard/kpis",
            "dashboard": "GET /api/dashboard/summary",
            "trainers": "GET /api/trainers",
            "trainer_detail": "GET /api/trainers/{id}",
            "actions": "GET /api/actions",
            "action_detail": "GET /api/actions/{id}",
            "action_update": "POST /api/actions/{id}/update",
            "unified_data": "GET /api/data/unified-manager-intelligence"
        },
        "base_url": request.host_url,
        "running_on": "Vercel (24/7)"
    }), 200

# ============================================================================
# Main
# ============================================================================

if __name__ == '__main__':
    # Development
    app.run(debug=False, port=8080)
