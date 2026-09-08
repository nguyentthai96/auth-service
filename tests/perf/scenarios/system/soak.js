/**
 * Soak Test — Memory Leak / Resource Exhaustion Detection.
 *
 * FR-009: Sustain moderate load for extended period.
 * Duration: 1h (CI/CD default), configurable up to 4h via SOAK_DURATION env var.
 *
 * Monitor: JVM heap trend, connection pool stats, error rate stability.
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, SOAK_DURATION } from '../../config/env.js';
import { DEFAULT_HEADERS, authHeaders, loginAndGetToken } from '../../helpers/auth.js';
import { getUserForVU } from '../../helpers/data.js';
import { totalTransactions } from '../../helpers/metrics.js';

export const options = {
  scenarios: {
    soak: {
      executor: 'constant-vus',
      vus: 200,
      duration: SOAK_DURATION,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const user = getUserForVU(__VU);

  // Mix of operations (similar to production)
  const rand = Math.random();

  if (rand < 0.6) {
    // Auth flow (60%)
    const res = http.post(`${BASE_URL}/api/v1/auth/login`,
      JSON.stringify({ username: user.username, password: user.password }),
      { headers: DEFAULT_HEADERS, tags: { flow: 'auth' } });
    check(res, { 'login ok': (r) => r.status === 200 });

    if (res.status === 200) {
      const token = JSON.parse(res.body).accessToken || JSON.parse(res.body).access_token;
      // Do a profile fetch
      http.get(`${BASE_URL}/api/v1/profiles/me`,
        { headers: authHeaders(token), tags: { flow: 'profile' } });
    }
  } else if (rand < 0.8) {
    // Validate flow (20%)
    const token = loginAndGetToken(user.username, user.password);
    if (token) {
      http.post(`${BASE_URL}/api/v1/auth/validate`,
        JSON.stringify({ token }),
        { headers: DEFAULT_HEADERS, tags: { flow: 'validate' } });
    }
  } else {
    // Health check (20%) — lightweight
    http.get(`${BASE_URL}/actuator/health`,
      { tags: { flow: 'health' } });
  }

  totalTransactions.add(1);
  sleep(1 + Math.random());
}
