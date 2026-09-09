/**
 * Chain Test — Admin (P1): Login(Admin) → List Users → Assign Role → Verify
 * FR-006
 */
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from '../../config/env.js';
import { DEFAULT_HEADERS, authHeaders, loginAdminUser } from '../../helpers/auth.js';
import { chainDuration, chainSuccess, stepLatency } from '../../helpers/metrics.js';
import { buildHandleSummary } from '../../helpers/report.js';

export const options = {
  scenarios: { admin_chain: { executor: 'per-vu-iterations', vus: 10, iterations: 10, maxDuration: '5m' } },
  thresholds: { chain_duration: ['p(95)<3000'], chain_success: ['rate>0.90'] },
};

export default function () {
  const start = Date.now();

  const token = loginAdminUser();
  if (!token) { chainSuccess.add(0); return; }

  // Step 1: List users
  const usersRes = http.get(`${BASE_URL}/admin/users?page=0&size=10`,
    { headers: authHeaders(token), tags: { step: 'list_users' } });
  stepLatency.add(usersRes.timings.duration, { step: 'list_users', chain: 'admin' });
  check(usersRes, { 'users 200': (r) => r.status === 200 });

  // Step 2: Assign role (idempotent — assign existing role)
  const assignRes = http.post(`${BASE_URL}/admin/domains/1/roles/1/users`,
    JSON.stringify({ userId: 1 }),
    { headers: authHeaders(token), tags: { step: 'assign_role' } });
  stepLatency.add(assignRes.timings.duration, { step: 'assign_role', chain: 'admin' });

  // Step 3: Verify role assignment
  const verifyRes = http.get(`${BASE_URL}/admin/users/1/roles`,
    { headers: authHeaders(token), tags: { step: 'verify_role' } });
  stepLatency.add(verifyRes.timings.duration, { step: 'verify_role', chain: 'admin' });

  chainDuration.add(Date.now() - start);
  chainSuccess.add(1);
}

export const handleSummary = buildHandleSummary('chain_admin');
