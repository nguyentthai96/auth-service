/**
 * Chain Test — Key Exchange (P1): Init Key Exchange → Encrypted Request → Response
 * FR-006
 */
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from '../../config/env.js';
import { DEFAULT_HEADERS, authHeaders } from '../../helpers/auth.js';
import { getUserForVU } from '../../helpers/data.js';
import { chainDuration, chainSuccess, stepLatency } from '../../helpers/metrics.js';

export const options = {
  scenarios: { key_exchange_chain: { executor: 'per-vu-iterations', vus: 20, iterations: 10, maxDuration: '5m' } },
  thresholds: { chain_duration: ['p(95)<2000'], chain_success: ['rate>0.90'] },
};

export default function () {
  const start = Date.now();
  const user = getUserForVU(__VU);

  // Login first
  const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username: user.username, password: user.password }),
    { headers: DEFAULT_HEADERS, tags: { step: 'login' } });
  stepLatency.add(loginRes.timings.duration, { step: 'login', chain: 'key_exchange' });
  if (!check(loginRes, { 'login ok': (r) => r.status === 200 })) { chainSuccess.add(0); return; }
  const token = JSON.parse(loginRes.body).accessToken || JSON.parse(loginRes.body).access_token;

  // Step 1: Init key exchange
  const initRes = http.post(`${BASE_URL}/api/v1/encryption/key-exchange`,
    JSON.stringify({ clientPublicKey: 'mock-public-key-base64' }),
    { headers: authHeaders(token), tags: { step: 'key_init' } });
  stepLatency.add(initRes.timings.duration, { step: 'key_init', chain: 'key_exchange' });

  // Step 2: Send encrypted request
  const encryptedRes = http.post(`${BASE_URL}/api/v1/encryption/decrypt`,
    JSON.stringify({ encryptedData: 'mock-encrypted-payload', keyId: 'test-key-id' }),
    { headers: authHeaders(token), tags: { step: 'encrypted_req' } });
  stepLatency.add(encryptedRes.timings.duration, { step: 'encrypted_req', chain: 'key_exchange' });

  chainDuration.add(Date.now() - start);
  chainSuccess.add(1);
}
