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

- **Transport:** TLS 1.2+ enforced, HSTS enabled
- **Authentication:** JWT (HS256) with short-lived access tokens (15 min)
- **OTP:** BCrypt-hashed, rate-limited (5 attempts, 60s cooldown)
- **API:** Rate limiting on authentication endpoints
- **Headers:** X-Frame-Options, X-Content-Type-Options, X-XSS-Protection, HSTS
- **Data:** Phone numbers stored as SHA-256 hashes
- **Infrastructure:** Secrets via environment variables, no credentials in code

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
