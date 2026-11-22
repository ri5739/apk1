const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("products").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").set({
      userId: ALICE_UID,
      name: "Watch",
      price: 1200,
      currency: "৳",
      category: "Accessories",
      description: "Smart watch",
      inStock: true,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner: valid BusinessProfile create, update, and shadow-field rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      businessName: "Dhaka Gadget Hub",
      ownerName: "Abir",
      greetingStyle: "FRIENDLY",
      supportPolicy: "Delivery 60 BDT inside Dhaka.",
      casualChatEnabled: true,
      autoReplyEnabled: true,
      replyToGroups: false,
      enabledApps: ["com.whatsapp.w4b", "com.facebook.orca"],
      languagePreference: "BN",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      autoReplyEnabled: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost/shadow field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Authenticated owner: valid Product & ReplyLog CRUD without @DocumentId id field", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").set({
      userId: ALICE_UID,
      name: "Classic Leather Wallet",
      price: 850.0,
      currency: "৳",
      category: "Fashion",
      description: "Genuine leather bi-fold wallet",
      inStock: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").update({
      price: 790.0,
      inStock: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject extra `id` field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_2").set({
      id: "prod_2",
      userId: ALICE_UID,
      name: "Invalid",
      price: 100,
      currency: "৳",
      category: "Misc",
      description: "",
      inStock: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("replyLogs").doc("log_1").set({
      userId: ALICE_UID,
      senderName: "Rahim",
      platform: "WHATSAPP_BUSINESS",
      incomingMessage: "Wallet er dam koto?",
      aiReply: "ভাইয়া, আমাদের Classic Leather Wallet এর দাম ৮৫০ টাকা।",
      intent: "PRODUCT_INQUIRY",
      repliedAutomatically: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("replyLogs").get()
  );
});

test("Cross-user isolation: Bob cannot read or modify Alice's profile, products, or logs", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const created = new Date(Date.now() - 10000);
    await db.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").set({
      userId: ALICE_UID,
      name: "Alice Product",
      price: 500,
      currency: "৳",
      category: "General",
      description: "Private item",
      inStock: true,
      createdAt: created,
      updatedAt: created,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("products").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").update({
      name: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").delete()
  );
});
