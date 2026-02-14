# 07 — Safety & Compliance

> Quality attribute: Adherence to legal requirements, regulatory standards, and safe operation.

---

## 1. Regulatory Framework

### 1.1 Applicable Regulations

| Regulation | Scope | Status | Priority |
|-----------|-------|--------|----------|
| **KVKK** (Law No. 6698) | Turkish personal data protection (GDPR equivalent) | Partially compliant | P0 |
| **BTK Law No. 5651** | Internet content regulation, reporting obligations | Infrastructure ready | P0 |
| **GDPR** | EU data protection (if serving EU users) | Not assessed | P2 |
| **e-Commerce Law (6563)** | Electronic communication consent | Not assessed | P1 |
| **Turkish Consumer Law** | Terms of service, user rights | Privacy policy written | P1 |

### 1.2 Regulatory Contacts

| Authority | Scope | Website |
|-----------|-------|---------|
| KVKK (Kişisel Verileri Koruma Kurumu) | Data protection authority | kvkk.gov.tr |
| BTK (Bilgi Teknolojileri ve İletişim Kurumu) | Telecommunications regulator | btk.gov.tr |
| TİB (Telekomünikasyon İletişim Başkanlığı) | Content regulation | — |

---

## 2. KVKK Compliance

### 2.1 Data Processing Principles (Article 4)

| Principle | Requirement | Implementation | Status |
|-----------|------------|----------------|--------|
| Lawfulness | Legal basis for processing | Consent at registration | Deployed |
| Purpose limitation | Process only for stated purpose | Messaging only | Deployed |
| Data minimization | Collect only necessary data | Phone + display name | Deployed |
| Accuracy | Keep data accurate | User can edit profile | Deployed |
| Storage limitation | Delete when no longer needed | Soft delete + export | Deployed |
| Security | Technical/organizational measures | TLS, auth, sanitization | Deployed |
| Accountability | Document compliance | This document | In progress |

### 2.2 Data Subject Rights (Articles 11-13)

| Right | Endpoint | Status | Test |
|-------|----------|--------|------|
| Right to know | `GET /api/v1/users/data/export` | Deployed | P0 |
| Right to access | `GET /api/v1/users/data/export` | Deployed | P0 |
| Right to rectification | `PATCH /api/v1/users/me` | Deployed | P0 |
| Right to erasure | `DELETE /api/v1/users/data/account` | Deployed (soft delete) | P0 |
| Right to portability | `GET /api/v1/users/data/export` (JSON) | Deployed | P0 |
| Right to object | Account deletion | Deployed | P0 |
| Right to withdraw consent | Account deletion | Deployed | P0 |

### 2.3 Data Export Contents

The data export endpoint (`GET /api/v1/users/data/export`) must include:

| Data Category | Included | Format |
|--------------|----------|--------|
| User profile (phone, name, about) | Yes | JSON |
| All sent/received messages | Yes | JSON |
| All conversations | Yes | JSON |
| Media files metadata | Yes | JSON (URLs) |
| Device information | Yes | JSON |
| Call history | Yes | JSON |
| Starred messages | Yes | JSON |
| Report history | Pending | JSON |
| Block list | Pending | JSON |
| Login history | Pending | JSON |

### 2.4 Data Retention Schedule

| Data Type | Retention Period | Deletion Method | Legal Basis |
|-----------|-----------------|-----------------|-------------|
| Messages | Until user deletes | Soft delete (`deleted_at`) | User consent |
| Media files | Until user deletes | MinIO object removal | User consent |
| OTP codes | 5 minutes (Redis TTL) | Auto-expire | Technical necessity |
| Refresh tokens | 30 days | Rotation + cleanup | Session management |
| Presence data | 60 seconds (Redis TTL) | Auto-expire | Technical necessity |
| Statuses | 24 hours | Scheduled cleanup | Feature design |
| Call history | Until user deletes | Soft delete | User consent |
| Backups | 90 days (`expires_at`) | Auto-expire | User consent |
| Moderation reports | 1 year | Archive | Legal obligation |
| Deleted accounts | 30 days (grace period) | Hard delete | KVKK Art. 7 |

