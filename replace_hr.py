import os
import re

frontend_dir = r"C:\Users\Aishw\OneDrive - Koenig Solutions Ltd\SkillEdge\frontend"

replacements = [
    # Visual UI labels
    ("HR Hold", "HR Flag"),
    ("HR Risk", "HR Flag"),
    ("HR risk", "HR flag"),
    ("HR Incident", "HR Flag"),
    ("HR Incidents", "HR Flags"),
    ("HR incident", "HR flag"),
    ("HR/Feedback", "Review/Feedback"),
    ("HR/Quality", "Review/Quality"),
    ("HR risk status", "HR flag status"),
]

def process_file(filepath):
    try:
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()

        new_content = content
        for old, new in replacements:
            new_content = new_content.replace(old, new)
            
        if new_content != content:
            with open(filepath, 'w', encoding='utf-8', errors='ignore') as f:
                f.write(new_content)
            print(f"Updated {filepath}")
    except Exception as e:
        print(f"Failed to process {filepath}: {e}")

for root, _, files in os.walk(frontend_dir):
    for filename in files:
        if (filename.endswith(".html") or filename.endswith(".js")) and not filename.endswith(".min.js"):
            filepath = os.path.join(root, filename)
            process_file(filepath)
print("Replacement complete.")
