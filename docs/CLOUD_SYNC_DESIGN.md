# MIND Cloud Sync Design

Status: partially implemented. The app has explicit manual cloud backup and confirmed restore flows, versioned snapshots, revision-checked publication, checksum verification, per-account local backups, and Firestore rules tests. Automatic background sync, conflict merging, generation cleanup, and real-device/two-device end-to-end verification are not complete.

## Current verified baseline

- Authentication code includes Firebase Email/Password and Google Sign-In credential exchange in `AuthScreen.kt`; neither provider's complete flow has yet been verified on a physical device in this project.
- The app currently stores its complete working state locally in Preferences DataStore (`mind_local_data`).
- `MainActivity` restores the local state on startup and writes it back after state changes.
- `FirestoreMindSyncRepository` currently uploads versioned snapshots and restores the active generation after explicit user action. Settings exposes manual backup, check, and confirm-restore actions.
- Firestore dependency is already declared (Firebase BOM 32.8.1, Firestore 24.11.1).
- Firestore rules now scope sync metadata, generation manifests, and chunks to the authenticated UID, validate document shape/schema/checksum/payload bounds, and deny deletion of published sync documents.
- Several local histories can contain thousands of records (up to 30,000 step entries and 20,000 entries in some other histories). A single Firestore document is therefore not a safe storage target.

## Non-negotiable behavior

1. Local-first: every feature remains usable without a network connection.
2. Account isolation: every cloud path is scoped to the authenticated Firebase UID.
3. No silent data loss: a first sign-in must never blindly replace non-empty local data with cloud data, or overwrite existing cloud data with an empty local state.
4. A sync is reported as complete only after all data chunks are committed and the active snapshot pointer is published.
5. Retryable failures leave local data intact and must not publish a partial snapshot as current.
6. Never sync passwords, Firebase tokens, device sensor baselines, or transient service state.

## Proposed Firestore layout

```
users/{uid}/sync/meta
users/{uid}/sync/meta/snapshots/{generation}
users/{uid}/sync/meta/snapshots/{generation}/chunks/{feature}_{chunkIndex}
```

The metadata document is `users/{uid}/sync/meta`. Its `snapshots` subcollection contains generation documents, and each generation document has a `chunks` subcollection. This follows Firestore's collection/document path alternation: metadata and generation are documents; snapshots and chunks are collections. The metadata document contains the schema version, active generation, revision, chunk manifest, and server update timestamp. A generation document describes its feature set and chunk counts. Chunk documents contain bounded portions of one feature's serialized records.

Keep each chunk comfortably below Firestore's 1 MiB document limit (target at most 256 KiB serialized payload). Split histories by record count and measured serialized size; do not assume a fixed number of records always fits. Use deterministic record identifiers or stable chunk ordering so retries are idempotent.

Write all chunks for a new generation first. Verify every write, then publish the new active generation in the metadata document using a transaction / revision check. Clean up old generations only after the new pointer is confirmed. This avoids exposing incomplete uploads as the current backup.

## Data classification

- Sync user-owned content and history: habit definitions/history, water history and goal, sleep sessions and preferences, study sessions, completed workout summaries, food entries, journal entries, notes, and user-configurable focus settings.
- Keep presentation-only settings (language/theme) local by default unless a later product decision explicitly requires cross-device settings sync.
- Do not sync the step-counter sensor's device baseline or transient counter state. If step history is synced, sync timestamped daily deltas as history only.
- Do not sync an active GPS session, raw location trail, foreground-service state, or alarm runtime state. Completed workout summaries may be synced.
- Treat notes and journal text as private user data; keep them under the UID-scoped path and never expose them publicly.

## First-link and restore policy

When a verified account first connects, the intended product behavior is:
- If no cloud snapshot exists and local data exists, offer a clear upload choice.
- If cloud data exists and local data is non-empty, eventually present explicit choices: merge compatible histories, restore cloud data, or keep this device's data. Do not choose destructive replacement automatically.
- If local data is empty and a cloud snapshot exists, restore only after validating the manifest and all chunks.
- Validate schema version and record bounds before writing restored data into DataStore.
- Preserve a local backup/export path before any destructive restore or replacement.

Current implementation gap: Settings currently exposes manual upload and an explicit check/confirm-restore flow, but it does not yet implement the full first-link decision screen or history merge. The restore path writes a private local backup first. Treat this as a guarded manual backup/restore feature, not a complete multi-device sync experience.

## Conflict handling

Use a monotonically increasing per-user revision and transactionally compare the expected revision before publishing. Append-only histories should merge by stable record identity and timestamp, then deduplicate. For scalar settings and editable text, detect concurrent revisions and prefer an explicit conflict decision rather than silently applying last-write-wins across devices. A retry must be idempotent.

## Offline execution

Keep DataStore as the source of truth for immediate UI updates. Queue pending sync work and retry when authenticated connectivity returns; use WorkManager with network constraints for durable background retries. Do not block app startup or feature interactions on Firestore availability.

## Security rules implemented

The current Firestore rules use explicit matches for the sync metadata document, generation manifests, and chunk documents. Access is restricted to the authenticated owner UID. The rules validate allowed fields, schema version, revision, checksum format, encoding, timestamp, chunk index, and bounded non-empty payloads; deletion of published sync documents is denied. Emulator tests cover owner access, unauthenticated and cross-user denial, malformed/oversized documents, invalid metadata, and denied deletes.

Do not replace these explicit rules with a broad recursive read/write rule: that would weaken the current document-level validation.

## Required tests before enabling sync

- UID isolation for root metadata, generations, and chunks.
- Empty-local / empty-cloud and all first-link decision combinations.
- Chunk sizing against actual serialized UTF-8 byte length.
- Interrupted upload: active generation remains unchanged.
- Successful upload: pointer changes only after all chunks exist.
- Retry idempotency and duplicate history merge.
- Concurrent device revision conflict.
- Offline queue retry and process restart recovery.
- Restore validation rejects missing chunks, invalid records, and unsupported schema versions.
- API 23 compatibility and existing unit/build checks.

## Implementation status and remaining sequence

Completed in code and covered by CI:
1. Pure Kotlin snapshot serialization, schema/record validation, UTF-8 chunk sizing, and round-trip/negative unit tests.
2. Firestore security rules with UID isolation and emulator tests.
3. Repository upload, manifest verification, restore, checksum validation, and revision-checked active-generation publication.
4. Manual backup and explicit check/confirm-restore UI; restore creates a private local backup before replacing local data.
5. Restore payload size is checked before concatenating and Base64-decoding cloud chunks; encoded snapshots are also bounded before upload.

Still required:
1. First-link decision UI (upload, restore, keep local, and merge where safe).
2. History merge/deduplication and explicit scalar/text conflict resolution.
3. Durable WorkManager retry and visible sync status/error handling.
4. Safe stale-generation cleanup after confirming the active pointer.
5. Physical-device tests for Email/Password and Google Sign-In, Firebase upload/restore, interrupted uploads, two-device revision conflicts, and Android API 23 compatibility.

The latest GitHub Actions run for commit `38a617a` passed Firestore rules tests, Kotlin unit tests, and debug APK assembly. This is CI evidence only; it is not a substitute for live Firebase or physical-device end-to-end verification.

Manual cloud backup and user-confirmed restore are implemented in code. Google Sign-In credential exchange is also implemented in code. These flows must not be described as production-verified until actual Firebase account sign-in, upload/restore, concurrent-device conflict, interrupted upload, and API 23 device tests have passed. The first-link merge/keep-local decision UI, automatic offline retry, and generation garbage collection remain unimplemented.
