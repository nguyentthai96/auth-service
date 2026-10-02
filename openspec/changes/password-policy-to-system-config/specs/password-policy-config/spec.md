## Purpose

Enables centralized, real-time synchronized management of password policies across microservices using a multi-tier fallback caching architecture.

## ADDED Requirements

### Requirement: Centralized Policy Source
The system SHALL retrieve password policy parameters from the `AUTH_LOGIN` configuration group managed centrally, rather than a local standalone database table.

#### Scenario: Real-time update application
- **WHEN** an administrator updates a password policy parameter (e.g., `password.min_length`) in the central configuration
- **THEN** the authentication service immediately applies the new policy without requiring a restart or redeployment

### Requirement: Multi-tier Resilience
The system SHALL employ a fallback mechanism to ensure password policies are always available, prioritizing zero-latency local cache, then shared cache, then remote API, and finally static local defaults.

#### Scenario: Caches unavailable
- **WHEN** both the local in-memory cache and the shared Redis cache are empty or unavailable
- **THEN** the system fetches the configuration from the remote API, and if that fails, falls back to the static YAML defaults without failing the user authentication request
