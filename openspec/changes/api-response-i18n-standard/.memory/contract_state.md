---
# Contract State — Auto-managed by wf_api_contract
# User-editable: gate_rules section
# DO NOT manually edit other sections unless debugging
contract_version: "1.0"
backend_status: implemented
frontend_status: implemented
last_generated: "2026-08-11T19:47:00+07:00"
last_validated: "2026-08-11T20:01:00+07:00"
endpoints_count: 0
error_codes_count: 27
cross_cutting: true
drift_detected: false
drift_log: []
consumers:
  - platform: web
    workflow: wf_fe_spec
    status: ready
gate_rules:
  frontend_active: true
  auto_generate: true
  require_validation: true
---
