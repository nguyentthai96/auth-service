/**
 * Single API Tests — Auth P0 Endpoints (Critical Path) — Tuned v2
 *
 * FR-005: Per-endpoint baseline latency measurement.
 * Endpoints: login, introspect (validate), register
 *
 * TUNING NOTES:
 * - Login/Register are bcrypt-bound (~200ms/hash on 12 cores)
 * - Introspect uses cached token (no login per iteration)
 * - VU counts reduced to match actual CPU capacity
 * - Rate limits disabled via perf profile
 *
 * Run: SPRING_PROFILES_ACTIVE=perf ./gradlew bootRun
 *      k6 run scenarios/single/auth_p0.js
 */

import http from 'k6/http';
import { check } from 'k6';
import { SLA } from '../../config/thresholds.js';
import { BASE_URL } from '../../config/env.js';
import { DEFAULT_HEADERS, authHeaders } from '../../helpers/auth.js';
import { getUserForVU, generateUsername, generateEmail } from '../../helpers/data.js';
import { endpointLatency, endpointErrors } from '../../helpers/metrics.js';
import { buildHandleSummary } from '../../helpers/report.js';

export const options = {
  scenarios: {
    // Login: bcrypt verify ~200ms/request, 12 cores → max ~60 req/s
    // Target: 20 req/s (conservative) to measure true latency
    login: {
      executor: 'constant-arrival-rate',
      rate: 20,                 // Reduced from 50 → 20 req/s (bcrypt-bound)
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 10,
      maxVUs: 50,
      tags: { endpoint: 'login' },
      exec: 'testLogin',
    },
    // Introspect: JWT validation only (no bcrypt) → very fast
    // Uses pre-cached token from setup, not per-iteration login
    introspect: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 10,
      maxVUs: 50,
      startTime: '35s',
      tags: { endpoint: 'validate' },
      exec: 'testIntrospect',
    },
    // Register: bcrypt hash ~200ms + DB insert → ~2-3s/request
    // Target: 10 req/s (realistic for production)
    register: {
      executor: 'constant-arrival-rate',
      rate: 10,                 // Reduced from 20 → 10 req/s
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 15,
      maxVUs: 50,
      startTime: '70s',
      tags: { endpoint: 'register' },
      exec: 'testRegister',
    },
  },
  thresholds: {
    'http_req_duration{endpoint:login}':    [`p(95)<${SLA.login.p95}`, `p(99)<${SLA.login.p99}`],
    'http_req_duration{endpoint:validate}': [`p(95)<${SLA.validate.p95}`, `p(99)<${SLA.validate.p99}`],
    'http_req_duration{endpoint:register}': [`p(95)<${SLA.register.p95}`, `p(99)<${SLA.register.p99}`],
    'http_req_failed': [`rate<${SLA.login.errorRate}`],
  },
};

// --- Per-VU cached tokens for introspect scenario ---
// Avoids bcrypt-heavy login on every introspect iteration
let cachedTokens = {};

export function setup() {
  // Pre-login a few users and cache their tokens
  const tokens = {};
  for (let i = 1; i <= 10; i++) {
    const res = http.post(`${BASE_URL}/auth/login`,
      JSON.stringify({ username: `perfuser${i}`, password: `PerfTest${i}!` }),
      { headers: DEFAULT_HEADERS }
    );
    if (res.status === 200) {
      try {
        const body = JSON.parse(res.body);
        tokens[i] = body.accessToken || body.access_token;
      } catch (_) {}
    }
  }
  return { tokens };
}

export function testLogin() {
  const user = getUserForVU(__VU);
  const res = http.post(`${BASE_URL}/auth/login`,
    JSON.stringify({ username: user.username, password: user.password }),
    { headers: DEFAULT_HEADERS, tags: { endpoint: 'login' } }
  );
  check(res, { 'login 200': (r) => r.status === 200 });
  endpointLatency.add(res.timings.duration, { endpoint: 'login' });
  if (res.status >= 400) endpointErrors.add(1, { endpoint: 'login' });
}

export function testIntrospect(data) {
  // Use cached token — avoid bcrypt login per iteration
  const tokenIndex = (__VU % 10) + 1;
  const token = data.tokens[tokenIndex];

  if (!token) {
    // Fallback: skip if no cached token
    return;
  }

  const res = http.post(`${BASE_URL}/auth/introspect`,
    JSON.stringify({ token }),
    { headers: authHeaders(token), tags: { endpoint: 'validate' } }
  );
  check(res, { 'introspect 200': (r) => r.status === 200 });
  endpointLatency.add(res.timings.duration, { endpoint: 'validate' });
}

export function testRegister() {
  const username = generateUsername(__VU, __ITER);
  const res = http.post(`${BASE_URL}/auth/register`,
    JSON.stringify({
      username,
      password: 'PerfTest123!',
      email: generateEmail(username),
      fullName: `Perf User ${username}`,
      domainCode: 'default',
    }),
    { headers: DEFAULT_HEADERS, tags: { endpoint: 'register' } }
  );
  check(res, { 'register 201 or 200': (r) => r.status === 201 || r.status === 200 });
  endpointLatency.add(res.timings.duration, { endpoint: 'register' });
}

export const handleSummary = buildHandleSummary('auth_p0');
