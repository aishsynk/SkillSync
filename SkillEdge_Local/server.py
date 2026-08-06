#!/usr/bin/env python
"""SkillEdge / Manager OS — single executable entry point.

Run:  python server.py

This launcher only wires up the import path and starts the real backend
application (backend/app.py). All server behaviour — port, environment,
authentication, manager scoping, RMS relay, static serving, and the unified
intelligence endpoint — lives in the backend package and is unchanged.
"""
import sys
from pathlib import Path

# Make the backend package importable (backend/ holds app.py, api/, services/,
# shared/, intelligence.py, intelligence_engines/, knowledge/).
BACKEND_DIR = Path(__file__).resolve().parent / "backend"
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

import app  # backend/app.py  (resolved via BACKEND_DIR on sys.path)


if __name__ == "__main__":
    app.main()
