## SkillEdge v2.4.0 — Capability Portfolio

### Why this release
Managers need to see capability risk and bench depth as decisions, not browse a course directory.

### What changed
- Added authenticated, manager-scoped `/api/v2/capability/portfolio`.
- Added portfolio health, readiness depth, single-owner dependencies, certification exposure, future-skill coverage and vendor-level coverage.
- Rebuilt the top of Capability Marketplace with compact decision KPIs, coverage bars, evidence confidence and the highest-priority intervention.
- Android now consumes the protected V2 contract.
- Unknown/unverified domain taxonomy remains explicitly unavailable; no empty or invented domain data is shown.

### Published commit
`d9b7610b715fa60412a982ee3f65c36bccb801bb`

### Version rationale
Minor version increased from 2.3.0 to 2.4.0 because this adds a protected business contract and a new manager intelligence capability. Version code increased from 73 to 74 for direct Android upgrade eligibility.

### User gain
Managers can immediately identify single-person delivery dependencies, courses without certified cover, vendor concentration and the next capability action.

### Validation
- Backend: 38/38 tests passed.
- Android: unit/render tests, release lint and signed assembly passed.
- Published APK: `com.example.skillsync`, v2.4.0/code 74.
- Signing certificate unchanged: `c6868b14bec9982642d908a5d4f535116daaf4e932a1e5ac27ed957671a41808`.
- Published APK SHA-256: `F9CDDD45CC4873D74B1B4B4D49881096674FA7A308AD56155165B251DD29021A`.
- Physical install-over-v2.3 remains to be run when an ADB-connected device is available.

### Rollback
Release v2.3.0.73 remains available in GitHub release history.
