"""Certification graph scaffolding for SkillEdge."""


def normalize_cert_name(value):
    return " ".join(str(value or "").strip().split())


def build_cert_node(cert):
    return {
        "certification_name": normalize_cert_name(cert.get("name") or cert.get("certification") or ""),
        "vendor": cert.get("vendor") or cert.get("vendor_name") or "",
        "exam_required": cert.get("exam_required"),
        "courses": cert.get("courses") or [],
    }


def get_certification_links(cert):
    node = build_cert_node(cert)
    return node.get("courses", [])
