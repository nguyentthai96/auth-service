/**
 * Centralized SLA threshold definitions for auth-service performance tests.
 *
 * FR-016: Shared SLA thresholds — single source of truth for all K6 scripts.
 *
 * Usage:
 *   import { SLA, buildThresholds } from '../config/thresholds.js';
 *
 * SLA values align with NFR-001 through NFR-011 in pre_openspec.md.
 * K6 exits with code 99 when any threshold is violated.
 */

export const SLA = {
  // Auth endpoints (P95 < 200ms, P99 < 500ms — NFR-001, NFR-002)
  login:    { p95: 200, p99: 500, errorRate: 0.01 },
  refresh:  { p95: 150, p99: 300, errorRate: 0.01 },
  validate: { p95: 50,  p99: 100, errorRate: 0.001 },
  register: { p95: 300, p99: 500, errorRate: 0.01 },

  // Profile / CRUD endpoints (P95 < 500ms — NFR-003)
  profile:  { p95: 100, p99: 200, errorRate: 0.01 },
  crud:     { p95: 500, p99: 1000, errorRate: 0.01 },

  // Chain endpoints (NFR-006)
  authChain:     { p95: 800,  p99: 1500, errorRate: 0.01 },
  registerChain: { p95: 1500, p99: 3000, errorRate: 0.01 },

  // System-level (NFR-005, NFR-007, NFR-004)
  system: {
    targetTPS: 500,
    maxP99: 1000,
    maxErrorRate: 0.001,
  },
};

/**
 * Build K6 thresholds object for a given SLA category.
 *
 * @param {string} metricName - K6 metric name (e.g., 'http_req_duration')
 * @param {object} sla - SLA object with p95, p99, errorRate
 * @returns {object} K6 thresholds config
 */
export function buildThresholds(metricName, sla) {
  const thresholds = {};
  if (sla.p95) thresholds[metricName] = [`p(95)<${sla.p95}`];
  if (sla.p99) {
    thresholds[metricName] = thresholds[metricName] || [];
    thresholds[metricName].push(`p(99)<${sla.p99}`);
  }
  if (sla.errorRate !== undefined) {
    thresholds['http_req_failed'] = [`rate<${sla.errorRate}`];
  }
  return thresholds;
}
