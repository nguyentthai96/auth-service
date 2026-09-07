---
# Contract State — Auto-managed by wf_api_contract
# User-editable: gate_rules section ONLY
# DO NOT manually edit other sections unless debugging
# ─────────────────────────────────────

# Contract metadata (auto-set by wf_api_contract)
contract_version: "1.0"
backend_status: pending         # pending | implemented | drift | tested
frontend_status: pending        # pending | implemented | tested
last_generated: "2026-09-07T11:18:00+07:00"
last_validated: null            # ISO 8601 timestamp
endpoints_count: 3
drift_detected: false
drift_log: []

# Consumers — platforms consuming this contract
consumers:
  - platform: web
    workflow: wf_fe_spec
    status: spec_generated      # fe_tasks.md generated → ready for fe_apply

# ─────────────────────────────────────
# Gate Rules — User-editable section
# These rules control wf_api_contract behavior
# ─────────────────────────────────────
gate_rules:
  frontend_active: true         # Primary gate — is frontend pipeline active?
  auto_generate: true           # Auto-generate contract after openspec completes?
  require_validation: true      # Require validation before fe_spec can run?
---
