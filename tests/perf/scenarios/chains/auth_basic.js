/**
 * Chain Test — Auth Basic (P0): Login → Profile → Refresh → Logout
 *
 * FR-006: API chain test — measures end-to-end auth flow latency.
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { SLA } from '../../config/thresholds.js';
import { BASE_URL } from '../../config/env.js';
import { DEFAULT_HEADERS, authHeaders } from '../../helpers/auth.js';
import { getUserForVU } from '../../helpers/data.js';
import { chainDuration, chainSuccess, stepLatency } from '../../helpers/metrics.js';
import { buildHandleSummary } from '../../helpers/report.js';

export const options = {
  scenarios: {
    auth_basic_chain: {
      executor: 'per-vu-iterations',
      vus: 50,
      iterations: 10,
      maxDuration: '5m',
    },
  },
  thresholds: {
    chain_duration: [`p(95)<${SLA.authChain.p95}`],
    chain_success: ['rate>0.99'],
  },
};

export default function () {
  const chainStart = Date.now();
  const user = getUserForVU(__VU);
  let success = true;

  // Step 1: Login
  const loginRes = http.post(`${BASE_URL}/auth/login`,
    JSON.stringify({ username: user.username, password: user.password }),
    { headers: DEFAULT_HEADERS, tags: { step: 'login' } });
  stepLatency.add(loginRes.timings.duration, { step: 'login', chain: 'auth_basic' });

  if (!check(loginRes, { 'login 200': (r) => r.status === 200 })) { chainSuccess.add(0); return; }

  const body = JSON.parse(loginRes.body);
  const token = body.accessToken || body.access_token;
  const refreshToken = body.refreshToken || body.refresh_token;

  // Step 2: Get Profile
  const profileRes = http.get(`${BASE_URL}/account/profile`,
    { headers: authHeaders(token), tags: { step: 'profile' } });
  stepLatency.add(profileRes.timings.duration, { step: 'profile', chain: 'auth_basic' });
  if (!check(profileRes, { 'profile 200': (r) => r.status === 200 })) success = false;

  sleep(0.5);

  // Step 3: Refresh Token
  const refreshRes = http.post(`${BASE_URL}/auth/refresh`,
    JSON.stringify({ refreshToken }),
    { headers: DEFAULT_HEADERS, tags: { step: 'refresh' } });
  stepLatency.add(refreshRes.timings.duration, { step: 'refresh', chain: 'auth_basic' });
  if (!check(refreshRes, { 'refresh 200': (r) => r.status === 200 })) success = false;

  // Step 4: Logout
  const newToken = refreshRes.status === 200
    ? (JSON.parse(refreshRes.body).accessToken || JSON.parse(refreshRes.body).access_token || token)
    : token;
  const logoutRes = http.post(`${BASE_URL}/auth/logout`, null,
    { headers: authHeaders(newToken), tags: { step: 'logout' } });
  stepLatency.add(logoutRes.timings.duration, { step: 'logout', chain: 'auth_basic' });

  chainDuration.add(Date.now() - chainStart);
  chainSuccess.add(success ? 1 : 0);
}

export const handleSummary = buildHandleSummary('chain_auth_basic');
