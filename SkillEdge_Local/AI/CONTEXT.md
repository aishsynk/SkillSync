# Project Context

## Architecture
- **Type**: Local web application (Manager CRM).
- **Backend**: Python 3.x acting as a unified API and static file server (`backend/app.py`). It aggregates external API data, utilizes pandas/scikit-learn for processing, and contains an integrated "Agent Copilot" module (`agentic/`).
- **Frontend**: Multi-page application built with HTML, CSS (Sean Theme), and Vanilla JavaScript (`frontend/`).
- **Data Flow**: Frontend `api.js` calls local Python backend API endpoints. Backend securely holds tokens, calls external APIs, processes data, caches it locally, and serves structured JSON to the frontend.

## External APIs (Trainer Portal)
- **Authentication**: Requires a standard 2-step process (Get Token -> Access API with Token).
- **Domains**: 
  - **Trainers & Skills**: IDs, skills, resumes, utilization, and availability.
  - **Courses & Curriculum**: Outlines, modules, syllabus, and exam linkages.
  - **Assignments & Schedules**: Upcoming/previous assignments, pax lists, schedules.
  - **Feedback & Quality**: Student feedback, negative feedback counts, HR incidents.
  - **Operations**: Sales contracts (SCID), recording URLs.
