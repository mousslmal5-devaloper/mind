const fs = require("node:fs");
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} = require("@firebase/rules-unit-testing");
const { doc, getDoc, setDoc } = require("firebase/firestore");

let testEnv;

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

describe("MIND Firestore sync ownership rules", () => {
  it("allows an authenticated user to read and write their sync metadata", async () => {
    const db = testEnv.authenticatedContext("user-a").firestore();
    const reference = doc(db, "users/user-a/sync/meta");

    await assertSucceeds(setDoc(reference, { schemaVersion: 1, revision: 1 }));
    await assertSucceeds(getDoc(reference));
  });

  it("allows the owner to write generation chunks", async () => {
    const db = testEnv.authenticatedContext("user-a").firestore();
    const reference = doc(
      db,
      "users/user-a/sync/meta/snapshots/generation-1/chunks/water_0"
    );

    await assertSucceeds(setDoc(reference, { records: ["sample"] }));
    await assertSucceeds(getDoc(reference));
  });

  it("denies unauthenticated reads and writes", async () => {
    const db = testEnv.unauthenticatedContext().firestore();
    const reference = doc(db, "users/user-a/sync/meta");

    await assertFails(getDoc(reference));
    await assertFails(setDoc(reference, { revision: 1 }));
  });

  it("denies another account from reading or writing sync metadata", async () => {
    const ownerDb = testEnv.authenticatedContext("user-a").firestore();
    const otherDb = testEnv.authenticatedContext("user-b").firestore();
    const ownerReference = doc(ownerDb, "users/user-a/sync/meta");
    const otherReference = doc(otherDb, "users/user-a/sync/meta");

    await assertSucceeds(setDoc(ownerReference, { revision: 1 }));
    await assertFails(getDoc(otherReference));
    await assertFails(setDoc(otherReference, { revision: 2 }));
  });

  it("denies another account from writing nested generation chunks", async () => {
    const db = testEnv.authenticatedContext("user-b").firestore();
    const reference = doc(
      db,
      "users/user-a/sync/meta/snapshots/generation-1/chunks/water_0"
    );

    await assertFails(setDoc(reference, { records: ["stolen"] }));
  });
});
