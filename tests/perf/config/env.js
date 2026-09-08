/**
 * Environment configuration for K6 performance tests.
 *
 * FR-007 support: Configurable traffic mix ratios via env vars.
 *
 * Usage:
 *   import { BASE_URL, TRAFFIC_MIX, SOAK_DURATION } from '../config/env.js';
 */

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const TRAFFIC_MIX = {
  auth:      parseInt(__ENV.MIX_AUTH     || '60'),
  profile:   parseInt(__ENV.MIX_PROFILE  || '20'),
  register:  parseInt(__ENV.MIX_REGISTER || '10'),
  admin:     parseInt(__ENV.MIX_ADMIN    || '5'),
  mfa_sso:   parseInt(__ENV.MIX_MFA_SSO  || '5'),
};

export const SOAK_DURATION = __ENV.SOAK_DURATION || '1h';

export const PROMETHEUS_REMOTE_WRITE_URL =
  __ENV.K6_PROMETHEUS_RW_SERVER_URL || 'http://localhost:9090/api/v1/write';

// Test user credentials (must exist in test DB)
export const TEST_USER = {
  username: __ENV.TEST_USER || 'perfuser',
  password: __ENV.TEST_PASS || 'PerfTest123!',
};

export const ADMIN_USER = {
  username: __ENV.ADMIN_USER || 'admin',
  password: __ENV.ADMIN_PASS || 'Admin123!',
};
