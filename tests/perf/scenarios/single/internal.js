/**
 * Single API Tests — Internal Endpoints
 *
 * FR-005: Internal validate (P0), events (P2), rate-limits admin (P2)
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
    internal_validate: {
      executor: 'constant-arrival-rate', rate: 100, timeUnit: '1s',
      duration: '30s', preAllocatedVUs: 50, maxVUs: 200,
      tags: { endpoint: 'internal_validate' }, exec: 'testInternalValidate',
    },
    events: {
      executor: 'constant-arrival-rate', rate: 50, timeUnit: '1s',
      duration: '30s', preAllocatedVUs: 30, maxVUs: 100,
      startTime: '35s', tags: { endpoint: 'events' }, exec: 'testEvents',
    },
    rate_limits: {
      executor: 'constant-arrival-rate', rate: 30, timeUnit: '1s',
      duration: '30s', preAllocatedVUs: 20, maxVUs: 60,
      startTime: '70s', tags: { endpoint: 'rate_limits' }, exec: 'testRateLimits',
    },
  },
  thresholds: {
    'http_req_duration{endpoint:internal_validate}': [`p(95)<${SLA.validate.p95}`],
    'http_req_duration{endpoint:events}': [`p(95)<${SLA.crud.p95}`],
    'http_req_duration{endpoint:rate_limits}': [`p(95)<${SLA.crud.p95}`],
  },
};

export function testInternalValidate() {
  const user = getUserForVU(__VU);
  const token = loginAndGetToken(user.username, user.password);
  if (!token) return;
  const res = http.post(`${BASE_URL}/internal/validate`,
    JSON.stringify({ token }),
    { headers: DEFAULT_HEADERS, tags: { endpoint: 'internal_validate' } });
  check(res, { 'internal validate 200': (r) => r.status === 200 });
  endpointLatency.add(res.timings.duration, { endpoint: 'internal_validate' });
}

export function testEvents() {
  const user = getUserForVU(__VU);
  const token = loginAndGetToken(user.username, user.password);
  if (!token) return;
  const res = http.get(`${BASE_URL}/internal/events?page=0&size=10`,
    { headers: authHeaders(token), tags: { endpoint: 'events' } });
  endpointLatency.add(res.timings.duration, { endpoint: 'events' });
}

export function testRateLimits() {
  const user = getUserForVU(__VU);
  const token = loginAndGetToken(user.username, user.password);
  if (!token) return;
  const res = http.get(`${BASE_URL}/admin/rate-limits`,
    { headers: authHeaders(token), tags: { endpoint: 'rate_limits' } });
  endpointLatency.add(res.timings.duration, { endpoint: 'rate_limits' });
}

export const handleSummary = buildHandleSummary('internal');
