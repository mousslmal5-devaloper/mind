package app.mind.mobile

import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Firestore transport for a validated, versioned MIND snapshot.
 *
 * This class deliberately does not schedule work or make first-link decisions.
 * Callers must obtain explicit user consent before upload or restore.
 */
internal class FirestoreMindSyncRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    data class CloudSnapshotInfo(
        val revision: Long,
        val generation: String,
        val schemaVersion: Int
    )

    sealed interface RestoreResult {
        data object NoCloudSnapshot : RestoreResult
        data class Restored(val snapshot: MindCloudSnapshot, val info: CloudSnapshotInfo) : RestoreResult
    }

    suspend fun readSnapshotInfo(uid: String): CloudSnapshotInfo? {
        requireVerifiedOwner(uid)
        val metadata = metadataRef(uid).get().awaitTask()
        return if (metadata.exists()) parseSnapshotInfo(metadata) else null
    }

    private fun parseSnapshotInfo(metadata: DocumentSnapshot): CloudSnapshotInfo {
        val generation = metadata.getString(FIELD_ACTIVE_GENERATION)
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Cloud metadata has no active generation")
        val revision = metadata.getLong(FIELD_REVISION)
            ?: throw IllegalStateException("Cloud metadata has no revision")
        val schemaVersion = (metadata.getLong(FIELD_SCHEMA_VERSION) ?: -1L).toInt()
        require(revision >= 0L) { "Cloud revision is invalid" }
        require(schemaVersion == MindCloudSnapshot.CURRENT_SCHEMA_VERSION) {
            "Cloud snapshot schema is not supported"
        }
        return CloudSnapshotInfo(revision, generation, schemaVersion)
    }

    /**
     * Uploads chunks first, then publishes the active generation using a revision
     * checked transaction. A failed upload never changes the active cloud pointer.
     */
    suspend fun uploadSnapshot(
        uid: String,
        snapshot: MindCloudSnapshot,
        expectedCloudRevision: Long
    ): CloudSnapshotInfo {
        requireVerifiedOwner(uid)
        require(expectedCloudRevision >= 0L) { "Expected revision cannot be negative" }
        CloudSyncCodec.validate(snapshot)
        require(snapshot.revision == expectedCloudRevision + 1L) {
            "Snapshot revision must be the next cloud revision"
        }

        val json = CloudSyncCodec.encode(snapshot)
        val encoded = android.util.Base64.encodeToString(
            json.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        val chunks = encoded.chunked(CHUNK_CHAR_COUNT)
        require(chunks.isNotEmpty() && chunks.size <= MAX_CHUNKS) {
            "Snapshot exceeds the supported cloud size"
        }

        val generation = UUID.randomUUID().toString()
        val generationRef = snapshotsRef(uid).document(generation)
        val chunkCollection = generationRef.collection(COLLECTION_CHUNKS)
        val digest = sha256(json)
        val generationData = mapOf(
            FIELD_SCHEMA_VERSION to snapshot.schemaVersion,
            FIELD_REVISION to snapshot.revision,
            FIELD_CHUNK_COUNT to chunks.size,
            FIELD_SHA256 to digest,
            FIELD_ENCODING to ENCODING_BASE64_UTF8
        )

        // Firestore batches are limited to 500 writes. Keep a safety margin.
        chunks.chunked(MAX_BATCH_WRITES).forEachIndexed { batchIndex, batchChunks ->
            val batch = firestore.batch()
            batchChunks.forEachIndexed { localIndex, payload ->
                val index = batchIndex * MAX_BATCH_WRITES + localIndex
                batch.set(
                    chunkCollection.document(index.toString()),
                    mapOf(FIELD_INDEX to index, FIELD_PAYLOAD to payload)
                )
            }
            batch.commit().awaitTask()
        }
        generationRef.set(generationData).awaitTask()

        val metadataRef = metadataRef(uid)
        firestore.runTransaction { transaction ->
            val current = transaction.get(metadataRef)
            val currentRevision = if (current.exists()) {
                current.getLong(FIELD_REVISION)
                    ?: throw IllegalStateException("Cloud metadata revision is malformed")
            } else {
                0L
            }
            check(currentRevision == expectedCloudRevision) {
                "Cloud data changed on another device. Refresh before uploading."
            }
            transaction.set(
                metadataRef,
                mapOf(
                    FIELD_ACTIVE_GENERATION to generation,
                    FIELD_REVISION to snapshot.revision,
                    FIELD_SCHEMA_VERSION to snapshot.schemaVersion,
                    FIELD_CHUNK_COUNT to chunks.size,
                    FIELD_SHA256 to digest,
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            snapshot.revision
        }.awaitTask()

        return CloudSnapshotInfo(snapshot.revision, generation, snapshot.schemaVersion)
    }

    suspend fun restoreSnapshot(uid: String): RestoreResult {
        requireVerifiedOwner(uid)
        // Read the active pointer and its manifest fields from one consistent document snapshot.
        val metadata = metadataRef(uid).get().awaitTask()
        if (!metadata.exists()) return RestoreResult.NoCloudSnapshot
        val info = parseSnapshotInfo(metadata)
        val expectedCount = metadata.getLong(FIELD_CHUNK_COUNT)?.toInt()
            ?: throw IllegalStateException("Cloud chunk manifest is missing")
        val expectedDigest = metadata.getString(FIELD_SHA256)
            ?: throw IllegalStateException("Cloud checksum is missing")
        require(expectedCount in 1..MAX_CHUNKS) { "Cloud chunk count is invalid" }

        val generationRef = snapshotsRef(uid).document(info.generation)
        val generation = generationRef.get().awaitTask()
        require(generation.exists()) { "Active cloud generation is missing" }
        require(generation.getLong(FIELD_REVISION) == info.revision) {
            "Cloud generation revision does not match metadata"
        }
        require(generation.getLong(FIELD_SCHEMA_VERSION)?.toInt() == info.schemaVersion) {
            "Cloud generation schema does not match metadata"
        }
        require(generation.getLong(FIELD_CHUNK_COUNT)?.toInt() == expectedCount) {
            "Cloud generation manifest does not match metadata"
        }
        require(generation.getString(FIELD_SHA256) == expectedDigest) {
            "Cloud generation checksum does not match metadata"
        }
        require(generation.getString(FIELD_ENCODING) == ENCODING_BASE64_UTF8) {
            "Cloud generation encoding is not supported"
        }

        val documents = generationRef.collection(COLLECTION_CHUNKS)
            .orderBy(FIELD_INDEX)
            .get()
            .awaitTask()
            .documents
        require(documents.size == expectedCount) { "Cloud snapshot is incomplete" }

        val encoded = buildString {
            documents.forEachIndexed { expectedIndex, document ->
                val actualIndex = document.getLong(FIELD_INDEX)?.toInt()
                require(actualIndex == expectedIndex) { "Cloud snapshot chunk sequence is invalid" }
                append(document.getString(FIELD_PAYLOAD)
                    ?: throw IllegalStateException("Cloud snapshot contains an empty chunk"))
            }
        }
        val json = try {
            String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } catch (error: IllegalArgumentException) {
            throw IllegalStateException("Cloud snapshot encoding is invalid", error)
        }
        require(sha256(json) == expectedDigest) { "Cloud snapshot checksum validation failed" }
        val snapshot = CloudSyncCodec.decode(json)
        require(snapshot.revision == info.revision) { "Cloud snapshot revision is inconsistent" }
        return RestoreResult.Restored(snapshot, info)
    }

    private fun requireVerifiedOwner(uid: String) {
        require(uid.isNotBlank() && uid == uid.trim()) { "A valid account UID is required" }
        val user = auth.currentUser
        check(user != null && user.uid == uid && user.isEmailVerified) {
            "A verified session for this account is required"
        }
    }

    private fun metadataRef(uid: String) =
        firestore.collection(COLLECTION_USERS).document(uid)
            .collection(COLLECTION_SYNC).document(DOCUMENT_META)

    private fun snapshotsRef(uid: String) = metadataRef(uid).collection(COLLECTION_SNAPSHOTS)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private suspend fun <T> Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { continuation ->
            val listener = OnCompleteListener<T> { task ->
                if (!continuation.isActive) return@OnCompleteListener
                if (task.isSuccessful) {
                    continuation.resume(task.result)
                } else {
                    continuation.resumeWithException(
                        task.exception ?: IllegalStateException("Firebase operation failed")
                    )
                }
            }
            addOnCompleteListener(listener)
            continuation.invokeOnCancellation {
                removeOnCompleteListener(listener)
            }
        }

    private companion object {
        const val COLLECTION_USERS = "users"
        const val COLLECTION_SYNC = "sync"
        const val DOCUMENT_META = "meta"
        const val COLLECTION_SNAPSHOTS = "snapshots"
        const val COLLECTION_CHUNKS = "chunks"
        const val FIELD_ACTIVE_GENERATION = "activeGeneration"
        const val FIELD_REVISION = "revision"
        const val FIELD_SCHEMA_VERSION = "schemaVersion"
        const val FIELD_CHUNK_COUNT = "chunkCount"
        const val FIELD_SHA256 = "sha256"
        const val FIELD_ENCODING = "encoding"
        const val FIELD_INDEX = "index"
        const val FIELD_PAYLOAD = "payload"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val ENCODING_BASE64_UTF8 = "base64-utf8"
        const val CHUNK_CHAR_COUNT = 180 * 1024
        const val MAX_BATCH_WRITES = 450
        const val MAX_CHUNKS = 256
    }
}
