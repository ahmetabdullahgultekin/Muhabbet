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
| B | Medium (security) | `uploadDocument` has no content-type allowlist **and** presigned URLs carry no `Content-Disposition: attachment` → an uploaded `text/html` / `image/svg+xml` document renders inline from the media origin (stored-XSS surface) | Documented — owner decision (see below) |
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

## Finding B — Document content-type allowlist + inline rendering  *(needs owner decision)*

`uploadImage`/`uploadAudio` validate the content type against `ValidationRules.ALLOWED_*_TYPES`.
`uploadDocument` validates **only size** — any content type is accepted. That is partly intentional
(WhatsApp-style document sharing allows arbitrary types: PDF, docx, zip, …). The real risk is the
**combination** with delivery: `MinioMediaStorageAdapter.getPresignedUrl` issues a GET URL with the
*stored* content type and **no `response-content-disposition`**. So a document uploaded as
`text/html` or `image/svg+xml` is served **inline** from the media origin — a stored-XSS surface if
that origin shares a cookie/trust boundary with the app, and a phishing surface regardless.

**Recommended fix (not applied — has product implications):** when issuing presigned GET URLs for
documents, set `response-content-disposition=attachment` (MinIO supports it via
`GetPresignedObjectUrlArgs` extra query params) so browsers download rather than render. Optionally
force a neutral `response-content-type` (e.g. `application/octet-stream`) for non-allowlisted types.
Images/audio are fetched by the app's media loader (not a browser) so an `attachment` disposition
would not change in-app behaviour, but this should be confirmed against the mobile image pipeline
before flipping. **Left for owner sign-off** because it touches the storage adapter and the
public-endpoint URL-rewrite path, and the inline-vs-attachment choice is a product decision.

Tracking: add as a **P1 security** item in `TODO.md` ("force attachment disposition on media
presigned URLs").

---

## Finding C — Storage-usage document bucketing  *(low)*

`getStorageUsage` sums document bytes/count via `sumSizeByUploaderAndContentTypePrefix(userId,
"application/")`. Because documents accept any content type (Finding B), a `text/plain` or other
non-`application/*` document is stored but **excluded** from the `documentBytes`/`documentCount`
totals (and from `totalBytes`). Low impact (display-only KVKK/storage stat), but the accounting is
not exhaustive. If Finding B introduces a document content-type allowlist, align the bucketing with
it; otherwise compute documents as "everything not image/* or audio/*".

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
- [ ] **P1 (security):** force `Content-Disposition: attachment` on media presigned URLs (Finding B).
- [ ] **P2:** make storage-usage document bucketing exhaustive (Finding C).
- [ ] **P3:** carry content-type/size on the GET-by-id presigned response (Finding D).

## Verification
- `./gradlew :backend:test --tests "com.muhabbet.media.domain.service.MediaServiceTest"` — green
  (existing 22 + 6 new document tests).
</content>
