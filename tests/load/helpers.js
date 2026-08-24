import http from 'k6/http';
import { check } from 'k6';

/**
 * Shared K6 helper utilities for auth-service load testing.
 *
 * FR-003, FR-010: Provides reusable functions across K6 scripts.
 *
 * Usage:
 *   import { loginAndGetToken, BASE_URL, DEFAULT_HEADERS } from './helpers.js';
 *
 *   export default function () {
 *     const token = loginAndGetToken('user1', 'pass1');
 *     // Use token for authenticated requests...
 *   }
 */

/** Base URL for auth-service. Uses localhost with --network host Docker mode. */
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

/** Default JSON headers for API requests. */
export const DEFAULT_HEADERS = {
    'Content-Type': 'application/json',
};

/**
 * Login and extract access token from response.
 *
 * @param {string} username - User credentials
 * @param {string} password - User credentials
 * @returns {string|null} Access token or null if login failed
 */
export function loginAndGetToken(username, password) {
    const url = `${BASE_URL}/api/v1/auth/login`;
    const payload = JSON.stringify({
        username: username,
        password: password,
    });

    const res = http.post(url, payload, { headers: DEFAULT_HEADERS });

    const loginOk = check(res, {
        'login status 200': (r) => r.status === 200,
        'login has accessToken': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.accessToken !== undefined || body.access_token !== undefined;
            } catch (_) {
                return false;
            }
        },
    });

    if (!loginOk) {
        return null;
    }

    try {
        const body = JSON.parse(res.body);
        return body.accessToken || body.access_token || null;
    } catch (_) {
        return null;
    }
}

/**
 * Create authorization headers with Bearer token.
 *
 * @param {string} token - JWT access token
 * @returns {Object} Headers object with Authorization and Content-Type
 */
export function authHeaders(token) {
    return {
        ...DEFAULT_HEADERS,
        'Authorization': `Bearer ${token}`,
    };
}

/**
 * Get profile with authenticated request.
 *
 * @param {string} token - JWT access token
 * @returns {Object} K6 http response
 */
export function getProfile(token) {
    const url = `${BASE_URL}/api/v1/profiles/me`;
    return http.get(url, { headers: authHeaders(token) });
}
