/**
 * Stress Test — Breaking Point Detection.
 *
 * FR-008: Ramp beyond target capacity to find breaking point.
 * Records: TPS saturation, latency degradation onset, recovery after ramp-down.
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { SLA } from '../../config/thresholds.js';
import { BASE_URL } from '../../config/env.js';
import { DEFAULT_HEADERS, loginAndGetToken } from '../../helpers/auth.js';
import { getUserForVU } from '../../helpers/data.js';
import { totalTransactions } from '../../helpers/metrics.js';
import { buildHandleSummary } from '../../helpers/report.js';

export const options = {
  scenarios: {
    stress: {
      executor: 'ramping-arrival-rate',
      startRate: 100,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 2000,
      stages: [
        { duration: '2m', target: 200 },
        { duration: '2m', target: 500 },
        { duration: '2m', target: 1000 },
        { duration: '2m', target: 2000 },
        { duration: '2m', target: 3000 },
        // Recovery phase
        { duration: '2m', target: 500 },
        { duration: '1m', target: 100 },
        { duration: '1m', target: 0 },
      ],
    },
  },
  thresholds: {
    // No strict thresholds — stress test is exploratory
    // Record metrics for analysis
    http_req_duration: ['p(95)<5000'], // Lenient — we expect degradation
  },
};

export default function () {
  const user = getUserForVU(__VU);

  const res = http.post(`${BASE_URL}/auth/login`,
    JSON.stringify({ username: user.username, password: user.password }),
    { headers: DEFAULT_HEADERS, tags: { test_type: 'stress' } });

  check(res, {
    'status is 200': (r) => r.status === 200,
    'status not 500': (r) => r.status !== 500,
    'status not 503': (r) => r.status !== 503,
  });

  totalTransactions.add(1);
  sleep(0.1);
}

export const handleSummary = buildHandleSummary('stress');
