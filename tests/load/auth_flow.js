import http from 'k6/http';
import { check, sleep } from 'k6';
import { loginAndGetToken, BASE_URL, DEFAULT_HEADERS, authHeaders } from './helpers.js';

/**
 * Multi-scenario auth load test — expanded from 50 VUs to 500 VUs.
 *
 * FR-003: E2E load test K6 mở rộng
 * FR-006: CI/CD performance gate
 *
 * Scenarios:
 * 1. auth_login (ramping-vus): Stress test login endpoint, 0→500 VUs over 30s
 *    → Finds breaking point under increasing load
 * 2. profile_get (constant-arrival-rate): Steady-state profile access, 100 req/s
 *    → Measures sustained TPS capacity
 *
 * Thresholds (CI/CD gate — exit code 99 on failure → BUILD FAILED):
 * - Login P95 < 200ms
 * - Profile P95 < 150ms
 * - Error rate < 1%
 *
 * Run: docker run --rm -i -v $(pwd):/scripts --network host grafana/k6 run /scripts/auth_flow.js
 */
export let options = {
    scenarios: {
        auth_login: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 100 },
                { duration: '10s', target: 300 },
                { duration: '10s', target: 500 },
            ],
            exec: 'loginScenario',
            tags: { scenario: 'auth_login' },
        },
        profile_get: {
            executor: 'constant-arrival-rate',
            rate: 100,
            timeUnit: '1s',
            duration: '30s',
            preAllocatedVUs: 50,
            maxVUs: 200,
            exec: 'profileScenario',
            tags: { scenario: 'profile_get' },
        },
    },
    thresholds: {
        // Login endpoint — P95 < 200ms
        'http_req_duration{scenario:auth_login}': ['p(95)<200'],
        // Profile endpoint — P95 < 150ms
        'http_req_duration{scenario:profile_get}': ['p(95)<150'],
        // Global error rate < 1%
        'http_req_failed': ['rate<0.01'],
    },
};

/**
 * Login scenario — ramping VUs from 0 to 500.
 * Tests auth endpoint under increasing load to find saturation point.
 */
export function loginScenario() {
    const url = `${BASE_URL}/api/v1/auth/login`;
    const payload = JSON.stringify({
        username: 'loaduser',
        password: 'password123',
    });

    const res = http.post(url, payload, { headers: DEFAULT_HEADERS });

    check(res, {
        'login: status 200': (r) => r.status === 200,
        'login: has response body': (r) => r.body && r.body.length > 0,
    });

    sleep(0.5);
}

/**
 * Profile scenario — constant arrival rate at 100 req/s.
 * Tests authenticated profile access under sustained load.
 * Uses dynamic token from login for realistic auth flow.
 */
export function profileScenario() {
    // Get a fresh token for authenticated profile access
    const token = loginAndGetToken('loaduser', 'password123');

    if (!token) {
        // If login fails, still make profile request to measure error handling
        const url = `${BASE_URL}/api/v1/profiles/me`;
        http.get(url, {
            headers: {
                ...DEFAULT_HEADERS,
                'Authorization': 'Bearer invalid-token',
            },
        });
        return;
    }

    const url = `${BASE_URL}/api/v1/profiles/me`;
    const res = http.get(url, { headers: authHeaders(token) });

    check(res, {
        'profile: status 200': (r) => r.status === 200,
    });

    sleep(0.3);
}
