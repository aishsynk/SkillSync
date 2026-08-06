"""Stable constants for the SkillEdge backend."""

API_BASE = "https://api.koenig-solutions.com"
TOKEN_ENDPOINT = "/api/Kites/Operator/GetToken"
DATA_ENDPOINT = "/api/Kites/Operator/common"
DEFAULT_TIMEOUT = 40
CACHE_TTL_SECONDS = 4 * 60 * 60

DATASET_NAMES = (
    "trainer_operations_df",
    "course_allocation_df",
    "trainer_timeline_df",
    "manager_action_df",
    "trainer_availability_engine_df",
    "custom_course_match_df",
    "data_health_df",
)
