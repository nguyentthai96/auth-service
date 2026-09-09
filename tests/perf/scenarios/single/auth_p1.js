/**
 * Single API Tests — Auth P1 Endpoints
 *
 * FR-005: Per-endpoint baseline for secondary auth endpoints.
 * Endpoints: sessions, mfa/verify, sso/login, captcha/challenge
 */

import http from 'k6/http';
import { check } from 'k6';
import { SLA } from '../../config/thresholds.js';
import { BASE_URL } from '../../config/env.js';
import { loginAndGetToken, authHeaders, DEFAULT_HEADERS } from '../../helpers/auth.js';
import { getUserForVU } from '../../helpers/data.js';
import { endpointLatency } from '../../helpers/metrics.js';
import { buildHandleSummary } from '../../helpers/report.js';

export const options = {
  scenarios: {
    sessions: {
      executor: 'constant-arrival-rate', rate: 50, timeUnit: '1s',
      duration: '30s', preAllocatedVUs: 30, maxVUs: 100,
      tags: { endpoint: 'sessions' }, exec: 'testSessions',
    },
    mfa_verify: {
      executor: 'constant-arrival-rate', rate: 50, timeUnit: '1s',
      duration: '30s', preAllocatedVUs: 30, maxVUs: 100,
      startTime: '35s', tags: { endpoint: 'mfa_verify' }, exec: 'testMfaVerify',
    },
    sso_login: {
      executor: 'constant-arrival-rate', rate: 50, timeUnit: '1s',
      duration: '30s', preAllocatedVUs: 30, maxVUs: 100,
      startTime: '70s', tags: { endpoint: 'sso_login' }, exec: 'testSsoLogin',
    },
    captcha: {
      executor: 'constant-arrival-rate', rate: 50, timeUnit: '1s',
      duration: '30s', preAllocatedVUs: 30, maxVUs: 100,
      startTime: '105s', tags: { endpoint: 'captcha' }, exec: 'testCaptcha',
    },
  },
  thresholds: {
    'http_req_duration{endpoint:sessions}':   [`p(95)<${SLA.crud.p95}`],
    'http_req_duration{endpoint:mfa_verify}':  [`p(95)<${SLA.login.p95}`],
    'http_req_duration{endpoint:sso_login}':   [`p(95)<${SLA.login.p95}`],
    'http_req_duration{endpoint:captcha}':     [`p(95)<${SLA.crud.p95}`],
  },
};

export function testSessions() {
  const user = getUserForVU(__VU);
  const token = loginAndGetToken(user.username, user.password);
  if (!token) return;
  const res = http.get(`${BASE_URL}/auth/sessions`,
    { headers: authHeaders(token), tags: { endpoint: 'sessions' } });
  check(res, { 'sessions 200': (r) => r.status === 200 });
  endpointLatency.add(res.timings.duration, { endpoint: 'sessions' });
}

export function testMfaVerify() {
  const res = http.post(`${BASE_URL}/auth/mfa/verify`,
    JSON.stringify({ code: '123456', method: 'totp' }),
    { headers: DEFAULT_HEADERS, tags: { endpoint: 'mfa_verify' } });
  endpointLatency.add(res.timings.duration, { endpoint: 'mfa_verify' });
}

export function testSsoLogin() {
  const res = http.get(`${BASE_URL}/auth/sso/login?provider=google`,
    { headers: DEFAULT_HEADERS, tags: { endpoint: 'sso_login' }, redirects: 0 });
  endpointLatency.add(res.timings.duration, { endpoint: 'sso_login' });
}

export function testCaptcha() {
  const res = http.get(`${BASE_URL}/captcha/challenge`,
    { headers: DEFAULT_HEADERS, tags: { endpoint: 'captcha' } });
  endpointLatency.add(res.timings.duration, { endpoint: 'captcha' });
}

export const handleSummary = buildHandleSummary('auth_p1');
