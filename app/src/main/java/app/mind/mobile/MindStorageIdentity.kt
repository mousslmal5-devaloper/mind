package app.mind.mobile

import java.security.MessageDigest

/**
 * Stable, privacy-preserving identity helpers for the per-account local store.
 *
 * Firebase UIDs are never written into filenames. The full SHA-256 digest avoids
 * collisions caused by truncating or sanitizing arbitrary UID characters.
 */
internal object MindStorageIdentity {
    fun accountKey(uid: String): String {
        require(uid.isNotBlank() && uid == uid.trim()) { "A non-blank canonical user ID is required" }
        return MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    fun dataStoreFileName(uid: String): String {
        require(uid.isNotBlank() && uid == uid.trim()) { "A non-blank canonical user ID is required" }
        return "mind_user_${accountKey(uid)}.preferences_pb"
    }

    fun legacyImportDecision(
        requestedUid: String,
        legacyDataExists: Boolean,
        recordedOwnerUid: String?,
        alreadyImported: Boolean
    ): LegacyImportDecision {
        require(requestedUid.isNotBlank() && requestedUid == requestedUid.trim()) {
            "A non-blank canonical user ID is required"
        }
        if (!legacyDataExists) return LegacyImportDecision.NoLegacyData
        if (recordedOwnerUid != null && recordedOwnerUid != requestedUid) {
            return LegacyImportDecision.OwnedByAnotherUser
        }
        if (recordedOwnerUid == requestedUid) {
            return LegacyImportDecision.AlreadyImported
        }
        if (alreadyImported) return LegacyImportDecision.InconsistentOwnership
        return LegacyImportDecision.AvailableForExplicitImport
    }
}

internal enum class LegacyImportDecision {
    NoLegacyData,
    AvailableForExplicitImport,
    AlreadyImported,
    OwnedByAnotherUser,
    InconsistentOwnership
}
