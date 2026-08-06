# SkillEdge Page Design Map

This map defines how each SkillEdge page should be shaped using SeanTheme Color Admin patterns, while keeping all data sourced from the unified endpoint only:

`GET /data/unified-manager-intelligence?email=<manager_email>`

No HTML page should call RMS APIs directly.

## Global Rules

- Cards first, evidence later.
- Tables are secondary and collapsed when possible.
- Charts come from verified data only.
- Timelines are for progression, assignments, and readiness journeys.
- Calendars are only for schedule/availability views.
- Modals are for detail inspection, not primary navigation.
- Forms/uploads are only for input-heavy workflows.
- Every page must answer a manager decision.
- Menu active state must be updated every time a page is created.
- No fake data.
- No raw dataframe labels.
- No broken links.

## Page Map

### 1. Dashboard / `index.html`

SeanTheme references:
- `index.html`
- `index_v2.html`
- `index_v3.html`
- `widget.html`
- `chart-apex.html`

Use:
- hero cockpit
- KPI cards
- action inbox
- charts
- trainer cards
- data health summary

Dataset(s):
- `trainer_operations_df`
- `manager_action_df`
- `course_allocation_df`
- `trainer_availability_engine_df`
- `data_health_df`

Manager decision:
- Who reports to me?
- Who is ready now?
- Who needs coaching?
- What needs attention today?

### 2. Trainer 360 / `trainer-detail.html`

SeanTheme references:
- `extra_order_details.html`
- `extra_timeline.html`
- `widget.html`
- `chart-apex.html`
- `table_manage_combine.html`

Use:
- profile hero
- score strip
- readiness chart
- certification panel
- assignment timeline
- evidence accordion

Dataset(s):
- `trainer_operations_df`
- `trainer_timeline_df`
- `trainer_availability_engine_df`
- `data_health_df`

Manager decision:
- Is this trainer safe to allocate?
- What is the evidence behind their readiness?

### 3. Allocation Desk / `allocation-desk.html`

SeanTheme references:
- `pos_customer_order.html`
- `extra_orders.html`
- `extra_order_details.html`
- `form_plugins.html`
- `chart-apex.html`

Use:
- course-first selection
- best match panel
- course-trainer cards
- candidate split
- charts
- evidence table

Dataset(s):
- `course_allocation_df`
- `trainer_operations_df`
- `trainer_availability_engine_df`
- `data_health_df`

Manager decision:
- Which trainer should be allocated to which course?

### 4. Custom Course Match / `custom-course-match.html`

SeanTheme references:
- `form_plugins.html`
- `form_elements.html`
- `email_inbox.html`
- `chart-apex.html`
- `extra_timeline.html`

Use:
- upload / paste panel
- extracted course intelligence
- trainer match inbox/cards
- risk-taker panel
- preparation timeline

Dataset(s):
- `custom_course_match_df`
- `trainer_operations_df`
- `course_allocation_df`
- `trainer_availability_engine_df`
- `data_health_df`

Manager decision:
- If I upload a custom course outline, who should receive it and who needs prep?

### 5. Action Center / `actions.html`

SeanTheme references:
- `email_inbox.html`
- `widget.html`
- `ui_modal_notification.html`
- `ui_buttons.html`
- `extra_timeline.html`

Use:
- action inbox
- grouped actions
- action detail modal
- priority badges
- preparation timeline

Dataset(s):
- `manager_action_df`
- `trainer_operations_df`
- `course_allocation_df`
- `data_health_df`

Manager decision:
- What should I do today for my trainers and courses?

### 6. My Team / `team.html`

SeanTheme references:
- `widget.html`
- `table_manage_combine.html`
- `extra_data_management.html`
- `ui_general.html`

Use:
- team KPI cards
- trainer cards
- filters
- comparison matrix
- quick detail modal
- evidence accordion

Dataset(s):
- `trainer_operations_df`
- `trainer_availability_engine_df`
- `manager_action_df`
- `data_health_df`

Manager decision:
- What is the current shape of my team?

### 7. Capability Builder / `capability-builder.html`

SeanTheme references:
- `widget.html`
- `chart-apex.html`
- `extra_timeline.html`
- `email_inbox.html`

Use:
- upgrade candidate cards
- future skill opportunities
- skill gap charts
- upgrade path timeline
- development action list

Dataset(s):
- `trainer_operations_df`
- `course_allocation_df`
- `manager_action_df`
- `trainer_timeline_df`
- `data_health_df`

Manager decision:
- Who can be upgraded, to what, and how do I make them ready?

### 8. Risk-Taker Candidates / `risk-takers.html`

SeanTheme references:
- `widget.html`
- `chart-apex.html`
- `email_inbox.html`
- `extra_timeline.html`

Use:
- Safe Expert / Growth Candidate / Risk-Taker / Do Not Risk cards
- risk vs opportunity charts
- preparation journey
- manager action cards

Dataset(s):
- `trainer_operations_df`
- `course_allocation_df`
- `manager_action_df`
- `trainer_timeline_df`
- `data_health_df`

Manager decision:
- Who is a stretch candidate and who should not be risked?

### 9. Quality & Risk / `quality-risk.html`

SeanTheme references:
- `email_inbox.html`
- `ui_modal_notification.html`
- `widget.html`
- `chart-apex.html`
- `extra_timeline.html`

Use:
- risk inbox
- feedback evidence cards
- HR risk widgets
- quality trend
- incident timeline

Dataset(s):
- `trainer_operations_df`
- `manager_action_df`
- `trainer_timeline_df`
- `data_health_df`

Manager decision:
- Who has quality or HR risk that needs intervention?

### 10. Timeline / `timeline.html`

SeanTheme references:
- `calendar.html`
- `extra_timeline.html`

Use:
- trainer activity timeline
- assignment events
- upcoming delivery calendar
- feedback events
- utilization trend events

Dataset(s):
- `trainer_timeline_df`
- `trainer_availability_engine_df`
- `data_health_df`

Manager decision:
- What is the sequence of activity across trainers and deliveries?

### 11. Data Health / `data-health.html`

SeanTheme references:
- `extra_data_management.html`
- `table_manage_combine.html`
- `ui_general.html`

Use:
- API health cards
- missing data cards
- blocked signal list
- schema mismatch table
- cache / freshness panel

Dataset(s):
- `data_health_df`
- `trainer_operations_df`
- `trainer_availability_engine_df`

Manager decision:
- What data is missing, stale, or unreliable?

## Common Component Rules

- KPI cards from `widget.html`
- inbox/action lists from `email_inbox.html`
- tables only from `table_manage_combine.html` and only as evidence/drill-down
- charts primarily from `chart-apex.html`
- calendars from `calendar.html` only for schedule/availability
- timelines from `extra_timeline.html`
- modals from `ui_modal_notification.html`
- buttons from `ui_buttons.html`
- forms/uploads from `form_elements.html` and `form_plugins.html`
- data health/admin areas from `extra_data_management.html`
- coming soon routes from `extra_coming_soon.html`
- error page from `extra_404_error.html`
- login from `login_v3.html`

## Build Order Guidance

When creating a new page:
1. Choose the best matching SeanTheme patterns.
2. Decide the manager decision the page must answer.
3. Pick the unified datasets that power it.
4. Put cards first.
5. Put charts or inbox items next.
6. Put evidence tables behind collapse/secondary views.
7. Update menu active state.
8. Verify in browser.

