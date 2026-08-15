# New APIs — e2ee-performance-compliance

| Method | Path | Description | Auth Required |
|--------|------|-------------|---------------|
| POST | `/auth/key-exchange` | X25519 ECDH key exchange — negotiates E2EE session | Yes (JWT) |

## POST /auth/key-exchange

**Request:**
```json
{
  "clientPublicKey": "Base64-encoded X25519 public key (32 bytes)",
  "deviceId": "unique-device-id",
  "appVersion": "1.0.0",
  "platform": "Android|iOS|Web",
  "userId": "optional-user-id"
}
```

**Response (200):**
```json
{
  "serverPublicKey": "Base64-encoded server X25519 public key",
  "keyId": "UUID — use in X-Key-ID header for subsequent requests",
  "keyVersion": 1,
  "algorithm": "AES_GCM",
  "expiresAt": 1723833600000
}
```

**Error Responses:**
| Code | Error | HTTP |
|------|-------|------|
| AUTH_039 | E2EE_MAX_DEVICES | 429 |

**Notes:**
- Excluded from CipherFilter (plaintext endpoint)
- Idempotent: same clientPublicKey + deviceId → returns existing session
- Max 5 devices per user (configurable via `app.security.cipher.key-exchange.max-devices-per-user`)
