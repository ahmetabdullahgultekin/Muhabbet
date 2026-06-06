# Muhabbet — Product & Growth Roadmap

> **Date:** 2026-06-06
> **Produced by:** a 12-agent market/competitive/domain/platform/premium/legal research sweep (verified WHOIS, KVKK/legal sources, competitor analysis), synthesized into one plan.
> **Reads with:** `docs/PROD_READINESS_AND_PLAN_2026-06-06.md` (the P0/P1 security & correctness register and the FIVUCSAS-as-IdP decision). This roadmap *sequences* that work alongside presence, platforms, features, premium and legal — it does not repeat it.

## Vision

A privacy-first, KVKK-native Turkish messaging platform that people choose over WhatsApp/BiP because it is honestly secure (real E2E, no Meta data-sharing, data they can actually erase), culturally Turkish (Turkish stickers, voice-to-text, KVKK trust dashboard), and present everywhere they are (Android first, then iOS, web, desktop). Auth is owned by FIVUCSAS as the shared identity provider over time (OIDC SMS-OTP, zero app-side biometric burden), with Muhabbet's own phone+OTP as the permanent kill-switch fallback. Monetized with a price-right Turkish "Muhabbet Plus" cosmetic/power tier and a KVKK-compliant Business tier — but only after the live data-leak P0s are closed, because a privacy brand cannot coexist with a leaking API.

## Current state

Backend is live at muhabbet-api.rollingcatsoftware.com but NOT production-ready: a 10-agent source-verified sweep found 26 P0 blockers. The live API is simultaneously (a) leaking — two confirmed IDORs let any authenticated user read every conversation's messages and any media blob, link-preview is an SSRF, /actuator/prometheus + full health detail are public, GET /users/{id} returns raw phone numbers — and (b) non-functional/stale — it runs a 10-week-old image off the wrong compose file with FCM off and SMS_PROVIDER=mock (OTPs logged, never sent), so real users cannot even log in. Root cause the holes hid: controller tests instantiate controllers directly and bypass the Spring Security filter chain, so authorization holes pass green CI (same failure class as Sarnic). Feature breadth is genuinely large (25+ features) but several "DONE" claims are false: E2E is flag-OFF and libsignal 0.86.5 cannot compile against its own pinned API (Curve.* removed ~0.76); voice calls connect to LiveKit but never publish a mic track (zero audio); Redis pub/sub is published but never subscribed (multi-instance silently drops messages); KVKK erasure is a soft-delete only (phone/messages/media retained); two-step PIN does not gate login. No signed keystore/AAB exists, so Play Store is impossible. No web/desktop client; iOS is stub-heavy (APNs/calls/Firebase-auth/E2E all stubbed). No branded domain or public website; privacy policy is unserved and contains two materially false claims (GCP europe-west1 vs actual Hetzner Germany; "encrypted MinIO storage" with no SSE configured). VERBIS unregistered; no Terms of Service; no consent gate. Decided direction (locked 2026-06-06): harden FIVUCSAS first, ship Muhabbet on its own Twilio-backed phone+OTP, integrate FIVUCSAS OIDC last (gated on api.fivucsas.com Turkish-ISP reachability + the OAuth2 refresh-grant client-binding fix). Genuine strengths: clean hexagonal architecture with ArchUnit, correct default-OFF feature flags with kill-switches, 369 backend tests green, 462/462 TR+EN i18n parity, GPG-encrypted daily DB backups.

> **Identity model (decided 2026-06-06): phone is OPTIONAL, not enforced.**
> Muhabbet's identity anchor is the **FIVUCSAS `sub`**, not a phone number. Login is whatever the FIVUCSAS tenant flow says (email / passkey / SMS-OTP / …). A phone number buys exactly one thing — **WhatsApp-style auto-discovery from your contacts** — which is a *feature*, not an auth requirement, and it cuts against the privacy-first positioning (Signal & Telegram are both moving away from mandatory phone). So: **discovery = username + QR + invite links as primary, with opt-in phone-contact sync** for users who want it. Do **not** force phone at signup. (FIVUCSAS roadmap item F2 only makes the verified-phone claim correct *when* a user chooses SMS-OTP.) The open growth decision — how hard to lean on phone discovery for adoption vs. lead with privacy — is in §8.

## 1. Web presence & domains

**Primary domain:** `muhabbet.com.tr`

**Alternates / defensive:**
- muhabbet.app (modern, app-store-adjacent — redirect to primary)
- muhabbet.net (defensive hold)
- muhabbet.com (registered by a private GoDaddy party until Aug 2027 — broker-outreach only, do not block launch on it)

**Subdomain architecture:**

