# Muhabbet project and application review — 2026-11-12

> **Status: Proposed — pending owner approval.** This review is part of the multi-transport design
> package. Its recommendations are not accepted until the owner records approval in
> [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md). **No
> code may be written** for any scope until that scope is approved there (owner decisions 3 and 4).
> The security emergencies in §3 are incident response; the decision brief (§14) recommends the owner
> authorise them now, independently of design approval.

| | |
|---|---|
| **Date** | 2026-11-12 |
| **Subject** | `dev` @ 2434324 (the working branch `claude/muhabbet-p2p-architecture-wdan5r` adds documentation only) |
| **Audience** | The owner (senior software architect) |
| **Single source of truth** | [Decision brief](../architecture/multi-transport/decision-brief-2026-11-12.md). This review agrees with it; deviations are marked `Reviewer note (open)`. |
| **Evidence** | [Findings register](2026-11-12-findings-register.md): 211 findings, 0 refuted. Every claim below cites a finding ID. |
| **Companion documents** | [Multi-transport roadmap](../architecture/multi-transport/roadmap.md) · [ADR index (0010–0033)](../adr/) · [Security incident record](../process/security-incident-2026-11-env-prod.md) · [CI trust boundary](../process/ci-trust-boundary.md) · [GitHub governance](../process/github-governance.md) |

---

## Türkçe yönetici özeti

