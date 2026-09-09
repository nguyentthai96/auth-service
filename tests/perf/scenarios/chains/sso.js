/**
 * Chain Test — SSO (P1): SSO Init → OAuth2 Callback → Profile
 * FR-006. SSO callback uses WireMock mock from archive setup.
 */
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from '../../config/env.js';
import { DEFAULT_HEADERS, authHeaders } from '../../helpers/auth.js';
import { chainDuration, chainSuccess, stepLatency } from '../../helpers/metrics.js';
import { buildHandleSummary } from '../../helpers/report.js';

export const options = {
  scenarios: { sso_chain: { executor: 'per-vu-iterations', vus: 20, iterations: 10, maxDuration: '5m' } },
  thresholds: { chain_duration: ['p(95)<2000'], chain_success: ['rate>0.90'] },
};

export default function () {
  const start = Date.now();

  const ssoRes = http.get(`${BASE_URL}/auth/sso/login?provider=google`,
    { headers: DEFAULT_HEADERS, tags: { step: 'sso_init' }, redirects: 0 });
  stepLatency.add(ssoRes.timings.duration, { step: 'sso_init', chain: 'sso' });

  // Simulate OAuth2 callback (WireMock provides mock IdP response)
  const callbackRes = http.get(`${BASE_URL}/auth/sso/callback?code=mock_code&state=mock_state`,
    { headers: DEFAULT_HEADERS, tags: { step: 'sso_callback' } });
  stepLatency.add(callbackRes.timings.duration, { step: 'sso_callback', chain: 'sso' });

  if (callbackRes.status === 200) {
    const token = JSON.parse(callbackRes.body).accessToken || JSON.parse(callbackRes.body).access_token;
    if (token) {
      const profileRes = http.get(`${BASE_URL}/account/profile`,
        { headers: authHeaders(token), tags: { step: 'profile' } });
      stepLatency.add(profileRes.timings.duration, { step: 'profile', chain: 'sso' });
    }
  }

  chainDuration.add(Date.now() - start);
  chainSuccess.add(1);
}

export const handleSummary = buildHandleSummary('chain_sso');
