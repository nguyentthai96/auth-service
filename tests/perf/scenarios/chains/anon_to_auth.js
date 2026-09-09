/**
 * Chain Test — Anonymous to Authenticated (P2): Anon Token → Browse → Register → Promote
 * FR-006
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL } from '../../config/env.js';
import { DEFAULT_HEADERS, authHeaders } from '../../helpers/auth.js';
import { generateUsername, generateEmail } from '../../helpers/data.js';
import { chainDuration, chainSuccess, stepLatency } from '../../helpers/metrics.js';
import { buildHandleSummary } from '../../helpers/report.js';

export const options = {
  scenarios: { anon_to_auth_chain: { executor: 'per-vu-iterations', vus: 10, iterations: 5, maxDuration: '5m' } },
  thresholds: { chain_duration: ['p(95)<5000'], chain_success: ['rate>0.80'] },
};

export default function () {
  const start = Date.now();

  // Step 1: Get anonymous token
  const anonRes = http.post(`${BASE_URL}/auth/anonymous`,
    null, { headers: DEFAULT_HEADERS, tags: { step: 'anon_token' } });
  stepLatency.add(anonRes.timings.duration, { step: 'anon_token', chain: 'anon_to_auth' });

  let anonToken = null;
  if (anonRes.status === 200) {
    const body = JSON.parse(anonRes.body);
    anonToken = body.accessToken || body.access_token;
  }

  sleep(0.3);

  // Step 2: Browse (health check as public browse)
  const browseRes = http.get(`${BASE_URL}/actuator/health`,
    { tags: { step: 'browse' } });
  stepLatency.add(browseRes.timings.duration, { step: 'browse', chain: 'anon_to_auth' });

  // Step 3: Register
  const username = generateUsername(__VU, __ITER);
  const regRes = http.post(`${BASE_URL}/auth/register`,
    JSON.stringify({ username, password: 'PerfTest123!', email: generateEmail(username) }),
    { headers: DEFAULT_HEADERS, tags: { step: 'register' } });
  stepLatency.add(regRes.timings.duration, { step: 'register', chain: 'anon_to_auth' });

  sleep(0.3);

  // Step 4: Login as registered user (promote to authenticated)
  const loginRes = http.post(`${BASE_URL}/auth/login`,
    JSON.stringify({ username, password: 'PerfTest123!' }),
    { headers: DEFAULT_HEADERS, tags: { step: 'login_promote' } });
  stepLatency.add(loginRes.timings.duration, { step: 'login_promote', chain: 'anon_to_auth' });

  chainDuration.add(Date.now() - start);
  chainSuccess.add(loginRes.status === 200 ? 1 : 0);
}

export const handleSummary = buildHandleSummary('chain_anon_to_auth');
