"""Explainability helpers for SkillEdge."""

def health_dataset_for(api_name):
    a = (api_name or "").lower()
    if "availability engine" in a:
        return "trainer_availability_engine_df"
    if "intelligence engine" in a:
        return "trainer_operations_df, trainer_availability_engine_df"
    if "assignment" in a:
        return "course_allocation_df, trainer_timeline_df"
    if "feedback" in a or "hr" in a:
        return "trainer_operations_df, manager_action_df"
    return "trainer_operations_df"


def health_page_for(api_name):
    a = (api_name or "").lower()
    if "availability engine" in a:
        return "Data Health, Risk-Taker Candidates, Trainer 360"
    if "intelligence engine" in a:
        return "Data Health, Dashboard, Trainer 360"
    if "assignment" in a:
        return "Allocation Desk, Trainer 360"
    return "Trainer 360, Dashboard"


def health_impact_for(issue_type):
    i = (issue_type or "").lower()
    if "failed" in i:
        return "Signal unavailable for this trainer; dependent scores use fewer inputs and lower confidence."
    if "parse" in i or "mismatch" in i or "unexpected" in i:
        return "Response could not be parsed; the affected metric is treated as unknown."
    if "unavailable" in i or "missing" in i or i.startswith("no "):
        return "This signal is missing; affected readiness/availability is estimated with reduced confidence."
    return "Reduced data completeness for this trainer."


def health_fix_for(issue_type):
    i = (issue_type or "").lower()
    if "missing email" in i:
        return "Ensure this reportee has an official email in RMS."
    if "employee id" in i:
        return "Ensure this reportee has an EmpId in RMS."
    if "parse" in i or "mismatch" in i:
        return "Check the source API response format and adjust the normalizer."
    return "Verify the source API is reachable and returning data, then re-run refresh."


def health_scrub(msg, issue_type):
    txt = str(msg or "").strip()
    for bad in ("accessToken", "deviceToken", "userPassword", "apikey", "userName", "userRole", "Bearer"):
        if bad in txt:
            txt = txt.split(bad)[0].strip() + " [redacted]"
    txt = txt[:240]
    return txt or (issue_type or "Unknown issue")