**Kısa hüküm.** Muhabbet, yazılı niyeti ve dürüstlük kültürü güçlü, ama üretim güvenliği, mimari
sınırları ve doğrulama hattı bir "gizlilik öncelikli" mesajlaşma uygulamasının taşıması gereken yükü
taşımayan bir projedir. Kod tabanının cepleri profesyoneldir (tasarım sistemi modülü, hata kodu
modeli, JWT boot guard'ı, Testcontainers IDOR testleri, dürüst CHANGELOG). Etrafındaki zarf ise zayıftır.
Yargısal ortalama **3,5 / 10** (§2).

**Önce yapılması gerekenler (bu hafta, tasarım onayından bağımsız):**

1. **CICD-06 (kritik):** `.env.prod` dosyası herkese açık depo geçmişinde duruyor (commit `3925f708`).
   İçindeki dört üretim değeri (`JWT_SECRET`, `POSTGRES_PASSWORD`, `REDIS_PASSWORD`,
   `MINIO_ROOT_PASSWORD`) yanmış sayılmalı ve döndürülmelidir. `JWT_SECRET` tek başına her kullanıcı
   adına token üretmeye yeter.
2. **CICD-01 (kritik):** Herkese açık depodaki `pull_request` iş akışları, biri üretim sunucusu olan
   kalıcı self-hosted runner'larda çalışıyor. `deploy` kullanıcısı docker grubunda, yani üretim
   sunucusunda root'a eşdeğer.
3. **CICD-03 / CICD-02 / CICD-05:** Kimsenin tanımlamadığı ikinci runner (`muhabbet-mordecai`), depo
   düzeyindeki imzalama ve Play sırları, SHA'ya sabitlenmemiş action'lar (`trivy-action@master` dahil).

Kesin adımlar §3'te.

**Ürün gerçeği.** Cihazda doğrulanmış alanlar 1:1 metin (bilinen kusurlarla: #725, #726, #313) ve
sesli mesajdır; medya kısmen çalışıyor (§9). Sesli ve görüntülü aramalar hiçbir katmanda çalışmıyor; `main` hâlâ arama düğmesi gösteriyor, `dev`
"yakında" gösteriyor (FT-01). Klasörler, sohbet içi sabitleme, yedekleme ve
botlar yalnızca sunucuda var (FT-02). GIF/çıkartma bozuk (FT-03). KVKK silme işlemi medyayı
silmiyor (#327). iOS uygulaması başlatılamıyor (KMP-01). E2E kapalı ve libsignal derlemede yok
(KMP-13). Tablo §9'da.

**KMP/CMP profesyonelce mi kullanılıyor?** Kısmen. Kod paylaşımı profesyonel: mobil Kotlin satırlarının
yaklaşık %87'si commonMain'de ve commonMain'de tek bir platform sızıntısı yok; `:mobile:designsystem`
derleyicinin zorladığı gerçek bir sınır. Ama zarf profesyonel değil: sürüm kataloğu ve convention
plugin yok (KMP-05), AGP 9 geçici kapatma bayraklarına dayanılıyor (KMP-04), iOS başlatılamıyor ve
CI'da derlenmiyor (KMP-01/02), `:shared` testleri CI'da hiç çalışmıyor (KMP-03), `composeApp` 26,5 bin
satırlık, domain katmanı olmayan bir monolit (KMP-08). Ayrıntı §6'da.

**Ölçülen kapılar (dev üzerinde, bu ortamda):** `:shared:jvmTest` 87/87; backend 1084 test, Docker
olmadığı için 14 Testcontainers sınıfı `initializationError`, başka hata yok; `composeApp` birim
testleri 400/400; `designsystem` 41/41; `assembleDebug` başarılı (~83 MB APK); `verifyUi` ve `detekt`
başarılı. iOS derlemesi, cihaz davranışı ve Testcontainers burada doğrulanamadı.

**Önerilen sıra:** Faz 0 (güvenlik acilleri + tasarım onayı) → 0.4 "uygulama doğruyu söyler" →
0.5.0 Honest (teslim motoru, yalnızca uyandıran push, şifreli yerel DB) → 0.6.0 (iOS kararı bloklayıcı
kapı) → 0.7.0 (aramalar) → 1.0.0 (E2E). Ayrıntı §12 ve
[yol haritası](../architecture/multi-transport/roadmap.md).

---

## 1. Method

### 1.1 Workflows and agents

| Step | What ran | Output |
|---|---|---|
| WF1 review | 11 area reviewers (GitHub governance, CI/CD and supply chain, backend architecture, KMP/CMP structure, mobile architecture, product state and docs, Gemini code review, four external fact-checks: proximity, networking, crypto and sync, broadcast/RF/legal), each paired with an **adversarial verifier** that re-ran its greps, file:line reads, GitHub queries and URL fetches | 211 findings; **0 refuted**; severities re-graded after verification |
| Dev re-check | 6 agents re-checked every code-area finding against `dev` @ 2434324 (the checkout labelled "main" was 120 commits behind `dev`, BE-01) | Per-finding status on `dev`: 93 still-present, 19 partially-fixed, 5 fixed-on-dev, 3 changed |
| WF2 design | 3 architecture proposals (evolutionary, target-state, risk-first), judged by 3 judges (security-legal, feasibility, architecture quality) | The [decision brief](../architecture/multi-transport/decision-brief-2026-11-12.md), which this review agrees with |
| Gates | Gradle run on `dev` in this cloud container (Android SDK and Maven mirror bootstrapped as ADR-0033 describes) | §1.2 |

Rules followed: evidence is file:line, command output, a GitHub object or a fetched URL. Reviewer
recommendations were treated as candidates, not facts, until a verifier reproduced them. Where a
verifier corrected a reviewer, the verifier's reading is used (example: GOV-03's advice to close #327
was wrong; #327 stays open, GOV MISS-02).

### 1.2 Measured gates (`dev`, 2026-11-12)

| Gate | Result | Note |
|---|---|---|
| `:shared:jvmTest` | **87/87 pass** | Not run by any CI workflow (KMP-03) |
| `:backend:test` | **1084 tests; 14 `initializationError`; 0 other failures** | The 14 are `@Testcontainers` classes failing at class init ("no valid Docker environment"). They did **not** run. CLAUDE.md said "ten"; the number moves, so measure it every time |
| `:mobile:composeApp` unit tests | **400/400 pass** | commonTest runs on the Android JVM only (KMP-14) |
| `:mobile:designsystem` tests | **41/41 pass** | |
| `:mobile:composeApp:assembleDebug` | **OK**, ~83 MB debug APK | Compiles; proves nothing about launch |
| `verifyUi` | **OK** | Ratchets: hardcodedColor, rawScaffold, directIcons, rawStackAnimation all 0 |
| `:backend:detekt` | **OK on `dev`** | Fixed on `dev` in db9e59f; still dead on `main` until the dev→main merge (R22, CICD-11) |

### 1.3 Not verifiable here

| Item | Why | Who closes it |
|---|---|---|
| iOS build and launch | No macOS host; no Xcode project exists (KMP-01) | Owner decision at the 0.6 gate (ADR-0028) |
| Device behaviour (launch, notifications, calls, ticks) | No KVM on the CI host; the emulator is on the owner's Windows PC only | Owner observation (P13, ADR-0032) |
| The 14 Testcontainers classes | No Docker in the container | Any run with Docker and Redis up |
| GitHub settings: fork-PR approval policy, branch protection or rulesets, `production` environment scoping, secret scanning, the billing lock | Not readable with this session's scopes | Owner, in repository settings (§3) |
| What production actually runs | Deploy is wired to `main` only; `dev` has 32 unreleased merged PRs (SSOT MISS-01) | Owner: `docker inspect` label plus git SHA in `/actuator/info` (CICD-07) |

---

## 2. Overall verdict and scorecard

**Verdict.** A serious solo product with unusually honest written intent, real engineering in
several places, and a test suite of meaningful size. It runs on an unsafe pipeline (two critical
findings), with an architecture whose stated rules are not enforced on either side, and a product
surface that still advertises things that do not work. It is **not** production-grade for a
privacy-first messenger today. None of this is beyond repair by one engineer, provided the order is:
security emergencies, then truth (0.4), then the delivery engine (0.5), and only then new paths.

| Dimension | Score /10 | One-line justification |
|---|---|---|
| Product truthfulness | **3** | CHANGELOG and dev README are honest, but calls do not work at any layer; production (`main`) still offers them, while `dev` now shows "coming soon" (FT-01, 96eb965/f73040f, reaches users only with the dev→main merge); four server-only features are listed as built (FT-02), and SECURITY.md:56–57 on `dev` still claims hashed phone numbers and KVKK compliance (GOV-08, GOV MISS-03, SSOT-09) |
| Backend architecture | **5** | Sound error model, JWT and Flyway hygiene, 1084 tests; but scheduled messages leak (BE-02, critical), acks are unauthorised (BE-03), ordering has no sequence (BE-06), and ArchUnit enforces none of the stated boundaries (BE-08) |
| Mobile architecture | **3** | Good hygiene (zero `!!`, tested reconnect loop), but no source of truth (MOB-03), a lossy outbox (MOB-04), logout leaves plaintext data (MOB-01, critical) and no presentation layer (MOB-02) |
| KMP/CMP usage | **4** | ~87% commonMain with zero platform leakage and an exemplary designsystem module, but no catalog (KMP-05), AGP 9 opt-outs (KMP-04), no launchable iOS (KMP-01) and an unenforced 26.5k-line monolith (KMP-08) |
| Security | **2** | Two live criticals (CICD-06 leaked secrets, CICD-01 PR code on the prod host); WS sessions outlive logout (BE-12); non-atomic prekeys and silent identity replacement (BE-14) |
| Privacy / KVKK | **3** | #377 enforcement is real, but plaintext stays on device after logout (MOB-01), message text goes to FCM (BE-15), erasure leaves media (#327), and there is no under-15 age gate: the Law 7578 under-15 rule has been in force since 2026-11-01, and whether it binds Muhabbet depends on the social-network-provider classification (Q-L4); the brief builds the gate at 0.4 regardless (ADR-0029, LEG MISS-04) |
| CI/CD | **2** | Tests, compile, `verifyUi` and (on `dev`) detekt gate; JaCoCo, Trivy and the reporters do not (CICD-11); deploy rebuilds from source (CICD-07), the Play AAB is never the verified artefact (CICD-10) |
| GitHub governance | **3** | ROADMAP's method is good, but default-branch drift makes the August fixes inert (GOV-01), "closed" means "merged into dev" (GOV-02), and 231 merged PRs had zero reviews (GOV-06) |
| Documentation | **4** | CHANGELOG and ROADMAP are strong; CLAUDE.md is 100,597 bytes and does four jobs (SSOT-14), its START HERE pointer leads to a document with corrected falsehoods (SSOT-03), and decisions live in two logs (SSOT-08) |
| Testing | **6** | Volume is real (1084 + 400 + 87 + 41), with IDOR integration tests; but shared tests never run in CI (KMP-03), no screen-state or launch tests exist (MOB-12, CICD-16), no iOS test runs (KMP-14) and JaCoCo failures are swallowed (CICD-11) |
| **Mean** | **3.5** | |

Scores are reviewer judgement (0 = absent, 5 = works with material defects, 10 = professional and
enforced); KMP sub-scores come from the KMP/CMP digest; the mean is unweighted.

---

## 3. Security emergencies (Phase 0; owner action now)

These are tracked as incident **INC-2026-11-ENV-PROD** in the
[security incident record](../process/security-incident-2026-11-env-prod.md) and decided in
[ADR-0031](../adr/0031-ci-trust-boundary.md); operational detail is in
[`ci-trust-boundary.md`](../process/ci-trust-boundary.md). **No secret value may appear in any issue,
PR, commit, log or chat. Names only.**

### 3.1 CICD-06 — production secrets in public history (critical)

`.env.prod` was committed and later untracked in commit `3925f708`. The public commit diff holds four
non-empty production values: `JWT_SECRET` (63 characters), `POSTGRES_PASSWORD`, `REDIS_PASSWORD`,
`MINIO_ROOT_PASSWORD`. `.gitleaks.toml` allowlists the path, so the scanner is permanently silent
about it. No rotation is tracked (risk R1).

| # | Owner action | Done when |
|---|---|---|
| 1 | Generate new values for all four names on the production host and write them only into `.env.prod` there. Rotate the database, Redis and MinIO credentials at the service first, then the file. The Postgres, Redis and MinIO containers are shared with other projects on the host: confirm per credential which projects it serves before rotating (incident record §4) | All four rotated; the incident rotation log (§6) lists names and dates only |
| 2 | Redeploy with `--env-file .env.prod` (mandatory; without it the JWT boot guard crash-loops) | Backend healthy **and** a request with a token signed by the old secret is rejected (brief Phase 0 exit). Old access tokens are rejected; users are not logged out, because refresh tokens are random, stored hashed and do not depend on `JWT_SECRET` (ADR-0031 reviewer note); the client silently refreshes. Forcing re-login is a separate owner decision: revoke all refresh tokens (incident record A4) |
| 3 | Decide: rewrite history, or accept the exposure as documented. Rotation is required either way | Decision recorded in the incident record |
| 4 | Replace the path allowlist in `.gitleaks.toml` with a commit-scoped allowlist that records the rotation | Gitleaks no longer ignores the path |
| 5 | Enable GitHub secret scanning and push protection | Visible in repository security settings |

### 3.2 CICD-01 — PR code runs on the production host (critical)

The repository is public. All jobs in all workflows run on `[self-hosted, linux, x64]`, including
`pull_request` workflows with no fork guard. One runner is `muhabbet-cx43`, the production host; its
runner user `deploy` is in the docker group, which is root on that box (risk R2). The root cause is
the account billing lock that refuses GitHub-hosted jobs (CICD-04); hosted runners are free for public
repositories.

| # | Owner action | Done when |
|---|---|---|
| 1 | Settings → Actions → set fork-PR workflow approval to require approval for **all** external contributors | Setting visible |
| 2 | Disable every `pull_request`-triggered job on self-hosted runners, or route it to a non-prod runner (brief §14 item 2). ADR-0031 target: no PR job ever runs on the production host. Additionally, as a backstop for forks only, add `if: github.event.pull_request.head.repo.full_name == github.repository` to every self-hosted job; this guard still runs same-repository branch PRs on `muhabbet-cx43`, so it does **not** satisfy the exit criterion on its own | A test PR runs no job on `muhabbet-cx43` (brief Phase 0 exit) |
| 3 | Guard the deploy trigger: add `github.event.workflow_run.event == 'push' && github.event.workflow_run.head_repository.full_name == github.repository` to the deploy job and deploy `head_sha` explicitly (CICD MISS-01) | Workflow inspection |
| 4 | Clear the account billing lock (CICD-04), then move PR and push CI to `ubuntu-latest` | Hosted jobs start |
| 5 | Stop writing `DEBUG_KEYSTORE` into the persistent runner home (CICD MISS-03): write to `$RUNNER_TEMP`, delete in an `if: always()` step | Workflow inspection |

### 3.3 CICD-03, CICD-02, CICD-05 (high)

| ID | Problem | Owner action |
|---|---|---|
| CICD-03 | A second runner, `muhabbet-mordecai` (machine `mordecai`), with identical labels, appeared between 2026-09-02 and 2026-10-26 and takes arbitrary jobs, including potentially release and deploy | Identify the machine and its controller. Until then **deregister** it, or give it a label nothing targets. If two runners are kept, use explicit custom labels, never bare `self-hosted` |
| CICD-02 | The release keystore and Play credential are repository-level secrets, readable by any same-repo branch PR, with a persistent shared `GRADLE_USER_HOME` | Create a `release` environment restricted to tags `v*` with a required reviewer; move all five secrets there and delete the repo-level copies. Confirm `production` environment scoping for `VPS_*` secrets (CICD MISS-02). Sign only on an ephemeral hosted runner |
| CICD-05 | 44 `uses:` refs on `dev` (42 on `main`), none SHA-pinned; `trivy-action@master` at security.yml:82 and :119 (`dev`); 12 Security runs fell in the March 2026 Trivy compromise window (logs gone, impact unverifiable) | Pin every action to a 40-character SHA with a `# vX.Y.Z` comment; remove `@master`; enable the `github-actions` Dependabot ecosystem on the default branch; add actionlint/zizmor failing on unpinned refs |

Also in Phase 0 (brief §14 item 6): **GOV-08** — correct SECURITY.md on both branches. `dev`
SECURITY.md:56 still says phone numbers are "stored as SHA-256 hashes (never in plaintext)"; V1 stores
`phone_number VARCHAR(15)` in E.164. SECURITY.md:57 equates soft deletion with KVKK compliance
(GOV MISS-03, SSOT MISS-03).

**Rule from ADR-0031:** no long-lived signing, device-CA, uplink or crypto key is created until
ADR-0031 is in force, and none ever lives on a host that runs PR code.

---

## 4. Backend

### 4.1 Assessment

A hexagonal-by-convention modular monolith whose conventions are not enforced. The error model, JWT
handling, persistence basics and integration tests are good. The delivery core (ordering, acks,
idempotency, session lifecycle, prekeys) has correctness and security defects that also happen to be
the exact things a multi-transport design depends on. "Modules communicate via ApplicationEvent" is
false: zero `publishEvent` calls (BE-10); auth and messaging import each other's out-ports (BE-08).

### 4.2 Findings (status on `dev`) and where the brief fixes them

| ID | Sev | On dev | Defect | Fix (brief or register¹) | Milestone |
|---|---|---|---|---|---|
| BE-02 | critical | still-present | Scheduled messages visible to recipients in history and sync before they are due | Seq assigned at release, one transaction per release (ADR-0014) | 0.4 |
| BE-03 | high | still-present | Acks accepted for any messageId from any user; broadcast regardless | Per-device `uptoSeq` ack, members only, existing seqs (ADR-0013) | 0.4 |
| BE-06 | high | still-present | Order and cursors on `serverTimestamp` only; no tie-break, no gap detection | `conversations.last_seq`, `messages.seq/change_seq`, `/changes` (ADR-0014, V28) | 0.4 (V28) / 0.5 |
| BE-07 | medium | partially-fixed | Retried send gets `MSG_DUPLICATE` error; raw `e.message` echoed | `UNIQUE(sender_device_id, client_message_id)`, replayed `ServerAck` (ADR-0013, V29) | 0.4 |
| BE-12 | high | still-present | WS sessions outlive expiry, logout, revoke, deletion; JWT in query string | `SessionRevocationPort`, first-frame or header auth (ADR-0022) | 0.4 |
| BE-13 | medium | still-present | Refresh rotation racy, no reuse detection | Conditional revoke, family revoke on reuse | 0.4 |
| BE-14 | high | still-present | Non-atomic one-time-prekey claim; per-user keys; silent identity replacement | Atomic claim and rate limit (0.4), per-device schema and `IdentityKeyChanged` (0.6) (ADR-0018) | 0.4 / 0.6 |
| BE-15 | high | still-present | Content stored, searched, link-previewed and pushed to FCM in plaintext | Wake-only push (0.5); no server plaintext for E2E conversations (1.0) (ADR-0019) | 0.5 / 1.0 |
| BE-08 | high | still-present | ArchUnit rules enforce none of the stated boundaries | Slice rules with `FreezingArchRule` before any new port | 0.4 |
| BE-11 | medium | changed | `AuthService` 6 use cases / ~21 ctor params; `MessageService` 761 lines on dev; 522-line `AppConfig` | `MessageService` split (`SendMessageService`, `MessageMutationService`, brief §4.2) | 0.5 |
| BE-18 | medium | still-present | Delivery status per user, not per device | `message_device_delivery` (V32) | 0.6 |
| BE-05 | medium | still-present | No transactional outbox; post-commit fan-out can be lost | **Not adopted**: pull-by-seq makes lost hints harmless (brief §3, judge disagreement) | — |
| BE-09 | medium | partially-fixed | Controllers and schedulers inject out-ports and SpringData repos | ¹ArchUnit `adapter.in` rule with BE-08 | 0.4 (proposed) |
| BE-10 | low | still-present | Dead event layer | ¹Delete; seams are ports, not events | 0.5 (proposed) |
| BE-17 | low | still-present | Single-instance assumptions (schedulers, presence, rate limit) | ¹Defer until scale-out (YAGNI) | — |
| BEV-M1 | low | fixed-on-dev | Scheduled release in one transaction | Kept fixed; one tx per message (ADR-0014) | — |
| BEV-M2 | low | still-present | `direction` parameter ignored | Seq cursor (ADR-0014) | 0.5 |
| BE-04, BE-16 | none | fixed-on-dev | Fan-out inside tx; concurrent session writes | Shipped only once `dev` reaches `main` (ADR-0030) | Phase 0 |

¹ Register recommendation, not in the brief: the brief does not mention BE-09, BE-10 or BE-17.

> **Reviewer note (open):** The brief assigns no milestone to BE-09 or BE-10. This review proposes
> folding BE-09 into the BE-08 ArchUnit work at 0.4 and deleting the dead event layer (BE-10) at 0.5,
> when the engine work touches the same seams. Both need the owner to adopt them into the brief.

### 4.3 Strengths to keep

`BusinessException(ErrorCode)` with a thorough `GlobalExceptionHandler`; JWT fail-closed boot guard,
issuer check, 900 s access TTL, hashed and rotated refresh tokens; actuator locked down with a test;
27 gap-free Flyway migrations on `dev` (V1–V27; next is V28, brief §11.1) and UUID keys; blocking enforced at the single write path; read-receipt
privacy enforced at publish time on WS and REST (#377); `TransactionRunner` moving fan-out after commit
on `dev`; IDOR integration tests (Message, Search, Media).

### 4.4 Target

The component layout of brief §4.2: `messaging` gains `ConversationSequencer`, `ChangeFeedService`,
`DeliveryAckPolicy`, `DeliveryHintPublisher`; a `keys` bounded context at 0.6; `auth` gains
`AgeAssurance` (0.4) and `SessionRevocation`; WS gets a `WsGateway` with hello/version. The CLAUDE.md
Code Style rule "Use `Result<T>` or sealed class for operation results" (CLAUDE.md:167) should be amended to the practice: `BusinessException(ErrorCode)` at the
boundary, sealed outcomes only where callers branch (BE-20).

---

## 5. Mobile

### 5.1 Assessment

There is no presentation layer and no domain layer. Decompose is used as a router only
(`childFactory = { config, _ -> config }`); screens pull dependencies with `koinInject()` (167 calls
in 58 UI files) and hold state in `remember { mutableStateOf }` (295 sites). The UI never observes
the SQLDelight cache (0 `asFlow`/`mapToList`), incoming WS messages are never persisted, and DELIVERED
is acked from RAM, twice. `ChatScreen.kt` is 1,188 lines with ten `WsMessage.SendMessage(` sites
(MOB-02, MOB-03, MOB-06). Hygiene is good and should not be lost in the rework.

### 5.2 Findings and fixes

| ID | Sev | On dev | Defect | Fix (brief) | Milestone |
|---|---|---|---|---|---|
| MOB-01 | critical | still-present | Logout and account deletion leave the plaintext DB and outbox on the device; the next account on the same phone sees the previous one's data and drains its queue | `SessionTeardown` (0.4); SQLCipher fresh DB plus resync (ADR-0020) | 0.4 / 0.5 |
| MOB-03 | high | still-present | No SSOT; WS messages not persisted; DELIVERED before storage | Persist before ack (0.4); DB is the SSOT (ADR-0011) | 0.4 / 0.5 |
| MOB-04 | high | still-present | Outbox deletes on socket write, drops `viewOnce`/`thumbnailUrl`/`forwardedFrom`, reorders | Stopgap "delete only on `ServerAck`" (0.4); durable `outbox_entry` state machine (ADR-0013) | 0.4 / 0.5 |
| MOB-05 | medium | still-present | Edit dedup key ignores `editedAt`; second edit dropped | Key includes `editedAt` | 0.4 |
| MOB MISS-01 | medium | still-present | Group READ from one member turns all own messages blue; status dedup ignores member | Aggregate from server rows (per-user until V32 at 0.6, then per-device); never promote on one member's `StatusUpdate`; dedup key includes `userId` (brief §6.4) | 0.4 |
| MOB-06 | high | still-present | `ChatScreen` ~1,060-line composable; 18 UI files over 300 lines | `SendMessage` use case in the engine removes the send sites | 0.5 |
| MOB-11 | high | still-present | `WsClient` fuses transport, outbox, dedup and E2E hook; UI speaks wire frames | Strangle into `WsServerLink` + `WsInboundSource`; 17 `wsClient.send` sites as the migration inventory; `verifyUi` ratchet to 0 (ADR-0012) | 0.5 |
| MOB-08 | medium | still-present | No domain layer | Messaging domain only, in `:mobile:messaging` (ADR-0012); CRUD screens stay on repositories | 0.5 |
| MOB-10 | medium | still-present | Background sync watermark from the device clock | Server seq watermark | 0.5 |
| MOB-02 | high | changed | No presentation layer; state lost on configuration change | **Not bundled.** The no-ViewModel decision stays (ADR-0011); screen-state survival needs its own ADR if wanted (Q-O10) | — |
| MOB-12 | medium | partially-fixed | No screen-state tests; dedup and drain untested | Engine tests with `SimulatedLink`; dedup tests | 0.4 / 0.5 |
| MOB-09, MOB-13, MOB-15 | low | still-present | Cancellation swallowed; unsynchronised `WsClient` state; hardcoded `BASE_URL` | Absorbed by the engine rewrite; `BASE_URL` build-config injected | 0.5 |
| MOB MISS-02 | low | partially-fixed | Background sync not cancelled on logout | Part of `SessionTeardown` | 0.4 |
| MOB-07 | none | fixed-on-dev | Localised labels sent as message content | Kept fixed; client label cleanup at 0.5 | — |

> **Reviewer note (open):** Brief §6.4 describes group receipt aggregation from per-device rows, but
> the brief puts the MOB MISS-01 fix at 0.4, and per-device delivery rows arrive only with V32 at 0.6
> (BE-18). At 0.4 the server holds per-user `message_delivery_status` rows. Recommend that the brief
> say the 0.4 fix aggregates per-user rows and switches to per-device rows at 0.6.

### 5.3 Strengths to keep

#374 fixed and tested (`ApiClient` throws `ApiException` on non-2xx); zero `!!` in production code;
`runCatchingCancellable` at ~135 sites; the `WsClient` connect loop (generation tokens, single-flight
connect, capped backoff with jitter) with 12 tests; narrow cache interfaces with fakes; E2E honestly
NoOp and flag-off, and `registerKeys` gated (#379 fixed); #381 guarded; deep links consumed once
(#642); `allowBackup=false`; the `MuhabbetStackAnimations` rule holds.

---

## 6. KMP & CMP professionalism assessment

**The owner's question: is KMP/CMP used professionally?**

**Answer: the code is; the project around it is not.** The sharing ratio and source-set discipline
are what a professional KMP codebase looks like, and `:mobile:designsystem` is better than most.
But a professional multiplatform product needs a build that a dependency bot can update, every
declared target compiled in CI, shared contract tests run in CI, and boundaries the compiler
enforces. Muhabbet has one of those four boundaries (designsystem) and none of the rest. Today it is
an Android app written in KMP. Sub-scores from the KMP review: build structure 3, source-set hygiene
7, platform abstraction 5, iOS readiness 1, shared contract discipline 6, modularity 3 (designsystem
alone 8), testability 4.

### 6.1 What is professional

| Practice | Evidence |
|---|---|
| commonMain-heavy sharing | `composeApp` commonMain 191 files / 30,388 lines vs androidMain 2,750 and iosMain 1,766 (~87% common) |
| Clean commonMain | `grep -rnE '^import (java\|javax\|android)\.'` over all three modules' commonMain returns nothing; no `System.currentTimeMillis`, `String.format` or `@Synchronized` (KMP-M2) |
| Compiler-enforced design system | `:mobile:designsystem` cannot see `composeApp`; colour schemes are `internal val`; single entry point `com.muhabbet.designsystem.Muhabbet` |
| Ratcheted UI guardrails | `gradle/ui-guardrails.gradle.kts` with a baseline, runs without an Android SDK, in CI (`verifyUi`) |
| Default hierarchy template | No manual `dependsOn`; `iosX64` dropped with a reason |
| Correct per-target engines | OkHttp + Android SQLite driver; Darwin + native driver; Coil via ktor3 in common |
| Small contract module | `:shared` = 9 commonMain files, only serialization, coroutines and datetime; one sealed `WsMessage` hierarchy with `@SerialName` discriminators |
| Honest iOS stubs | `SymmetricCipher.ios` throws instead of returning plaintext |

### 6.2 What is not

| ID | Sev | Problem | Evidence |
|---|---|---|---|
| KMP-01 | high | iOS cannot launch | No `.xcodeproj`, no Swift host, no `MainViewController`; `iosPlatformModule()` referenced only at its definition |
| KMP-02 | high | No iOS compilation in CI | The only iOS job is gated by `vars.ENABLE_MACOS_CI` (mobile-ci.yml:312-315 on `dev`) |
| KMP-03 | high | Shared tests never run in CI | No workflow runs a `:shared` test task; the wire contract is tested only when someone runs it locally |
| KMP-04 | high | Build depends on AGP 9 temporary opt-outs | `android.builtInKotlin=false`, `android.newDsl=false`; KMP plugin applied with `com.android.application`; stale R8 suppression and a `jscanvas` flag with no JS target |
| KMP-05 | medium | No version catalog, no convention plugins | `gradle/` holds only the guardrails and wrapper; compileSdk 35 in `:shared` vs 36 elsewhere |
| KMP-08 | high | `composeApp` is a 26.5k-line layer-organised monolith | No `domain/`; 167 `koinInject` in `ui/`; 15 UI files import `data.remote`; `ChatScreen.kt` 1,188 lines (dev) |
| KMP-07 | medium | Backend duplicates 17 shared DTOs as non-`@Serializable` classes | Two serializers, one contract (e.g. `CommunityResponse` defaults differ) |
| KMP-10 | medium | Stateful services as `expect class` | `CallEngine`, `SymmetricCipher` cannot be faked in commonTest |
| KMP-11 | medium | Protocol has no version and no `Unknown` frame | `wsJson` has no default deserializer |
| KMP-13 | high | libsignal is AGPL-3.0; repo is MIT; libsignal is not even a dependency | `build.gradle.kts:126` commented out at 0.100.0 |
| KMP-14 | medium | commonTest runs only on the Android JVM | No native test ever runs |
| KMP-M1 | medium | Two static Kotlin frameworks configured | Would duplicate the Kotlin runtime in one iOS app |
| KMP-06, KMP-12, KMP-15 | low | Deprecated `compose.*` aliases; kotlinx types as `implementation`; JitPack and compose-dev repos unfiltered | — |

### 6.3 Target structure (brief ADR-0028)

Modules are introduced only when their milestone needs them; each named second implementation is
already on the roadmap (P7).

```text
:shared                          existing, MIT. DTOs, WsMessage, protocol/ (envelopes, golden vectors)
:mobile:crypto            0.5    SealedFrame (internal ctor), EnvelopeSealer, SessionCryptoPort
:mobile:crypto-testkit    0.5    test-only fakes, vectors, contract suite (banned from release classpath)
:mobile:messaging         0.5    delivery engine; commonMain + jvmTest; no Compose, Koin annotations or WsMessage
:mobile:composeApp        existing  UI + server adapters (WsServerLink, RestChangeFeedSource, FcmHintSource)
:mobile:designsystem      existing  unchanged
:mobile:transport-lan / -nearby   1.2
:mobile:transport-meshtastic      1.4, excluded from TR builds by Gradle
```

Prerequisites, in order: `gradle/libs.versions.toml` → one `build-logic` convention plugin
(`muhabbet.kmp.library`) → SQLDelight `.sqm` migrations with `verifyMigrations` → `:shared:jvmTest`
and `:mobile:messaging:jvmTest` in CI. **Not** prerequisites: a linuxX64 target, the AGP 9
`:androidApp` split, per-feature modules (host at ~92% disk, one runner). Platform rule:
`expect`/`actual` only for leaf functions in `platform/`; stateful platform services are commonMain
interfaces bound in platform Koin modules (KMP-10). iOS is a **blocking gate at 0.6**: either
KMP-01 is fixed (entry point, Xcode project, DI, iOS CI build) or an ADR scopes iOS out of 1.0.

Where this differs from the KMP reviewer's proposals, the brief governs: the reviewer's
`:core:*`/`:feature:*` split and linuxX64 target are deferred (YAGNI, host limits). The AGP 9 split
(KMP-04) is still real build debt; it is not a prerequisite for the engine, but it must land before
the next AGP major removes the opt-outs.

---

## 7. CI/CD & supply chain

### 7.1 Assessment

Compile, test and `verifyUi` steps gate, and detekt does on `dev` (db9e59f; on `main` only after the
merge, R22). Coverage, Trivy and the PR reporters are decorative (CICD-11), and the trust boundary is
broken (§3). Last 50 runs: Backend CI 39/4/7 (success/failure/cancelled), Mobile CI
37/9/4, Security & Quality 24/11/15. Deploy: no successful run since 2026-08-17; `dev` holds ≥100
unreleased commits (CICD-08).

| ID | Sev | Problem | Brief / doc |
|---|---|---|---|
| CICD-01, -02, -03, -05, -06 | critical/high | Trust boundary and secrets | §3; ADR-0031 |
| CICD-04 | high | Billing lock forces all CI onto production | §3.2; ADR-0031 |
| CICD-07 | high | Deploy runs `git pull origin main` and rebuilds on the prod host; never the CI-tested SHA or image | ADR-0030: deploy the CI-built image or SHA |
| CICD-08 | high | CD stopped; dev→main batching | ADR-0030: trunk on `main` |
| CICD-10 | high | Mobile release builds twice; the Play AAB was never launched (the #633 failure class) | ADR-0030: build once, install and launch that artefact on emulator-5554, promote the same artefact |
| CICD-11 | medium (partially-fixed on dev) | JaCoCo failure swallowed (`continue-on-error`), reporters 403, Trivy `exit-code 0` | Gates that gate (below) |
| CICD-12 | medium | Security & Quality never runs on PRs into `dev`; CodeQL ignores mobile | ADR-0031: Gitleaks on every PR, no paths filter |
| CICD-13 | high | Dependabot mega-PRs fail; fixed config inert on `dev`; no Docker ecosystem; no dependency verification | Lands with the dev→main merge; add `docker`, `github-actions`, verification metadata |
| CICD-09 | medium | Rollback restores image, not schema (#295) | Expand/contract discipline (brief §11.1) |
| CICD-16 | high | No launch, instrumentation, screenshot, contract or iOS stages | See the reviewer note in §7.3 |
| CICD-17 | medium | Multi-transport has no verification path on this pipeline | `SimulatedLink` property suite (brief §16.2); device matrix by owner |
| CICD-14, -15 | medium/low | Protection inferred review-only; no timeouts, broad permissions | Rulesets with required checks (ADR-0030); per-job permissions and timeouts |

### 7.2 Strengths to keep

Release signing asserts the expected signer SHA-256 and v2 signature; dispatch inputs reach the shell
only through `env` with a regex-checked tag; every Play API call checks status and commits the edit
last; Testcontainers isolate from production; `dev`'s `paths-changed.sh` keeps check runs present for
required-check use; deploy concurrency queues without cancelling; multi-stage non-root Dockerfile with
a healthcheck; `verifyBuildInfoVersion` and `verifyUi`.

### 7.3 Gates that gate (target)

1. One aggregate required check per PR (the `ci-gate` pattern from the CI/CD review), covering backend
   tests, mobile unit tests, `:shared:jvmTest` (KMP-03), `verifyUi`, detekt, Gitleaks.
2. JaCoCo as a ratchet: per-package minimums at today's measured values, `continue-on-error` removed.
3. Trivy SHA-pinned, `exit-code: 1` on CRITICAL; image scan also on the weekly schedule.
4. Release: tag → build once (APK, AAB, `SHA256SUMS`, SBOM, provenance) → owner installs and launches
   the release APK **and** the bundletool universal APK built from that AAB on emulator-5554 (ROADMAP
   release step 6, P13, ADR-0030 V6) → promote the same AAB, checked against `SHA256SUMS`, no Gradle
   step, staged rollout for production.
5. Backend: build the image once in CI, deploy by digest, and fail the deploy unless
   `/actuator/info` reports the expected git SHA.

> **Reviewer note (open):** The brief (§16.3) says CI cannot verify app launch because there is no
> KVM on the Hetzner host. That is true of the Hetzner host, but CICD-16(a) proposes a launch smoke
> on a GitHub-hosted Linux runner with KVM once the billing lock (CICD-04) is cleared. Such a smoke
> gate does not replace the owner's sign-off (P13); it would make the #633 failure class impossible to
> publish even when the manual step is forgotten. Recommend adding it to ADR-0030's release flow as an
> additional gate after CICD-04 is resolved. Availability of KVM on the chosen runner size is
> unverified.

---

## 8. GitHub governance (summary)

Details, rulesets, labels, milestones, Projects fields and templates:
[`docs/process/github-governance.md`](../process/github-governance.md). Branching and release:
[`branching-and-release.md`](../process/branching-and-release.md). Agent conduct:
[`agent-workflow.md`](../process/agent-workflow.md).

| ID | Sev | Finding | Decision |
|---|---|---|---|
| GOV-01 | high | Default-branch drift: issue forms, Dependabot config, stale-branch cron and corrected CONTRIBUTING exist only on `dev`, where GitHub ignores them | ADR-0030: merge `dev` into `main` once, retarget open PRs, delete `dev` |
| GOV-02 | high | 69 issues closed "completed" since the last release, none PR-linked, none shipped | ADR-0030: status axis "merged, not verified" → "released" → "verified on device" |
| GOV-03 | high | Stale P0s, open duplicates, 97 unverified bot findings (34%) | One-time tracker cleanup before any multi-transport epic. **#327 stays open** (GOV MISS-02) |
| GOV-06 | high | 231 merged PRs, zero reviews; the approval rule is unmeetable and bypassed with `--admin`, which also skips checks; agents act as the owner | ADR-0032: agent GitHub App or machine user; owner approves agent PRs |
| GOV-08 | high | False SECURITY.md phone-hash claim, on both branches | Phase 0 (§3) |
| GOV MISS-01 | high | Security scans never run on the integration branch | ADR-0031: Gitleaks on every PR, required |
| GOV-04, -05, -07, -09, -10, -11, -12 | medium | Label duplication; milestones as buckets; contradictory branch policy; release-note regression; native issue types unused; process docs contradict; no claim signal | Governance doc |
| GOV-13, -14, -15 | low | Bot noise on the prod runner; no PR template, CODEOWNERS, code of conduct; mixed TR/EN tracker | Governance doc |

All of the above is applied **only after owner approval** (owner decision 3; scope `process` in
APPROVAL.md).

---

## 9. Product truth table

Evidence: `main`@418eb54 code, `dev`@2434324 CHANGELOG, open issues as of 2026-11-12 (product-state
review, verified). **VD** = works, verified on a device or emulator per the CHANGELOG · **CO** = works,
compiled and tested only · **P** = partial · **B** = broken · **NS** = not started. "dev-only" = fixed
on `dev`, not merged to `main`, not released, and therefore not in production (SSOT MISS-01).

| Feature | State | Evidence | Contradicted claim |
|---|---|---|---|
| 1:1 text | VD, with caveats | Daily use; rate-limited send gets no ack (#725); deletion dropped as duplicate (#726); >2.7 KB insert fails (#313) | CLAUDE.md DONE |
| Groups | P | Fan-out out of tx on dev only (#491/#669); list paginates in memory (#290); wrong group ticks (MOB MISS-01) | DONE |
| Media (images, docs) | P | EXIF lost (#408); dead links after 7 days (#296); thumbnails world-readable (#273); OOM risks (#308, #312) | DONE |
| Voice notes | VD | 0.3.9 re-checked on emulator; playback stops on leaving chat (#603) | — |
| Voice transcription | CO | #381 guarded; not device-verified | — |
| Calls 1:1 | **B** | No `call.initiate` on client; NoOp provider live; #367–#372, #606, #715 (FT-01); call UI shows "coming soon" on `dev` only (96eb965, f73040f) | CLAUDE.md Phase 3 |
| Calls group | **B / NS** | #373, #86 | — |
| Push (FCM) | P | Firebase misconfigured (#458); Google sees message text (#502, BE-15) | — |
| Presence / typing | P | Typing to any conversation id (#352); Redis loss kills WS connect (#309) | DONE |
| Receipts | P | #377 enforced server-side; any-id acks (#299, BE-03); third publish path dev-only (#620) | — |
| Reactions | CO | Concurrent reactions 500 (#336) | — |
| Reply / forward | CO | Forward does not jump to original (#463) | — |
| Edit / delete | P | Second edit dropped (MOB-05); deletion dropped (#726) | — |
| Disappearing | **B** | Messages and media do not disappear (#328); on-screen expiry dev-only (#513) | DONE |
| Polls | P | Voters, anonymous, multiple choice missing (#482) | DONE |
| Location | P | Coordinates typed by hand (#480) | DONE |
| Status / stories | P | Two-way block dev-only (#294/#687); scope undecided | DONE |
| Channels | CO | Announcement switch fix dev-only (#509) | — |
| Communities | P | Cannot gain members without a shared group; no invite (#387, #416) | — |
| Broadcast lists | **B** | Cannot gain a recipient or be sent to (#449) | — |
| Search | P | Home search works (emulator-checked); trigram index unusable (#318) | — |
| Starred | CO | No open defect | — |
| Backup | **B** (server-only) | No client caller (#393); in-memory tx defect (#321); no prod DB backup (#289) (FT-02) | Phase 4 DONE |
| Chat folders, pinned-in-chat, bots | **B** (server-only) | 0 mobile callers (#393, FT-02) | "full verticals with mobile UI" |
| Stickers / GIFs | **B** | Giphy key 403 BANNED, shown as "no results" (#732, FT-03) | Phase 2 DONE |
| Multi-device | NS (flag OFF) | Crypto seam throws; QR placeholder (#242) | ADR 0007 Accepted |
| E2E | **NS** | Flag OFF; libsignal not linked (KMP-13); disabled sources do not compile | "WIRED but flag OFF" overstates |
| App lock | CO | #378 biometric plus lifecycle gate; #545 open | — |
| Privacy settings | CO | Three fields enforced server-side (#377/#382) | — |
| Two-step PIN | CO, dev-only | #566 fixed on dev; main never gates login | — |
| Wallpaper | CO, dev-only | #380 fixed on dev | — |
| Moderation / report / block | P | Block works; admin role cannot be granted, so reports cannot be actioned (#333) | BTK 5651 claim |
| Contact sync | P | Android only; uploads without consent (#334); unsalted hashes are not privacy (C8) | — |
| Account deletion / export (KVKK) | **B** | Account row anonymised, but media blobs remain in MinIO (#327, GOV MISS-02) | SECURITY.md:57, CLAUDE.md:4 "KVKK-compliant" |
| Age gate (Law 7578, under-15) | **NS** | Law 7578 under-15 rule in force since 2026-11-01; whether it binds Muhabbet depends on the social-network-provider classification (Q-L4); the brief builds the gate at 0.4 regardless (ADR-0029, LEG MISS-04). Not built | — |
| iOS app | **NS** | No entry point, no Xcode project, no CI (KMP-01, FT-04) | Phase 5 "implemented" |

> **Reviewer note (open):** The brief's prerequisite list (§14) and 0.4/0.5 scopes do not name two
> product-honesty items: FT-03 (the GIF/sticker picker shows a banned Giphy key as "no results") and
> FT-02 (chat folders, pinned-in-chat, backup and bots are server-only, with 0 mobile callers on
> `dev`, yet listed as built). FT-02 has no UI entry point to hide; it needs a documentation fix (drop
> the "full verticals with mobile UI" and Phase 4 "DONE" claims) and a decision: wire the features
> under a milestone or delete the endpoints (YAGNI). FT-01's dishonest UI is already fixed on `dev`
> (96eb965, f73040f: call entry points show "coming soon") and reaches users with the dev→main merge.
> Separately, brief §8.6 asks to correct SECURITY.md and "the KVKK texts" but does not name
> SECURITY.md:57 (SSOT MISS-03), CLAUDE.md:4 (SSOT-09) or CLAUDE.md:188 (SSOT MISS-04, soft delete
> presented as KVKK erasure), and does not place them in Phase 0. Recommend naming them in §14
> item 6. ROADMAP's 0.5.0 exit criterion ("every control either works or is not shown") already
> covers FT-03.

---

## 10. Documentation SSOT

| ID | Sev | On dev | Finding | Recommendation |
|---|---|---|---|---|
| SSOT-01 | high | still-present | Default branch 32 merged PRs and 2.5 months behind `dev` | ADR-0030 trunk on `main` |
| SSOT-03 | high | still-present | CLAUDE.md "START HERE" → HANDOFF_2026-08-16.md, which still asserts "no emulator" and "prod OTPs cannot be read", both corrected elsewhere | Demote HANDOFF to `docs/history/` with a superseded banner; one environments runbook |
| SSOT-14 | medium (originally high) | still-present | CLAUDE.md is rules, status log, architecture and runbook at once; **100,597 bytes on `dev`** (measured) | Slim to <10 KB of rules, verification rules, forbidden actions and pointers |
| SSOT-08 | medium | still-present | `decisions.md` vs `adr/`, two numbering schemes (001–003, 0007+), gap 0004–0006 | One log, 4-digit MADR-style ADRs. This package already numbers 0010–0033; renumbering 001–003 and filling 0004–0006 from `decisions.md` remain |
| SSOT-09, SSOT MISS-03, SSOT MISS-04 | medium | partially-fixed / still-present | KVKK compliance still claimed (SECURITY.md:57, CLAUDE.md:4); soft delete codified as erasure (CLAUDE.md:188) | Remove until #327, #334, #314 close; a **claims register** is a 1.0 exit criterion (brief §13, RQ-20) |
| SSOT-02, SSOT-10 | medium | partially-fixed | Tracker does not reflect code; branch model documented four ways | Resolved by ADR-0030 |
| SSOT-04, -05, -06, -11, -13 | low | partially-fixed / still-present | Versions, libsignal narrative, SMS provider, counts and DONE markers drift in prose | No versions or counts in prose; status in the tracker and CHANGELOG; doc-drift guardrails in CI |
| SSOT-07, SSOT-12 | low | partially-fixed | Four competing plan authorities; stale design and QA docs still linked | Milestones are the roadmap; dated plans to `docs/history/` |

**Target documentation architecture (one fact, one home):** README (honest status line linking the
truth table); `docs/architecture/` (arc42; this package's [SAD](../architecture/multi-transport/sad.md)
is the multi-transport part); `docs/adr/` as the only decision log; `docs/runbooks/` for deploy,
release, environments, CI and incidents; `docs/process/` for governance; GitHub milestones as the only
roadmap; CHANGELOG for release truth; CLAUDE.md as rules plus pointers.

---

## 11. Strengths worth keeping

| Area | Strength | Why it matters |
|---|---|---|
| Culture | CHANGELOG records what was **not** device-verified and keeps a "Not fixed, and not claimed" section | The project's best asset; the claims register should be built on it |
| Method | ROADMAP: milestones are versions with exit criteria; 1.0.0 means E2E on; done = persistence + reader + mechanism | The right target to enforce, not replace |
| Design system | `:mobile:designsystem` boundary, `internal` palettes, ratcheted `verifyUi` | Precedent for every new module boundary (ADR-0028) |
| Navigation | `MuhabbetStackAnimations` is the only place that builds a `StackAnimation` (guardrail = 0) | Shows ratchets work here |
| Backend | `ErrorCode` model, JWT fail-closed boot guard, actuator lockdown, IDOR integration tests, #377 server-side enforcement | Keep through the BE-11 split |
| Mobile | `WsClient` reconnect loop and its 12 tests; `runCatchingCancellable`; zero `!!`; honest NoOp E2E and throwing iOS stubs | The reconnect loop becomes `WsServerLink` (ADR-0012) |
| Release | Signer SHA-256 assertion; env-only dispatch inputs; Play edit committed last | Keep in the build-once flow |
| Dev branch | detekt fixed (db9e59f); `TransactionRunner` (BE-04); `ConcurrentWebSocketSessionDecorator` (BE-16); issue forms with "observed where"; `paths-changed.sh` | Reach users only through the dev→main merge |
| Code sharing | ~87% commonMain, no platform leakage | Makes iOS cheaper if it is ever funded |

---

## 12. Prioritised action list mapped to milestones

Milestone names follow ROADMAP and the brief: 0.4 truth · 0.5.0 Honest · 0.6.0 Reachable ·
0.7.0 Live · 1.0.0 Encrypted; 1.1–1.4 are **Proposed (gated)**. Full scope, entry and exit criteria:
[multi-transport roadmap](../architecture/multi-transport/roadmap.md). Device criteria are signed off
by the owner only (P13).

| # | When | Action | Findings | Decision |
|---|---|---|---|---|
| 1 | **Phase 0, now** | Rotate every `.env.prod` value; verify the old JWT fails; commit-scoped gitleaks allowlist; secret scanning and push protection | CICD-06 | ADR-0031 |
| 2 | Phase 0, now | No PR job on `muhabbet-cx43`; fork approval; deploy trigger guard; clear the billing lock | CICD-01, CICD-04, CICD MISS-01 | ADR-0031 |
| 3 | Phase 0, now | Identify or deregister `muhabbet-mordecai` | CICD-03 | ADR-0031 |
| 4 | Phase 0 | `release` environment for signing and Play secrets; confirm `production` scoping | CICD-02, CICD MISS-02 | ADR-0031 |
| 5 | Phase 0 | SHA-pin actions, remove `trivy-action@master`, workflow linter | CICD-05 | ADR-0031 |
| 6 | Phase 0 | Correct SECURITY.md and the other public claims (see reviewer note in §9); remove the "full verticals with mobile UI" and Phase 4 "DONE" claims for server-only features; owner decides: wire them under a milestone or delete the endpoints (YAGNI) | GOV-08, SSOT-09, SSOT MISS-03/04, FT-02 | Brief §14 item 6 |
| 7 | Phase 0 | Owner reviews brief and ADRs; counsel engaged (Q-L1–Q-L9); threat model and DPIA screening; licence decision opened (Q-O1) | KMP-13, LEG-* | Brief §13 Phase 0 |
| 8 | Phase 0, after approval | Merge `dev` into `main`; trunk; rulesets; agent identity; tracker cleanup (keep #327 open) | GOV-01/02/03/06, SSOT-01/02, CICD-08 | ADR-0030, ADR-0032 |
| 9 | **0.4** | Backend: BE-02, BE-03, BE-07, BE-12 (+JWT out of query), BE-13, atomic prekey claim and rate limit, contact-lookup cap, **age gate (V30)**, V28/V29 seq and device-scoped idempotency, BE-08 ArchUnit | BE-02/03/06/07/08/12/13/14, C8, LEG MISS-04 | ADR-0013/0014/0018/0029 |
| 10 | 0.4 | Mobile: `SessionTeardown`, persist before DELIVERED, edit key, outbox stopgap, group receipt aggregation | MOB-01/03/04/05, MOB MISS-01/02 | ADR-0011/0013/0020 |
| 11 | 0.4 | Build: version catalog, one convention plugin, SQLDelight `.sqm` + `verifyMigrations`, `:shared:jvmTest` in CI | KMP-03, KMP-05 | ADR-0028 |
| 12 | **0.5.0** | `:mobile:crypto` (type gate), `:mobile:messaging` engine, `WsClient` strangled, UI observes DB, `verifyUi` wire-type ratchet to 0, behind `engine.enabled` | MOB-02/06/08/10/11, KMP-08 | ADR-0011/0012/0016 |
| 13 | 0.5.0 | SQLCipher fresh DB + resync; wake-only FCM with OEM fallback; `/changes`, hello/version, `Unknown` frame; DTO de-duplication; `MessageService` split | MOB-01, BE-15, KMP-07/11, BE-11 | ADR-0019/0020/0022 |
| 14 | 0.5.0 | Hide the GIF/sticker picker or proxy Giphy server-side (FT-03); keep the `dev` call "coming soon" state (FT-01, whose UI fix ships with row 8, the dev→main merge) (ROADMAP 0.5 exit criterion) | FT-01, FT-03 | See reviewer note in §9 |
| 15 | **0.6.0** | Contact discoverability with a reader; per-device delivery (V32) and key schema (V33); `IdentityKeyChanged`; **iOS blocking gate** (launch or scoping ADR) | BE-14, BE-18, KMP-01/02/09, FT-04 | ADR-0018/0028 |
| 16 | **0.7.0** | ADR 0009 resolved; LiveKit on a separate host; client sends `call.initiate` and publishes the mic; relay-only for non-contacts | FT-01, #367–#373 | ADR-0023 |
| 17 | **1.0.0** | Licence executed for `mobile/` (AGPL), counsel on Play and App Store, iOS bridge spike or Android-only 1.0; libsignal adapters; mailbox (V34); history cutover (Q-O4); external crypto review; claims register | KMP-13, BE-15, SSOT-09 | ADR-0017/0021 |
| 18 | 1.1–1.4 (gated) | Direct, Nearby, Relay, Off-grid hardware, each only after its admission gate | PROX-*, NAT-*, RF-*, LEG-* | ADR-0023–0027 |
| 19 | Independent build debt | AGP 9 `:androidApp` split before the next AGP major; remove stale gradle.properties flags; dependency verification; migration N-1 compatibility job | KMP-04, CICD-13, CICD-09 | Not a prerequisite (ADR-0028); schedule explicitly |
| 20 | Independent docs debt | Slim CLAUDE.md; demote HANDOFF; one ADR log (renumber 001–003, fill 0004–0006) | SSOT-03/08/14 | Governance doc |

---

## Appendix

- **Findings register:** [2026-11-12-findings-register.md](2026-11-12-findings-register.md) —
  211 findings across GitHub governance, CI/CD, backend, KMP/CMP, mobile, product and docs, the Gemini
  code review and the four external fact-checks, each with its verifier verdict and status on `dev`.
- **Decision brief:** [decision-brief-2026-11-12.md](../architecture/multi-transport/decision-brief-2026-11-12.md).
- **Approval record:** [APPROVAL.md](../architecture/multi-transport/APPROVAL.md).
