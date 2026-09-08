/**
 * Chain Test — Session Management (P1): Login → List Sessions → Revoke Session
 * FR-006
 */
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from '../../config/env.js';
import { DEFAULT_HEADERS, authHeaders } from '../../helpers/auth.js';
import { getUserForVU } from '../../helpers/data.js';
import { chainDuration, chainSuccess, stepLatency } from '../../helpers/metrics.js';

export const options = {
  scenarios: { session_chain: { executor: 'per-vu-iterations', vus: 30, iterations: 10, maxDuration: '5m' } },
  thresholds: { chain_duration: ['p(95)<1500'], chain_success: ['rate>0.95'] },
};

export default function () {
  const start = Date.now();
  const user = getUserForVU(__VU);

  const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username: user.username, password: user.password }),
    { headers: DEFAULT_HEADERS, tags: { step: 'login' } });
  stepLatency.add(loginRes.timings.duration, { step: 'login', chain: 'session_mgmt' });
  if (!check(loginRes, { 'login ok': (r) => r.status === 200 })) { chainSuccess.add(0); return; }

  const token = JSON.parse(loginRes.body).accessToken || JSON.parse(loginRes.body).access_token;

  const sessionsRes = http.get(`${BASE_URL}/api/v1/auth/sessions`,
    { headers: authHeaders(token), tags: { step: 'list_sessions' } });
  stepLatency.add(sessionsRes.timings.duration, { step: 'list_sessions', chain: 'session_mgmt' });

  const revokeRes = http.del(`${BASE_URL}/api/v1/auth/sessions/current`,
    null, { headers: authHeaders(token), tags: { step: 'revoke_session' } });
  stepLatency.add(revokeRes.timings.duration, { step: 'revoke_session', chain: 'session_mgmt' });

  chainDuration.add(Date.now() - start);
  chainSuccess.add(1);
}
