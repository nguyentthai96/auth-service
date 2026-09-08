/**
 * Chain Test — Registration (P0): Register → Verify Email → Login → Profile
 * FR-006. Unique username per VU via data.js.
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL } from '../../config/env.js';
import { DEFAULT_HEADERS, authHeaders } from '../../helpers/auth.js';
import { generateUsername, generateEmail } from '../../helpers/data.js';
import { chainDuration, chainSuccess, stepLatency } from '../../helpers/metrics.js';

export const options = {
  scenarios: { registration_chain: { executor: 'per-vu-iterations', vus: 20, iterations: 5, maxDuration: '5m' } },
  thresholds: { chain_duration: ['p(95)<3000'], chain_success: ['rate>0.95'] },
};

export default function () {
  const start = Date.now();
  const username = generateUsername(__VU, __ITER);
  const email = generateEmail(username);
  const password = 'PerfTest123!';

  const regRes = http.post(`${BASE_URL}/api/v1/auth/register`,
    JSON.stringify({ username, password, email }),
    { headers: DEFAULT_HEADERS, tags: { step: 'register' } });
  stepLatency.add(regRes.timings.duration, { step: 'register', chain: 'registration' });
  if (!check(regRes, { 'register ok': (r) => r.status === 200 || r.status === 201 })) { chainSuccess.add(0); return; }

  sleep(0.5);

  const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username, password }),
    { headers: DEFAULT_HEADERS, tags: { step: 'login' } });
  stepLatency.add(loginRes.timings.duration, { step: 'login', chain: 'registration' });
  if (!check(loginRes, { 'login ok': (r) => r.status === 200 })) { chainSuccess.add(0); return; }

  const token = JSON.parse(loginRes.body).accessToken || JSON.parse(loginRes.body).access_token;

  const profileRes = http.get(`${BASE_URL}/api/v1/profiles/me`,
    { headers: authHeaders(token), tags: { step: 'profile' } });
  stepLatency.add(profileRes.timings.duration, { step: 'profile', chain: 'registration' });

  chainDuration.add(Date.now() - start);
  chainSuccess.add(1);
}