### 2.5 KVKK Test Cases

| Test | Description | Priority |
|------|-------------|----------|
| Data export includes all user messages | Verify completeness | P0 |
| Data export includes all conversations | Verify completeness | P0 |
| Account deletion marks user as deleted | Verify soft delete | P0 |
| Deleted user cannot log in | Verify access blocked | P0 |
| Deleted user data export still works (30 day grace) | Verify data portability | P0 |
| After 30 days, deleted user data is hard deleted | Verify permanent erasure | P1 |
| Phone hash removed on account deletion | Verify contact sync removal | P1 |
| Media files removed on account deletion | Verify MinIO cleanup | P1 |

---

## 3. BTK Law 5651 Compliance

### 3.1 Content Moderation Requirements

| Requirement | Implementation | Status |
|-------------|---------------|--------|
| Report mechanism | `POST /api/v1/moderation/reports` | Deployed |
| Block mechanism | `POST /api/v1/moderation/blocks` | Deployed |
| Report review | Admin review endpoints | Deployed |
| Report categories | SPAM, HARASSMENT, INAPPROPRIATE_CONTENT, IMPERSONATION, OTHER | Deployed |
| Report tracking | PENDING → REVIEWED → RESOLVED/DISMISSED | Deployed |
| Content removal | Soft delete on reported content (by admin) | Partial |
| User blocking | Bi-directional block (no messages, no calls) | Deployed |
| Self-block prevention | Cannot block yourself (BLOCK_SELF error) | Deployed |

### 3.2 Legal Content Obligations

| Obligation | Requirement | Status |
|-----------|-------------|--------|
| Respond to user reports | Within 48 hours | Policy needed |
| Respond to BTK requests | Within 24 hours | Process needed |
| Content removal on court order | Immediate compliance | Process needed |
| Log retention for law enforcement | 1-2 years (metadata only) | Not implemented |
| Quarterly transparency report | Report on content actions | Not implemented |
| Local data storage | Data in Turkey or EU | GCP europe-west1 (Belgium) — EU region, not Turkey |

### 3.3 Test Cases

| Test | Description | Priority |
|------|-------------|----------|
| User can report another user | Full report flow | P0 |
| User can block another user | Full block flow | P0 |
| Blocked user cannot send messages | Verify enforcement | P0 |
| Admin can review and resolve reports | Admin workflow | P0 |
| Report includes all required metadata | Verify completeness | P1 |
| Self-block returns appropriate error | Edge case | P1 |
| Duplicate report handling | No duplicate reports | P1 |

### 3.4 Moderation Error Codes

| Error Code | HTTP Status | Trigger |
|-----------|------------|---------|
| `REPORT_NOT_FOUND` | 404 | Report ID doesn't exist |
| `BLOCK_SELF` | 400 | User attempts to block themselves |

### 3.5 Data Flow for Moderation

```
User reports content → POST /api/v1/moderation/reports
                       → ModerationService.reportUser()
                       → ReportRepository.save()
                       → Returns reportId

Admin reviews        → GET /api/v1/moderation/reports?status=PENDING
                       → PATCH /api/v1/moderation/reports/{id}
                       → Updates status: PENDING → REVIEWED → RESOLVED/DISMISSED
```

---

## 4. Privacy by Design

### 4.1 Privacy Controls

| Control | Implementation | Status |
|---------|---------------|--------|
| Phone number privacy | Hash-based contact sync (SHA-256) | Deployed |
| Online status privacy | Last seen visible to contacts only (future) | Partial |
| Profile photo privacy | Visible to all users currently | Gap (needs visibility settings) |
| Read receipt privacy | Always on (no opt-out yet) | Gap |
| Typing indicator privacy | Always on (no opt-out yet) | Gap |
| Status privacy | Visible to all contacts | Deployed |
| Group add privacy | Anyone can add (no permission yet) | Gap |
| Message forwarding privacy | "Forwarded" label shown | Deployed |

