# V&V Review — `media` Module (Backend)

> **Date:** 2026-06-08 · **Reviewer:** automated SE loop (Task 3 — V&V of least-reviewed feature)
> **Scope:** `backend/.../com/muhabbet/media/**` (upload image/audio/document, presigned URLs,
> storage usage, thumbnailing, MinIO adapter, IDOR access policy).
> **Why this module:** it was one of the two thinnest-tested backend modules (2 test files vs.
> 24 for `messaging`) and had **never had a dedicated module-level review**, while being
> security-sensitive (presigned URLs, object keys, content-type handling).

## Method
- Static read of all 17 source files + 2 test files.
- Assessed against `CLAUDE.md` SE principles (SOLID / hexagonal / DRY / KISS / no-hardcoded-strings),
  the OWASP-flavoured checklist in `docs/qa/02-security.md`, and the IDOR fixes from PR #55.
- Verified findings by tracing data flow from `MediaController` → `MediaService` →
  `MinioMediaStorageAdapter` / `MediaAccessPolicyAdapter`.

## Summary

| # | Severity | Title | Status |
|---|----------|-------|--------|
| A | **Medium** (security / correctness) | Document object-key extension derived from unsanitized client filename → path-segment injection into MinIO key | **FIXED 2026-06-08** + regression test |
| B | Medium (security) | `uploadDocument` has no content-type allowlist **and** presigned URLs carry no `Content-Disposition: attachment` → an uploaded `text/html` / `image/svg+xml` document renders inline from the media origin (stored-XSS surface) | **FIXED 2026-06-08** — presigned URLs now force `attachment`; 3 unit tests |
| C | Low (correctness) | Storage-usage accounting buckets documents by `application/` prefix only; documents may have any content type, so `text/*`/other docs are uncounted | Documented |
| D | Low (API quality) | `MediaController.getPresignedUrl` returns `contentType=""`, `sizeBytes=0` — the GET-by-id endpoint drops metadata the upload path returns | Documented |
| E | Info (test gap) | `uploadDocument` had **zero** unit tests before this review | **FIXED** — 6 tests added |

---

## Finding A — Path-segment injection via document filename  *(FIXED)*

**Location:** `MediaService.uploadDocument` (`media/domain/service/MediaService.kt`).

**Before:**
```kotlin
val extension = command.originalFilename?.substringAfterLast('.', "bin") ?: "bin"
val fileKey = "documents/${command.uploaderId}/$fileId.$extension"
```

`command.originalFilename` originates from `MultipartFile.getOriginalFilename()` — fully
client-controlled. `substringAfterLast('.')` keeps everything after the last dot, **including `/`
and `..`**. A crafted upload filename such as `invoice.evil/../../images/victim/owned` yields:

```
documents/<uploaderId>/<uuid>.evil/../../images/victim/owned
```

i.e. attacker-controlled `/` and `..` segments injected into the object key. Image and audio uploads
are **not** affected — they derive the extension from the already-allowlisted *content type*
(`extensionFromMime` / `audioExtensionFromMime`), never from the filename. Only the document path
trusted the raw filename.

**Impact:** the storage key is no longer a single flat segment under
`documents/<uploaderId>/`. Depending on how MinIO + the nginx `/<bucket>/` proxy normalise `..`
in the request path, this is a path-vs-signature confusion / key-spoofing vector and breaks the
invariant the IDOR access policy relies on (keys are derived, opaque, and uploader-scoped). Rated
**Medium** (defense-in-depth + correctness) rather than High because MinIO stores keys as opaque
strings and the `documents/<uploaderId>/<uuid>.` prefix is fixed — but trusting a client filename in
a storage key is exactly the class of bug a review should close.

**Fix:** sanitize the extension to lowercase alphanumerics, length-capped, with a `"bin"` fallback:
```kotlin
private fun safeExtension(filename: String?): String {
    val raw = filename?.substringAfterLast('.', "") ?: ""
    val cleaned = raw.lowercase().filter { it.isLetterOrDigit() }.take(MAX_EXTENSION_LENGTH)
    return cleaned.ifEmpty { "bin" }
}
```
`MAX_EXTENSION_LENGTH = 12` lives in a companion object (no magic number, per `CLAUDE.md`).

**Regression test:** `should sanitize malicious filename so object key cannot be path-injected`
asserts the key matches `documents/<uploaderId>/<uuid>.[a-z0-9]+` and contains no `..`.

---

## Finding B — Inline rendering of uploaded documents  *(FIXED 2026-06-08)*

`uploadImage`/`uploadAudio` validate the content type against `ValidationRules.ALLOWED_*_TYPES`.
`uploadDocument` validates **only size** — any content type is accepted. That is partly intentional
(WhatsApp-style document sharing allows arbitrary types: PDF, docx, zip, …). The real risk is the
**combination** with delivery: `MinioMediaStorageAdapter.getPresignedUrl` issued a GET URL with the
*stored* content type and **no `response-content-disposition`**. So a document uploaded as
`text/html` or `image/svg+xml` was served **inline** from the media origin — a stored-XSS surface if
that origin shares a cookie/trust boundary with the app, and a phishing surface regardless.

