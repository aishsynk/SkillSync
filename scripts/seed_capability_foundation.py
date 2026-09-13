"""One-off Phase 1 seed: canonical capability taxonomy + DRAFT course profiles
for the 8 Phase-0 live-verified validation courses.

Run manually: `python scripts/seed_capability_foundation.py`

Everything this script writes lands as `MappingStatus.DRAFT`
(`source="agent_authored_pending_human_review"`) — NOT APPROVED. Per the
Phase 1 instruction, an agent may produce a first curated draft from real,
publicly documented exam/course syllabi (not from the course title string),
but only a named human curator can move a course profile to APPROVED, which
is the only status a future matching engine may treat as authoritative.

Capability requirements below are drawn from each vendor's own public exam
objectives/skills-measured pages for these courses (Microsoft Learn exam
pages for DP-700/DP-600/AI-102/AZ-104/SC-300, Cisco's CCNA 200-301 exam
topics, AWS's SAA-C03 exam guide, and ISC2's CISSP domains) — not inferred
from the course title, per the instruction's explicit constraint.
"""

from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from domain.capability.models import MappingStatus  # noqa: E402
from repositories.capability_store import CapabilityStore  # noqa: E402
from services.capability.capability_service import CapabilityService  # noqa: E402

SEED_SOURCE = "agent_authored_pending_human_review"
SEED_BY = "phase1_seed_script"


# ── Canonical capabilities: (id, name, family) ───────────────────────────────
CAPABILITIES = [
    # Data Engineering / Fabric family
    ("microsoft_fabric", "Microsoft Fabric", "Data Engineering"),
    ("lakehouse", "Lakehouse", "Data Engineering"),
    ("onelake", "OneLake", "Data Engineering"),
    ("fabric_pipelines", "Fabric Data Pipelines", "Data Engineering"),
    ("fabric_notebooks", "Fabric Notebooks", "Data Engineering"),
    ("apache_spark", "Apache Spark", "Data Engineering"),
    ("delta_lake", "Delta Lake", "Data Engineering"),
    ("data_ingestion", "Data Ingestion", "Data Engineering"),
    ("data_transformation", "Data Transformation", "Data Engineering"),
    ("data_warehouse_fabric", "Fabric Data Warehouse", "Data Engineering"),
    ("power_bi_semantic_model", "Power BI Semantic Models", "Data Analytics"),
    ("dax", "DAX", "Data Analytics"),
    ("data_governance_fabric", "Fabric Data Governance", "Data Analytics"),
    # AI
    ("azure_ai_services", "Azure AI Services", "Artificial Intelligence"),
    ("azure_openai", "Azure OpenAI Service", "Artificial Intelligence"),
    ("computer_vision", "Computer Vision (Azure AI Vision)", "Artificial Intelligence"),
    ("nlp_language_service", "Natural Language Processing (Azure AI Language)", "Artificial Intelligence"),
    ("azure_ai_search", "Azure AI Search", "Artificial Intelligence"),
    ("responsible_ai", "Responsible AI", "Artificial Intelligence"),
    # Azure infra
    ("azure_identity_governance", "Azure Identity & Governance", "Cloud Infrastructure"),
    ("azure_storage", "Azure Storage", "Cloud Infrastructure"),
    ("azure_compute", "Azure Compute", "Cloud Infrastructure"),
    ("azure_virtual_networking", "Azure Virtual Networking", "Cloud Infrastructure"),
    ("azure_monitor_backup", "Azure Monitoring & Backup", "Cloud Infrastructure"),
    # Identity
    ("entra_id", "Microsoft Entra ID", "Identity & Access Management"),
    ("identity_governance", "Identity Governance", "Identity & Access Management"),
    ("access_management", "Access Management (RBAC/Conditional Access)", "Identity & Access Management"),
    ("identity_security", "Identity Security & Threat Protection", "Identity & Access Management"),
    # Networking (Cisco)
    ("network_fundamentals", "Network Fundamentals", "Networking"),
    ("network_access", "Network Access (VLAN/Trunking)", "Networking"),
    ("ip_connectivity", "IP Connectivity & Routing", "Networking"),
    ("ip_services", "IP Services (NAT/NTP/DHCP)", "Networking"),
    ("network_security_fundamentals", "Network Security Fundamentals", "Networking"),
    ("network_automation", "Network Automation & Programmability", "Networking"),
    # AWS
    ("aws_resilient_architecture", "Designing Resilient Architectures", "Cloud Architecture"),
    ("aws_high_performing_architecture", "Designing High-Performing Architectures", "Cloud Architecture"),
    ("aws_secure_architecture", "Designing Secure Architectures", "Cloud Architecture"),
    ("aws_cost_optimized_architecture", "Designing Cost-Optimized Architectures", "Cloud Architecture"),
    # Security (CISSP, vendor-neutral)
    ("security_risk_management", "Security & Risk Management", "Security"),
    ("asset_security", "Asset Security", "Security"),
    ("security_architecture", "Security Architecture & Engineering", "Security"),
    ("comms_network_security", "Communication & Network Security", "Security"),
    ("iam_security_domain", "Identity & Access Management (Security)", "Security"),
    ("security_assessment_testing", "Security Assessment & Testing", "Security"),
    ("security_operations", "Security Operations", "Security"),
    ("software_dev_security", "Software Development Security", "Security"),
]

