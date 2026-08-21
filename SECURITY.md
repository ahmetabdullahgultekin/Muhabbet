# Security Policy

## Supported Versions

Pre-1.0, only the newest release is supported. There is no backporting — the fix ships in the next
version.

| Version | Supported |
|---------|-----------|
| Latest release (see [CHANGELOG.md](CHANGELOG.md)) | Yes |
| Anything older | No — upgrade |

## Reporting a Vulnerability

If you discover a security vulnerability in Muhabbet, please report it responsibly:

1. **DO NOT** open a public GitHub issue for security vulnerabilities
2. Email **security@rollingcatsoftware.com** with details
3. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

## Response Timeline
- **Acknowledgment:** Within 48 hours
- **Assessment:** Within 1 week
- **Fix:** Depending on severity, within 1-4 weeks

## Security Measures

Muhabbet implements the following security measures:

### Transport & Headers
- **TLS 1.2+** enforced on all connections
- **HSTS** with max-age=31536000 (1 year), includeSubDomains
- **X-Frame-Options** DENY — prevents clickjacking
- **X-Content-Type-Options** nosniff — prevents MIME sniffing
- **Content-Security-Policy** `default-src 'self'; frame-ancestors 'none'; form-action 'self'`
- **Referrer-Policy** strict-origin-when-cross-origin
- **Permissions-Policy** restricts geolocation, camera, microphone access
- **X-XSS-Protection** enabled with block mode

### Authentication & Authorization
- **JWT (HS256)** with short-lived access tokens (15 min) + refresh token rotation
- **OTP** BCrypt-hashed, rate-limited (5 attempts, 60s cooldown)
- **Rate limiting** on authentication endpoints (10 req/min/IP)

### Input Validation & Sanitization
- **InputSanitizer** — server-side HTML entity escaping, control character stripping, HTTPS-only URL validation
- **Display name** sanitization with length limits (64 chars)
- **Message content** length limiting (10,000 chars)
- **URL validation** rejects `javascript:` and `data:` schemes

### Data Protection
- **Phone numbers** stored as SHA-256 hashes (never in plaintext)
- **KVKK compliance** — data export endpoint, account soft-deletion
- **Secrets** via environment variables, no credentials in code
- **End-to-end encryption is OFF.** Messages are encrypted in transit (TLS) and are readable by the
  server. Signal Protocol key exchange, storage and a send/receive path exist behind a default-off
  flag; the Android primitive does not currently compile against its pinned libsignal version, so
  the flag must not be turned on. Treat message history on the server as plaintext when assessing
  impact. 1.0.0 is reserved for the release that changes this.

### CI/CD Security
- **Trivy** vulnerability scanning (filesystem + Docker images)
- **Gitleaks** secret detection in commits
- **CodeQL** static analysis for Java/Kotlin
- **Automated scanning** on every push + weekly scheduled scans

## Scope

The following are in scope for security reports:
- Authentication/authorization bypass
- Data exposure or leakage
- Injection vulnerabilities (SQL, XSS, etc.)
- Cryptographic weaknesses
- Server-side vulnerabilities

Out of scope:
- Social engineering attacks
- Physical attacks
- Denial of service (volumetric)
- Issues in third-party dependencies (report to upstream)

## Acknowledgments

We appreciate responsible disclosure and will acknowledge reporters in our security advisories (with permission).
