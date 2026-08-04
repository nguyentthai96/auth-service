# BRD-03: Functional Requirements

## Priority Legend
- 🔴 **Critical** — MVP must-have
- 🟡 **Major** — Important for completeness
- 🟢 **Nice-to-have** — Post-MVP

---

## FR-001: User Authentication 🔴

**Given** a user with valid credentials
**When** they submit login request (POST /api/auth/login)
**Then** the system validates credentials and returns JWT + refresh token

**Acceptance Criteria:**
- AC-01: JWT contains userId, domainIds, roles, permissions
- AC-02: Token expires in configurable time (default 30min)
- AC-03: Refresh token expires in 7 days
- AC-04: Failed login returns 401 with error code

---

## FR-002: User Registration 🔴

**Given** a new user with valid registration data
**When** they submit registration (POST /api/auth/register)
**Then** the system creates user account and assigns to specified domain

**Acceptance Criteria:**
- AC-01: Username and email must be unique
- AC-02: Password hashed with BCrypt/Argon2
- AC-03: User auto-assigned to default group (VIEWER) of specified domain
- AC-04: Email verification optional (configurable per domain)

---

## FR-003: Domain Management 🔴

**Given** a Super Admin
**When** they create a new domain (POST /api/domains)
**Then** the system creates domain with default roles/resources/permissions

**Acceptance Criteria:**
- AC-01: Domain code must be unique (e.g., "booking", "rental")
- AC-02: Auto-create default roles: DOMAIN_ADMIN, VIEWER
- AC-03: Auto-create default resources from domain template
- AC-04: Domain can be activated/deactivated

---

## FR-004: Group Management 🔴

**Given** a Domain Admin
**When** they create/edit groups within their domain
**Then** the system manages group membership and role assignments

**Acceptance Criteria:**
- AC-01: Group is scoped to exactly one domain
- AC-02: Group can have multiple roles assigned
- AC-03: User can belong to multiple groups within same domain
- AC-04: Group deletion soft-deletes (preserves audit trail)

---

## FR-005: Role Management 🔴

**Given** a Domain Admin
**When** they define roles for their domain
**Then** the system stores role with hierarchy level and description

**Acceptance Criteria:**
- AC-01: Role code unique within domain (e.g., HOTEL_OWNER, RECEPTIONIST)
- AC-02: Hierarchy level (0 = highest, higher number = lower privilege)
- AC-03: Higher-level roles can manage lower-level users
- AC-04: Predefined roles cannot be deleted, only deactivated

---

## FR-006: Resource Registration 🔴

**Given** a Domain Admin
**When** they register API resources for their domain
**Then** the system stores resource definitions for permission mapping

**Acceptance Criteria:**
- AC-01: Resource has unique code within domain (e.g., "bookings", "rooms", "payments")
- AC-02: Resource can have parent-child hierarchy (optional)
- AC-03: Resources represent API modules/endpoints groups

---

## FR-007: Permission Matrix 🔴

**Given** a Domain Admin
**When** they configure the permission matrix (Role × Resource × Action)
**Then** the system stores granular permission grants

**Acceptance Criteria:**
- AC-01: Actions: READ, CREATE, UPDATE, DELETE, APPROVE, EXPORT
- AC-02: Permission = (role_id, resource_id, action_id) triple
- AC-03: Bulk assignment supported (assign all actions at once)
- AC-04: Read-only = only READ action granted across all resources

---

## FR-008: Permission Check API 🔴

**Given** a service requesting permission verification
**When** it calls the permission check endpoint
**Then** the system evaluates RBAC + PBAC and returns ALLOW/DENY

**Acceptance Criteria:**
- AC-01: Input: userId, domainId, resourceCode, actionCode, context (optional)
- AC-02: RBAC check first (fast path)
- AC-03: PBAC evaluation if policies exist for this resource
- AC-04: Response < 50ms uncached, < 10ms cached
- AC-05: Batch check supported (multiple resource×action pairs)

---

## FR-009: Policy Management 🟡

**Given** a Domain Admin
**When** they create dynamic policies (PBAC)
**Then** the system stores and evaluates policy conditions

**Acceptance Criteria:**
- AC-01: Policy has: name, resource, action, conditions (JSONB), effect (ALLOW/DENY)
- AC-02: Condition types: attribute comparison, time-based, ownership
- AC-03: Example: `{"attribute": "resource.owner_id", "operator": "eq", "value": "$user.id"}`
- AC-04: Policies evaluated AFTER RBAC check (layered)
- AC-05: DENY policies take precedence over ALLOW

---

## FR-010: User Domain Membership 🔴

**Given** a registered user
**When** they are assigned to a domain
**Then** the system tracks their membership and active domain context

**Acceptance Criteria:**
- AC-01: User can be member of multiple domains
- AC-02: Each membership tracks: joined date, status, primary domain flag
- AC-03: Domain switch changes active context (re-generates JWT)
- AC-04: Removing membership revokes all domain-specific access

---

## FR-011: JWT Token Management 🔴

**Given** an authenticated user
**When** a JWT is generated
**Then** the token contains all necessary claims for authorization

**Acceptance Criteria:**
- AC-01: Claims: sub (userId), domains[], activeDomain, roles[], permissions[]
- AC-02: Permissions encoded compactly: `resource:action` format
- AC-03: Token size < 2KB
- AC-04: Refresh token rotation (old token invalidated on refresh)

---

## FR-012: Read-Only Access Control 🔴

**Given** a user with read-only role
**When** they attempt write operations (POST/PUT/DELETE)
**Then** the system denies with 403 Forbidden

**Acceptance Criteria:**
- AC-01: Read-only = role with only READ action on all resources
- AC-02: Enforced at both API level (filter) and service level
- AC-03: Read-only users can access GET endpoints matching their resource scope
- AC-04: Clear error message: "Read-only access - write operations not permitted"

---

## FR-013: Hierarchy-based Access 🟡

**Given** a user with a high-level role (e.g., HOTEL_OWNER, level=0)
**When** they access resources of lower-level roles
**Then** the system allows (hierarchical inheritance)

**Acceptance Criteria:**
- AC-01: Role level 0 can do everything role levels 1, 2, 3... can do
- AC-02: Inheritance is automatic, no explicit permission needed
- AC-03: Can be disabled per domain (flat permission model)

---

## FR-014: Audit Trail 🟢

**Given** any permission-related change
**When** a role/permission/policy is created/updated/deleted
**Then** the system logs the change with actor, timestamp, and details

**Acceptance Criteria:**
- AC-01: Log: who, what, when, old_value, new_value
- AC-02: Stored in audit_log table
- AC-03: Queryable via API (Super Admin only)
