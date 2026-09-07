## Purpose

Đồng bộ eager giữa session, access token (JTI blacklist), và refresh token khi revoke/kick/logout — đảm bảo token bị vô hiệu hóa ngay lập tức.

## ADDED Requirements

### Requirement: Atomic revocation
Khi revoke session, hệ thống SHALL thực hiện ĐỒNG THỜI trong cùng transaction: revoke session record + blacklist access token JTI + revoke refresh token.

#### Scenario: Kick device revocation
- **WHEN** user kicks một device (session)
- **THEN** hệ thống MUST trong cùng @Transactional: (1) set session.revokedAt + revokeReason, (2) addToBlacklist(session.accessTokenJti), (3) revoke refresh token

#### Scenario: Logout revocation
- **WHEN** user logout
- **THEN** hệ thống MUST trong cùng @Transactional: (1) revoke current session, (2) blacklist current JTI, (3) revoke current refresh token

#### Scenario: Revocation rollback
- **WHEN** bất kỳ step nào trong revocation chain fails
- **THEN** hệ thống MUST rollback toàn bộ transaction — session, blacklist, refresh token đều KHÔNG bị thay đổi

### Requirement: Refresh token rotation with JTI blacklist
Khi refresh token, hệ thống SHALL blacklist old access token JTI và update session.

#### Scenario: Successful refresh
- **WHEN** user refresh token thành công
- **THEN** hệ thống MUST: (1) revoke old refresh token, (2) blacklist old access JTI vào 3-tier cache, (3) issue new token pair, (4) update session.accessTokenJti = new JTI

#### Scenario: Old access token after refresh
- **WHEN** request dùng old access token SAU KHI refresh đã xảy ra
- **THEN** hệ thống MUST reject 401 vì old JTI nằm trong blacklist

### Requirement: Session tracks current access token
LoginSessionEntity SHALL track access token JTI hiện tại.

#### Scenario: Login records JTI
- **WHEN** user đăng nhập thành công
- **THEN** session entity MUST có accessTokenJti = JTI của access token vừa generate

#### Scenario: Refresh updates JTI
- **WHEN** user refresh token thành công
- **THEN** session entity MUST update accessTokenJti = JTI của new access token
