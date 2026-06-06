# Muhabbet — Production Readiness & Next-Phase Plan

> **Date:** 2026-06-06
> **Verdict:** **NOT-READY** (planner: `not-ready`)
> **Live:** Backend live at https://muhabbet-api.rollingcatsoftware.com (healthy). Mobile not store-launched. E2E + multi-device ship dark (flags OFF).
> **How this was produced:** a 10-agent source-verified V&V sweep (architecture / security / test / ops / product, ×2 projects) + a 4-agent FIVUCSAS integration assessment. Every finding cites file:line. **Project docs (CLAUDE.md / ROADMAP / TODO) were treated as unverified claims** — see §2 for where they were wrong.

---

## 0. Executive summary

**Muhabbet is not production-ready.** A 10-agent source-verified V&V sweep found **26 P0 blockers**, several of which mean the *currently-live* backend is actively leaking data: any authenticated user can read **every** conversation's messages and **any** media file (two confirmed IDORs), the link-preview endpoint is an SSRF, and `/actuator/prometheus` + full health detail are public. On top of that, login is **silently broken in production** (`SMS_PROVIDER` unset → `MockOtpSender` → OTPs are logged, never sent), production secrets were committed to git history, KVKK erasure is incomplete, the E2E (libsignal) layer won't even compile, Redis cross-instance fan-out is wired but never subscribed (so scaling past one instance silently drops messages), and the deploy pipeline doesn't depend on tests passing.

**Root cause of why this hid for so long:** the test suite mocks or bypasses the Spring Security filter chain (controllers are unit-tested by direct instantiation), so authorization holes pass green CI. This is the same class of failure that hid the Sarnıç solver bug. The fix is not just patching the holes — it's adding the two-user isolation + real-filter-chain tests that would have caught them.

**Planner's bottom line:** Treat Phase 0 as an incident, not a roadmap item: the live API at muhabbet-api.rollingcatsoftware.com is simultaneously (a) leaking — any authenticated user can read every conversation's messages and any media via two confirmed IDORs, plus public /actuator/prometheus and full health detail — and (b) non-functional and stale — it runs a 10-week-old image off the wrong compose file with FCM off, no real SMS, and no error tracking, so real users can't even log in while their data is exposed. Before ANY new feature or the E2E/libsignal work, ship the seven Phase 0 fixes behind the cross-user 403 integration tests, redeploy current HEAD from the consolidated infra compose with Netgsm secrets, and prove the holes are closed with a live curl. Only then is a single-tenant pilot conceivable; a public Play Store launch additionally requires the signed AAB, true KVKK erasure + an honest privacy policy, and at least one real load run and pen test. Do not flip the E2E flag — the pinned libsignal cannot compile against its own API and would silently send plaintext.

---

## 1. Current state (verified)

Source-verified against HEAD (1485593, 2026-06-06) and the live API. Muhabbet is a feature-broad, architecturally deliberate (hexagonal + ArchUnit) modular-monolith messaging backend with a Compose Multiplatform mobile client. The architecture and feature breadth are genuinely impressive for a solo MVP, but it is NOT production-ready and several documented "DONE" claims are false. CONFIRMED P0s: (1) Global message-search IDOR — searchGlobal/searchInConversation JPQL (SpringDataMessageRepository.kt:79-87) have NO conversation-membership filter; SearchController only passes userId to resolveDeliveryStatuses, so any authed user can read every conversation's messages. (2) Media presigned-URL IDOR — MediaController.getPresignedUrl (line 134) passes no userId to the use case; any authed user can fetch any media URL. (3) SSRF — LinkPreviewController calls Jsoup.connect(url) directly (line 26) with InputSanitizer never invoked (zero call sites in main code). (4) Redis Pub/Sub cross-instance delivery is dead — RedisBroadcastListener.handleMessage exists but NO RedisMessageListenerContainer bean / addMessageListener exists anywhere; there is no RedisConfig.kt. Horizontal scaling silently drops messages. (5) UserController.getUserById returns phoneNumber to any authed caller; onlineStatusVisibility is stored but never enforced on GET (KVKK Art.12). (6) KVKK erasure is a soft-delete only — UserDataService.requestAccountDeletion sets status=DELETED + revokes tokens + removes from conversations, but never nulls phone/name/avatar, deletes messages/media/phone_hashes. (7) PRODUCTION IS BROKEN AND STALE: the live muhabbet-backend container was built 2026-03-31 (10+ weeks behind HEAD, missing V18) and runs the ROOT docker-compose.prod.yml (FCM_ENABLED=false, no SMS_PROVIDER → MockOtpSender, SENTRY_DSN empty) — confirmed via docker inspect. So in prod NO OTP SMS is sent, NO push fires, and there is zero error tracking. Live probes confirm /actuator/health leaks full DB/Redis/disk/SSL details and /actuator/prometheus returns 200 publicly. (8) Voice calls carry no audio — CallEngine.android connect() never publishes a mic track / calls setMicrophoneEnabled(true). (9) Two-Step PIN does not gate login — no twoStep check in AuthService. (10) libsignal-android pinned 0.86.5 but SignalKeyManager uses Curve.generateKeyPair/decodePoint + 2-arg SessionCipher (removed ~0.76/0.91) — E2E cannot compile if flag flips; E2EConfig.ENABLED=false keeps it dark. (11) Privacy policy falsely claims GCP europe-west1 (actually Hetzner Germany) and MinIO encryption-at-rest (none configured). No signed keystore/AAB exists, CI quality gates are continue-on-error, only 1 of 36 backend test files is an integration test and it disables Redis, k6 never run, no pen test. Feature flags (E2E/multi-device) are correctly default-OFF and the flag-OFF path is byte-identical to pre-wiring HEAD — that posture is sound.

---

## 2. Doc-vs-reality corrections (22)

These project-doc claims did **not** match the code. Fix them at the source so the next session isn't misled.

