# Manager Application Rebuild Blueprint

**Objective:** Make the existing manager product smooth and coherent without adding capabilities.
This is an information-architecture and truthfulness rebuild over screens and APIs already present.

## Recommended manager journey

### 1. Today — decide what needs attention

**Keep:** Pulse, one-sentence brief, top three priorities, notification evidence, action queue.  
**Move out:** capability catalogue, long reports, configuration, and duplicate KPI grids.  
**Fix:** One route from each alert to a valid destination; consistent freshness/source labels;
Start/Close/Escalate semantics already clarified.  
**Success:** A manager can understand the day and begin the first real task in under 30 seconds.

### 2. People — understand and develop the team

**Keep:** searchable complete roster, capacity, current/next assignment, skill/certification depth.  
**Drill-in:** Trainer 360 with Overview, Capability, Performance, Development.  
**Place here:** skill requests and team benchmark.  
**Fix before polish:** remove the locally fabricated Trainer Index card, 65% utilization default,
94% sentiment default, and default “High Performer” label. Use backend evidence or unavailable.  
**Success:** Every number opens its evidence and no absent metric becomes a favorable default.

### 3. Plan — prepare supply for real demand

**Keep:** RMS demand order, filters, Batch Detail, eligibility blockers and safe skill marking.  
**Place here:** Pipeline Radar and Capacity Runway.  
**Fix:** Distinguish batch facts, trainer facts and policy rules. A reportee mark ceiling is not an
assignment-required level. Allocation remains recommendation-only.  
**Success:** The manager sees what is unstaffed, why each trainer is eligible/blocked, and the
permitted preparation action without implying the app allocated anyone.

### 4. Work — oversee scheduled delivery

**Keep:** team calendar, personal schedule, delivery compliance.  
**Place here:** roster health, recording compliance, feedback evidence and recording library.  
**Fix:** Delivery-alert taps must route by assignment ID, not demand ID. “Roster below expected”
must remain a current shortfall, never a claimed drop.  
**Success:** From one assignment, the manager reaches schedule, roster, recording and feedback.

### 5. Search — reach anything without knowing its location

**Keep:** trainer, course, demand and action results.  
**Fix:** Result permissions, empty/error states, and destination correctness for both roles.  
**Success:** Search deep-links to the same canonical detail used by the main tabs.

## Secondary destinations

- Weekly report and HR monthly report: accessible from Today/People respectively, not primary tabs.
- Accounts and Ramp: planning/people analytical drills, not Today tiles competing for attention.
- Viber configuration/automation: manager settings/operations only; never visible to reportees.
- Copilot: contextual entry from Today/Trainer 360; every answer must carry evidence/confidence.

## Rebuild order

1. **Truth gate:** remove fabricated defaults and hardcoded performance inputs.
2. **Permission gate:** one role-capability matrix; audit every visible manager-only control.
3. **Navigation gate:** fix notification targets and consolidate existing pages under the five tabs.
4. **Data-contract gate:** move Android to canonical V2 routes and preserve unknown/error/freshness.
5. **Security gate:** provision secrets, rotate exposed RMS credentials, remove plaintext copies.
6. **UX pass:** reduce duplicate cards, standardize loading/empty/error states, preserve scroll/state.
7. **Validation:** complete automated suites, on-device manager/reportee matrices, Development using
   the identical package, then explicit operator confirmation before Production.

## Definition of ready

- No fabricated data or favorable fallback label.
- No visible control that the signed-in role cannot execute.
- No alert opens the wrong entity type.
- Every API failure is distinguishable from a real empty/zero result.
- All active Android endpoints use the canonical contract.
- All secrets are outside source and rotated.
- Full tests and on-device journey checklist pass.
- Operator explicitly confirms publication.
