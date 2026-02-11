# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added — Auth Module (Week 1)
- **Domain models**: User aggregate, Device entity, OtpRequest value object
- **Use case ports**: RequestOtp, VerifyOtp, RefreshToken, Logout
- **Repository ports**: UserRepository, OtpRepository, DeviceRepository, RefreshTokenRepository, PhoneHashRepository
- **AuthService**: Full business logic — OTP request with cooldown, OTP verify with BCrypt, token refresh with rotation, logout
- **JPA persistence**: 5 JPA entities with domain mappers, 5 Spring Data repos, 5 persistence adapters
- **MockOtpSender**: Logs OTP to console for development (conditional on `muhabbet.otp.mock-enabled`)
- **JWT security**: JwtProvider (HS256 via JJWT), JwtAuthFilter, SecurityConfig (stateless, CSRF disabled)
- **AuthenticatedUser**: Utility to extract userId/deviceId from SecurityContext
- **REST endpoints**: `POST /api/v1/auth/otp/request`, `otp/verify`, `token/refresh`, `logout`
- **User endpoints**: `GET /api/v1/users/me`, `PATCH /api/v1/users/me`
- **Phone hash**: SHA-256 hash stored on user creation for contact sync
- **Unit tests**: 9 AuthService tests (MockK) covering happy paths and all error codes
- **Integration test**: AuthControllerIntegrationTest with Testcontainers PostgreSQL

### Added — Shared KMP Module (Week 1)
- Domain models: Message, Conversation, UserProfile, Contact
- WebSocket protocol: WsMessage sealed class with all frame types
- DTOs: Auth, User, Conversation, Media request/response types
- ValidationRules: Turkish phone, OTP, display name, message length, media size
- EncryptionPort interface + NoOpEncryption (TLS-only MVP)

### Added — Project Setup (Week 1)
- Gradle multi-project build (backend, shared, mobile)
- Gradle wrapper 8.12
- Docker Compose: PostgreSQL 16 + Redis 7 + MinIO
- Flyway migration V1: users, devices, otp_requests, refresh_tokens, conversations, messages, media_files
- Application profiles: dev (mock OTP, debug SQL), prod (real SMS, JSON logs)
- ErrorCode enum with Turkish messages
- GlobalExceptionHandler + ApiResponseBuilder

### Fixed
- Android Gradle Plugin declared in root build.gradle.kts to fix shared module build
- JVM target 21 set for shared module's jvm() target
- Added kotlinx-datetime dependency to backend for shared model compatibility
