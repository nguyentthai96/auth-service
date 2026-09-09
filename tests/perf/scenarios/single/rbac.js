/**
 * Single API Tests — RBAC Endpoints
 *
 * FR-005: Roles, users, permissions, policies endpoints
 */

import http from 'k6/http';
import { check } from 'k6';
import { SLA } from '../../config/thresholds.js';
import { BASE_URL } from '../../config/env.js';
import { loginAdminUser, authHeaders } from '../../helpers/auth.js';
import { endpointLatency } from '../../helpers/metrics.js';
import { buildHandleSummary } from '../../helpers/report.js';

export const options = {
  scenarios: {
    roles: {
      executor: 'constant-arrival-rate', rate: 50, timeUnit: '1s',
      duration: '30s', preAllocatedVUs: 30, maxVUs: 100,
      tags: { endpoint: 'roles' }, exec: 'testRoles',
    },
    users: {
      executor: 'constant-arrival-rate', rate: 50, timeUnit: '1s',
      duration: '30s', preAllocatedVUs: 30, maxVUs: 100,
      startTime: '35s', tags: { endpoint: 'users' }, exec: 'testUsers',
    },
    permissions: {
      executor: 'constant-arrival-rate', rate: 30, timeUnit: '1s',
      duration: '30s', preAllocatedVUs: 20, maxVUs: 60,
      startTime: '70s', tags: { endpoint: 'permissions' }, exec: 'testPermissions',
    },
    policies: {
      executor: 'constant-arrival-rate', rate: 30, timeUnit: '1s',
      duration: '30s', preAllocatedVUs: 20, maxVUs: 60,
      startTime: '105s', tags: { endpoint: 'policies' }, exec: 'testPolicies',
    },
  },
  thresholds: {
    'http_req_duration{endpoint:roles}':       [`p(95)<${SLA.crud.p95}`],
    'http_req_duration{endpoint:users}':       [`p(95)<${SLA.crud.p95}`],
    'http_req_duration{endpoint:permissions}': [`p(95)<${SLA.crud.p95}`],
    'http_req_duration{endpoint:policies}':    [`p(95)<${SLA.crud.p95}`],
  },
};

export function testRoles() {
  const token = loginAdminUser();
  if (!token) return;
  const res = http.get(`${BASE_URL}/admin/domains/1/roles`,
    { headers: authHeaders(token), tags: { endpoint: 'roles' } });
  check(res, { 'roles 200': (r) => r.status === 200 });
  endpointLatency.add(res.timings.duration, { endpoint: 'roles' });
}

export function testUsers() {
  const token = loginAdminUser();
  if (!token) return;
  const res = http.get(`${BASE_URL}/admin/users?page=0&size=10`,
    { headers: authHeaders(token), tags: { endpoint: 'users' } });
  check(res, { 'users 200': (r) => r.status === 200 });
  endpointLatency.add(res.timings.duration, { endpoint: 'users' });
}

export function testPermissions() {
  const token = loginAdminUser();
  if (!token) return;
  const res = http.get(`${BASE_URL}/admin/domains/1/roles/1/permissions`,
    { headers: authHeaders(token), tags: { endpoint: 'permissions' } });
  endpointLatency.add(res.timings.duration, { endpoint: 'permissions' });
}

export function testPolicies() {
  const token = loginAdminUser();
  if (!token) return;
  const res = http.get(`${BASE_URL}/admin/domains/1/policies`,
    { headers: authHeaders(token), tags: { endpoint: 'policies' } });
  endpointLatency.add(res.timings.duration, { endpoint: 'policies' });
}

export const handleSummary = buildHandleSummary('rbac');