**Fix (applied):** `MinioMediaStorageAdapter.getPresignedUrl` now signs a
`response-content-disposition=attachment` override into every presigned GET URL via the MinIO Java
SDK so browsers download rather than render:

```kotlin
GetPresignedObjectUrlArgs.builder()
    .method(Http.Method.GET)
    .bucket(...)
    .`object`(key)
    .expiry(expirySeconds, TimeUnit.SECONDS)
    .extraQueryParams(mapOf("response-content-disposition" to "attachment"))
    .build()
```

- **MinIO SDK API:** `io.minio:minio:9.0.0`. The override is set via
  `BaseArgs.Builder.extraQueryParams(java.util.Map<String, String>)` (inherited by
  `GetPresignedObjectUrlArgs.Builder`) with the S3 query key `response-content-disposition`. Verified
  against the published 9.0.0 jar (`javap`): `extraQueryParams(Map)` exists and the param is signed
  into the URL (SigV4 covers query params), so the disposition cannot be stripped or altered without
  invalidating the signature.
- **Applied uniformly** (not just documents): the storage port only knows the object key, not the
  media category, and the in-app image/audio loader fetches the raw **bytes** — it never relies on
  inline browser rendering, so forcing download does not change in-app media behaviour. This keeps
  the `MediaStoragePort` interface minimal (no per-call category flag threaded from `MediaService`).
- **Endpoint rewrite preserved:** the internal→public endpoint string-replace only swaps the
  scheme+host prefix, so the signed `response-content-disposition` query param survives the rewrite
  (covered by a dedicated test).

**Tests:** `MinioMediaStorageAdapterTest` (3 tests) — the presigned args carry
`response-content-disposition=attachment`; the disposition is requested uniformly regardless of key
type (image vs document); and the public-endpoint rewrite preserves the signed query param. The
args-building and rewrite were extracted into `internal` seams so they are unit-testable without a
live MinIO (the SDK presign does a network round-trip).

> Forcing a neutral `response-content-type` for non-allowlisted types (e.g. `application/octet-stream`)
> was considered and **left out** as redundant: `attachment` already prevents inline rendering, and a
> document content-type allowlist would change the WhatsApp-style arbitrary-document product behaviour
> (out of scope — would also need Finding C re-bucketing). YAGNI.

---

## Finding C — Storage-usage document bucketing  *(low)*

`getStorageUsage` sums document bytes/count via `sumSizeByUploaderAndContentTypePrefix(userId,
"application/")`. Because documents accept any content type (Finding B), a `text/plain` or other
non-`application/*` document is stored but **excluded** from the `documentBytes`/`documentCount`
totals (and from `totalBytes`). Low impact (display-only KVKK/storage stat), but the accounting is
not exhaustive. Compute documents as "everything not image/* or audio/*".

## Finding D — GET-by-id presigned endpoint drops metadata  *(low)*

`GetMediaUrlUseCase.getPresignedUrl` returns only `MediaUrlResult(url, thumbnailUrl)`, so
`MediaController.getPresignedUrl` hardcodes `contentType=""`, `sizeBytes=0` in the response. Clients
that re-resolve a URL by media id get a blank content type. If any client relies on it, widen
`MediaUrlResult` to carry the `MediaFile` metadata (it is already loaded in the service). YAGNI
otherwise — documented, not changed.

---

## What was good (no action)
- **Hexagonal boundaries are clean.** `MediaService` depends only on out-ports; the access policy is
  a port with a native-SQL adapter (`MediaAccessQueryRepository`) that deliberately avoids importing
  `messaging` JPA types — ArchUnit-safe cross-module decoupling. Good DIP/ISP.
- **IDOR fix (PR #55) holds up.** `getPresignedUrl` enforces uploader-or-conversation-member before
  issuing any URL; the membership check is a single `EXISTS` native query.
- **Error handling** consistently maps to `ErrorCode` (no inline message strings), and upload paths
  re-throw `BusinessException` while wrapping unexpected failures as `MEDIA_UPLOAD_FAILED`.
- **`MinioMediaStorageAdapter`** fails soft at startup (logs a warning instead of crashing the
  Spring context when MinIO is down) — matches the documented test-context gotcha.

## Follow-ups for `TODO.md`
- [x] **P0 (security):** force `Content-Disposition: attachment` on media presigned URLs (Finding B). **DONE 2026-06-08.**
- [ ] **P2:** make storage-usage document bucketing exhaustive (Finding C).
- [ ] **P3:** carry content-type/size on the GET-by-id presigned response (Finding D).

## Verification
- `./gradlew :backend:test` — **386 tests pass**, 6 Testcontainers integration tests error on the
  CI sandbox host only (no Docker daemon: `IllegalStateException: ... find a Docker environment`),
  which is environmental and unrelated to this change (392 total).
- `./gradlew :backend:compileKotlin` — green.
