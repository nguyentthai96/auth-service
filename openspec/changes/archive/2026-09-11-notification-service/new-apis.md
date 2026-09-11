# New APIs: notification-service

## REST Endpoints

| # | Method | Path | Purpose | Request | Response |
|---|--------|------|---------|---------|----------|
| 1 | POST | `/api/v1/notifications` | Enqueue new notification | `EnqueueNotificationRequest` | `201 Created` + `{id, status}` |
| 2 | GET | `/api/v1/notifications/{id}` | Get notification status | Path: `id` (Long) | `200 OK` + `NotificationStatusResponse` |
| 3 | POST | `/api/v1/notifications/{id}/retry` | Retry FAILED notification | Path: `id` (Long) | `200 OK` + `{id, status, message}` |
| 4 | POST | `/api/v1/notifications/{id}/revoke` | Revoke/cancel notification | `RevokeRequest` | `200 OK` + `{id, message}` |

## SDK Interface (notification-client)

| Method | Signature | Purpose |
|--------|-----------|---------|
| `enqueue` | `NotificationPort.enqueue(NotificationRequest): Long` | Transactional outbox insert |
