/**
 * Custom K6 metrics definitions for performance tests.
 *
 * FR-005, FR-006 support: Per-endpoint and chain-level metrics.
 *
 * Usage:
 *   import { endpointLatency, chainDuration, totalTransactions } from '../helpers/metrics.js';
 */

import { Trend, Rate, Counter } from 'k6/metrics';

// Per-endpoint metrics (used in single API tests)
export const endpointLatency = new Trend('endpoint_latency', true);
export const endpointErrors  = new Rate('endpoint_errors');

// Chain-level metrics (used in chain tests)
export const chainDuration = new Trend('chain_duration', true);
export const chainSuccess  = new Rate('chain_success');
export const stepLatency   = new Trend('step_latency', true);

// System metrics (used in full load / stress / soak)
export const totalTransactions = new Counter('total_transactions');
export const systemTPS         = new Trend('system_tps', true);
