/**
 * Full Load Test — Mixed Workload simulating production traffic.
 *
 * FR-007: Mixed workload with configurable ratios.
 * FR-013: K6 → Prometheus remote write (--out experimental-prometheus-rw).
 *
 * Run: k6 run --out experimental-prometheus-rw scenarios/system/full_load.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { SLA } from '../../config/thresholds.js';
import { BASE_URL, TRAFFIC_MIX } from '../../config/env.js';
import { DEFAULT_HEADERS, authHeaders, loginAndGetToken, loginAdminUser } from '../../helpers/auth.js';
import { getUserForVU, generateUsername, generateEmail } from '../../helpers/data.js';
import { totalTransactions } from '../../helpers/metrics.js';
import { buildHandleSummary } from '../../helpers/report.js';

export const options = {
  scenarios: {
    auth_flow: {
      executor: 'ramping-vus',
      stages: [
        { duration: '1m', target: 100 },
        { duration: '3m', target: 500 },
        { duration: '3m', target: 1000 },
        { duration: '2m', target: 500 },
        { duration: '1m', target: 0 },
      ],
      exec: 'mixedWorkload',
    },
  },
  thresholds: {
    http_req_duration: [`p(99)<${SLA.system.maxP99}`],
    http_req_failed: [`rate<${SLA.system.maxErrorRate}`],
  },
};

export function mixedWorkload() {
  const rand = Math.random() * 100;
  const authBound = TRAFFIC_MIX.auth;
  const profileBound = authBound + TRAFFIC_MIX.profile;
  const registerBound = profileBound + TRAFFIC_MIX.register;
  const adminBound = registerBound + TRAFFIC_MIX.admin;

  if (rand < authBound) {
    doAuthFlow();
  } else if (rand < profileBound) {
    doProfileFlow();
  } else if (rand < registerBound) {
    doRegisterFlow();
  } else if (rand < adminBound) {
    doAdminFlow();
  } else {
    doMfaSsoFlow();
  }

  totalTransactions.add(1);
  sleep(0.5 + Math.random() * 0.5);
}

function doAuthFlow() {
  const user = getUserForVU(__VU);
  const res = http.post(`${BASE_URL}/auth/login`,
    JSON.stringify({ username: user.username, password: user.password }),
    { headers: DEFAULT_HEADERS, tags: { flow: 'auth' } });
  check(res, { 'auth login ok': (r) => r.status === 200 });

  if (res.status === 200) {
    const body = JSON.parse(res.body);
    const token = body.accessToken || body.access_token;
    // 50% chance to also refresh
    if (Math.random() < 0.5 && body.refreshToken) {
      http.post(`${BASE_URL}/auth/refresh`,
        JSON.stringify({ refreshToken: body.refreshToken }),
        { headers: DEFAULT_HEADERS, tags: { flow: 'auth' } });
    }
  }
}

function doProfileFlow() {
  const user = getUserForVU(__VU);
  const token = loginAndGetToken(user.username, user.password);
  if (!token) return;
  http.get(`${BASE_URL}/account/profile`,
    { headers: authHeaders(token), tags: { flow: 'profile' } });
}

function doRegisterFlow() {
  const username = generateUsername(__VU, __ITER);
  http.post(`${BASE_URL}/auth/register`,
    JSON.stringify({ username, password: 'PerfTest123!', email: generateEmail(username) }),
    { headers: DEFAULT_HEADERS, tags: { flow: 'register' } });
}

function doAdminFlow() {
  const token = loginAdminUser();
  if (!token) return;
  http.get(`${BASE_URL}/admin/users?page=0&size=10`,
    { headers: authHeaders(token), tags: { flow: 'admin' } });
}

function doMfaSsoFlow() {
  const user = getUserForVU(__VU);
  const token = loginAndGetToken(user.username, user.password);
  if (!token) return;
  http.post(`${BASE_URL}/auth/mfa/verify`,
    JSON.stringify({ code: '123456', method: 'totp' }),
    { headers: authHeaders(token), tags: { flow: 'mfa_sso' } });
}

export const handleSummary = buildHandleSummary('full_load');