| Host | Purpose |
|---|---|
| `api.muhabbet.com.tr` | Spring Boot REST + WebSocket API — replaces muhabbet-api.rollingcatsoftware.com (narrow CORS/Allowed-Origins to *.muhabbet.com.tr at the same time) |
| `cdn.muhabbet.com.tr` | Media delivery — repoint MINIO_PUBLIC_ENDPOINT here so media decouples from the API rate-limit zones and a CDN/object-store swap is a one-line change (MinIO upstream archived Feb 2026) |
| `status.muhabbet.com.tr` | Public uptime/incident page (Upptime or BetterUptime hitting /actuator/health) — trust signal after the 10-week-stale-container incident |
| `help.muhabbet.com.tr` | Support/FAQ (Turkish-first) — required as the Play/App Store support URL, distinct from website + privacy URLs; hosts KVKK delete-account guidance |
| `app.muhabbet.com.tr` | Reserved now for the Tier-3 web client (CMP Wasm) — not deployed until multi-device per-device fan-out ships |
| `blog.muhabbet.com.tr` | Reserved (deferred) — SEO/press content (WhatsApp alternatifleri, KVKK uyumlu mesajlasma) |

**Site map (pages):**

| Page | Purpose | Pri |
|---|---|---|
| `/` | Turkish-first landing: positioning, Play Store badge, honest feature highlights, EN toggle. Minimum gate to unlock Play Store submission | P0 |
| `/gizlilik-politikasi` | Corrected, publicly-served privacy policy (Hetzner Germany not GCP; remove false encryption claim; VERBIS no.; privacy@muhabbet.com.tr) — KVKK Art.10 | P0 |
| `/kullanim-kosullari` | Terms of Service (Turkish primary + EN) — required for Play/App Store, moderation authority, age + applicable-law (Turkey) | P0 |
| `/aydinlatma-metni` | Standalone KVKK clarification text, separate document from consent (Board Decision 2026/347) | P0 |
| `/cerez-politikasi` | Cookie policy + first-visit consent banner (essential-only by default) — KVKK Cookie Guide | P0 |
| `/indir` | Download page: Play badge + deep link, honest 'iOS coming soon', beta APK; UTM target for dl/get attribution; later Huawei AppGallery badge | P0 |
| `/guvenlik` | Security & privacy trust page — the primary differentiator vs WhatsApp/BiP. Honest: 'TLS today, E2E arriving'. Never claim E2E until the flag is genuinely flipped | P0 |
| `/yardim` | Help/FAQ index (>=5 TR articles incl. how to delete account for KVKK) | P1 |
| `/ozellikler` | Features page mapped honestly against WhatsApp/BiP (live vs coming-soon) | P1 |
| `/hakkimizda` | About/mission — Turkish-origin, privacy-first story (post-2021 WhatsApp backlash) | P2 |
| `/seffaflik` | BTK 5651 transparency report (quarterly counts) + designated BTK/representative contact | P2 |

## 2. Client platforms

