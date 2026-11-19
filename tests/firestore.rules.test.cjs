const fs = require("node:fs");
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} = require("@firebase/rules-unit-testing");
const { doc, getDoc, setDoc } = require("firebase/firestore");

let testEnv;

const checksum = "0".repeat(64);
const validMetadata = {
  activeGeneration: "generation-1",
  revision: 1,
  schemaVersion: 1,
  chunkCount: 1,
  sha256: checksum,
  updatedAt: new Date()
};
const validManifest = {
  schemaVersion: 1,
  revision: 1,
  chunkCount: 1,
  sha256: checksum,
  encoding: "base64-utf8"
};

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: "demo-mind-rules",
    firestore: {
      rules: fs.readFileSync("firestore.rules", "utf8")
    }
  });
});

after(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

describe("MIND Firestore sync ownership and schema rules", () => {
  it("allows the owner to read and publish valid sync metadata", async () => {
    const db = testEnv.authenticatedContext("user-a").firestore();
    const reference = doc(db, "users/user-a/sync/meta");

    await assertSucceeds(setDoc(reference, validMetadata));
    await assertSucceeds(getDoc(reference));
  });

  it("allows the owner to publish a valid generation manifest and chunk", async () => {
    const db = testEnv.authenticatedContext("user-a").firestore();
    const manifest = doc(
      db,
      "users/user-a/sync/meta/snapshots/generation-1"
    );
    const chunk = doc(
      db,
      "users/user-a/sync/meta/snapshots/generation-1/chunks/0"
    );

    await assertSucceeds(setDoc(manifest, validManifest));
    await assertSucceeds(setDoc(chunk, { index: 0, payload: "eyJ2ZXJzaW9uIjoxfQ==" }));
    await assertSucceeds(getDoc(chunk));
  });

  it("denies unauthenticated reads and writes", async () => {
    const db = testEnv.unauthenticatedContext().firestore();
    const reference = doc(db, "users/user-a/sync/meta");

    await assertFails(getDoc(reference));
    await assertFails(setDoc(reference, validMetadata));
  });

  it("denies another account from reading or writing sync metadata", async () => {
    const ownerDb = testEnv.authenticatedContext("user-a").firestore();
    const otherDb = testEnv.authenticatedContext("user-b").firestore();
    const ownerReference = doc(ownerDb, "users/user-a/sync/meta");
    const otherReference = doc(otherDb, "users/user-a/sync/meta");

    await assertSucceeds(setDoc(ownerReference, validMetadata));
    await assertFails(getDoc(otherReference));
    await assertFails(setDoc(otherReference, validMetadata));
  });

  it("denies another account from writing nested generation chunks", async () => {
    const db = testEnv.authenticatedContext("user-b").firestore();
    const reference = doc(
      db,
      "users/user-a/sync/meta/snapshots/generation-1/chunks/0"
    );

    await assertFails(setDoc(reference, { index: 0, payload: "stolen" }));
  });

  it("rejects malformed metadata and unsupported schema versions", async () => {
    const db = testEnv.authenticatedContext("user-a").firestore();
    const metadata = doc(db, "users/user-a/sync/meta");

    await assertFails(setDoc(metadata, { ...validMetadata, revision: -1 }));
    await assertFails(setDoc(metadata, { ...validMetadata, schemaVersion: 99 }));
    await assertFails(setDoc(metadata, { ...validMetadata, sha256: "not-a-checksum" }));
    await assertFails(setDoc(metadata, { ...validMetadata, unexpected: true }));
  });

  it("rejects oversized or malformed chunk documents", async () => {
    const db = testEnv.authenticatedContext("user-a").firestore();
    const oversized = doc(
      db,
      "users/user-a/sync/meta/snapshots/generation-1/chunks/0"
    );
    const malformed = doc(
      db,
      "users/user-a/sync/meta/snapshots/generation-1/chunks/1"
    );

    await assertFails(setDoc(oversized, {
      index: 0,
      payload: "x".repeat(184321)
    }));
    await assertFails(setDoc(malformed, { index: -1, payload: "" }));
  });
});
