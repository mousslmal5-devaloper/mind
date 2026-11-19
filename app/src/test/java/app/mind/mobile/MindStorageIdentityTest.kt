package app.mind.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MindStorageIdentityTest {
    @Test
    fun sameUidAlwaysProducesSameFilename() {
        assertEquals(
            MindStorageIdentity.dataStoreFileName("firebase-user-123"),
            MindStorageIdentity.dataStoreFileName("firebase-user-123")
        )
    }

    @Test
    fun differentUidsProduceDifferentFilenames() {
        assertNotEquals(
            MindStorageIdentity.dataStoreFileName("user-one"),
            MindStorageIdentity.dataStoreFileName("user-two")
        )
    }

    @Test
    fun filenameDoesNotExposeUid() {
        val uid = "private-firebase-uid"
        val fileName = MindStorageIdentity.dataStoreFileName(uid)
        assertTrue(fileName.startsWith("mind_user_"))
        assertTrue(fileName.endsWith(".preferences_pb"))
        assertTrue(!fileName.contains(uid))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankUidIsRejected() {
        MindStorageIdentity.dataStoreFileName("  ")
    }

    @Test
    fun legacyDataRequiresAnExplicitImportWhenUnclaimed() {
        assertEquals(
            LegacyImportDecision.AvailableForExplicitImport,
            MindStorageIdentity.legacyImportDecision("uid-a", true, null, false)
        )
    }

    @Test
    fun legacyDataCannotBeClaimedByASecondAccount() {
        assertEquals(
            LegacyImportDecision.OwnedByAnotherUser,
            MindStorageIdentity.legacyImportDecision("uid-b", true, "uid-a", true)
        )
    }

    @Test
    fun sameAccountCannotImportLegacyDataTwice() {
        assertEquals(
            LegacyImportDecision.AlreadyImported,
            MindStorageIdentity.legacyImportDecision("uid-a", true, "uid-a", true)
        )
    }

    @Test
    fun missingLegacyDataNeverOffersImport() {
        assertEquals(
            LegacyImportDecision.NoLegacyData,
            MindStorageIdentity.legacyImportDecision("uid-a", false, null, false)
        )
    }

    @Test
    fun inconsistentImportMarkerFailsClosed() {
        assertEquals(
            LegacyImportDecision.InconsistentOwnership,
            MindStorageIdentity.legacyImportDecision("uid-a", true, null, true)
        )
    }
}
