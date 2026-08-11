---
# Contract State — Auto-managed by wf_api_contract
# User-editable: gate_rules section
# DO NOT manually edit other sections unless debugging
contract_version: "1.0"
backend_status: implemented
frontend_status: implemented
last_generated: "2026-08-11T10:32:00+07:00"
last_validated: "2026-08-11T18:56:00+07:00"
endpoints_count: 7
drift_detected: false
drift_log:
  - "AUTH_008 not handled in JwtSignInForm — fixed"
consumers:
  - platform: web
    workflow: wf_fe_apply
    status: implemented
    tasks_count: 11
    tasks_completed: 11
    complexity: HIGH
    last_applied: "2026-08-11T18:56:00+07:00"
    build_status: pass
    review_status: pass
gate_rules:
  frontend_active: true
  auto_generate: true
  require_validation: true
---
