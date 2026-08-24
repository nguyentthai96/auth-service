import http from 'k6/http';
import { check, sleep } from 'k6';
import { loginAndGetToken, BASE_URL, authHeaders, DEFAULT_HEADERS } from './helpers.js';

/**
 * Cache encryption benchmark — TPS comparison across encryption modes.
 *
 * FR-010: Cache benchmark K6 script
 *
 * Benchmarks TPS impact of cache encryption modes (NONE / FULL / PARTIAL).
 * Uses constant-arrival-rate for comparable, reproducible results across modes.
 *
 * Environment variables:
 *   CACHE_ENCRYPTION_MODE - Cache encryption mode to test (default: NONE)
 *   BASE_URL              - Auth service URL (default: http://localhost:8080)
 *
 * Usage:
 *   # Test with NONE mode
 *   docker run --rm -i -v $(pwd):/scripts --network host \
 *     -e CACHE_ENCRYPTION_MODE=NONE grafana/k6 run /scripts/cache_benchmark.js
 *
 *   # Test with FULL mode
 *   docker run --rm -i -v $(pwd):/scripts --network host \
 *     -e CACHE_ENCRYPTION_MODE=FULL grafana/k6 run /scripts/cache_benchmark.js
 *
 *   # Test with PARTIAL mode
 *   docker run --rm -i -v $(pwd):/scripts --network host \
 *     -e CACHE_ENCRYPTION_MODE=PARTIAL grafana/k6 run /scripts/cache_benchmark.js
 *
 * Compare results across modes to measure encryption overhead on TPS.
 */

const CACHE_MODE = __ENV.CACHE_ENCRYPTION_MODE || 'NONE';

export let options = {
    scenarios: {
        cache_benchmark: {
            executor: 'constant-arrival-rate',
            rate: 50,
            timeUnit: '1s',
            duration: '30s',
            preAllocatedVUs: 30,
            maxVUs: 100,
        },
    },
    thresholds: {
        'http_req_duration': ['p(95)<300'],
        'http_req_failed': ['rate<0.05'],
    },
    tags: {
        cacheMode: CACHE_MODE,
    },
};

export function setup() {
    console.log(`Cache Benchmark — Mode: ${CACHE_MODE}`);
    console.log(`Target: ${BASE_URL}`);

    // Obtain auth token for authenticated cache-hitting endpoints
    const token = loginAndGetToken('loaduser', 'password123');
    if (!token) {
        console.warn('Setup login failed — cache benchmark will use static token');
    }

    return {
        token: token || 'fallback-test-token',
        cacheMode: CACHE_MODE,
    };
}

export default function (data) {
    // Access profile endpoint — triggers cache read/write
    // Cache behavior depends on CACHE_ENCRYPTION_MODE server-side config
    const profileUrl = `${BASE_URL}/api/v1/profiles/me`;
    const profileRes = http.get(profileUrl, { headers: authHeaders(data.token) });

    check(profileRes, {
        [`${data.cacheMode}: profile status 200`]: (r) => r.status === 200,
        [`${data.cacheMode}: response time < 300ms`]: (r) => r.timings.duration < 300,
    });

    sleep(0.1);
}

export function handleSummary(data) {
    const mode = CACHE_MODE;
    const p95 = data.metrics.http_req_duration
        ? data.metrics.http_req_duration.values['p(95)']
        : 'N/A';
    const p99 = data.metrics.http_req_duration
        ? data.metrics.http_req_duration.values['p(99)']
        : 'N/A';
    const avgDuration = data.metrics.http_req_duration
        ? data.metrics.http_req_duration.values['avg']
        : 'N/A';
    const totalReqs = data.metrics.http_reqs
        ? data.metrics.http_reqs.values.count
        : 'N/A';
    const errorRate = data.metrics.http_req_failed
        ? (data.metrics.http_req_failed.values.rate * 100).toFixed(2)
        : 'N/A';

    const summary = `
╔══════════════════════════════════════════════╗
║  Cache Benchmark Results — Mode: ${mode.padEnd(10)}   ║
╠══════════════════════════════════════════════╣
║  Total Requests:  ${String(totalReqs).padEnd(25)}  ║
║  Avg Duration:    ${String(typeof avgDuration === 'number' ? avgDuration.toFixed(2) + 'ms' : avgDuration).padEnd(25)}  ║
║  P95 Duration:    ${String(typeof p95 === 'number' ? p95.toFixed(2) + 'ms' : p95).padEnd(25)}  ║
║  P99 Duration:    ${String(typeof p99 === 'number' ? p99.toFixed(2) + 'ms' : p99).padEnd(25)}  ║
║  Error Rate:      ${String(errorRate + '%').padEnd(25)}  ║
╚══════════════════════════════════════════════╝
`;

    return {
        stdout: summary,
    };
}
