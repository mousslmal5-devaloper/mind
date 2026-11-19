# MIND Cloud Sync Design

Status: design approved for implementation planning; cloud sync is not implemented by this document.

## Current verified baseline

- Authentication uses Firebase Email/Password in `AuthScreen.kt`.
- The app currently stores its complete working state locally in Preferences DataStore (`mind_local_data`).
- `MainActivity` restores the local state on startup and writes it back after state changes.
- The app currently returns to the dashboard after sign-in; it does not read from or write to Firestore.
- Firestore dependency is already declared (Firebase BOM 32.8.1, Firestore 24.11.1).
- Current Firestore rules allow access to the user's root `/users/{uid}` document only. Firestore rules do not automatically inherit into subcollections; rules must be extended before any subcollection sync is enabled.
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

When a verified account first connects:
- If no cloud snapshot exists and local data exists, offer a clear upload choice.
- If cloud data exists and local data is non-empty, present explicit choices: merge compatible histories, restore cloud data, or keep this device's data. Do not choose destructive replacement automatically.
- If local data is empty and a cloud snapshot exists, restore only after validating the manifest and all chunks.
- Validate schema version and record bounds before writing restored data into DataStore.
- Preserve a local backup/export path before any destructive restore or replacement.

## Conflict handling

Use a monotonically increasing per-user revision and transactionally compare the expected revision before publishing. Append-only histories should merge by stable record identity and timestamp, then deduplicate. For scalar settings and editable text, detect concurrent revisions and prefer an explicit conflict decision rather than silently applying last-write-wins across devices. A retry must be idempotent.

## Offline execution

Keep DataStore as the source of truth for immediate UI updates. Queue pending sync work and retry when authenticated connectivity returns; use WorkManager with network constraints for durable background retries. Do not block app startup or feature interactions on Firestore availability.

## Security rules required before implementation

The current root-only rule is insufficient for the proposed nested documents. Before enabling sync, add an explicit recursive match under `/users/{userId}/sync/{document=**}` that permits read/write only when `request.auth != null && request.auth.uid == userId`. Add validation for document shape, schema version, and bounded payload size where practical. Test that unauthenticated users and a different UID cannot read or write any sync document.

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

## Implementation sequence

1. Add pure Kotlin snapshot/chunk serialization and validation with unit tests; no network writes yet.
2. Extend and emulator-test Firestore security rules for UID isolation.
3. Add repository methods for upload, manifest verification, restore, and revision-checked publication.
4. Add explicit first-link/restore choice UI and preserve local data.
5. Add durable WorkManager retry and sync status/error UI.
6. Run unit tests, assembleDebug, and connected device tests where supported before claiming sync is operational.

Until these steps are implemented and verified, the app must continue to describe its data as local-only.
