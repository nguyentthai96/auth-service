# BRD-04: Business Rules

## BR-001: Domain Code Uniqueness
- Domain codes MUST be unique across the system
- Format: lowercase, alphanumeric + hyphen (e.g., "hotel-booking")
- Max length: 50 characters
- Cannot be changed after creation

## BR-002: Role Hierarchy
- Role hierarchy_level: 0 = highest privilege
- Higher-level roles inherit all permissions of lower levels
- Within a domain, hierarchy is linear (no branching)
- Decision Table:

| User Role Level | Target Resource Level | Access |
|:---:|:---:|:---:|
| 0 | 0, 1, 2, 3 | ✅ Full |
| 1 | 0 | ❌ Denied |
| 1 | 1, 2, 3 | ✅ Inherited |
| 2 | 0, 1 | ❌ Denied |
| 2 | 2, 3 | ✅ Inherited |

## BR-003: Permission Precedence
- DENY always wins over ALLOW
- Order of evaluation:
  1. Check RBAC (role → permission exists?)
  2. If ALLOW → check PBAC policies
  3. If any DENY policy matches → final DENY
  4. If all ALLOW policies match (or no policies) → final ALLOW

## BR-004: Group Scoping
- A Group MUST belong to exactly one Domain
- A User can be in multiple Groups within the same Domain
- Effective permissions = UNION of all group permissions
- No GROUP can span multiple domains

## BR-005: User Domain Membership
- A User CAN belong to multiple Domains
- Each domain membership is independent
- Removing domain membership removes ALL roles/groups in that domain
- User MUST have at least one active domain

## BR-006: Default Roles
- When a domain is created, 2 default roles are auto-created:
  - `DOMAIN_ADMIN` (level=0): Full access to all resources
  - `VIEWER` (level=99): READ-only access to all resources
- Default roles cannot be deleted, only deactivated

## BR-007: Actions Standard Set
- System-wide actions (non-domain-specific):

| Action Code | HTTP Method | Description |
|:---|:---|:---|
| `READ` | GET | View/list resources |
| `CREATE` | POST | Create new resources |
| `UPDATE` | PUT/PATCH | Modify existing resources |
| `DELETE` | DELETE | Remove resources (soft/hard) |
| `APPROVE` | POST | Approve/reject workflows |
| `EXPORT` | GET | Export/download data |

## BR-008: JWT Claims Structure
```json
{
  "sub": "user-uuid",
  "iss": "auth-service",
  "iat": 1719950400,
  "exp": 1719952200,
  "domains": ["booking", "rental"],
  "active_domain": "booking",
  "roles": ["HOTEL_OWNER"],
  "permissions": ["bookings:*", "rooms:*", "payments:READ"],
  "groups": ["hotel-a-staff"]
}
```
- Wildcard `*` means all actions on resource
- Compact format to keep JWT < 2KB

## BR-009: Password Policy
- Minimum 8 characters
- Must contain: uppercase, lowercase, number
- Special characters optional but encouraged
- Max 3 failed login attempts → account locked for 15 min
- Password hash: BCrypt (cost=12)

## BR-010: Soft Delete
- All entities use soft-delete (active=false)
- Soft-deleted entities excluded from queries by default
- Hard delete only by Super Admin via special API
- Referential integrity maintained (cascade soft-delete)

## BR-011: Policy Condition Types
| Type | Example | Description |
|:---|:---|:---|
| `attribute_eq` | `resource.owner_id == $user.id` | Attribute equality |
| `attribute_in` | `$user.department IN ["finance", "hr"]` | Set membership |
| `time_range` | `current_time BETWEEN 08:00 AND 17:00` | Time-based access |
| `ip_range` | `$request.ip IN 192.168.1.0/24` | IP-based restriction |
| `custom` | `$context.approval_status == "APPROVED"` | Custom context |

## BR-012: Multi-Domain Permission Isolation
- Permissions from Domain A MUST NOT affect Domain B
- Even if same user has ADMIN in Domain A, they get NO access in Domain B unless explicitly assigned
- Domain switching requires JWT re-generation