ALIASES = {
    "microsoft_fabric": ["Fabric", "MS Fabric", "Fabric Analytics"],
    "apache_spark": ["Spark", "PySpark", "Spark SQL"],
    "entra_id": ["Azure AD", "Azure Active Directory", "AAD"],
    "azure_ai_services": ["Azure Cognitive Services", "Cognitive Services"],
}

RELATIONSHIPS = [
    ("lakehouse", "microsoft_fabric", "CHILD_OF"),
    ("onelake", "microsoft_fabric", "CHILD_OF"),
    ("fabric_pipelines", "microsoft_fabric", "CHILD_OF"),
    ("fabric_notebooks", "microsoft_fabric", "CHILD_OF"),
    ("data_warehouse_fabric", "microsoft_fabric", "CHILD_OF"),
    ("power_bi_semantic_model", "microsoft_fabric", "CHILD_OF"),
    ("delta_lake", "apache_spark", "RELATED_TO"),          # not equivalent — related only
    ("fabric_pipelines", "data_ingestion", "RELATED_TO"),
    ("identity_governance", "entra_id", "CHILD_OF"),
    ("access_management", "entra_id", "CHILD_OF"),
    ("identity_security", "entra_id", "CHILD_OF"),
    ("iam_security_domain", "identity_security", "RELATED_TO"),
]

