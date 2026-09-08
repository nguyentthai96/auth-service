/**
 * Test data generators for K6 performance tests.
 *
 * FR-005 support: SharedArray for pre-generated test users, unique username gen.
 *
 * Usage:
 *   import { testUsers, generateUsername, adminCreds } from '../helpers/data.js';
 */

import { SharedArray } from 'k6/data';

/**
 * Pre-generated test users (SharedArray — shared across VUs, read-only).
 * These users must exist in the test database before running load tests.
 */
export const testUsers = new SharedArray('test_users', function () {
  const users = [];
  for (let i = 1; i <= 50; i++) {
    users.push({
      username: `perfuser${i}`,
      password: `PerfTest${i}!`,
      email: `perfuser${i}@test.local`,
    });
  }
  return users;
});

/**
 * Generate a unique username per VU + iteration.
 * Avoids collisions in registration tests.
 * @param {number} vuId - __VU
 * @param {number} iter - __ITER
 * @returns {string} Unique username
 */
export function generateUsername(vuId, iter) {
  const ts = Date.now();
  return `reg_vu${vuId}_i${iter}_${ts}`;
}

/**
 * Generate unique email for registration tests.
 * @param {string} username
 * @returns {string} Email address
 */
export function generateEmail(username) {
  return `${username}@perftest.local`;
}

/**
 * Get a test user based on VU ID (round-robin).
 * @param {number} vuId - __VU
 * @returns {object} { username, password, email }
 */
export function getUserForVU(vuId) {
  return testUsers[vuId % testUsers.length];
}