| Doc claims | Reality | Evidence |
|---|---|---|
| CLAUDE.md:436 — 'E2E encryption client (Signal Protocol) — INFRA DONE, send/receive path WIRED but flag OFF' | SignalKeyManager uses Curve.generateKeyPair(), Curve.calculateSignature(), Curve.decodePoint(), and the 2-arg SessionCipher constructor — all removed from libsignal-android around v0.76-0.91. The code would fail at runtime if E2EConfig.ENABLED were set to true. 'WIRED' overstates the readiness; the code is structurally present but API-incompatible with the declared dependency version. | mobile/composeApp/src/androidMain/kotlin/com/muhabbet/app/crypto/SignalKeyManager.kt:49,50,67,90,99,117,124 and mobile/composeApp/build.gradle.kts:100 (libsignal-android:0.86.5) |
| CLAUDE.md documents Redis Pub/Sub horizontal scaling as complete for cross-instance WebSocket message delivery | RedisBroadcastListener.handleMessage() is declared in RedisMessageBroadcaster.kt but no RedisMessageListenerContainer bean or addMessageListener() call exists anywhere in the backend codebase. The listener has no subscription registration; received messages are never processed on any instance. | grep -rn 'addMessageListener' /opt/projects/Muhabbet/backend returns zero results. RedisMessageBroadcaster.kt declares RedisBroadcastListener but no container wiring. |
| infra/docker-compose.prod.yml implies SMS delivery is production-ready (Netgsm/Twilio configuration present in application.yml) | docker-compose.prod.yml:26 sets 'SMS_PROVIDER: ${SMS_PROVIDER:-mock}'. The .env.prod file does not set SMS_PROVIDER. The default 'mock' causes MockOtpSender to log OTPs to console instead of sending them via any real SMS provider. | infra/docker-compose.prod.yml line SMS_PROVIDER: ${SMS_PROVIDER:-mock}; application.yml:102 'provider: ${SMS_PROVIDER:mock}' |
| CLAUDE.md line 337 marks 'Production SMS (Netgsm) — DONE (NetgsmOtpSender, @ConditionalOnProperty)' | .env.prod contains no SMS_PROVIDER, NETGSM_USERCODE, or NETGSM_PASSWORD. docker-compose.prod.yml:26 defaults SMS_PROVIDER to 'mock'. MockOtpSender is @ConditionalOnProperty(matchIfMissing=true). Production almost certainly uses MockOtpSender. | infra/docker-compose.prod.yml:26 (SMS_PROVIDER: ${SMS_PROVIDER:-mock}); .env.prod (grep NETGSM returns nothing); MockOtpSender.kt:9 (@ConditionalOnProperty(..., matchIfMissing = true)) |
| ROADMAP.md:76 marks 'KVKK export / delete — Done' | requestAccountDeletion in UserDataService.kt does not anonymize phoneNumber, does not delete messages, does not remove media from MinIO, does not clear phone_hashes or encryption_keys. Only sets status=DELETED and deletedAt. | UserDataService.kt:48-72 — the full method body contains no phoneNumber nulling, no message deletion, no MinIO cleanup, no phone_hash removal |
| nginx comment on /actuator/prometheus location: 'Restrict to internal/monitoring IPs in production' | No allow/deny directives are present in the location block. The endpoint is publicly accessible. | infra/nginx/conf.d/muhabbet.conf — location /actuator/prometheus { # comment only; proxy_pass; } with no IP restriction directives |
| CLAUDE.md 'Security Hardening' completed section mentions InputSanitizer for message content protection | InputSanitizer.sanitizeMessageContent(), sanitizeDisplayName(), and sanitizeHtml() are never called in production code paths. grep -rn 'InputSanitizer' backend/src/main/kotlin (excluding the class itself and tests) returns zero results. | backend/src/main/kotlin/com/muhabbet/shared/security/InputSanitizer.kt exists but zero call sites found in production code |
| CLAUDE.md and TODO.md both list E2E encryption as '[x] Done' / 'wired behind feature flag' | E2EConfig.ENABLED=false (flag OFF in production). SignalKeyManager.kt uses Curve.generateKeyPair() and other APIs removed from libsignal at ~v0.76; the pinned dependency is 0.86.5. The code cannot compile against its own declared dependency. Crypto tests use a byte-flip FlipEncryption stub, not real libsignal. No two-device end-to-end test exists. | mobile/composeApp/src/commonMain/kotlin/com/muhabbet/app/crypto/E2EConfig.kt:ENABLED=false; mobile/composeApp/src/androidMain/kotlin/com/muhabbet/app/crypto/SignalKeyManager.kt:49,67; mobile/composeApp/src/commonTest/kotlin/com/muhabbet/app/crypto/MessageEncryptorTest.kt uses FlipEncryption |
| CLAUDE.md states 'BackupController is tested' and the test suite covers backup and bot management | BackupControllerTest.kt never instantiates the BackupController class. The comment in the file explicitly says '@AuthenticationPrincipal complicates direct controller instantiation, so we test the use case layer directly'. All test assertions call manageBackupUseCase.createBackup(userId) directly — the HTTP layer, Spring Security auth, and controller mapping are completely untested. | backend/src/test/kotlin/com/muhabbet/messaging/adapter/in/web/BackupControllerTest.kt — no 'controller' variable declared anywhere in the file; BotControllerTest.kt follows identical pattern |
| docs/qa/README.md status column shows k6 load tests as 'Scripts created' implying ready to run | No CI step executes k6. TODO.md explicitly lists '[ ] Run k6 load tests against staging' as not done. The scripts have never produced a test report. | docs/qa/README.md status='Scripts created'; .github/workflows/backend-ci.yml contains no k6 step; TODO.md line for k6 is unchecked |
| docs/qa/02-security.md security checklist implies comprehensive coverage | The same document explicitly lists IDOR cross-user conversation access, OWASP ZAP baseline scan, and external pen test as unchecked '[ ]' items. The media IDOR (presigned URL with no ownership check) is not even listed as a scenario — a more serious gap than documented. | docs/qa/02-security.md: '[ ] IDOR: Can user A access user B's conversations?', '[ ] OWASP ZAP baseline scan', '[ ] External pen test commissioned'; MediaController.kt:130-145 IDOR not in checklist at all |
| CLAUDE.md: 'Production SMS (Netgsm) — DONE (NetgsmOtpSender, @ConditionalOnProperty)' | The running production container has no SMS_PROVIDER env var set. MockOtpSender is the active bean (matchIfMissing = true in MockOtpSender.kt). OTPs are only logged to console in the live service. | docker inspect muhabbet-backend env output shows no SMS_PROVIDER; docker-compose.prod.yml (root, confirmed active by container labels) has no SMS_PROVIDER entry; MockOtpSender.kt:9 @ConditionalOnProperty matchIfMissing = true |
| CLAUDE.md: 'Push notifications — DONE (FCM, push token registration, offline delivery)' | FCM_ENABLED=false in the live production container. Push notifications are not delivered. | docker inspect muhabbet-backend shows FCM_ENABLED=false; docker-compose.prod.yml (root) line 26: FCM_ENABLED: 'false' |
| CLAUDE.md Tech Stack: 'Monitoring: SLF4J + Logback (JSON) + Spring Actuator + Sentry' | Sentry auto-configuration is permanently excluded (MuhabbetApplication.kt:9 excludeName), SENTRY_DSN is empty string in prod, and no Sentry capture calls exist in backend source code. Sentry is present as a JAR dependency but completely non-functional. | MuhabbetApplication.kt:9; docker inspect env shows SENTRY_DSN=; grep of backend/src/main/kotlin for SentryHub/captureException returns zero results |
| CHANGELOG.md Phase 3: 'Sentry SDK — Sentry Android SDK... auto-init via AndroidManifest meta-data' | The Spring Boot backend Sentry is excluded and the backend SENTRY_DSN is empty. Android mobile Sentry may be wired, but the server-side claim is misleading given the active exclusion. | MuhabbetApplication.kt:9 @SpringBootApplication(excludeName = ["io.sentry.spring.boot.jakarta.SentryAutoConfiguration"]) |
| ROADMAP.md / TODO.md: 'Backend LIVE + healthy at https://muhabbet-api.rollingcatsoftware.com (db/redis/ssl UP). Do NOT deploy' | The running container code is from 2026-03-31 — 10 weeks behind HEAD. The service is live but not running the code described in the roadmap/TODO (V18 migration, BackupService rewrite, CGLIB proxy fix, multi-device scaffolding are all absent from the running jar). | docker inspect muhabbet-backend Created: 2026-03-31T11:53:52Z; git log shows 7 backend commits after that date including V18 migration and BackupService rewrite at 299f34f |
| ROADMAP.md L320: 'Voice calls are culturally non-negotiable (Android live; iOS = Tier 2.1).' ROADMAP.md L81: 'Android wired to LiveKit (CallEngine.android.kt connect/mute/speaker real)' | CallEngine.android.kt connects to the LiveKit room but never publishes a LocalMicrophoneTrack. setMuted() iterates an empty trackPublications list. No audio is transmitted or received. | mobile/composeApp/src/androidMain/kotlin/com/muhabbet/app/platform/CallEngine.android.kt:17-57 — connect() and setMuted() — grep for publishAudio across all .kt files returns no results |
| docs/privacy-policy.html L54 (TR): 'Verileriniz Avrupa (GCP europe-west1) bölgesinde saklanır.' L117 (EN): 'Data is stored in Europe (GCP europe-west1 region).' | Server is Hetzner VPS CX43 (Germany), not Google Cloud Platform europe-west1 (Belgium). Entirely different cloud provider and location. | CLAUDE.md L469: 'Server: Hetzner VPS (IP: 116.203.222.213)'; infra/docker-compose.prod.yml uses no GCP services |
| docs/privacy-policy.html L55 (TR): 'Medya dosyaları şifreli depolama alanında tutulur.' L119 (EN): 'Media files are kept in encrypted storage.' | MinIO runs with 'minio server /data' — no MINIO_KMS_KES_ENDPOINT, no SSE-S3 or SSE-KMS configuration. Blobs are stored as plaintext on the host block storage. | infra/docker-compose.prod.yml L131-L155: minio service block has no encryption environment variables |
| ROADMAP.md L84: 'Two-step verification (PIN): Partial — TwoStepVerificationService.kt exists; confirm wired into OTP login + UI'. TODO.md header: 'Tier-1 engineering = DONE'. | TwoStepVerificationService.setupPin/verifyPin are post-auth endpoints only. AuthService.verifyOtp() and verifyFirebaseToken() issue tokens without checking twoStepEnabledAt or twoStepPinHash. 2FA provides zero login protection. | backend/src/main/kotlin/com/muhabbet/auth/domain/service/AuthService.kt L100-L138 — no twoStep check in verifyOtp() or verifyFirebaseToken() |
| TODO.md P2: '[ ] Close the 6 client-side UI TODO stubs ... HomeShellScreen.kt L98 (search), MessageBubble.kt L91 (view-once mark), WallpaperPickerScreen.kt L191 (gallery picker), InviteLinkSheet.kt L149 (platform share), CommunityDetailScreen.kt L167 (add group), BroadcastListScreen.kt L191 (open detail)' — marked as still open. | All 6 are in fact wired: PR#35 wired InviteLinkSheet share (ShareLauncher) and MessageBubble view-once (onViewOnce → messageRepository.markViewOnce). PR#36 wired HomeShellScreen search, WallpaperPickerScreen gallery (galleryPicker.launch()), CommunityDetailScreen add group ('coming soon' dialog), BroadcastListScreen open detail (navigation to BroadcastDetailScreen). The [ ] checkbox in TODO.md is stale. | git log: '155aa60 feat(tier1/§1.5): wire 4 remaining dead buttons — search, wallpaper gallery, add-group, broadcast detail (#36)'; '9577f4c feat(tier1): §1.2 receipt correctness tests + §1.5 partial dead-button wiring (#35)' |
| CLAUDE.md (Completed Phases section): 'LiveKit WebRTC Client: CallEngine expect/actual (Android: LiveKit SDK, iOS: stub), CallRoomInfo WsMessage, backend room creation + token generation on call answer, ActiveCallScreen wired to LiveKit, DisposableEffect cleanup' | The wiring to LiveKit is only room-level (connect/disconnect). No microphone track is published. 'CallEngine wired to LiveKit' implies audio flows; it does not. | mobile/composeApp/src/androidMain/kotlin/com/muhabbet/app/platform/CallEngine.android.kt:21-29 — connect() does not publish tracks |

---

## 3. P0 — production blockers (26)

> Multiple reviewers independently surfaced some of these (e.g. the media IDOR) — overlap = corroboration, not duplication.


#### Authorization / Messaging

**P0-1. Global message search returns all users' private messages (IDOR)**
- *Evidence:* backend/src/main/kotlin/com/muhabbet/messaging/adapter/in/web/SearchController.kt:36 — searchGlobal() executes JPQL 'WHERE m.isDeleted = false AND LOWER(m.content) LIKE :query' with no userId/conversation-membership filter. searchInConversation() at line 34 also performs no membership verification before returning results.
- *Impact:* Any authenticated user can exfiltrate the full message history of every conversation on the platform by issuing a wildcard search. This is a total confidentiality failure for paying tenants.
- *Fix:* Add a JOIN to conversation_members table filtered by the authenticated userId in both JPQL queries. Enforce the check at the domain service layer and add an integration test with two distinct users verifying cross-user isolation.


#### Security / Messaging

**P0-2. SSRF in LinkPreviewController — InputSanitizer bypassed**
- *Evidence:* backend/src/main/kotlin/com/muhabbet/messaging/adapter/in/web/LinkPreviewController.kt:26 — 'Jsoup.connect(url).userAgent("MuhabbetBot/1.0").timeout(5000).get()' is called directly without invoking InputSanitizer.sanitizeUrl(). The InputSanitizer class exists and has a sanitizeUrl() method, but it is not called on this code path.
- *Impact:* Any authenticated user can make the backend server issue HTTP requests to internal services (metadata endpoints, Redis, database health checks, cloud provider IMDS at 169.254.169.254). Credential theft and internal network mapping are straightforward.
- *Fix:* Call InputSanitizer.sanitizeUrl(url) before the Jsoup.connect() call. Block RFC-1918 and link-local address ranges at the sanitizer level. Add a unit test asserting that internal-network URLs are rejected.


#### Architecture / Messaging / Scalability

**P0-3. Redis Pub/Sub broadcast listener is declared but never subscribed — silent multi-instance delivery failure**
- *Evidence:* backend/src/main/kotlin/com/muhabbet/messaging/adapter/out/external/RedisMessageBroadcaster.kt — RedisBroadcastListener.handleMessage() is declared at approximately line 163 but no RedisMessageListenerContainer @Bean or addMessageListener() call exists anywhere in the codebase (confirmed by grep -rn 'addMessageListener' /opt/projects/Muhabbet/backend returning zero results). The CLAUDE.md documents Redis Pub/Sub horizontal scaling as 'DONE'.
- *Impact:* Running two or more backend instances causes WebSocket messages to be published to Redis but never received by peer instances. Users on different server instances never receive each other's messages. Horizontal scaling is silently broken.
- *Fix:* Register RedisBroadcastListener with a RedisMessageListenerContainer @Bean in a Redis configuration class using container.addMessageListener(listener, ChannelTopic(CHANNEL)). Add an integration test with two application contexts verifying cross-instance delivery.


#### Configuration / Authentication

**P0-4. Production docker-compose defaults SMS_PROVIDER to 'mock' — OTPs silently discarded**
- *Evidence:* infra/docker-compose.prod.yml — SMS_PROVIDER: ${SMS_PROVIDER:-mock}. The .env.prod file does not set SMS_PROVIDER. application.yml:102 confirms 'provider: ${SMS_PROVIDER:mock}'. With mock provider active, MockOtpSender logs OTPs to console instead of delivering them via Netgsm or Twilio.
- *Impact:* Phone-number OTP authentication is the primary login factor. In production, if SMS_PROVIDER is not set in the deployment environment, no OTP SMS is ever sent. Users cannot log in. Authentication is silently broken.
- *Fix:* Remove the default from docker-compose.prod.yml (SMS_PROVIDER: ${SMS_PROVIDER}) so that a missing value causes a visible startup failure rather than silent mock mode. Add a Spring @PostConstruct validation that refuses to start if provider=mock and a prod profile is active.


#### Authorization / Media

**P0-5. Media presigned-URL endpoint has no ownership or membership check (IDOR)**
- *Evidence:* backend/src/main/kotlin/com/muhabbet/media/adapter/in/web/MediaController.kt:131-145 — GET /{mediaId}/url generates a MinIO presigned URL for any mediaId without verifying that the requesting user owns the media or is a member of the conversation that contains it.
- *Impact:* Any authenticated user who knows or guesses a mediaId (sequential or UUID-discoverable) can obtain a presigned URL granting direct access to another user's images, videos, and files. This breaks media confidentiality.
- *Fix:* Before generating the presigned URL, look up the media record's conversationId and verify the requesting user is a member of that conversation. Return 403 on failure. Add an integration test with two users asserting cross-user media access is rejected.


#### Security / Operations

**P0-6. Prometheus/metrics/info actuator endpoints publicly accessible**
- *Evidence:* backend/src/main/kotlin/com/muhabbet/shared/security/SecurityConfig.kt:52 — permitAll() is applied to /actuator/info, /actuator/metrics, /actuator/prometheus. application-prod.yml sets show-details: always for health endpoint.
- *Impact:* JVM heap, thread pool saturation, DB connection pool utilization, Redis latency histograms, and HTTP endpoint call counts are publicly readable by unauthenticated actors. This provides reconnaissance data for DDoS targeting and exposes internal service topology.
- *Fix:* Restrict /actuator/** to admin IP or require ADMIN role. Remove show-details: always from application-prod.yml. Expose /actuator/prometheus only to Prometheus scraper IP via Traefik middleware or SecurityConfig IP filter.


#### Authorization / Broken Access Control

**P0-7. GET /messages/{id}/info — no conversation-membership check (BOLA/IDOR)**
- *Evidence:* MessageController.kt:109-138 — calls messageRepository.findById(messageId) directly, sets userId = AuthenticatedUser.currentUserId() but never calls conversationRepository.findMember(). The MessageService.getMessages() and getMediaMessages() both call findMember(), but getMessageInfo is handled entirely in the controller with no such gate. MessageControllerTest.kt:129-165 tests happy-path only, no non-member scenario.
- *Impact:* Any authenticated user who learns or guesses a UUID can read the content, sender, recipients, delivery timestamps, and mediaUrl of any message in the system — including private DMs between other users.
- *Fix:* Add a membership check before returning info: verify that the requestingUserId is a member of message.conversationId via conversationRepository.findMember(), throw MSG_FORBIDDEN otherwise. Add a unit test for the non-member case.

**P0-8. GET /media/{id}/url — no authorization check, any authed user can fetch presigned URL for any media**
- *Evidence:* MediaController.kt:130-148 — getPresignedUrl calls getMediaUrlUseCase.getPresignedUrl(mediaId). MediaService.kt:177-185 — only calls mediaFileRepository.findById(mediaId), returns a MinIO presigned URL with no check that the requesting user uploaded the file or belongs to the conversation where the media was sent. No userId passed to the service method at all.
- *Impact:* Any authenticated user can retrieve a time-limited but functional MinIO presigned URL for any media blob (image, voice, document, video) in the system by guessing or enumerating UUIDs. Combined with the message-info IDOR above, an attacker can reconstruct the full content of private conversations.
- *Fix:* Pass the requesting userId into getPresignedUrl. Verify the requesting user is either the uploader (mediaFile.uploaderId == requestingUserId) or a member of any conversation that references the media. Alternatively, move the presigned-URL check behind a backend-proxied download endpoint that enforces membership.


#### Privacy / KVKK

**P0-9. GET /users/{userId} — raw phone number exposed to any authenticated user; privacy settings not enforced**
- *Evidence:* UserController.kt:36-57 — returns UserProfile including phoneNumber = user.phoneNumber for any UUID lookup. No check against user.onlineStatusVisibility / lastSeenAt visibility. getUserDetail (line 59) also returns phoneNumber. The DB has onlineStatusVisibility, readReceiptsEnabled, aboutVisibility columns (set via PATCH /users/me/privacy) but these are never read in the GET endpoints.
- *Impact:* Any registered user can harvest the real phone numbers of all other users by iterating or guessing UUIDs. For a messaging app marketed on KVKK privacy grounds, exposing phone numbers to arbitrary peers is a direct regulatory and reputational breach.
- *Fix:* Remove phoneNumber from the public-facing UserProfile DTO. Return it only in GET /users/me (own profile). Enforce onlineStatusVisibility (EVERYONE / CONTACTS / NOBODY) and lastSeenAt visibility in the GET /users/{id} handler by reading the target user's privacy settings.

**P0-10. KVKK right to erasure is incomplete — phone number and messages retained after account deletion**
- *Evidence:* UserDataService.kt:48-72 — requestAccountDeletion soft-deletes by setting status=DELETED and deletedAt; does NOT null out phoneNumber, displayName, avatarUrl, about. Does NOT delete messages, MediaFiles from MinIO, encryption keys, or phone_hash rows. V1__initial_schema.sql:20 uses deleted_at for soft delete. The ROADMAP.md:76 marks 'KVKK export / delete — Done'.
- *Impact:* A user exercising KVKK Art.7 right to erasure retains their phone number, profile data, all message content, media files, and biometric-equivalent encryption keys in the database indefinitely. This constitutes a direct KVKK violation that can result in KVKK Board fines up to 5 million TRY per violation.
- *Fix:* Implement true erasure: null/randomize phoneNumber, displayName, avatarUrl, about; purge phone_hashes row; asynchronously delete all media from MinIO; anonymize message senderIds in all conversations (replace with a tombstone UUID); purge encryption_keys and one_time_pre_keys. Add a KVKK erasure audit log. Schedule post-deletion data checks.


#### Authentication / OTP

**P0-11. Production SMS defaults to MockOtpSender — real OTP never delivered to users**
- *Evidence:* infra/docker-compose.prod.yml:26 — SMS_PROVIDER: ${SMS_PROVIDER:-mock}. .env.prod contains POSTGRES_PASSWORD, REDIS_PASSWORD, JWT_SECRET, MINIO_ROOT_PASSWORD — no SMS_PROVIDER, no NETGSM_USERCODE, no NETGSM_PASSWORD. MockOtpSender.kt:14-15 — logs OTP to server console only (log.info). NetgsmOtpSender.kt:11 — @ConditionalOnProperty(havingValue='netgsm'). CLAUDE.md:43 — 'OTP via MockOtpSender (Netgsm later)'.
- *Impact:* Real users requesting OTP receive nothing on their phone. OTP is logged server-side only. The service is non-functional for real user registration and login. Additionally, anyone with log access can authenticate as any phone number.
- *Fix:* Add SMS_PROVIDER=netgsm, NETGSM_USERCODE, and NETGSM_PASSWORD to .env.prod on the production host. Verify NetgsmOtpSender activates by checking the Spring context on next startup. Add a startup health check that asserts the active OtpSender is NOT MockOtpSender in production profile.


#### Secret Management

**P0-12. Production secrets were committed to git history (commit 3925f70) — rotation required**
- *Evidence:* git show 3925f70 reveals .env.prod was tracked containing: POSTGRES_PASSWORD=wg8OCmcGq93hIbFdP3hKym2AKbuv9Dq, REDIS_PASSWORD=L0rqku3Xv54WhOMvvZEtAajhHc062jwS, JWT_SECRET=pb5ZcSbKVrsUUMe9RwOM9wENCzvCDbSuneNQkIQfshbH2Rh2X45vAhD8ZKQsLek. Current .env.prod has different values, indicating rotation occurred, but the old credentials persist in git history indefinitely and are accessible to anyone with repo clone access.
- *Impact:* If the repo is ever made public or shared with a contractor, historical production credentials are immediately visible. The old JWT_SECRET allows forging tokens that were valid until rotation; old DB/Redis passwords allow retrospective access to any backup made before rotation.
- *Fix:* Confirm all three leaked secrets were rotated after removal (current .env.prod shows different values — good). Document the rotation date. Consider running `git filter-repo` to scrub commit 3925f70 from history before any public release. Add gitleaks as a pre-commit hook to prevent recurrence.


#### Security / Media module

**P0-13. IDOR: Any authenticated user can download any user's media file**
- *Evidence:* backend/src/main/kotlin/com/muhabbet/media/adapter/in/web/MediaController.kt:130-145 — GET /api/v1/media/{mediaId}/url calls getPresignedUrl(mediaId) with no caller-identity check. MediaService.getPresignedUrl() at backend/src/main/kotlin/com/muhabbet/media/adapter/in/web/MediaService.kt:177-185 only checks 'mediaFile != null', not uploader ownership. docs/qa/02-security.md explicitly lists '[ ] IDOR: Can user A access user B's conversations?' as unchecked.
- *Impact:* Any registered user can enumerate media IDs and download private photos, voice notes, and videos belonging to other users. Violates KVKK Article 12 (technical safeguards). Immediately exploitable on the live backend at muhabbet-api.rollingcatsoftware.com.
- *Fix:* Before returning a presigned URL, verify that the requesting user is a member of the conversation that contains the media. Add a @SpringBootTest integration test that asserts a 403 when user B requests user A's media ID.


#### Mobile crypto / Signal Protocol

**P0-14. libsignal API mismatch: E2E crypto layer cannot compile against declared dependency**
- *Evidence:* mobile/composeApp/src/androidMain/kotlin/com/muhabbet/app/crypto/SignalKeyManager.kt:49,67 — calls Curve.generateKeyPair(), Curve.calculateSignature(), Curve.decodePoint(), all removed from libsignal ~v0.76. mobile/composeApp/build.gradle.kts pins org.signal:libsignal-android:0.86.5. SessionCipher(store, address) is missing the required localAddress arg added in 0.91. PreKeyBundle uses the 8-arg constructor removed in favour of 11-arg Kyber form.
- *Impact:* E2EConfig.ENABLED=false conceals this today, but enabling the product's primary privacy differentiator — end-to-end encrypted messaging — will cause a compile-time or runtime crash before any message is encrypted. Play Store listing as a 'privacy-first' messenger is dishonest while this remains unresolved.
- *Fix:* Either migrate SignalKeyManager to the current libsignal 0.86.5 API (Curve → ECKeyPair.generate(), etc.) or hold E2EConfig.ENABLED=false permanently and remove the privacy-first marketing claim. Add a JVM integration test that instantiates SignalKeyManager against the real libsignal jar so the mismatch surfaces at test time, not at flag-flip time.


#### Test quality / Messaging module

**P0-15. BackupController and BotController have zero HTTP/security coverage — controller objects never instantiated**
- *Evidence:* backend/src/test/kotlin/com/muhabbet/messaging/adapter/in/web/BackupControllerTest.kt — comment says '@AuthenticationPrincipal complicates direct controller instantiation, so we test the use case layer directly'; no 'controller' variable is ever declared. BotControllerTest.kt follows identical pattern. All assertions target use-case mock expectations, not HTTP status codes or response bodies.
- *Impact:* Auth enforcement on backup-export and bot-management endpoints is completely untested. A misconfigured permitAll() or missing @PreAuthorize would go undetected. This is the same class of failure that allowed Sarnıç's 490 green tests to conceal a broken persistence path.
- *Fix:* Replace direct use-case calls with @WebMvcTest + MockMvc tests that send real HTTP requests through the full filter chain including JwtAuthFilter. Verify that unauthenticated requests receive 401 and requests from non-owners receive 403.


#### WebSocket / Infrastructure

**P0-16. Redis Pub/Sub horizontal scaling has zero integration tests; only integration test explicitly disables Redis**
- *Evidence:* backend/src/test/kotlin/com/muhabbet/auth/adapter/in/web/AuthControllerIntegrationTest.kt:L27 — registry.add('spring.autoconfigure.exclude') { 'org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration' }. RedisMessageBroadcaster is @Primary for WS fan-out but has no test. .github/workflows/backend-ci.yml provisions a Redis service container that is never contacted by any test.
- *Impact:* Message delivery under horizontal scale (multiple pods) depends on Redis Pub/Sub that has never been exercised. A misconfigured PUBLISH/SUBSCRIBE or serialization mismatch would silently drop messages for users on different pods — invisible until production load hits.
- *Fix:* Add a @SpringBootTest + @Testcontainers integration test that starts a real Redis container, connects two simulated WebSocket clients, sends a message from client A, and asserts client B receives it via the Redis broadcast path.


#### Deployment Pipeline

**P0-17. Production container is 10+ weeks behind HEAD with an undeployed Flyway migration**
- *Evidence:* docker inspect muhabbet-backend shows Created: 2026-03-31T11:53:52Z, StartedAt: 2026-04-07T06:00:08Z. HEAD is 1485593 (2026-06-06). Commits not deployed include 299f34f (BackupService rewrite), 97626f8 (CGLIB proxy fix), 9577f4c, ab4ca0c, 99f6ce2, 1485593. The last of these adds backend/src/main/resources/db/migration/V18__multi_device_linking.sql which the running DB has never seen.
- *Impact:* When the next deploy finally runs, Flyway will attempt to apply V18 on a schema that has had no migration applied since the build cut. With validate-on-migrate: false (application.yml:41) Flyway skips checksum validation on existing migrations, but it still tries to apply V18. If the deploy fails mid-migration due to a transient error, the DB ends up in a partially-migrated state with no automated rollback. Meanwhile the current service has been silently missing the CGLIB proxy null-field fix (97626f8) since April.
- *Fix:* Verify that the CI/CD deploy automation actually fired for all pushes to main since April (check GitHub Actions run history). If the runner was down or the path filters were not triggered, re-run the deploy now after confirming V18 is safe to apply against the live DB. Add a pre-deploy step that queries flyway_schema_history to confirm expected baseline.

**P0-18. Deploy workflow has no dependency on passing CI tests — broken code can go straight to production**
- *Evidence:* /.github/workflows/deploy-hetzner.yml has no 'needs:' key. Both backend-ci and deploy-hetzner trigger independently on push to main. A push that fails tests will still trigger the deploy job in parallel.
- *Impact:* A regression that breaks all tests will deploy to the live API serving real users. The pattern has already manifested: seven backend commits landed on main without triggering a visible deploy (the deploy may have run but the container shows no rebuild since March). If the deploy did fire and the JAR failed to build, the health-check loop exits with code 1 but does not roll back to the previous running image.
- *Fix:* Add 'needs: build-and-test' to the deploy job in deploy-hetzner.yml. Also add an explicit rollback step that re-tags the previous SHA image and restarts if the health check fails (the sha-tagging mechanism exists in infra/deploy.sh but no SHA-tagged images were found for muhabbet: only muhabbet-muhabbet-backend:latest exists with no older sha- tags).


#### Release Artifacts / Configuration

**P0-19. FCM push notifications and real SMS OTP are both disabled in the active production compose file**
- *Evidence:* docker inspect muhabbet-backend shows FCM_ENABLED=false and SENTRY_DSN= (empty). The active config file is confirmed by compose labels: com.docker.compose.project.config_files = /opt/projects/Muhabbet/docker-compose.prod.yml. That root file hardcodes FCM_ENABLED: 'false' (line 26) and has no SMS_PROVIDER entry, causing the backend to instantiate MockOtpSender (backend/src/main/kotlin/com/muhabbet/auth/adapter/out/external/MockOtpSender.kt, @ConditionalOnProperty matchIfMissing = true), which logs OTP to console only. The infra/docker-compose.prod.yml (which has correct values) is NOT what is deployed.
- *Impact:* Every real user attempting to register or log in on the live API receives no SMS and no OTP delivery. Push notifications for new messages are silently swallowed. This is a complete functional breakage for the core user journey.
- *Fix:* Update /opt/projects/Muhabbet/docker-compose.prod.yml (the file actually used by the deploy script per docker compose labels) to set FCM_ENABLED: 'true', FIREBASE_CREDENTIALS_PATH: /app/firebase-adminsdk.json, SMS_PROVIDER: netgsm (or mock for pre-launch testing), and add the firebase-adminsdk.json volume mount pointing to infra/firebase-adminsdk.json. The real credentials file already exists at infra/firebase-adminsdk.json (mode 600, project_id: muhabbet-app-prod).


#### Release Artifacts

**P0-20. No signed release keystore — Play Store submission structurally impossible**
- *Evidence:* mobile/composeApp/build.gradle.kts:137 shows signingConfig only activates when MUHABBET_KEYSTORE_FILE env var is set. No such secret exists in .github/workflows/mobile-ci.yml (grep for MUHABBET_KEYSTORE across all workflows returns zero hits). The CI builds only assembleDebug (mobile-ci.yml line 90). versionCode = 1, versionName = "0.1.0" at build.gradle.kts:125-126. The TODO.md P0 section explicitly confirms: 'no keystore exists in repo or CI — there is no signed release artifact to upload to Play Console. Play Store submission cannot start.'
- *Impact:* A public Play Store launch cannot happen. Debug APKs cannot be submitted. Even internal testing via Play Console requires a signed AAB.
- *Fix:* Generate a production release keystore offline (keytool -genkey -v -keystore muhabbet-release.jks -alias muhabbet -keyalg RSA -keysize 4096), store it in a secrets manager or GitHub Secret as base64, inject it via MUHABBET_KEYSTORE_FILE in a new release build step in mobile-ci.yml, add bundleRelease task, and upload the AAB artifact. Document keystore location and backup in a runbook.


#### Observability

**P0-21. Sentry auto-configuration is permanently excluded — zero error tracking in production**
- *Evidence:* MuhabbetApplication.kt:9 hardcodes excludeName = ["io.sentry.spring.boot.jakarta.SentryAutoConfiguration"]. The live container has SENTRY_DSN= (empty string). No Sentry capture calls exist in any backend Kotlin file beyond the exclude annotation itself (grep of backend/src/main/kotlin returns only the exclusion). application-prod.yml sets sentry.dsn: ${SENTRY_DSN:} with an empty default. The TODO.md P1 item confirms this is open: 'Configure Sentry DSN in production.'
- *Impact:* Unhandled exceptions and service errors in the live API produce no alerts, no aggregated error reports, and no notification to the developer. A silent regression can go undetected until a user reports it manually. The CLAUDE.md claim 'Monitoring: SLF4J + Logback (JSON) + Spring Actuator + Sentry' is only half-true: Sentry is included as a dependency but disabled at the framework level.
- *Fix:* Wait for Sentry to release a Spring Boot 4.x-compatible version (tracked in TODO.md) or configure manual Sentry Hub wiring (SentryClient.capture). Set SENTRY_DSN in the production .env.prod. Until Sentry is functional, ensure Loki/Grafana alerting fires on ERROR-level log lines from the muhabbet-backend container as a fallback.


#### Mobile — Voice Calls

**P0-22. Voice calls silently carry no audio on Android**
- *Evidence:* mobile/composeApp/src/androidMain/kotlin/com/muhabbet/app/platform/CallEngine.android.kt: connect() calls lkRoom.connect() then stores the Room, but never calls room.localParticipant.publishAudioTrack() or any equivalent. setMuted() iterates trackPublications.values for a LocalAudioTrack that was never published — it is always empty. No other file in the codebase publishes a microphone track (grep for publishAudio/enableMicrophone/publishTrack across all .kt files returns no results). ROADMAP.md L81 and L320 claim 'Android live', which is incorrect.
- *Impact:* A user initiates or accepts a voice call, the call connects to the LiveKit room (UI shows 'in call'), but no audio is transmitted or received by either party. Voice calls are described as 'culturally non-negotiable' in the ROADMAP for Turkish market adoption. A reviewer, paying client, or beta tester would immediately discover this during a first call test.
- *Fix:* After lkRoom.connect() succeeds, call room.localParticipant.setMicrophoneEnabled(true) (LiveKit Android SDK ≥0.14 API) or the equivalent publishAudioTrack for older API versions, then verify with a two-device end-to-end voice test before marking calls as functional.


#### Legal / KVKK Compliance

**P0-23. Privacy policy claims GCP europe-west1 but actual server is Hetzner Germany**
- *Evidence:* docs/privacy-policy.html L54 (Turkish): 'Verileriniz Avrupa (GCP europe-west1) bölgesinde saklanır.' L117 (English): 'Data is stored in Europe (GCP europe-west1 region).' Actual server: CLAUDE.md L197 'CI/CD on self-hosted runner hetzner-cx43', L469 'Server: Hetzner VPS (IP: 116.203.222.213)'. Hetzner CX43 is a Hetzner-managed VPS in Germany, not a Google Cloud platform instance. The cloud provider, specific service, and location identifier are all incorrect.
- *Impact:* The privacy policy is a legally binding document for KVKK compliance. Stating a false data processor (GCP/Google) and location (europe-west1 = Belgium) is a material misrepresentation. Under KVKK, users have the right to know exactly where and by whom their data is processed. This is an embarrassing error if a regulator or journalist examines it.
- *Fix:* Update privacy-policy.html to accurately state: 'Verileriniz Avrupa (Almanya, Hetzner CX43 sunucu)' / 'Data is stored in Europe (Germany, Hetzner cloud infrastructure)'. Review all KVKK Article 10 and 11 disclosures for accuracy before any public release.

**P0-24. Privacy policy claims media files are in encrypted storage but MinIO has no encryption at rest**
- *Evidence:* docs/privacy-policy.html L55 (Turkish): 'Medya dosyaları şifreli depolama alanında tutulur.' L119 (English): 'Media files are kept in encrypted storage.' infra/docker-compose.prod.yml L131-L155: MinIO is launched with 'minio server /data' — no MINIO_KMS_KES_ENDPOINT, MINIO_KMS_KES_CERT_FILE, or any encryption-at-rest configuration. MinIO's encryption at rest (SSE-S3 or SSE-KMS) requires explicit KES/KMS configuration. Without it, blobs are stored as plaintext on the host's block storage.
- *Impact:* Users are told their photos, audio messages, and documents are encrypted at rest. They are not. This is a false advertising claim in a legally binding privacy policy. Under KVKK Article 12 (data security obligation), this is a compliance gap. If a data breach occurs, this statement would aggravate legal liability.
- *Fix:* Either (a) configure MinIO SSE-KMS or SSE-S3 encryption at rest, or (b) remove the claim from the privacy policy and replace with accurate language. Option (b) is faster for launch; option (a) is the correct long-term path. Also note: MinIO's upstream repo was archived in Feb 2026 (noted in docker-compose.prod.yml L128) with no further patches — plan migration to Chainguard MinIO or cloud S3/R2.


#### Security / Auth

**P0-25. Two-Step Verification (2FA PIN) does not gate the login flow**
- *Evidence:* backend/src/main/kotlin/com/muhabbet/auth/adapter/in/web/TwoStepVerificationController.kt: all endpoints (POST /setup, POST /verify, DELETE, POST /reset) require an authenticated user (@RequestMapping + AuthenticatedUser.currentUserId()). backend/src/main/kotlin/com/muhabbet/auth/domain/service/AuthService.kt: verifyOtp() and verifyFirebaseToken() call authenticatePhone() which issues JWT tokens without checking twoStepEnabledAt or twoStepPinHash on the user. A user who has enabled 2FA can log in via OTP alone — the PIN is never checked. ROADMAP.md L84 notes 'confirm wired into OTP login + UI (gap-analysis §1.3)' — it is not.
- *Impact:* The Two-Step Verification feature is presented to users in Settings as a security feature, but it provides no actual protection. If a user's phone number is compromised, an attacker can log in without knowing the PIN. This is misleading at best and a security false-assurance at worst.
- *Fix:* In AuthService.verifyOtp() and verifyFirebaseToken(), after creating or fetching the user, check if user.twoStepEnabledAt != null. If so, return an AuthResult with a 'requiresTwoStep: true' flag or a short-lived token that must be exchanged after PIN verification. Add a POST /api/v1/auth/two-step/verify-login endpoint (unauthenticated, accepts the short-lived token + PIN) that issues the final access/refresh tokens.


#### Mobile — Release / Distribution

**P0-26. No signed release keystore or AAB — Play Store submission impossible**
- *Evidence:* mobile/composeApp/build.gradle.kts L125-L126: versionCode = 1, versionName = '0.1.0'. L137-L143: signingConfig only activates if env var MUHABBET_KEYSTORE_FILE is set. .github/workflows/mobile-ci.yml: only builds assembleDebug — no bundleRelease or bundleReleaseAab task. No keystore file exists in repo, CI secrets (grep of workflows for MUHABBET_KEYSTORE returns nothing), or any documented storage location. TODO.md item '[ ] Generate release keystore + produce a signed Android AAB' remains open.
- *Impact:* Google Play Console requires a signed AAB for all new app submissions. Without a release keystore there is no signed artifact, so Play Store submission cannot even begin. This is a hard prerequisite for any public launch.
- *Fix:* Generate a release keystore (keytool -genkey), store it encrypted as a GitHub Actions secret (MUHABBET_KEYSTORE_BASE64 etc.), add a bundleRelease job to mobile-ci.yml that decodes the keystore and runs :mobile:composeApp:bundleRelease, then verify with apksigner verify --print-certs. Update versionName to 1.0.0 before first upload.


---

## 4. P1 — needed before launch / pilot (28)

| # | Area | Issue | Fix |
|---|---|---|---|
| 1 | Concurrency / Security | RateLimitFilter has TOCTOU race condition — window reset is non-atomic | Replace @Volatile Long windowStart with AtomicLong and use compareAndSet for the reset: 'val oldStart = windowStart.get(); if (now - oldStart > WINDOW_MS && … |
| 2 | CI/CD / Code Quality | CI quality gates are non-blocking — JaCoCo and Detekt both continue-on-error: true | Remove continue-on-error: true from both steps. Raise coverage thresholds: domain services to 80%, overall to 50%. Treat Detekt failures as errors (--all-rul… |
| 3 | Architecture / Hexagonal Adherence | DisappearingMessageCleanupJob bypasses hexagonal domain layer — injects JPA repository directly | Inject the MessageRepository domain port interface instead of SpringDataMessageRepository. Move the expiry query to a method on the MessageRepository port (e… |
| 4 | Architecture / Scalability | CallSignalingService stores active call state in-process ConcurrentHashMap — data lost on restart, invisible to peer instances | Store active call state in Redis with a TTL (e.g., HSET calls:{callId} ... EX 3600). Use Redis SETNX for atomic busy-check. This is consistent with the exist… |
| 5 | Architecture / SOLID | MessageController imports concrete MessageService and out-port MessageRepository — violates DIP | Inject MessageUseCase (in-port interface) and remove all direct MessageRepository imports from the controller. Route findById through the domain service. Add… |
| 6 | Architecture / Module Boundaries | ArchUnit module-isolation rule has a gap — auth.domain.port.out.UserRepository is imported by multiple messaging components | Extend the ArchUnit rule to also deny imports of 'auth.domain.port.out.*' from the messaging module. Expose a UserQueryService in-port in the auth module for… |
| 7 | Authorization | WebSocket TypingIndicator does not verify sender is a member of the conversation (enumeration / spam) | Before broadcasting, verify that userId is in members: if (userId !in members.map { it.userId }) { sendError(session, 'FORBIDDEN', 'Not a member'); return }. |
| 8 | Authentication / JWT | JWT aud (audience) claim not validated — tokens valid across any service sharing the secret | Add .audience(['muhabbet-api']) to generateAccessToken and .requireAudience(['muhabbet-api']) to the parser. No breaking change for current single-service de… |
| 9 | Authentication / Brute Force | OTP brute-force protection incomplete — no cumulative lockout after repeated cycles | Add a per-phone-number daily failure counter in Redis (TTL=24h). After N total failures (e.g., 25 across multiple OTP cycles), lock the phone number for X ho… |
| 10 | Input Validation / File Upload | Document upload has no MIME type whitelist — arbitrary file types uploadable | Add ALLOWED_DOCUMENT_TYPES = setOf('application/pdf', 'application/msword', ...) to ValidationRules. In MediaService.uploadDocument, validate command.content… |
| 11 | Input Validation / XSS | InputSanitizer exists but is never called on message content or user inputs | Call InputSanitizer.sanitizeMessageContent(content) before persisting messages in MessageService. Call sanitizeDisplayName() for displayName/groupName inputs… |
| 12 | Crypto Correctness | E2E encryption (libsignal) uses API removed in libsignal ~0.76 — Signal path will not compile when flag is enabled | Do not flip E2EConfig.ENABLED without first rewriting SignalKeyManager to use the current API (ECKeyPair.generate(), ECPrivateKey.calculateSignature(), ECPub… |
| 13 | Test quality / Security | All controller tests bypass Spring Security via direct instantiation — filter chain never exercised | Convert controller tests to @WebMvcTest with @AutoConfigureMockMvc. Use MockMvc to send HTTP requests so that SecurityConfig and JwtAuthFilter are active. Pr… |
| 14 | Security / Infrastructure | RateLimitFilter is in-memory and not wired into the Spring Security filter chain ordering | Back the rate limiter with Redis (INCR + EXPIRE) and explicitly wire it into SecurityConfig before JwtAuthFilter via addFilterBefore(rateLimitFilter, JwtAuth… |
| 15 | Security / Auth | JWT uses HS256 symmetric secret; isAdmin claim can be forged if secret leaks; no test verifies admin-privilege isolation | Switch to RS256 (asymmetric) so the signing key never leaves the server. Add a test that presents a JWT with isAdmin=true signed by a wrong key and verifies … |
| 16 | Security / Observability | Actuator /metrics and /prometheus endpoints are publicly accessible without authentication | Move metrics/prometheus to a management-only port (management.server.port) or require admin IP filtering at the Traefik layer, consistent with how FIVUCSAS h… |
| 17 | CI / Quality gates | JaCoCo coverage gate is continue-on-error:true — coverage failures cannot block CI | Remove continue-on-error:true from the jacocoTestCoverageVerification step. Raise the overall floor to at least 60% and the domain-services floor to 80%. Add… |
| 18 | Performance / Load testing | k6 load tests have never been executed; WebSocket load and Redis fan-out are completely unproven | Run k6 websocket-load-test.js against a staging environment before public launch. Establish baseline: 95th-percentile message latency < 200ms at 500 concurre… |
| 19 | Security / Testing | No OWASP ZAP scan, no pen test, and no IDOR test suite despite explicit security checklist gaps | Run OWASP ZAP baseline scan in CI as a non-blocking gate now and as a blocking gate before launch. Commission a pen test before public Play Store launch. Add… |
| 20 | Mobile / Release readiness | No signed release AAB; mobile never built beyond debug APK in CI | Add a release workflow that builds a signed AAB using a keystore stored in GitHub Actions secrets. Add R8/ProGuard rules for obfuscation. Make the AAB workfl… |
| 21 | Observability | No Prometheus scraper for backend metrics — /actuator/prometheus is exposed but never scraped | Either start the Muhabbet monitoring compose stack (infra/monitoring/docker-compose.monitoring.yml) or add the backend to the shared observability stack's Pr… |
| 22 | Observability / Security | /actuator/health exposes full component detail publicly in production | Change application-prod.yml to show-details: when-authorized (matches the application.yml default) so that unauthenticated callers receive only {status: UP/D… |
| 23 | Secrets Management | Previous production credentials were committed to git history and never fully rotated | If the repo is or may become public, perform a git history rewrite (git filter-repo) to remove the .env.prod file from all commits, or accept the risk as a p… |
| 24 | Security / Scalability | In-memory rate limiter does not survive restarts and is bypassable through header spoofing | Replace the in-memory ConcurrentHashMap with Redis-backed rate limiting (Spring Data Redis is already a dependency). Use a proper IP extraction that validate… |
| 25 | Mobile / Backend — Feature Gap | GET /api/v1/auth/two-step/status endpoint missing — TwoStepSetupScreen silently broken | Add @GetMapping to TwoStepVerificationController that returns TwoStepStatusResponse(enabled = userRepository.findById(userId)?.twoStepEnabledAt != null). Thi… |
| 26 | Observability / Operations | Sentry DSN not configured in production — blind to errors at launch | Create a free Sentry project, set SENTRY_DSN in .env.prod (this file is gitignored). Track the Sentry Spring Boot 4 compatibility issue and remove the exclud… |
| 27 | Mobile — iOS | iOS push notifications non-functional — users receive no offline alerts | For Android-first launch, explicitly document iOS as non-functional for calls, push, and E2E. For iOS launch: create AppDelegate.swift, bind registerForRemot… |
| 28 | Mobile — Security / E2E | libsignal-android Signal crypto code targets a removed API (≤0.70-era) and will not compile against its own pin | Per CLAUDE.md guidance: this requires an owner-driven rewrite on a real Android device with emulator. Replace Curve.generateKeyPair() with ECKeyPair.generate… |

---

## 5. Phased next-phase plan


### Phase 0: Stop the bleed: close live data-breach holes & make prod honest
*Goal:* Eliminate every confirmed P0 that lets one authenticated user read another user's private data, and bring the live deployment to a non-deceptive state (real SMS path, no public internals leak, deployed code matches HEAD). These are exploitable TODAY on the running API.
*Exit criteria:* An integration test with two distinct users proves (a) user B cannot read user A's messages via /search, (b) user B cannot obtain a presigned URL for user A's media, (c) GET /users/{A} does not return A's phone number to B; AND a live curl against the production API shows /actuator/prometheus returns 401/403 and /actuator/health returns no component details; AND the running container's image SHA matches current HEAD with V18 applied.

| Item | Pri | Effort | Flag | Why |
|---|---|---|---|---|
| Fix global/conversation message-search IDOR: add conversation_members JOIN filtered by authenticated userId to searchGlobal & searchInConversation JPQL; enforce membership in the service layer, not the controller | P0 | M |  | SpringDataMessageRepository.kt:79-87 has no membership filter; SearchController.kt passes userId only to resolveDeliveryStatuses. Any aut… |
| Fix media presigned-URL IDOR: thread requesting userId into GetMediaUrlUseCase.getPresignedUrl and verify uploader-owns-or-is-conversation-member before issuing the MinIO URL; return 403 otherwise | P0 | M |  | MediaController.kt:134 passes no userId; MediaService only checks the row exists. Any authed user can fetch any media blob URL. Combined … |
| Fix SSRF in LinkPreviewController: call InputSanitizer.sanitizeUrl(url) (which already exists) before Jsoup.connect; block RFC-1918/link-local/127.0.0.0-8/169.254.169.254 + redirect-revalidation; cap response size | P0 | M |  | LinkPreviewController.kt:26 connects directly; InputSanitizer has zero call sites in main code. Lets any authed user probe internal servi… |
| Stop exposing phoneNumber on GET /users/{id}; remove phoneNumber from the public UserProfile DTO (keep it only on GET /users/me) and enforce onlineStatusVisibility/lastSeen visibility on the GET path | P0 | M |  | UserController.kt:37-119 returns user.phoneNumber to any authed caller and never reads the stored onlineStatusVisibility. Mass phone-numb… |
| Switch the live deployment off the deceptive root docker-compose.prod.yml: make infra/docker-compose.prod.yml the single source of truth (FCM_ENABLED=true, real SMS_PROVIDER, firebase-adminsdk mount, pinned minio/mc), set SMS_PROVIDER+NETGSM creds in .env.prod, and redeploy current HEAD (incl. V18) after a backup + flyway baseline check | P0 | L |  | docker inspect: live container built 2026-03-31, uses root compose with FCM_ENABLED=false + no SMS_PROVIDER → MockOtpSender. Real users g… |
| Lock down actuator: remove /actuator/metrics and /actuator/prometheus from permitAll (gate by admin IP at nginx + SecurityConfig); set management.endpoint.health.show-details=when-authorized; verify live that prometheus→401/403 | P0 | S |  | Live probe: /actuator/prometheus returns 200 and /actuator/health leaks DB/Redis/disk/SSL detail to the public internet. SecurityConfig.k… |
| Add deploy gate: add `needs: [build-and-test]` to the deploy job in deploy-hetzner.yml so a failing test suite cannot deploy, plus an automatic rollback-to-previous-SHA-image step if the post-deploy /actuator/health check fails | P0 | S |  | deploy-hetzner.yml triggers on push to main with no needs: dependency; broken code can reach prod. The SHA-tag rollback mechanism already… |

### Phase 1: Restore the auth/notification core to actually work & be observable
*Goal:* Make the primary user journey (OTP login, real-time delivery across instances, push, 2FA, error visibility) genuinely functional and fail-loud rather than silently mock/broken.
*Exit criteria:* On a staging stack with two backend instances behind a load balancer, a message sent to a user connected on instance A is received by that user when their second device is on instance B (Redis fan-out proven); a real OTP SMS is delivered to a test phone via Netgsm and the app refuses to start if mock is active under the prod profile; an account with 2FA enabled cannot complete login with OTP alone; and a two-device voice call carries audible audio in both directions.

| Item | Pri | Effort | Flag | Why |
|---|---|---|---|---|
| Wire the Redis Pub/Sub subscriber: add a RedisConfig with a RedisMessageListenerContainer @Bean that registers RedisBroadcastListener via addMessageListener(listener, PatternTopic(ws:broadcast:*)); prove cross-instance delivery | P0 | M | 🚩 | RedisBroadcastListener.handleMessage exists but no container/addMessageListener anywhere; multi-instance delivery silently drops. Flag it… |
| Add a fail-loud production guard: @PostConstruct/ApplicationReadyEvent that throws if the active OtpSender is MockOtpSender (or otp.mock-enabled=true) while the prod profile is active; remove the mockCode field from RequestOtpResponse | P0 | S |  | MockOtpSender is @ConditionalOnProperty(matchIfMissing=true) so a missing SMS_PROVIDER silently mocks; mockCode in RequestOtpResponse (Dt… |
| Gate login on Two-Step PIN: in AuthService.verifyOtp/verifyFirebaseToken, when user.twoStepEnabledAt != null return a short-lived challenge token; add unauthenticated POST /auth/two-step/verify-login that exchanges token+PIN for the real access/refresh tokens; add the missing GET /auth/two-step/status endpoint | P0 | M | 🚩 | No twoStep check in AuthService — enabling the PIN gives zero login protection (false security assurance). Missing GET /status makes TwoS… |
| Publish the microphone track in CallEngine.android: after connect() call room.localParticipant.setMicrophoneEnabled(true); make setMuted toggle that track; verify a two-device round-trip carries audio both ways | P0 | M |  | CallEngine.android.kt connect() never publishes audio; setMuted iterates an always-empty trackPublications list. 'Voice calls (Android li… |
| Establish backend error observability without Sentry: add a Grafana/Loki alert on {container=muhabbet-backend} ERROR-rate and on /actuator/health DOWN, since SentryAutoConfiguration is excluded for Spring Boot 4 incompatibility and SENTRY_DSN is empty in prod | P1 | M |  | MuhabbetApplication excludes Sentry and the live container has SENTRY_DSN empty — zero error tracking. Loki already ingests the container… |
| Move rate limiting to Redis and into the security filter chain before JwtAuthFilter; stop trusting raw X-Forwarded-For (validate against the known nginx/Traefik proxy CIDR); add per-phone-number cumulative OTP-failure lockout in Redis | P1 | M |  | RateLimitFilter uses an in-memory ConcurrentHashMap (lost on restart, per-instance), trusts X-Forwarded-For blindly (spoofable), has a TO… |

### Phase 2: KVKK / legal truthfulness & privacy correctness (pilot gate)
*Goal:* Make every legally-binding privacy claim true and implement real KVKK rights, so a Turkish pilot client / regulator review cannot find a material misrepresentation.
*Exit criteria:* Deleting a test account is proven (DB + MinIO inspection) to leave no recoverable phone number, message body, or media for that user; the published privacy policy contains no statement contradicted by the running infrastructure; and a fresh registration cannot reach the OTP step without recording explicit consent.

| Item | Pri | Effort | Flag | Why |
|---|---|---|---|---|
| Implement real KVKK erasure: on account deletion, null/randomize phoneNumber+displayName+avatar+about, purge phone_hashes, async-delete the user's media from MinIO, anonymize message senderIds to a tombstone, purge encryption_keys/one_time_pre_keys; write a KVKK erasure audit log | P0 | L | 🚩 | UserDataService.requestAccountDeletion only sets status=DELETED + revokes tokens + removes from conversations; ROADMAP claims 'KVKK delet… |
| Correct the privacy policy: replace 'GCP europe-west1' with the true processor/location (Hetzner, Germany) in both TR and EN; remove or make-true the 'encrypted storage' media claim | P0 | S |  | privacy-policy.html:54/117 names GCP europe-west1 (actually Hetzner Germany) and :56/119 claims encrypted-at-rest media (MinIO runs `mini… |
| Either configure MinIO SSE-S3/KES encryption-at-rest OR (faster for pilot) drop the encryption-at-rest claim; document the decision in an ADR | P1 | L |  | Backs the privacy-policy correction above with a real technical decision. SSE-KES is the correct long-term path; dropping the claim is th… |
| Add a KVKK explicit-consent gate before first data collection: a one-time onboarding/consent screen shown before the OTP request, storing a consent timestamp; all strings via TR+EN i18n | P1 | M |  | No consent/onboarding screen exists; AuthService.requestOtp creates a User on first contact. KVKK Art.5 requires explicit informed consen… |
| Enforce document MIME whitelist + magic-byte sniffing on all media uploads (images/video/voice/document) instead of trusting the client content-type; store only the detected type | P1 | M |  | uploadDocument has no ALLOWED_DOCUMENT_TYPES and all paths trust MultipartFile.contentType. Combined with the (now-fixed) presigned-URL I… |
| Wire InputSanitizer into the service layer for message content, displayName and group name (it currently has zero call sites) | P2 | S |  | InputSanitizer.sanitizeMessageContent/sanitizeDisplayName exist but are never called; CLAUDE.md implies they protect content. Control-cha… |

### Phase 3: Make the test suite trustworthy & prove scale (pilot confidence)
*Goal:* Close the V&V gaps so green CI actually means something: real HTTP/security-filter coverage, real Redis integration, run the load tests, and add cross-user authorization tests for every resource.
*Exit criteria:* CI fails a PR that drops coverage or introduces a Detekt violation; the suite contains at least one cross-user 403 test per resource type (message, media, conversation, user) running through the real security filter chain; a k6 run report with P95 numbers at the target concurrency is committed to docs/qa; and a ZAP baseline runs in CI.

| Item | Pri | Effort | Flag | Why |
|---|---|---|---|---|
| Convert controller tests to @WebMvcTest + MockMvc through the real Spring Security filter chain (JwtAuthFilter + RateLimitFilter); give BackupController and BotController actual HTTP tests (they currently never instantiate the controller) | P1 | L |  | All controller tests inject SecurityContext directly and bypass the filter chain; BackupControllerTest/BotControllerTest call use-cases d… |
| Make CI quality gates blocking: remove continue-on-error from JaCoCo + Detekt in backend-ci.yml and mobile-ci.yml; raise floors (domain services 80%, overall 50%, add branch-coverage gate) | P1 | S |  | backend-ci.yml:104/109 and mobile-ci.yml:119/123 are continue-on-error → coverage can drop to zero and stay green. Actual coverage is LIN… |
| Run k6 against staging/production-like target and record P50/P95/P99 in docs/qa; establish a baseline (e.g. p95 message latency < 200ms at 500 concurrent WS) as a documented pre-pilot gate | P1 | M |  | Three k6 scripts exist in infra/k6 + infra/load-tests but have never been executed. The single Hetzner CX43 hosts 6+ services with -Xmx76… |
| Add an OWASP ZAP baseline scan to CI (non-blocking now, blocking before public launch) and commission an external pen test before public Play Store launch | P1 | M |  | docs/qa/02-security.md lists IDOR/ZAP/pen-test as unchecked; the media IDOR was found by code-reading in minutes. For a KVKK-regulated me… |
| Add a JVM compile/instantiation test that loads the real libsignal jar against SignalKeyManager so the API mismatch surfaces at test time, and keep a compileDebugKotlin canary in CI even with E2EConfig.ENABLED=false | P2 | M |  | SignalKeyManager uses Curve.generateKeyPair/decodePoint + 2-arg SessionCipher removed from the pinned 0.86.5; the flag-OFF constant hides… |

### Phase 4: Release-engineering & store-launch readiness (public launch gate)
*Goal:* Produce a submittable, signed, observable release artifact and harden the remaining architecture/dependency debt so a public Play Store launch is mechanically possible and operationally safe.
*Exit criteria:* A signed release AAB is produced by CI and verified with apksigner; a Muhabbet DB has been restored from an encrypted backup on a test instance within the documented RTO; and the moderation/admin endpoints reject forged/wrong-key admin tokens in an automated test.

| Item | Pri | Effort | Flag | Why |
|---|---|---|---|---|
| Add a signed-AAB release pipeline: generate a release keystore (operator), store it as a base64 GitHub secret, add a bundleRelease job to mobile-ci.yml with R8/ProGuard, archive the AAB; bump versionName to 1.0.0 | P0 | M |  | No keystore/AAB exists anywhere; CI builds only assembleDebug; versionName=0.1.0. Play Store requires a signed AAB — submission is struct… |
| Move active call state out of in-process maps into Redis (HSET + TTL, SETNX busy-check) so calls survive restart and are visible across instances | P1 | M | 🚩 | CallSignalingService keeps activeCalls/userActiveCalls in ConcurrentHashMap — lost on restart, invisible to peers, no cross-instance busy… |
| Fix hexagonal/DIP violations flagged by ArchUnit gaps: route MessageController.findById and DisappearingMessageCleanupJob through the domain port (not SpringDataMessageRepository); add ArchUnit rules denying adapter/in→adapter/out and messaging→auth.domain.port.out imports | P2 | M |  | MessageController imports the concrete out-port + service; the cleanup job injects the JPA repo directly; messaging imports auth.domain.p… |
| Add JWT aud claim (muhabbet-api) + requireAudience and a roles/admin claim; add a test that an isAdmin-claimed token signed with the wrong key is rejected at a moderation endpoint; rotate the human-readable dev-default secret and assert it's not used in prod | P2 | S |  | JwtProvider sets no aud and no roles/admin claim; the dev default secret is a readable string. No test verifies admin-privilege isolation. |
| Plan MinIO migration off the archived image: pin minio/mc in the active compose and evaluate cgr.dev/chainguard/minio or Hetzner Object Storage / Cloudflare R2; document EOL risk in the runbook | P2 | L |  | minio/minio upstream archived Feb 2026 (no future CVE patches); root compose pulls minio/mc:latest unpinned. This is the media store for … |
| Set validate-on-migrate=true and add a Muhabbet DB restore drill (extend backup-verify.sh to loop all DBs, install the cron, run one real muhabbet restore on a test PG instance) | P2 | M |  | application.yml:41 validate-on-migrate=false hides checksum drift; backup-verify.sh only tests identity_core and the only DR drill (2026-… |

---

## 6. Test plan — close the V&V gaps

**Gaps the current suite leaves open:**
- Every controller test injects SecurityContext directly and bypasses the Spring Security filter chain (JwtAuthFilter, RateLimitFilter, CORS, permit/deny rules) — auth enforcement is effectively untested at the HTTP layer.
- BackupControllerTest and BotControllerTest never instantiate the controller; they call use-cases directly, so backup-export and bot-management auth is completely uncovered (the Sarnıç '490 green tests / broken product' failure class).
- The only @SpringBootTest integration test (AuthControllerIntegrationTest) explicitly excludes RedisAutoConfiguration, so the Redis Pub/Sub fan-out — the sole production multi-instance delivery mechanism, and currently DEAD (no listener container) — has zero integration coverage.
- No cross-user authorization (IDOR/BOLA) test exists for ANY resource: message search, message-info, media presigned-URL, user profile. Two of these (search, media URL) are live data-breach holes.
- k6 load scripts (infra/k6 + infra/load-tests) have never been executed — WebSocket fan-out, DB pool, and the single-server capacity ceiling are empirically unknown.
- No OWASP ZAP scan and no external pen test, despite a KVKK-regulated private-message workload and the project's own security checklist listing them unchecked.
- E2E crypto is tested only with a byte-flip FlipEncryption stub; no test loads the real libsignal jar, so the guaranteed compile/runtime API mismatch (Curve/SessionCipher) is invisible until a flag flip.
- Voice-call audio was never verified end-to-end — the mic track is never published, yet 'Android live' shipped; no two-device audio test exists.
- KVKK erasure was marked 'Done' but no test asserts that phone number / messages / media are actually gone after account deletion.
- RateLimitFilter is not in the security chain and is in-memory; no test verifies a 429 on the N+1th request through the real chain, nor that X-Forwarded-For spoofing is rejected.

**Tests to add (closes the gaps above):**

| Type | Priority | Test |
|---|---|---|
| e2e | P0 | Live-API smoke against production after each deploy: assert /actuator/prometheus → 401/403, /actuator/health returns no component details to an unauthenticated caller, and a real OTP SMS is delivered to a test phone (or the app refuses to start under prod profile with mock active). |
| integration | P0 | Two-distinct-user cross-access suite (real Spring Security via MockMvc + Testcontainers PG): user B gets 403/empty on /search for A's messages, 403 on GET /media/{A-mediaId}/url, and no phoneNumber on GET /users/{A}. One test per resource (message, media, conversation, user). |
| integration | P0 | Redis fan-out test with a real Redis Testcontainer and TWO Spring application contexts: connect two simulated WS clients to different contexts, send from one, assert the other receives via the RedisMessageListenerContainer path. Must fail on current HEAD (proves the dead listener) and pass after wiring. |
| integration | P0 | KVKK erasure proof: create a user with messages + uploaded media, call account deletion, then assert via DB + MinIO inspection that phoneNumber/displayName are nulled, phone_hashes purged, the user's media objects are gone, and message senderIds are tombstoned. |
| integration | P0 | Two-Step login-gate test through the real auth flow: a user with twoStepEnabledAt set receives only a challenge token from verifyOtp and cannot obtain access/refresh tokens without POST /auth/two-step/verify-login + correct PIN. |
| manual-gui | P0 | Two-physical-device voice-call test on real Android hardware proving audio flows both directions after publishing the mic track, and that mute/unmute toggles it. (Cannot be unit-tested; reviewers were told to distrust green tests.) |
| security | P0 | SSRF unit test for LinkPreviewController asserting that http://169.254.169.254, http://127.0.0.1, and RFC-1918 URLs (and redirects to them) are rejected before any outbound fetch. |
| contract | P1 | @WebMvcTest MockMvc suite for BackupController and BotController: unauthenticated → 401, authenticated-non-owner → 403, owner → 200; asserting real HTTP status codes and the security filter chain, replacing the direct use-case calls. |
| integration | P1 | Media MIME/magic-byte test: uploading an .html/.exe/.svg with a spoofed image/jpeg content-type is rejected (document, image, audio paths) and only the detected type is stored. |
| load | P1 | Run k6 websocket-load-test against staging at 50/100/200/500 VUs; record P50/P95/P99 message latency, WS connection ceiling, HikariCP saturation, and JVM heap; commit results to docs/qa with a pass threshold (e.g. p95 < 200ms at 500 concurrent). |
| security | P1 | Rate-limit + brute-force suite through the real filter chain: 429 on the N+1th /auth request; X-Forwarded-For spoof does not reset the window; per-phone cumulative OTP-failure lockout fires after the configured threshold across multiple OTP cycles. |
| security | P1 | OWASP ZAP baseline scan wired into CI (non-blocking initially) targeting the staging API; escalate to a blocking gate plus a commissioned external pen test before public launch. |
| unit | P2 | libsignal-real-jar compile/instantiation test on the JVM that constructs SignalKeyManager against org.signal:libsignal-android so the Curve/SessionCipher/PreKeyBundle API mismatch fails the build instead of staying hidden behind E2EConfig.ENABLED=false. |
| unit | P2 | JWT authorization-isolation test: a token with isAdmin=true signed by the wrong key is rejected at a moderation endpoint; a token missing the muhabbet-api aud claim is rejected once requireAudience is added. |

---

## 7. Production-readiness checklist


**Code work**

- ❌ Add conversation-membership filter to message search (searchGlobal/searchInConversation) + service-layer enforcement
- ❌ Thread userId + ownership/membership check into media presigned-URL endpoint
- ❌ Invoke InputSanitizer.sanitizeUrl + block internal IP ranges in LinkPreviewController (SSRF)
- ❌ Remove phoneNumber from public user DTO + enforce visibility settings on GET /users/{id}
- ❌ Wire RedisMessageListenerContainer bean so RedisBroadcastListener actually subscribes (cross-instance delivery)
- ❌ Lock /actuator/metrics & /actuator/prometheus behind admin IP; set health show-details=when-authorized
- ❌ Add `needs: build-and-test` + auto-rollback to deploy-hetzner.yml
- ❌ Fail-loud prod guard rejecting MockOtpSender/otp-mock under prod profile; remove mockCode from RequestOtpResponse
- ❌ Gate login on Two-Step PIN (challenge-token exchange) + add GET /auth/two-step/status
- ❌ Publish microphone track in CallEngine.android (voice-call audio)
- 🟡 Implement real KVKK erasure (anonymize PII, delete messages/media/phone_hashes/keys) + audit log
- ❌ Correct privacy policy: Hetzner Germany (not GCP europe-west1); fix/remove encryption-at-rest claim (TR+EN)
- ❌ KVKK explicit-consent onboarding gate before OTP (TR+EN i18n)
- ❌ MIME whitelist + magic-byte validation on all upload paths
- ❌ Redis-backed rate limiter in the security chain; trusted-proxy X-Forwarded-For; per-phone OTP lockout
- ❌ Make JaCoCo + Detekt blocking in CI and raise floors
- ❌ Convert controller tests to MockMvc-through-security-chain; real HTTP tests for Backup/Bot controllers
- ❌ Signed-AAB release job in mobile-ci.yml (R8/ProGuard, archive artifact, versionName 1.0.0)

**Operator-only**

- ❌ Set SMS_PROVIDER=netgsm + NETGSM_USERCODE/NETGSM_PASSWORD in production .env.prod
- ❌ Consolidate to infra/docker-compose.prod.yml (FCM_ENABLED=true, firebase-adminsdk mount, pinned minio/mc) and redeploy current HEAD with V18 after backup
- ❌ Generate + securely custody the release keystore; store as base64 GitHub secret (MUHABBET_KEYSTORE_*)
- ❌ Provide GOOGLE_SERVICES_JSON CI secret for real Firebase Phone Auth in release builds
- ❌ Create Sentry project + set SENTRY_DSN (or stand up the Loki ERROR-rate alert as the interim signal)
- ❌ Run k6 against staging and record baseline P95; commission OWASP ZAP + external pen test
- ❌ Execute a real muhabbet DB restore drill; extend backup-verify.sh to all DBs + install the cron
- 🟡 Rotate/scrub the production secrets committed in git history (commit 3925f70/29874bc) before any repo goes public
- ❌ Play Store listing + privacy declarations + KVKK notice submission

**Decisions needing your sign-off**

- ❌ Decide: configure MinIO SSE-KMS encryption-at-rest vs. drop the claim (shapes privacy policy + effort)
- ❌ Decide whether to attempt the libsignal 0.86.5→0.94.4 rewrite for an E2E canary now, or hold E2E flag-OFF and defer the privacy-first marketing claim (needs real Android device + crypto review)
- ❌ Decide MinIO migration target (Chainguard MinIO vs Hetzner Object Storage vs Cloudflare R2) given the archived upstream
- ❌ Decide iOS launch posture: explicitly document iOS as non-functional for calls/push/E2E for an Android-first launch, or invest to close those gaps
- ❌ Decide pilot scope/scale for the first tenant given the unproven capacity ceiling on the shared CX43

---

## 8. FIVUCSAS auth integration — decided direction

> **Refinement (2026-06-06):** the full provider-client product model is in [`PRODUCT_ROADMAP_2026-06-06.md`](PRODUCT_ROADMAP_2026-06-06.md). Key decision: **phone is OPTIONAL, not an enforced anchor** — identity = FIVUCSAS `sub`, login is config-driven via FIVUCSAS, discovery = username/QR/invite (primary) + opt-in phone-contact sync. Native phone-OTP (Twilio) is the FIVUCSAS-unreachable **fallback only**.

**Decision (locked 2026-06-06): harden FIVUCSAS first, then stage — Muhabbet is the LAST integration.** Muhabbet keeps its own phone+OTP auth and ships to prod on it; FIVUCSAS-as-IdP comes only after the platform clears its gates.

- **Now:** wire **Twilio** as the SMS provider (you hold credits; `TwilioOtpSender.kt` already exists — it's a config + secrets change). This fixes the mock-SMS P0 that breaks login today. This native phone-OTP path is **also the permanent FIVUCSAS-down fallback**, so it is never throwaway work.
- **Later (strategic):** Muhabbet authenticates via **FIVUCSAS OIDC** (Android Custom Tabs + AppAuth / iOS ASWebAuthenticationSession), using **SMS_OTP only — no biometrics**, so there is **zero app-side biometric KVKK burden** (FIVUCSAS holds any biometric data as the separate controller). FIVUCSAS's SMS_OTP path is itself Twilio-based, so the credits get used either way.
- **Hard prerequisites before Muhabbet can depend on FIVUCSAS (all verified, several yours to fix):**
  1. **Reachability** — `api.fivucsas.com` is blocked on the Turkish mobile ISPs your consumer users sit on. Must be fronted by reachable/anycast/CDN infra. (#1 gate for a consumer app.)
  2. **API-2** — FIVUCSAS refresh-token grant doesn't bind to the issuing client (`OAuth2Service.java:529-573`). Patch before any external app depends on it.
  3. **Verified phone claim** — FIVUCSAS must *enforce* phone verification for Muhabbet users and reliably emit `phone_number_verified` (today phone is optional). Muhabbet's contact-sync social graph depends on a verified E.164 anchor.
  4. **Additive identity mapping** — map existing phone-keyed Muhabbet accounts ↔ FIVUCSAS `sub` (nullable column, default = legacy native-auth user; never drop the native path).

### Cross-cutting FIVUCSAS prerequisites (the critical path)
The whole "FIVUCSAS does all auth" vision is gated by **two issues, both of which are yours to fix** (you own the platform + the infra):
1. **Reachability** — `api.fivucsas.com` (Hetzner) is blocked on the specific Turkish mobile ISPs your users sit on. Front the auth origin with reachable/anycast/CDN infra. Verified in the Turkey/Hetzner reachability investigation.
2. **API-2** — the OIDC `refresh_token` grant does not authenticate confidential clients and tokens aren't bound to the issuing `client_id` (`identity-core-api/.../OAuth2Service.java:529-573`; tracked in FIVUCSAS `MASTER_ISSUE_REGISTER_2026-06-03`). Patch before any external app depends on RS256 tokens.

Until both clear, each app keeps its **own native auth as the primary path + kill-switch fallback**.

---

## 9. Design for scale (wider / more used)

Muhabbet's bottleneck at scale is real-time fan-out, not CRUD:
- **Fix + actually wire Redis pub/sub** — it's declared but never subscribed, so today a second instance silently drops messages. This must work *before* any horizontal scaling.
- **Move to a real broker** (Redis Streams / Kafka / NATS) for delivery guarantees, sharded presence, and push-notification batching.
- **Externalize in-process state** — `CallSignalingService` keeps active-call state and `RateLimitFilter` keeps counters in `ConcurrentHashMap`; both are lost on restart and invisible across instances. Move to Redis.
- **JWT HS256 → RS256/EdDSA** so verification distributes without sharing the secret; add key rotation (FIVUCSAS's `HsKeyRegistry` is a model).
- **Media → S3/CDN** with async thumbnail workers; partition + archive the message store as it grows.
- **SMS at scale** — Twilio throughput/cost controls + OTP anti-abuse (cumulative lockout).

---

## 10. How to verify it actually works (DB + backend + frontend)

Muhabbet's frontend is a **KMP mobile app**, and this server has **no Android emulator (no KVM)** — so the mobile GUI must be verified on your physical device or a device farm. What I *can* drive here:
- **DB layer:** real-Postgres Testcontainers; after each write, raw-SQL read-back confirms persistence (the pattern that caught Sarnıç #30).
- **Backend layer:** every entity's full CRUD + edge cases (401/403/404/409) against a running instance **through the real Spring Security filter chain** (not `addFilters=false`), plus **two-distinct-user isolation tests** — the exact tests that would have caught the message/media IDORs. Real WebSocket send/receive across two instances (to prove the Redis fix).
- **Shared logic:** the KMP `shared/` module business logic via `jvmTest`.

Verify against staging or a disposable test number — never by hammering the live consumer backend.

---

## Appendix — verified strengths (what's genuinely solid)


**Software Architect / Code-Quality** — no
- Deliberate hexagonal architecture with domain ports, adapters, and ArchUnit enforcement tests — the architectural intent is sound even where the implementation has gaps.
- Feature flags (E2EConfig.ENABLED=false, MULTI_DEVICE_ENABLED=false) correctly isolate incomplete subsystems behind compile-time constants and environment variables, preventing premature exposure.
- Redis is already wired for publish (RedisMessageBroadcaster @Primary) — the subscribe-side gap is a single missing @Bean registration, not an architectural redesign.
- Flyway manages schema migrations with Hibernate validate-only mode (ddl-auto: validate) — no accidental schema mutation from ORM.
- HikariCP connection pool is tuned with explicit timeouts, leak-detection threshold, and idle limits appropriate for the Hetzner VPS hosting context.
- Graceful shutdown (server.shutdown: graceful) is configured, protecting in-flight WebSocket sessions during rolling deploys.
- Testcontainers integration tests exist for the persistence layer, providing a realistic test environment without mocking the database.
- Detekt and JaCoCo are present in CI as a foundation for quality gating — the tooling infrastructure is in place even though the thresholds are not yet enforced.

**Application Security & Privacy** — no
- JWT issuer validation is implemented (requireIssuer('muhabbet') in JwtProvider.validateToken) and refresh token rotation is correctly implemented with SHA-256 hash storage (never storing raw tokens).
- OTP is bcrypt-hashed before storage (passwordEncoder.encode/matches in AuthService) — OTP not stored in plaintext.
- Strong HTTP security headers: HSTS (1yr + includeSubDomains), X-Frame-Options DENY, CSP (default-src 'self'; frame-ancestors 'none'), XSS protection, Referrer-Policy, Permissions-Policy — all applied in SecurityConfig.kt.
- Group authorization is correctly layered: addMembers/removeMember/updateGroupInfo all call requireAdminOrOwner() in GroupService.kt; owner-transfer on leaveGroup is implemented.
- Rate limiting exists at two layers: HTTP in-memory per-IP RateLimitFilter (10 req/min on /api/v1/auth/**) and per-user WebSocket sliding window (50 msg/10s in WebSocketRateLimiter.kt).
- WebSocket authentication is enforced at connection time: JwtProvider.validateToken called in afterConnectionEstablished; unauthenticated connections immediately closed with POLICY_VIOLATION.
- E2E encryption ships behind default-OFF compile-time flag (E2EConfig.ENABLED=false) with a kill-switch rollout runbook — the flag-OFF path is byte-identical to pre-wiring HEAD, preventing accidental E2E activation.
- Refresh token rotation is implemented: old token is revoked (revokeByTokenHash) before issuing new token in AuthService.refresh(), preventing token reuse after rotation.
- Document soft-delete (deleted_at column) and message soft-delete are correctly implemented for conversation history management.
- ArchUnit hexagonal architecture tests are present and run in CI, preventing cross-layer imports.

**Test Architect / V&V lead** — no
- ArchUnit: 14 rules in HexagonalArchitectureTest covering domain independence, adapter boundaries, module isolation, and naming conventions — genuinely catches architecture drift and is the strongest structural safety net in the project.
- DeviceLinkingServiceTest: 14 thorough tests including a concurrency race-condition case (cap filled between beginLink and completeLink), token uniqueness, expiry enforcement, idempotent revoke, and cross-user IDOR prevention at the domain layer.
- SymmetricCipherTest (androidUnitTest): Uses real javax.crypto AES-256-GCM — proves tamper detection, wrong-key rejection, and nonce uniqueness. One of the few tests exercising real cryptography rather than stubs.
- Security workflow: Trivy (CRITICAL/HIGH severity), Gitleaks, and CodeQL are all wired to CI — solid supply chain hygiene that would catch common dependency CVEs and secret leaks before merge.
- MessagingServiceTest: Strong domain service coverage with slot captures and verify calls testing membership enforcement, delivery status creation, and pagination — demonstrates correct use of MockK patterns.
- DeviceLinkControllerTest: Correctly tests flag-gating behavior (MULTI_DEVICE_ENABLED=false returns 403 for all endpoints) and documents the kill-switch guarantee at the test level.
- Flyway + ddl-auto=validate: Schema migration is managed by Flyway with Hibernate validation-only mode — prevents silent schema drift and is production-appropriate hygiene.
- application.yml graceful shutdown and Tomcat tuning: server.shutdown=graceful, connection timeout, keep-alive, and HikariCP pool settings show production awareness beyond a toy project.

**DevOps / SRE / Release-Readiness** — no
- DB backups are running daily (confirmed by /opt/projects/backups/ having entries for each of the past 7 days including muhabbet.sql.gz.gpg), GPG-encrypted, with a documented restore runbook and a successful drill for the shared identity_core DB.
- The shared infra deploy.sh correctly uses --env-file .env.prod on all docker compose invocations and SHA-tags built images for rollback capability, with RUNBOOK_ROLLBACK.md documenting the procedure.
- WebSocket auth is properly enforced server-side: ChatWebSocketHandler.afterConnectionEstablished (line 57) validates the JWT and closes the session with POLICY_VIOLATION if missing or invalid, despite /ws/** being permitAll() in Spring Security (required for the upgrade handshake).
- WebSocket origin is locked to https://muhabbet.rollingcatsoftware.com only via WebSocketConfig.setAllowedOrigins(), preventing cross-origin WS abuse.
- The global exception handler (GlobalExceptionHandler.kt) correctly returns only error code + default message for unexpected exceptions, never leaking stack traces to API callers.
- Security headers are comprehensive: HSTS (31536000s + includeSubDomains), X-Frame-Options DENY, CSP default-src 'self', Referrer-Policy STRICT_ORIGIN_WHEN_CROSS_ORIGIN, Permissions-Policy camera/mic/geo disabled — all set in SecurityConfig.kt.
- The production .env.prod credentials were rotated after the historical git commit exposure (JWT_SECRET and POSTGRES_PASSWORD confirmed changed), and the gitleaks allowlist path rule prevents false-positive rescans.
- All 5 key services (backend, postgres, redis, minio, nginx) have Docker healthchecks defined with appropriate intervals, and the deploy script polls /actuator/health with retries before declaring success.
- Flyway migration history is consistent (V1-V18 present, ordered correctly) and the modular migration approach (no DDL in code) means schema changes are auditable and reversible.
- Feature flags (E2EConfig.ENABLED, MULTI_DEVICE_ENABLED, LIVEKIT_ENABLED) are default-OFF with no-redeploy kill-switch via env var, correctly preventing accidental exposure of unfinished features.

**Product Owner / UX & client-readiness** — no
- Core 1:1 and group messaging is end-to-end functional on Android: OTP login via Firebase, JWT auth, WebSocket real-time messaging, delivery/read receipts (13-scenario test suite in DeliveryStatusTest.kt), SQLDelight offline cache, WS reconnect with jitter backoff.
- The hexagonal architecture is cleanly enforced: ArchUnit HexagonalArchitectureTest.kt enforces domain/adapter separation, all use-cases are interfaces, domain has no Spring annotations. 369 backend tests pass at HEAD (per CLAUDE.md, 2026-06-06).
- i18n is near-complete: 462 TR and 462 EN string keys with no missing keys between the two files (diff returns empty). All user-visible Compose Text() calls use stringResource(Res.string.*) — only one hardcoded English string was found (SharedMediaScreen.kt L371).
- E2E encryption architecture is honest and correctly gated: E2EConfig.ENABLED=false is a compile-time constant, CLAUDE.md accurately documents the libsignal API breakage, and MessageEncryptor.kt is a clean pass-through when disabled with no behavioral change to the prod message path.
- KVKK Privacy Dashboard is genuinely implemented: PrivacyDashboardScreen.kt with data export, account deletion, read-receipt toggle, last-seen and profile-photo visibility toggles, all wired to real backend endpoints (UserDataController.kt GET /users/data/export, DELETE /users/data/account).
- Chat archive and mute are fully wired: archiveConversation / unarchiveConversation and muteConversation / unmuteConversation are implemented in the mobile repository layer and backend ConversationController, with a MutePickerDialog (8h/1w/always) in ConversationListScreen.kt.
- Security headers are well-configured: SecurityConfig.kt sets HSTS (1 year + subDomains), X-Frame-Options DENY, CSP (default-src self; frame-ancestors none), XSS protection, Referrer-Policy strict-origin-when-cross-origin, Permissions-Policy camera=() microphone=() geolocation=().
- The whatsapp-gap-analysis.md (March 2026) and ROADMAP.md are unusually honest for solo-MVP documentation — they explicitly call out 'Partial', 'Missing', and stub implementations rather than overclaiming.
