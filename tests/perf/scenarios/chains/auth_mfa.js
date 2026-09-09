/**
 * Chain Test — Auth MFA (P0): Login → MFA Challenge → MFA Verify → Profile
 * FR-006
 */
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from '../../config/env.js';
import { DEFAULT_HEADERS, authHeaders } from '../../helpers/auth.js';
import { getUserForVU } from '../../helpers/data.js';
import { chainDuration, chainSuccess, stepLatency } from '../../helpers/metrics.js';
import { buildHandleSummary } from '../../helpers/report.js';

export const options = {
  scenarios: { auth_mfa_chain: { executor: 'per-vu-iterations', vus: 30, iterations: 10, maxDuration: '5m' } },
  thresholds: { chain_duration: ['p(95)<2000'], chain_success: ['rate>0.95'] },
};

export default function () {
  const start = Date.now();
  const user = getUserForVU(__VU);

  const loginRes = http.post(`${BASE_URL}/auth/login`,
    JSON.stringify({ username: user.username, password: user.password }),
    { headers: DEFAULT_HEADERS, tags: { step: 'login' } });
  stepLatency.add(loginRes.timings.duration, { step: 'login', chain: 'auth_mfa' });
  if (!check(loginRes, { 'login ok': (r) => r.status === 200 })) { chainSuccess.add(0); return; }

  const token = JSON.parse(loginRes.body).accessToken || JSON.parse(loginRes.body).access_token;

  const challengeRes = http.post(`${BASE_URL}/auth/mfa/challenge`,
    JSON.stringify({ method: 'totp' }),
    { headers: authHeaders(token), tags: { step: 'mfa_challenge' } });
  stepLatency.add(challengeRes.timings.duration, { step: 'mfa_challenge', chain: 'auth_mfa' });

  const verifyRes = http.post(`${BASE_URL}/auth/mfa/verify`,
    JSON.stringify({ code: '123456', method: 'totp' }),
    { headers: authHeaders(token), tags: { step: 'mfa_verify' } });
  stepLatency.add(verifyRes.timings.duration, { step: 'mfa_verify', chain: 'auth_mfa' });

  const profileRes = http.get(`${BASE_URL}/account/profile`,
    { headers: authHeaders(token), tags: { step: 'profile' } });
  stepLatency.add(profileRes.timings.duration, { step: 'profile', chain: 'auth_mfa' });

  chainDuration.add(Date.now() - start);
  chainSuccess.add(1);
}

export const handleSummary = buildHandleSummary('chain_auth_mfa');
