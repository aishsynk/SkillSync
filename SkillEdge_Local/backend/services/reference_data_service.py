"""Reference-data normalization for the RMS course/technology/domain graph."""

from __future__ import annotations

from typing import Any, Dict, Iterable, List


def _text(value: Any) -> str:
    return " ".join(str(value or "").strip().split())


def _course_id(row: Dict[str, Any]) -> str:
    return _text(row.get("Courseid") or row.get("CourseID") or row.get("course_id") or row.get("Cid") or row.get("CId"))


def build_course_master(
    course_rows: Iterable[Dict[str, Any]],
    *,
    detail_rows: Iterable[Dict[str, Any]] | None = None,
    technology_rows: Iterable[Dict[str, Any]] | None = None,
    domain_rows: Iterable[Dict[str, Any]] | None = None,
    exam_policy_rows: Iterable[Dict[str, Any]] | None = None,
) -> List[Dict[str, Any]]:
    master: Dict[str, Dict[str, Any]] = {}

    def ensure(cid: str, name: str = "") -> Dict[str, Any]:
        key = cid or f"name:{name.lower()}"
        return master.setdefault(key, {
            "course_id": cid or None, "course_name": name or None, "course_code": None,
            "vendor": None, "vendor_id": None, "course_url": None, "toc": None,
            "duration": None, "technologies": [], "domains": [], "exam_required": None,
            "course_status": None, "aliases": [], "source_datasets": [], "data_conflicts": [],
        })

    for row in course_rows or []:
        if not isinstance(row, dict):
            continue
        cid, name = _course_id(row), _text(row.get("Course") or row.get("course_name"))
        rec = ensure(cid, name)
        rec.update({"course_name": name or rec["course_name"], "vendor": _text(row.get("vendor_name")) or rec["vendor"], "vendor_id": row.get("vendor_id"), "course_url": row.get("course_url") or rec["course_url"]})
        rec["source_datasets"].append("courseList")

    for row in detail_rows or []:
        if not isinstance(row, dict):
            continue
        cid, name = _course_id(row), _text(row.get("Course") or row.get("course_name"))
        rec = ensure(cid, name)
        if rec.get("course_name") and name and rec["course_name"].lower() != name.lower():
            rec["aliases"].append(name)
        rec.update({"course_name": rec.get("course_name") or name, "course_code": row.get("course_code") or rec["course_code"], "vendor": _text(row.get("vendor_of_course")) or rec["vendor"], "duration": row.get("course_duration") or rec["duration"], "course_url": row.get("Course_Page") or rec["course_url"], "toc": row.get("TOC") or rec["toc"]})
        rec["source_datasets"].append("courseNames")

    for row in technology_rows or []:
        if not isinstance(row, dict):
            continue
        rec = ensure(_course_id(row), _text(row.get("course_name")))
        technology = _text(row.get("technology_name"))
        if technology and technology not in rec["technologies"]:
            rec["technologies"].append(technology)
        rec["source_datasets"].append("courseTechnology")

    for row in domain_rows or []:
        if not isinstance(row, dict):
            continue
        rec = ensure(_course_id(row), _text(row.get("CName")))
        domain = _text(row.get("DomainName"))
        if domain and domain not in rec["domains"]:
            rec["domains"].append(domain)
        rec["source_datasets"].append("courseDomain")

    for row in exam_policy_rows or []:
        if not isinstance(row, dict):
            continue
        rec = ensure(_course_id(row), _text(row.get("CName")))
        raw = _text(row.get("Exam Required or Not")).lower()
        rec["exam_required"] = None if not raw else not any(x in raw for x in ("no", "without", "not required"))
        rec["course_status"] = _text(row.get("CourseStatus")) or rec["course_status"]
        rec["source_datasets"].append("courseWithoutExam")

    for rec in master.values():
        rec["source_datasets"] = sorted(set(rec["source_datasets"]))
        rec["aliases"] = sorted(set(rec["aliases"]))
    return sorted(master.values(), key=lambda row: (row.get("course_name") or "").lower())


def build_unallocated_demand(rows: Iterable[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Normalize the Unallocated Assignment API into demand rows.

    The RMS payload uses fields like ``Coursename``, ``CourseSDate``,
    ``Delivery Mode`` and ``Assignment City`` — these are read first, with the
    older generic names kept only as fallbacks.
    """
    output = []
    for index, row in enumerate(rows or []):
        if not isinstance(row, dict):
            continue
        demand_id = _text(row.get("AssignmentID") or row.get("AssignmentId") or row.get("SCID")
                          or row.get("RequestId") or row.get("Id")) or f"unallocated:{index}"
        city = _text(row.get("Assignment City") or row.get("City"))
        country = _text(row.get("Assignment Country") or row.get("Country") or row.get("Region"))
        location = ", ".join(p for p in (city, country) if p) or None
        course_name = _text(row.get("Coursename") or row.get("CourseName") or row.get("Course") or row.get("CName")) or None
        start_date = row.get("CourseSDate") or row.get("StartDate") or row.get("StarDate") or row.get("fromDate")
        end_date = row.get("CourseEDate") or row.get("EndDate") or row.get("toDate")
        delivery_mode = _text(row.get("Delivery Mode") or row.get("Mode") or row.get("DeliveryMode")) or None
        vendor = _text(row.get("vendor") or row.get("Vendor")) or None
        customer = _text(row.get("Customer") or row.get("ClientName") or row.get("Third Party")) or None
        participant_count = row.get("NoOfParticipants")
        # Drop rows the source returned with no usable demand fields. Rendering
        # an unrecognised payload as an "Unallocated demand" row is noise — it
        # pollutes the dashboard queue and the allocation desk with blanks.
        if not any([demand_id and not demand_id.startswith("unallocated:"), course_name, start_date, end_date, location, vendor, customer, delivery_mode, participant_count]):
            continue
        output.append({
            "demand_id": demand_id,
            "course_id": _text(row.get("CourseId") or row.get("Courseid") or row.get("CourseID")) or _course_id(row) or None,
            "course_name": course_name,
            "start_date": start_date,
            "end_date": end_date,
            "delivery_mode": delivery_mode,
            "location": location,
            "vendor": vendor,
            "participant_count": participant_count,
            "customer": customer,
            "urgency": _text(row.get("Priority") or row.get("Urgency") or row.get("Allocation Required For")) or "Unspecified",
            "tentative": row.get("Tentetive or Not"),
            "status": "unallocated",
            "source_api": "Unallocated Assignment",
        })
    return output