### 4.2 Privacy Enhancement Roadmap

| Feature | Description | Priority |
|---------|-------------|----------|
| Last seen visibility | Contacts only / Nobody / Everyone | P1 |
| Profile photo visibility | Contacts only / Nobody / Everyone | P1 |
| Read receipt opt-out | Disable read receipts per user | P2 |
| Group add permission | Nobody / Contacts only / Everyone | P2 |
| Disappearing messages default | Set per-conversation or global | Deployed |
| End-to-end encryption | Signal Protocol (when implemented) | P0 |

---

## 5. Safety Measures

### 5.1 User Safety

| Measure | Implementation | Status |
|---------|---------------|--------|
| Block abusive users | Block feature | Deployed |
| Report abusive content | Report feature | Deployed |
| Disappearing messages | 24h, 7d, 90d options | Deployed |
| No screenshot notification | Not implemented (platform limitation) | N/A |
| Two-factor authentication | OTP only (no 2FA PIN) | Gap |
| Login notification | Not implemented | Gap |

### 5.2 Child Safety

| Measure | Requirement | Status |
|---------|------------|--------|
| Age verification | Age gate at registration (13+ or 18+) | Not implemented |
| Content filtering | AI-based content moderation | Not implemented |
| Parental controls | Not applicable (general messaging) | N/A |
| CSAM detection | PhotoDNA or similar hash matching | Not implemented |

### 5.3 Platform Safety

| Measure | Implementation | Status |
|---------|---------------|--------|
| Rate limiting | Auth: 10 req/60s per IP, WS: 50 msg/10s per user | Deployed |
| Spam prevention | Rate limits + block feature | Deployed |
| Abuse reporting | Report with categories | Deployed |
| Account recovery | Re-register with phone OTP | Deployed |
| Session management | Device list, logout all devices | Deployed |

---

## 6. Compliance Audit Checklist

### 6.1 Pre-Launch Audit

| Item | Check | Status |
|------|-------|--------|
| Privacy policy published | Turkish + English, KVKK compliant | Done |
| Terms of service | Legal review | Pending |
| Cookie/consent banner | Not applicable (native app) | N/A |
| Data processing registration | KVKK VERBİS registration | Pending |
| DPO appointment | Required if >50 employees or sensitive data | Assess |
| Privacy impact assessment | Required for high-risk processing | Pending |
| Data processing agreements | With GCP, Firebase, Netgsm, Sentry | Pending |
| Breach notification process | 72-hour KVKK notification | Process needed |

### 6.2 Ongoing Compliance

| Activity | Frequency | Owner |
|----------|-----------|-------|
| Review data processing activities | Quarterly | DPO/Legal |
| Update privacy policy | On feature changes | Legal |
| Respond to data subject requests | Within 30 days | Engineering |
| Security incident response drill | Semi-annually | Engineering |
| Audit access logs | Monthly | Security |
| Review moderation reports | Weekly | Operations |
| KVKK VERBİS update | Annually | Legal |

---

## 7. Action Items

### P0 (Pre-Launch)
- [ ] Complete VERBİS (KVKK data controller) registration
- [ ] Legal review of Terms of Service
- [ ] Verify data export includes ALL user data categories
- [ ] Test account deletion flow end-to-end (soft delete → grace period → hard delete)
- [ ] Ensure moderation report flow works for BTK requirements
- [ ] Verify data residency (GCP europe-west1 data location)

### P1 (Post-Launch)
- [ ] Implement last seen / profile photo visibility settings
- [ ] Implement login notification (new device alert)
- [ ] Add report history to data export
- [ ] Set up breach notification process
- [ ] Data processing agreements with all third parties
- [ ] Implement hard delete scheduler (30 days after soft delete)

### P2 (Growth)
- [ ] Privacy impact assessment for E2E encryption
- [ ] Age verification at registration
- [ ] Read receipt opt-out
- [ ] Transparency report generation automation
- [ ] GDPR compliance assessment (if expanding to EU)
