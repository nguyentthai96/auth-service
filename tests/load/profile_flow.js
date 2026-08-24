import http from 'k6/http';
import { check, sleep } from 'k6';
import { loginAndGetToken, BASE_URL, authHeaders } from './helpers.js';

/**
 * Profile endpoint load test — constant arrival rate with dynamic token.
 *
 * FR-003: E2E load test K6 mở rộng
 *
 * Uses constant-arrival-rate executor for steady-state TPS measurement.
 * Dynamic token obtained via login helper for realistic authenticated access.
 *
 * Thresholds:
 * - P95 latency < 150ms
 * - Error rate < 1%
 *
 * Run: docker run --rm -i -v $(pwd):/scripts --network host grafana/k6 run /scripts/profile_flow.js
 */
export let options = {
    scenarios: {
        profile_steady: {
            executor: 'constant-arrival-rate',
            rate: 100,
            timeUnit: '1s',
            duration: '30s',
            preAllocatedVUs: 50,
            maxVUs: 200,
        },
    },
    thresholds: {
        'http_req_duration': ['p(95)<150'],
        'http_req_failed': ['rate<0.01'],
    },
};

// Shared token — obtained in setup() to avoid login on every iteration
let sharedToken = null;

export function setup() {
    // Obtain a token once during setup for use across all VUs
    const token = loginAndGetToken('loaduser', 'password123');
    if (!token) {
        console.warn('Setup login failed — tests will use fallback static token');
    }
    return { token: token || 'fallback-test-token-for-load-testing' };
}

export default function (data) {
    const url = `${BASE_URL}/api/v1/profiles/me`;
    const res = http.get(url, { headers: authHeaders(data.token) });

    check(res, {
        'profile: status 200': (r) => r.status === 200,
        'profile: has response body': (r) => r.body && r.body.length > 0,
    });

    sleep(0.1);
}
