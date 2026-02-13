# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 0.1.x   | Yes       |

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
- **E2E encryption** infrastructure ready (Signal Protocol, key exchange endpoints built)

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
