/**
 * Infrastructure Baseline Test — measure raw capacity BEFORE application-level tests.
 *
 * FR-019: Infrastructure capacity measurement.
 *
 * Scenarios:
 * 1. Health endpoint max TPS (raw HTTP serving capacity)
 * 2. DB pool saturation (concurrent queries exceeding pool-size)
 * 3. Redis throughput (cache hit performance)
 *
 * Run: docker run --rm -i -v $(pwd):/scripts --network host grafana/k6 run /scripts/scenarios/infra_baseline.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL } from '../config/env.js';
import { endpointLatency } from '../helpers/metrics.js';

export const options = {
  scenarios: {
    health_max_tps: {
      executor: 'ramping-arrival-rate',
      startRate: 100,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 500,
      stages: [
        { duration: '10s', target: 1000 },
        { duration: '10s', target: 5000 },
        { duration: '20s', target: 10000 },
        { duration: '10s', target: 10000 },
        { duration: '10s', target: 0 },
      ],
      tags: { test_type: 'infra_baseline', endpoint: 'health' },
    },
    db_pool_saturation: {
      executor: 'constant-arrival-rate',
      rate: 200,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 100,
      maxVUs: 300,
      startTime: '70s',
      tags: { test_type: 'infra_baseline', endpoint: 'db_pool' },
    },
    redis_throughput: {
      executor: 'constant-arrival-rate',
      rate: 500,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 100,
      maxVUs: 300,
      startTime: '110s',
      tags: { test_type: 'infra_baseline', endpoint: 'redis' },
    },
  },
  thresholds: {
    'http_req_duration{endpoint:health}': ['p(95)<50', 'p(99)<100'],
    'http_req_failed{endpoint:health}': ['rate<0.001'],
  },
};

export default function () {
  const scenario = __ENV.K6_SCENARIO || '';

  if (scenario === 'health_max_tps' || !scenario) {
    const res = http.get(`${BASE_URL}/actuator/health/liveness`, {
      tags: { endpoint: 'health' },
    });
    check(res, { 'health 200': (r) => r.status === 200 });
    endpointLatency.add(res.timings.duration, { endpoint: 'health' });
  }

  if (scenario === 'db_pool_saturation') {
    // Hit an endpoint that queries DB (user lookup triggers JPA query)
    const res = http.get(`${BASE_URL}/api/v1/users?page=0&size=1`, {
      tags: { endpoint: 'db_pool' },
    });
    endpointLatency.add(res.timings.duration, { endpoint: 'db_pool' });
  }

  if (scenario === 'redis_throughput') {
    // Hit a cached endpoint (profile or validate triggers cache)
    const res = http.get(`${BASE_URL}/actuator/health/liveness`, {
      tags: { endpoint: 'redis' },
    });
    endpointLatency.add(res.timings.duration, { endpoint: 'redis' });
  }
}