# ── Course capability profiles, keyed on the Phase-0 live-verified Cid ───────
# mandatory=True unless noted. Sourced from each vendor's public exam-skills
# page, not the course title.
COURSE_PROFILES = {
    18301: {  # DP-700T00: Microsoft Fabric Data Engineer
        "course_code": "DP-700", "course_title": "DP-700T00: Microsoft Fabric Data Engineer", "vendor": "Microsoft",
        "required": ["microsoft_fabric", "lakehouse", "onelake", "fabric_pipelines",
                     "fabric_notebooks", "apache_spark", "data_ingestion", "data_transformation"],
        "preferred": ["delta_lake", "data_warehouse_fabric"],
        "required_certifications": ["DP-700"],
    },
    15509: {  # DP-600T00: Microsoft Fabric Analytics Engineer
        "course_code": "DP-600", "course_title": "DP-600T00: Microsoft Fabric Analytics Engineer", "vendor": "Microsoft",
        "required": ["microsoft_fabric", "lakehouse", "power_bi_semantic_model", "dax",
                     "data_transformation"],
        "preferred": ["data_governance_fabric", "onelake"],
        "required_certifications": ["DP-600"],
    },
    9716: {  # AI-102T00: Develop AI Solutions in Azure
        "course_code": "AI-102", "course_title": "AI-102T00: Develop AI Solutions in Azure", "vendor": "Microsoft",
        "required": ["azure_ai_services", "computer_vision", "nlp_language_service",
                     "azure_ai_search", "responsible_ai"],
        "preferred": ["azure_openai"],
        "required_certifications": ["AI-102"],
    },
    9055: {  # AZ-104T00-A: Microsoft Azure Administrator
        "course_code": "AZ-104", "course_title": "AZ-104T00-A: Microsoft Azure Administrator", "vendor": "Microsoft",
        "required": ["azure_identity_governance", "azure_storage", "azure_compute",
                     "azure_virtual_networking", "azure_monitor_backup"],
        "preferred": [],
        "required_certifications": ["AZ-104"],
    },
    9748: {  # SC-300T00: Microsoft Identity and Access Administrator
        "course_code": "SC-300", "course_title": "SC-300T00: Microsoft Identity and Access Administrator", "vendor": "Microsoft",
        "required": ["entra_id", "identity_governance", "access_management", "identity_security"],
        "preferred": [],
        "required_certifications": ["SC-300"],
    },
    11405: {  # Cisco Certified Network Associate (200-301 CCNA) Extended
        "course_code": "", "course_title": "Cisco Certified Network Associate (200-301 CCNA) Extended", "vendor": "Cisco Non Standard",
        "required": ["network_fundamentals", "network_access", "ip_connectivity",
                     "ip_services", "network_security_fundamentals"],
        "preferred": ["network_automation"],
        "required_certifications": ["CCNA"],
    },
    899: {  # AWS Certified Solutions Architect - Associate (Architecting on AWS)
        "course_code": "", "course_title": "AWS Certified Solutions Architect - Associate (Architecting on AWS)", "vendor": "AWS",
        "required": ["aws_resilient_architecture", "aws_high_performing_architecture",
                     "aws_secure_architecture", "aws_cost_optimized_architecture"],
        "preferred": [],
        "required_certifications": ["AWS-SAA"],
    },
    742: {  # Certified Information Systems Security Professional (CISSP)
        "course_code": "", "course_title": "Certified Information Systems Security Professional (CISSP)", "vendor": "ISC2",
        "required": ["security_risk_management", "asset_security", "security_architecture",
                     "comms_network_security", "iam_security_domain",
                     "security_assessment_testing", "security_operations", "software_dev_security"],
        "preferred": [],
        "required_certifications": ["CISSP"],
    },
}


def main():
    store = CapabilityStore(os.path.join(os.getenv("SKILLEDGE_STATE_DIR", "."), "skilledge_capability.sqlite3"))
    service = CapabilityService(store)

    for cid, name, family in CAPABILITIES:
        store.add_capability(cid, name, family)
    for cap_id, aliases in ALIASES.items():
        for alias in aliases:
            store.add_alias(cap_id, alias)
    for from_id, to_id, rel_type in RELATIONSHIPS:
        store.add_relationship(from_id, to_id, rel_type)

    for cid, profile in COURSE_PROFILES.items():
        requirements = (
            [{"capability_id": c, "mandatory": True} for c in profile["required"]]
            + [{"capability_id": c, "mandatory": False} for c in profile["preferred"]]
        )
        ok = service.submit_course_capability_draft(
            rms_cid=cid, requirements=requirements,
            required_certifications=profile["required_certifications"],
            course_code=profile["course_code"], course_title=profile["course_title"],
            vendor=profile["vendor"], curator_email=SEED_BY,
        )
        print(f"cid={cid:<6} {profile['course_title'][:50]:<50} draft_written={ok}")

    print("\nAll rows written as DRAFT / source=agent_authored_pending_human_review.")
    print("Nothing here is APPROVED — a named human curator must review and approve each")
    print("before it may influence any future allocation decision.")


if __name__ == "__main__":
    main()
