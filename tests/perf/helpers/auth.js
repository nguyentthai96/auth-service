/**
 * Shared auth utilities for performance tests.
 *
 * FR-005, FR-006 support: Reuses login logic from tests/load/helpers.js
 *
 * Usage:
 *   import { loginAndGetToken, authHeaders, BASE_URL } from '../helpers/auth.js';
 */

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, TEST_USER, ADMIN_USER } from '../config/env.js';

export const DEFAULT_HEADERS = {
  'Content-Type': 'application/json',
};

/**
 * Login and extract access token from response.
 * @param {string} username
 * @param {string} password
 * @returns {string|null} Access token or null on failure
 */
export function loginAndGetToken(username, password) {
  const res = http.post(`${BASE_URL}/auth/login`,
    JSON.stringify({ username, password }),
    { headers: DEFAULT_HEADERS, tags: { endpoint: 'login' } }
  );

  const ok = check(res, {
    'login status 200': (r) => r.status === 200,
    'login has token': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.accessToken !== undefined || body.access_token !== undefined;
      } catch (_) { return false; }
    },
  });

  if (!ok) return null;
  try {
    const body = JSON.parse(res.body);
    return body.accessToken || body.access_token || null;
  } catch (_) { return null; }
}

/**
 * Create Authorization headers with Bearer token.
 * @param {string} token - JWT access token
 * @returns {object} Headers with Authorization + Content-Type
 */
export function authHeaders(token) {
  return { ...DEFAULT_HEADERS, Authorization: `Bearer ${token}` };
}

/**
 * Login with default test user and return token.
 * @returns {string|null}
 */
export function loginTestUser() {
  return loginAndGetToken(TEST_USER.username, TEST_USER.password);
}

/**
 * Login with admin user and return token.
 * @returns {string|null}
 */
export function loginAdminUser() {
  return loginAndGetToken(ADMIN_USER.username, ADMIN_USER.password);
}

export { BASE_URL };