| Platform | Current | Plan | Pri |
|---|---|---|---|
| Android (Compose Multiplatform) | Full feature set and the only viable launch surface, BUT not store-shippable: no signed keystore/AAB (P0-20/26), voice calls carry no audio (P0-22), app-lock toggle never calls BiometricPrompt (placebo), E2E flag-OFF and libsignal won't compile. | Launch vehicle. Generate offline release keystore + bundleRelease in CI, fix the one-line mic-track publish, wire BiometricPrompt to the existing app-lock toggle, ship to closed testing then Play Store on Twilio-backed phone+OTP. FIVUCSAS OIDC added later via Custom Tabs + AppAuth, gated behind a default-OFF flag with native OTP as fallback. | P0 |
| iOS (Compose Multiplatform) | Compiles but stub-heavy: no AppDelegate/APNs hook (no notifications), CallEngine.ios is a no-op (no calls), FirebasePhoneAuth.ios stubbed (no login), E2E NoOp. Cannot be App Store-submitted as a messenger. | Stagger 4-8 weeks behind Android. Wire AppDelegate + APNs token + real phone auth (or FIVUCSAS ASWebAuthenticationSession), bridge LiveKit Swift SDK via Kotlin/Native cinterop for calls, add iPad split-pane (App Review §2.1), file accurate Apple Privacy Nutrition Labels. TestFlight before public. | P1 |
| Web client (Compose Multiplatform Wasm) | None. ROADMAP Tier 3.5, gated on multi-device per-device fan-out (the message_device_delivery rows are not built). Backend already serves WebSocket at /ws. | Lock CMP-Wasm (Beta Sep 2025) over React/TS BEFORE multi-device work begins so the QR device-linking + fan-out protocol is designed once. Reuses 100% of commonMain (Ktor/coroutines/serialization work on Wasm). Ship as installable PWA (manifest + service worker). Deploy at app.muhabbet.com.tr only after per-device delivery ships. | P2 |
| Desktop (Compose Multiplatform Desktop / JVM) | None. No jvmMain/desktopMain target in build.gradle.kts. | Add the desktopMain target NOW (1-2 day scaffold) in parallel with multi-device so commonMain incompatibilities surface early and the client is ready the moment fan-out ships. Package MSI/DMG/deb via compose.desktop nativeDistributions on a GitHub Actions OS matrix. No Electron (WhatsApp EOL'd theirs in 2023). | P2 |
| Huawei AppGallery (Android repackage) | None. BiP ships here; meaningful Turkish Huawei/no-GMS install base. | After the signed Play AAB exists, register on Huawei Developer Console and publish the same signed artifact; add an AppGallery badge to /indir. Packaging task, not a code change. | P2 |

## 3. Feature tracks

### Security & Correctness (Phase 0 — incident, not roadmap)
*The live API is actively leaking and login is broken. No web presence, identity work, feature, or premium tier may be built before these close — a privacy brand on a leaking API is worse than no brand. All fixes land behind two-distinct-user 403 integration tests through the REAL Spring Security filter chain (the missing tests that let these holes pass green CI).*

| Item | Tier | Pri | Effort | Inspiration |
|---|---|---|---|---|
| Fix message-search IDOR: JOIN conversation_members filtered by authed userId in searchGlobal/searchInConversation; enforce membership in the service layer | free | P0 | M | P0-1 — any authed user can read every conversation |
| Fix media presigned-URL IDOR: thread requesting userId into GetMediaUrlUseCase; verify uploader-or-member before issuing MinIO URL, else 403 | free | P0 | M | P0-5/8/13 — any authed user can fetch any media blob |
| Fix GET /messages/{id}/info BOLA: membership check before returning sender/recipients/mediaUrl | free | P0 | S | P0-7 |
| Fix SSRF: call existing InputSanitizer.sanitizeUrl before Jsoup.connect; block RFC-1918/link-local/169.254.169.254 + cap response size | free | P0 | M | P0-2 |
| Remove phoneNumber from public UserProfile DTO (keep on /users/me); enforce onlineStatusVisibility/lastSeen on the GET path | free | P0 | M | P0-9 — live KVKK Art.12 phone-harvest |
| Lock down actuator: drop /metrics+/prometheus from permitAll (admin-IP at nginx + SecurityConfig), health show-details=when-authorized | free | P0 | S | P0-6/22 |
| Wire real SMS via Twilio (TwilioOtpSender exists): remove SMS_PROVIDER mock default so missing value fails startup; @PostConstruct refuses mock in prod | free | P0 | S | P0-4/11/19 — OTPs never sent today; also the permanent FIVUCSAS-down fallback |
| Redeploy current HEAD off the single canonical infra compose (FCM on, firebase-adminsdk mount, V18 applied) after DB backup + flyway baseline check | free | P0 | L | P0-17/19 — 10-week-stale broken prod |
| Add deploy gate: needs:[build-and-test] + auto rollback-to-previous-SHA on failed post-deploy health check | free | P0 | S | P0-18 — broken code can ship straight to prod |
| Convert controller tests to @WebMvcTest/MockMvc through the full filter chain; add two-user isolation suite; make JaCoCo/Detekt blocking | free | P0 | L | P0-13/15, P1-2/13 — root cause the IDORs hid |
| Subscribe the Redis broadcast listener (RedisMessageListenerContainer @Bean) + two-context Testcontainers cross-instance delivery test | free | P0 | M | P0-3/16 — multi-instance silently drops messages |
| Gate two-step PIN into login: requiresTwoStep short-lived token + /auth/two-step/verify-login + status endpoint | free | P0 | M | P0-25, P1-25 — 2FA is a placebo today |
| Confirm rotation of git-history-leaked secrets; document date; add gitleaks pre-commit; plan filter-repo before any public release | free | P0 | S | P0-12 |
| OTP anti-abuse: per-phone cumulative daily lockout in Redis; Redis-backed RateLimitFilter (atomic, survives restart, wired before JwtAuthFilter) | free | P1 | M | P1-1/9/14/24 |
| JWT hardening: validate aud claim now; plan HS256->RS256/EdDSA so verification distributes without sharing the secret (needed for FIVUCSAS + scale) | free | P1 | M | P1-8/15, scale section |
| Run k6 WebSocket/Redis-fanout load test + OWASP ZAP baseline; commission external pen test before public launch | free | P1 | M | P1-18/19 — never run |

### Identity & Auth (FIVUCSAS integration)
*Decided direction: Muhabbet ships on its own Twilio phone+OTP; FIVUCSAS-as-IdP comes LAST, gated on platform readiness. Native auth is the permanent kill-switch fallback — never throwaway. All core auth changes are reversible/flag-gated (default OFF = native), additive identity mapping, dark->canary-one-tenant->broad.*

| Item | Tier | Pri | Effort | Inspiration |
|---|---|---|---|---|
| Additive identity mapping: nullable fivucsas_sub column on users (default null = legacy native-auth user); never drop the native path | free | P1 | M | FIVUCSAS prereq #4 |
| FIVUCSAS OIDC client (SMS_OTP only, no biometrics — zero app-side biometric KVKK burden): Android Custom Tabs + AppAuth, iOS ASWebAuthenticationSession, behind default-OFF APP_AUTH_USE_FIVUCSAS flag | free | P2 | L | FIVUCSAS decided direction; FIVUCSAS holds biometric data as separate controller |
| Consume verified phone claim: require phone_number_verified from FIVUCSAS so contact-sync's E.164 social graph stays sound | free | P2 | M | FIVUCSAS prereq #3 |
| BLOCKER to track (FIVUCSAS-side, owner owns both): front api.fivucsas.com with Turkish-ISP-reachable/anycast/CDN infra; patch OAuth2 refresh-grant client-binding (API-2). Until both clear, native auth stays primary | free | P2 | L | FIVUCSAS prereqs #1/#2 — #1 consumer gate |

### Web Presence & Go-to-Market
*Zero public web presence today; Play/App Store require website + privacy + support URLs. Keep the marketing site static on Hostinger per project rule; Turkish-first with EN toggle; KVKK consent everywhere.*

| Item | Tier | Pri | Effort | Inspiration |
|---|---|---|---|---|
| Register muhabbet.com.tr (primary) + muhabbet.app (redirect) + muhabbet.net (defensive); broker-outreach muhabbet.com only | free | P0 | S | decisions.md domain TBD; .com.tr signals KVKK domesticity |
| Ship the P0 static site (landing + corrected privacy + ToS + aydinlatma + cookie + indir + guvenlik), Turkish-first, on Hostinger | free | P0 | M | Play Store hard gate; Signal-lean not BiP-heavy |
| Subdomain + cert + CORS architecture (api/cdn/status/help; reserve app/blog); repoint MINIO_PUBLIC_ENDPOINT to cdn. | free | P0 | M | avoid retrofitting nginx/CORS; narrow Allowed-Origins to *.muhabbet.com.tr |
| Branded email on the domain: privacy@ / support@ / hello@muhabbet.com.tr (forward to one inbox) | free | P1 | S | parent-brand privacy@rollingcatsoftware.com undermines trust |
| Public status page (status.) + Help center (help.) + honest Features page (/ozellikler) | free | P1 | S | store support-URL requirement; operational maturity after stale-container incident |
| cdn/media CDN layer + status seo blog (deferred); Huawei AppGallery distribution after signed AAB | free | P2 | M | media dominates bandwidth at scale; BiP ships AppGallery |

### Feature Parity (table-stakes vs WhatsApp/BiP)
*Closes the most dangerous switching gaps once the product is safe and public. Honest sequencing: working calls and a real privacy story before flashy extras. E2E is the brand thesis but cannot be marketed until libsignal is rewritten and verified on a real device.*

| Item | Tier | Pri | Effort | Inspiration |
|---|---|---|---|---|
| Voice call audio: publish mic track (room.localParticipant.setMicrophoneEnabled(true)) + two-device verify | free | P0 | S | P0-22 — 'culturally non-negotiable', one-line fix wrongly marked done |
| True KVKK erasure: null PII, async-delete MinIO media, tombstone senderIds, purge phone_hashes/keys, erasure audit log, +30-day scheduled hard-delete | free | P0 | L | P0-10 — makes the privacy dashboard honest; gates paid backup |
| Wire BiometricPrompt (Android) / LocalAuthentication (iOS) to the existing app-lock toggle | free | P1 | S | competitive analysis — lock is a placebo today |
| Enforce stored privacy settings: gate last-seen/online by onlineStatusVisibility; gate WS read-receipt broadcast by readReceiptsEnabled | free | P1 | S | competitive — settings stored but not wired; KVKK promise |
| libsignal rewrite (ECKeyPair.generate, IdentityChange, 11-arg Kyber PreKeyBundle, SessionCipher localAddress) on a real Android device+emulator; JVM test against the real jar; then flip E2E flag dark->canary | free | P1 | XL | P0-14, CLAUDE.md libsignal BLOCKED — the privacy thesis; owner-driven, unverifiable on CI host |
| Username/@handle system: connect without sharing phone number (backend domain + mobile UI) | free | P1 | M | competitive — Telegram/Signal have it, WhatsApp 2026; doubly important for a KVKK product |
| Turkish-culture sticker store: curated artist packs + 'Premium' tab (free base packs, premium gated) | premium | P1 | M | competitive — BiP's Yigit Ozgur moat; Giphy is Western-skewed |
| Multi-device per-device fan-out (message_device_delivery rows) — non-crypto, unblocks web+desktop | free | P2 | M | platforms — single most important prerequisite for non-phone surfaces |
| Inline message translation (Turkish-anchored API) + emergency location broadcast ('Acil Konum') | premium | P2 | M | competitive — BiP 106-lang translation + earthquake-relevant emergency button |
| Instant circular video messages + notification mark-as-read/BigPicture + tablet/iPad split-pane | free | P2 | M | competitive parity gaps; App Review iPad requirement |
| Group voice/video calls (LiveKit multi-party; WsMessage.GroupCallStarted already defined) | free | P2 | XL | competitive — BiP up to 10-15; pulled earlier than the old Tier 3.4 |

### Premium UX, Brand & Monetization
*No monetization exists and the theme is a WhatsApp hex clone (brand + legal risk). Build only after Phase 0. Lead with a distinct Turkish identity, then gate concrete value (voice-to-text already built, premium wallpapers/stickers, power features). B2B KVKK-compliant tier is the real ARPU lever.*

| Item | Tier | Pri | Effort | Inspiration |
|---|---|---|---|---|
| Distinct Turkish visual identity (off WhatsApp hex), custom Turkish-friendly typography scale, motion/haptics polish | free | P1 | M | premium UX — MuhabbetTheme.kt is exact WhatsApp values; clone risk for reviewers/press |
| IAP/subscription infra: Play Billing 6 + StoreKit 2; subscription_tier enum (FREE/PLUS/BUSINESS) via Flyway V19; SubscriptionPolicy domain port; RTDN/App Store Server Notifications webhooks; prices stored in TRY (no live FX) | premium | P1 | L | premium model — zero infra today; lira volatility |
| Muhabbet Plus tier (~49 TRY/mo, 399 TRY/yr, 30-day trial): premium wallpapers/app-icons, Turkish artist stickers, voice-to-text (already built), message scheduling, incognito read ('mavi tik' relief), 20 pinned chats, chat-lock | premium | P1 | L | premium model — below Telegram TR 110 TRY; voice-to-text is the anchor |
| KVKK trust UI: 'Sifreli Baglanti' chat-header indicator (honest TLS-now) + expanded Gizlilik Raporu (Art.10 transparency) — only after privacy policy is corrected | free | P2 | S | premium differentiator — extend PrivacyDashboardScreen |
| Muhabbet Business tier (~149 TRY/seat/mo, KVKK-hosted Slack/Teams alternative): productize BotService/ChannelAnalytics/Community/Backup; admin workspace; per-message-free vs WhatsApp Business API | enterprise | P2 | XL | premium model — real revenue engine; Turkish healthcare/legal need Turkish-hosted team chat |
| Premium cloud backup with media (free=30-day text, Plus=media to 5GB, Business=unlimited) — after true erasure ships | premium | P3 | L | premium model — Signal free/paid backup precedent; ties storage cost to revenue |

## 4. Premium model & monetization

Three tiers, sequenced strictly AFTER Phase 0 (no premium on a leaking API). FREE: all core messaging (1:1, groups, calls once audio is fixed, KVKK controls, base Turkish + Giphy stickers, voice messages, standard backup, E2E once it genuinely ships), ~3 pinned chats, 1 device (then per-device when multi-device lands). MUHABBET PLUS (~49 TRY/month or 399 TRY/year, ~30-35% annual discount, 30-day trial — deliberately below Telegram TR's ~110 TRY but above zero): premium Turkish-artist sticker packs, premium wallpaper packs + custom app icons, voice-to-text transcription (already built in SpeechTranscriber, Turkish tr-TR — the anchor feature Turks pay Telegram for), message scheduling, incognito read (addresses documented Turkish 'mavi tik' anxiety), 20 pinned chats, working chat-lock PIN, 5 linked devices, larger file transfers, and (later) media cloud backup to 5GB. MUHABBET BUSINESS / ENTERPRISE (~149 TRY/seat/month, 5-seat minimum): the real ARPU lever — a KVKK-compliant, Turkish-data-resident Slack/Teams alternative built on the existing BotService + ChannelAnalytics + Community + Backup infrastructure, targeting Turkish healthcare/legal/public-sector SMBs who cannot legally keep team chat on US-hosted Slack/Teams; flat pricing vs WhatsApp Business API's per-message billing; unlimited backup retention. Payments: Google Play Billing + Apple IAP handle TRY natively (use Play regional pricing set in TRY, never auto-FX); evaluate iyzico (BDDK-licensed) for lower-commission web subscriptions later. Backend enforcement via a subscription_tier enum on users (Flyway V19) and a SubscriptionPolicy domain port so the hexagonal layer gates premium endpoints without coupling to the billing implementation.

## 5. Legal & compliance (KVKK / GDPR / sector)

| Document / control | Regulation | Required | Status | Owner |
|---|---|---|---|---|
| Privacy Policy (Gizlilik Politikasi) — corrected + publicly served | KVKK Art.10 | yes | partial |  |
| Aydinlatma Metni (standalone clarification text, separate from consent) | KVKK Art.10 + Board Decision 2026/347 | yes | partial |  |
| Acik Riza (explicit consent) onboarding gate + stored consentTimestamp/consentVersion | KVKK Art.5 + Board Decision 2026/347 | yes | missing |  |
| Terms of Service (Kullanim Kosullari) | Turkish Consumer/E-Commerce Law + Play/App Store policy | yes | missing |  |
| Cookie Policy (Cerez Politikasi) + consent banner | KVKK Cookie Guide | yes | missing |  |
| VERBIS data-controller registration (number then cited in policy) | KVKK Art.16 | yes | missing |  |
| True right-to-erasure implementation (not soft-delete) + 30-day hard-delete job | KVKK Art.7 | yes | partial |  |
| Data Processing Agreements + Turkish SCCs (Firebase/Google, Netgsm/Twilio, Hetzner) filed with KVKK within 5 business days | KVKK Art.9 (July 2024 cross-border regulation) | yes | missing |  |
| Data-breach notification process (72h KVKK + affected individuals) — note the live IDORs may already trigger a retroactive obligation | KVKK Art.12 | yes | missing |  |
| Phone-number access control on GET /users/{id} (live breach fix) | KVKK Art.12 | yes | partial |  |
| MinIO encryption-at-rest: implement SSE/KES OR remove the false policy claim | KVKK Art.12 | yes | missing |  |
| Age-verification gate at registration (recommend 18, or 16 per BiP with parental-consent clause) | KVKK children's data | yes | missing |  |
| Play Data Safety + Apple Privacy Nutrition Labels (accurate) | Google Play / App Store policy | yes | missing |  |
| BTK draft OTT-regulation assessment (Jan 2026 target — authorization/local-entity/representative thresholds) | BTK Electronic Communications Law | yes | missing |  |
| BTK Law 5651 metadata-log retention (1-2yr), 24h takedown SLA, quarterly transparency report | BTK Law 5651 | no | partial |  |
| GDPR scoping decision (restrict to +90 numbers to avoid GDPR, OR add an EU addendum) — documented as an ADR | GDPR | no | missing |  |
| Data-localization plan (Hetzner Germany is EU-adequate but not Turkish-soil) for the post-1M-user BTK threshold | BTK / data localization | no | missing |  |

## 6. Phased plan

### Phase 0 — Stabilize & Secure (treat as an incident)
*Goal:* Close every confirmed P0 that lets one authenticated user read another's data, and make the live deployment honest and functional (real Twilio SMS, no public internals, deployed code = HEAD with V18). No new feature, web page, identity, or premium work starts until this exits.
*Exit:* A two-user integration test proves B cannot read A's messages (search), cannot get a presigned URL for A's media, and GET /users/{A} returns no phone number to B; a live curl shows /actuator/prometheus returns 401/403 and /actuator/health leaks no component detail; the running container SHA = HEAD with V18 applied; a real OTP SMS is delivered via Twilio; a two-device call carries audio both ways.

- Fix the four IDOR/BOLA holes (message-search, media-URL, message-info) + SSRF behind two-distinct-user 403 integration tests through the real filter chain
- Remove phoneNumber from public DTO + enforce visibility settings
- Lock down actuator (admin-IP, health when-authorized)
- Wire Twilio SMS (kill the mock default; @PostConstruct refuses mock in prod) — also the permanent FIVUCSAS-down fallback
- Redeploy HEAD off the single canonical infra compose (FCM on, V18) after backup + flyway baseline; add deploy gate needs:[build-and-test] + auto-rollback
- Subscribe the Redis listener + cross-instance delivery test; gate two-step PIN into login
- Convert controller tests to MockMvc through SecurityConfig + add the two-user isolation suite; make JaCoCo/Detekt blocking
- Fix voice-call audio (publish mic track) — smallest highest-value usability win

### Phase 1 — Legal Foundation & Public Web Presence
*Goal:* Make Muhabbet legally safe to be public and give it a real, KVKK-compliant Turkish web identity — the hard gate before any store submission.
*Exit:* muhabbet.com.tr serves an accurate Turkish-first privacy policy, ToS, aydinlatma metni and cookie consent; VERBIS number is live and cited in the policy; a real account-deletion request verifiably nulls PII + removes media (raw-SQL read-back); the onboarding flow records explicit consent before any data is collected; signed DPAs/SCCs are on file.

- Register muhabbet.com.tr (+ .app redirect, .net defensive); stand up subdomain/cert/CORS architecture; repoint MINIO_PUBLIC_ENDPOINT to cdn.
- Correct + publicly serve the privacy policy (Hetzner Germany; remove false encryption claim); publish ToS, standalone aydinlatma metni, cookie policy + consent banner, /guvenlik, /indir
- Register with VERBIS; execute DPAs + file Turkish SCCs for Firebase/Twilio/Hetzner; stand up the 72h breach-notification process
- Implement true KVKK erasure + 30-day hard-delete job; add the acik-riza onboarding consent gate (stored timestamp/version); add age gate
- Branded email on the domain; status + help subdomains; GDPR scoping ADR (+90-only vs EU addendum); retain a Turkish tech-law firm to assess the BTK OTT regulation

### Phase 2 — Android MVP Public Launch
*Goal:* Get the safe, legal product into Turkish users' hands on Android via Play Store, on Muhabbet's own Twilio-backed phone+OTP.
*Exit:* A signed AAB is in Play production (or closed testing graduating to production) with an accurate Data Safety declaration; real users register via Twilio OTP and complete a working voice call; pen-test findings are triaged with no open criticals; app-lock actually authenticates.

- Generate offline release keystore + signed bundleRelease in CI; versionName 1.0.0; Turkish-first Play listing leading with KVKK/Turkiye sunucular (honest about E2E status)
- Wire BiometricPrompt to the app-lock toggle; enforce stored privacy settings on read paths
- Configure Sentry (or Loki/Grafana ERROR alerting fallback); run k6 load + OWASP ZAP baseline + external pen test
- Closed testing -> open testing -> production; add Huawei AppGallery with the same signed artifact
- Distinct Turkish visual identity + custom typography (de-clone the theme before screenshots are public)

### Phase 3 — Feature Parity & Trust (incl. real E2E)
*Goal:* Close the switching gaps that keep Turkish users on WhatsApp/BiP and deliver the privacy thesis honestly.
*Exit:* E2E is genuinely ON for at least one canary cohort with a verified two-device round-trip and a no-redeploy kill-switch; users can connect by @handle without sharing a phone number; iOS is on TestFlight with working login, notifications and calls; the /guvenlik claims match shipped reality.

- Owner-driven libsignal rewrite verified on a real device + JVM test against the real jar; flip E2E dark -> canary one cohort -> broad via the kill-switch runbook (never claim E2E until live)
- Username/@handle system; Turkish-culture sticker store; instant video messages; notification + tablet/iPad parity
- Multi-device per-device fan-out (unblocks non-phone surfaces); inline translation + emergency location ('Acil Konum')
- iOS to TestFlight: AppDelegate/APNs + phone auth (or FIVUCSAS ASWebAuthenticationSession) + LiveKit Swift bridge + iPad split-pane + accurate Apple privacy labels
- KVKK trust UI (honest 'Sifreli Baglanti' indicator + expanded Gizlilik Raporu)

### Phase 4 — Identity Convergence, Premium & Scale
*Goal:* Converge auth on FIVUCSAS (with native fallback intact), monetize, and harden for growth across web/desktop.
*Exit:* FIVUCSAS OIDC is the primary auth path for a canary tenant with native fallback proven via kill-switch; paying Muhabbet Plus subscribers exist with backend tier enforcement; the web/desktop client receives messages via per-device fan-out; the backend serves messages correctly across >=2 horizontally-scaled instances under a k6 load run; a documented BTK/localization posture is signed off by counsel.

- FIVUCSAS OIDC (SMS_OTP only, no biometrics) behind default-OFF flag, dark -> canary one tenant -> broad; native phone+OTP stays as permanent kill-switch; additive fivucsas_sub mapping — gated on api.fivucsas.com Turkish-ISP reachability + the OAuth2 refresh-grant client-binding fix
- IAP/subscription infra (Play Billing + StoreKit 2, subscription_tier V19, SubscriptionPolicy port, RTDN webhooks); launch Muhabbet Plus (~49 TRY) + premium backup
- Muhabbet Business tier (KVKK-hosted team-chat on existing Bot/Channel/Community infra)
- Web client (CMP Wasm PWA) at app.muhabbet.com.tr + Desktop (CMP JVM, MSI/DMG/deb) once fan-out is live; JWT HS256->RS256/EdDSA; Redis Streams/Kafka broker; externalize call/rate-limit state; media->CDN; SMS-at-scale controls
- BTK OTT-regulation compliance per legal advice; data-localization plan staged for the 1M-daily-user threshold

## 7. Ecosystem cross-promotion (FIVUCSAS + Muhabbet + Sarnıç)

The three projects are one product family and should be built, promoted, and sold **together**, with **FIVUCSAS as the shared identity backbone**. This is the strongest form of the provider-client model and a growth engine in itself.

- **Shared identity + SSO launcher.** One FIVUCSAS login → hop between Muhabbet and the other suite apps (the `fivucsas-launcher` web component + `links.fivucsas.com` hub already seed this). The network effect *is* the promotion.
- **"Secured by FIVUCSAS" badge** on the login screen (the reCAPTCHA / e-Devlet pattern) — promotes FIVUCSAS while lending Muhabbet third-party trust.
- **Cross-sell paths.** Schools running Sarnıç → Muhabbet for parent↔school comms and class/announcement channels; Muhabbet users who are teachers/parents → Sarnıç. Both are live proof points for FIVUCSAS's secure identity.
- **One brand family** — shared design language, cross-linked footers, a single suite/hub page, one launch narrative ("the Rollingcat suite").
- **KVKK guardrail.** Cross-promotion stays **UX-level, never silent data-merging**: pairwise `sub` (FIVUCSAS roadmap item F6) keeps the apps from correlating the same person, so you can promote across products without breaching data-minimization.

## 8. Decisions needing the owner

- Primary domain: confirm muhabbet.com.tr (Turkish-market/KVKK credibility, needs a Turkish legal entity for .com.tr registration) vs muhabbet.app (modern, no trademark proof). This gates all subdomain/cert/CORS/MINIO_PUBLIC_ENDPOINT work. Also decide whether to broker-purchase the premium muhabbet.com (private GoDaddy owner, expires Aug 2027) or permanently redirect.
- Turkish legal entity: founding an Istanbul/STI is effectively required for .com.tr and unlocks KVKK data-controller registration — is this on the roadmap, and on what timeline (it gates VERBIS, ToS jurisdiction wording, and the BTK OTT-authorization posture)?
- Pricing in TRY: confirm the Muhabbet Plus anchor (~49 TRY/mo / 399 TRY/yr) and Business (~149 TRY/seat/mo), annual-vs-monthly, and whether to add iyzico for lower-commission web subscriptions alongside the app-store rails.
- Monetization sequencing: B2B (multi-seat workspaces/admin/bot productization) vs consumer Plus first, given solo-engineer capacity — and confirm that NO premium infra is built before Phase 0 closes.
- E2E libsignal rewrite: when is the owner-driven session (real Android device + emulator) scheduled? Until it is verified two-device, E2E cannot be enabled or marketed on any surface, and 'privacy-first' store copy is false-advertising risk.
- Web client stack: lock CMP Wasm (same codebase, larger bundle, Beta) vs React/TS (smaller bundle, second stack) BEFORE multi-device fan-out is designed — recommendation is CMP Wasm for a Kotlin-only team.
- FIVUCSAS dependency timing: confirm when api.fivucsas.com Turkish-ISP reachability and the OAuth2 refresh-grant client-binding (API-2) will be fixed — both gate Muhabbet ever depending on FIVUCSAS; until then native auth stays primary.
- GDPR scope + minimum age: restrict registration to +90 numbers (eliminates GDPR for MVP) or add an EU addendum; and choose 18 (simplest) vs 16 (BiP) vs 13 (needs robust parental consent) — both affect onboarding UX and copy.
- BiP-competitive positioning: name BiP explicitly on /guvenlik ('unlike a telecom-tied app...') or avoid naming a Turkcell-backed competitor (Signal avoids comparisons) — a marketing/legal-risk call before security-page copy is written.
- Data localization risk appetite: stay on Hetzner Germany with SCCs (lower cost) vs proactively migrate to a Turkish datacenter (Turkcell Bulut/Natro/Radore) ahead of the BTK 1M-user threshold.
