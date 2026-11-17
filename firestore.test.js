const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DIRECTOR_A = "director_a_uid";
const DIRECTOR_B = "director_b_uid";
const TEACHER_A = "teacher_a_uid";
const ACCOUNTANT_A = "accountant_a_uid";
const PARENT_A = "parent_a_uid";
const SUPER_ADMIN_UID = "super_admin_uid";
const SUPER_ADMIN_EMAIL = "yanndirane@gmail.com";

const SCHOOL_A = "school_yaounde_1";
const SCHOOL_B = "school_douala_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function tsNow() {
  return new Date(Date.now() - 2000);
}

function schoolPayload(ownerUid, name) {
  const now = tsNow();
  return {
    ownerUid,
    name,
    slogan: "Votre établissement, mieux organisé.",
    schoolType: "Lycée / Collège",
    city: "Yaoundé",
    country: "Cameroun",
    academicYear: "2026-2027",
    campuses: ["Campus Principal"],
    planCode: "STARTER",
    subscriptionStatus: "ESSAI",
    monthlyPriceFcfa: 10000,
    trialEndsAtMillis: Date.now() + 14 * 86400000,
    nextBillingAtMillis: Date.now() + 14 * 86400000,
    gracePeriodDays: 7,
    graceEndsAtMillis: 0,
    memberUids: [ownerUid],
    momoConfigured: false,
    orangeConfigured: false,
    whatsappConfigured: false,
    smsConfigured: false,
    retentionMonths: 36,
    createdAt: now,
    updatedAt: now,
  };
}

function memberPayload(uid, schoolId, email, role, readerUids, linkedStudentIds = []) {
  const now = tsNow();
  return {
    uid,
    schoolId,
    email,
    displayName: `User ${role}`,
    role,
    linkedStudentIds,
    assignedClassIds: [],
    active: true,
    readerUids,
    createdAt: now,
    updatedAt: now,
  };
}

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

test("Unauthenticated users cannot read or write any tenant data", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("schools").get());
  await assertFails(unauthDb.collection("schools").doc(SCHOOL_A).set(schoolPayload(DIRECTOR_A, "Lycée Test")));
  await assertFails(unauthDb.collection("saas_plans").where("active", "==", true).get());
});

test("Email spoofing with unverified email cannot access Super Admin privileges", async () => {
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: SUPER_ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  await assertFails(
    spoofDb.collection("saas_plans").doc("STARTER").set({
      code: "STARTER",
      name: "Starter Hacked",
      monthlyPriceFcfa: 0,
      trialDays: 14,
      gracePeriodDays: 7,
      maxStudents: 500,
      features: ["Test"],
      active: true,
      updatedAt: tsNow(),
    })
  );
});

test("Super Admin with verified email can manage SaaS plans", async () => {
  const adminDb = testEnv.authenticatedContext(SUPER_ADMIN_UID, {
    email: SUPER_ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    adminDb.collection("saas_plans").doc("STARTER").set({
      code: "STARTER",
      name: "Starter",
      monthlyPriceFcfa: 10000,
      trialDays: 14,
      gracePeriodDays: 7,
      maxStudents: 300,
      features: ["Gestion des étudiants", "Gestion des classes"],
      active: true,
      updatedAt: tsNow(),
    })
  );

  await assertSucceeds(
    adminDb.collection("saas_plans").where("active", "==", true).get()
  );
});

test("School creation 5-doc batch creates school, member, school_link, user, and audit_log atomically", async () => {
  const dirADb = testEnv.authenticatedContext(DIRECTOR_A, {
    email: "dira@school.cm",
    email_verified: true,
  }).firestore();
  const now = tsNow();

  const batch = dirADb.batch();
  batch.set(dirADb.collection("schools").doc(SCHOOL_A), schoolPayload(DIRECTOR_A, "Collège Vogt"));
  batch.set(
    dirADb.collection("schools").doc(SCHOOL_A).collection("members").doc(DIRECTOR_A),
    memberPayload(DIRECTOR_A, SCHOOL_A, "dira@school.cm", "DIRECTEUR", [DIRECTOR_A])
  );
  batch.set(
    dirADb.collection("users").doc(DIRECTOR_A).collection("school_links").doc(SCHOOL_A),
    {
      userId: DIRECTOR_A,
      schoolId: SCHOOL_A,
      schoolName: "Collège Vogt",
      role: "DIRECTEUR",
      createdAt: now,
      updatedAt: now,
    }
  );
  batch.set(
    dirADb.collection("users").doc(DIRECTOR_A),
    {
      uid: DIRECTOR_A,
      email: "dira@school.cm",
      displayName: "Directeur Vogt",
      activeSchoolId: SCHOOL_A,
      createdAt: now,
      updatedAt: now,
    }
  );
  batch.set(
    dirADb.collection("schools").doc(SCHOOL_A).collection("audit_logs").doc("log_init_1"),
    {
      schoolId: SCHOOL_A,
      actorUid: DIRECTOR_A,
      actorName: "Directeur Vogt",
      actorRole: "DIRECTEUR",
      action: "CREATION_ETABLISSEMENT",
      entityType: "School",
      entityId: SCHOOL_A,
      details: "Création de l'établissement Collège Vogt",
      readerUids: [DIRECTOR_A],
      createdAt: now,
      updatedAt: now,
    }
  );
  await assertSucceeds(batch.commit());

  // Super Admin creating a school also succeeds with the 5-doc batch
  const saDb = testEnv.authenticatedContext(SUPER_ADMIN_UID, {
    email: SUPER_ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const saBatch = saDb.batch();
  saBatch.set(saDb.collection("schools").doc(SCHOOL_B), schoolPayload(SUPER_ADMIN_UID, "Lycée Pilote"));
  saBatch.set(
    saDb.collection("schools").doc(SCHOOL_B).collection("members").doc(SUPER_ADMIN_UID),
    memberPayload(SUPER_ADMIN_UID, SCHOOL_B, SUPER_ADMIN_EMAIL, "DIRECTEUR", [SUPER_ADMIN_UID])
  );
  saBatch.set(
    saDb.collection("users").doc(SUPER_ADMIN_UID).collection("school_links").doc(SCHOOL_B),
    {
      userId: SUPER_ADMIN_UID,
      schoolId: SCHOOL_B,
      schoolName: "Lycée Pilote",
      role: "DIRECTEUR",
      createdAt: now,
      updatedAt: now,
    }
  );
  saBatch.set(
    saDb.collection("users").doc(SUPER_ADMIN_UID),
    {
      uid: SUPER_ADMIN_UID,
      email: SUPER_ADMIN_EMAIL,
      displayName: "Super Admin",
      activeSchoolId: SCHOOL_B,
      createdAt: now,
      updatedAt: now,
    }
  );
  saBatch.set(
    saDb.collection("schools").doc(SCHOOL_B).collection("audit_logs").doc("log_init_sa"),
    {
      schoolId: SCHOOL_B,
      actorUid: SUPER_ADMIN_UID,
      actorName: "Super Admin",
      actorRole: "DIRECTEUR",
      action: "CREATION_ETABLISSEMENT",
      entityType: "School",
      entityId: SCHOOL_B,
      details: "Création de l'établissement Lycée Pilote",
      readerUids: [SUPER_ADMIN_UID],
      createdAt: now,
      updatedAt: now,
    }
  );
  await assertSucceeds(saBatch.commit());

  // Shadow update / ghost field must fail
  const ghostPayload = { ...schoolPayload(DIRECTOR_A, "Collège Shadow"), isVerified: true };
  await assertFails(dirADb.collection("schools").doc("school_shadow").set(ghostPayload));

  // Director cannot bypass Super Admin to self-activate subscriptionStatus
  await assertFails(
    dirADb.collection("schools").doc(SCHOOL_A).update({
      subscriptionStatus: "ACTIF",
      updatedAt: tsNow(),
    })
  );
});

test("Strict multi-tenant isolation and RBAC enforcement across schools", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("schools").doc(SCHOOL_A).set({
      ...schoolPayload(DIRECTOR_A, "Lycée Yaoundé"),
      memberUids: [DIRECTOR_A, TEACHER_A, ACCOUNTANT_A, PARENT_A],
    });
    await db.collection("schools").doc(SCHOOL_A).collection("members").doc(DIRECTOR_A).set(
      memberPayload(DIRECTOR_A, SCHOOL_A, "dira@cm", "DIRECTEUR", [DIRECTOR_A, TEACHER_A, ACCOUNTANT_A])
    );
    await db.collection("schools").doc(SCHOOL_A).collection("members").doc(TEACHER_A).set(
      memberPayload(TEACHER_A, SCHOOL_A, "profa@cm", "ENSEIGNANT", [DIRECTOR_A, TEACHER_A])
    );
    await db.collection("schools").doc(SCHOOL_A).collection("members").doc(ACCOUNTANT_A).set(
      memberPayload(ACCOUNTANT_A, SCHOOL_A, "compta@cm", "COMPTABLE", [DIRECTOR_A, ACCOUNTANT_A])
    );
    await db.collection("schools").doc(SCHOOL_A).collection("members").doc(PARENT_A).set(
      memberPayload(PARENT_A, SCHOOL_A, "parenta@cm", "PARENT", [DIRECTOR_A, PARENT_A], ["stu_1"])
    );

    await db.collection("schools").doc(SCHOOL_B).set(schoolPayload(DIRECTOR_B, "Collège Douala"));
    await db.collection("schools").doc(SCHOOL_B).collection("members").doc(DIRECTOR_B).set(
      memberPayload(DIRECTOR_B, SCHOOL_B, "dirb@cm", "DIRECTEUR", [DIRECTOR_B])
    );
    await db.collection("schools").doc(SCHOOL_B).collection("students").doc("stu_b1").set({
      schoolId: SCHOOL_B,
      matricule: "SM-2026-0001",
      firstName: "Jean",
      lastName: "Mbarga",
      gender: "M",
      birthDate: "2012-05-14",
      classId: "cls_b1",
      className: "6ème A",
      level: "6ème",
      academicYear: "2026-2027",
      campus: "Campus Principal",
      status: "INSCRIT",
      guardianName: "Paul Mbarga",
      guardianPhone: "+237690000000",
      tuitionTotalFcfa: 150000,
      historyNotes: ["Inscription initiale"],
      readerUids: [DIRECTOR_B],
      createdAt: tsNow(),
      updatedAt: tsNow(),
    });
  });

  const dirADb = testEnv.authenticatedContext(DIRECTOR_A, { email: "dira@cm", email_verified: true }).firestore();
  const teacherADb = testEnv.authenticatedContext(TEACHER_A, { email: "profa@cm", email_verified: true }).firestore();
  const accountantADb = testEnv.authenticatedContext(ACCOUNTANT_A, { email: "compta@cm", email_verified: true }).firestore();
  const parentADb = testEnv.authenticatedContext(PARENT_A, { email: "parenta@cm", email_verified: true }).firestore();

  // Cross-tenant isolation: Director A cannot read or list School B's students
  await assertFails(dirADb.collection("schools").doc(SCHOOL_B).collection("students").doc("stu_b1").get());
  await assertFails(dirADb.collection("schools").doc(SCHOOL_B).collection("students").get());
  await assertFails(
    dirADb.collection("schools").doc(SCHOOL_B).collection("students").where("readerUids", "array-contains", DIRECTOR_B).get()
  );
  const crossTenantEmpty = await assertSucceeds(
    dirADb.collection("schools").doc(SCHOOL_B).collection("students").where("readerUids", "array-contains", DIRECTOR_A).get()
  );
  if (!crossTenantEmpty.empty) {
    throw new Error("Cross-tenant leak detected!");
  }

  // Director A can create a student in School A
  const now = tsNow();
  await assertSucceeds(
    dirADb.collection("schools").doc(SCHOOL_A).collection("students").doc("stu_1").set({
      schoolId: SCHOOL_A,
      matricule: "SM-2026-0001",
      firstName: "Amina",
      lastName: "Ndi",
      gender: "F",
      birthDate: "2011-03-10",
      classId: "cls_1",
      className: "5ème A",
      level: "5ème",
      academicYear: "2026-2027",
      campus: "Campus Principal",
      status: "INSCRIT",
      guardianName: "Oumarou Ndi",
      guardianPhone: "+237677000111",
      tuitionTotalFcfa: 120000,
      historyNotes: ["Inscrit 2026-2027"],
      readerUids: [DIRECTOR_A, TEACHER_A, ACCOUNTANT_A, PARENT_A],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Filtered Kotlin query succeeds for all readers; unfiltered list succeeds only for Director/Admin (syncMemberReaderAccess) and fails for Teacher/Parent
  await assertSucceeds(
    dirADb.collection("schools").doc(SCHOOL_A).collection("students").where("readerUids", "array-contains", DIRECTOR_A).get()
  );
  await assertSucceeds(
    dirADb.collection("schools").doc(SCHOOL_A).collection("students").get()
  );
  await assertFails(
    teacherADb.collection("schools").doc(SCHOOL_A).collection("students").get()
  );
  await assertFails(
    parentADb.collection("schools").doc(SCHOOL_A).collection("students").get()
  );

  // Teacher can create grades, CANNOT create payments
  await assertSucceeds(
    teacherADb.collection("schools").doc(SCHOOL_A).collection("grades").doc("grd_1").set({
      schoolId: SCHOOL_A,
      studentId: "stu_1",
      studentName: "Amina Ndi",
      classId: "cls_1",
      className: "5ème A",
      subjectId: "sub_math",
      subjectName: "Mathématiques",
      coefficient: 4,
      term: "TRIMESTRE_1",
      evaluationType: "DEVOIR",
      score: 15.5,
      maxScore: 20,
      teacherName: "M. Kamga",
      readerUids: [DIRECTOR_A, TEACHER_A, PARENT_A],
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    teacherADb.collection("schools").doc(SCHOOL_A).collection("payments").doc("pay_1").set({
      schoolId: SCHOOL_A,
      receiptNumber: "REC-2026-001",
      studentId: "stu_1",
      studentName: "Amina Ndi",
      studentMatricule: "SM-2026-0001",
      classId: "cls_1",
      className: "5ème A",
      tranche: "TRANCHE_1",
      amountFcfa: 50000,
      method: "ESPECES",
      providerStatus: "CONFIRME",
      transactionRef: "ESP-001",
      recordedByUid: TEACHER_A,
      recordedByName: "Prof A",
      readerUids: [DIRECTOR_A, ACCOUNTANT_A, PARENT_A],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Accountant can record payment, cannot alter amountFcfa afterwards
  await assertSucceeds(
    accountantADb.collection("schools").doc(SCHOOL_A).collection("payments").doc("pay_1").set({
      schoolId: SCHOOL_A,
      receiptNumber: "REC-2026-001",
      studentId: "stu_1",
      studentName: "Amina Ndi",
      studentMatricule: "SM-2026-0001",
      classId: "cls_1",
      className: "5ème A",
      tranche: "TRANCHE_1",
      amountFcfa: 50000,
      method: "ESPECES",
      providerStatus: "CONFIRME",
      transactionRef: "ESP-001",
      recordedByUid: ACCOUNTANT_A,
      recordedByName: "Comptable A",
      readerUids: [DIRECTOR_A, ACCOUNTANT_A, PARENT_A],
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    accountantADb.collection("schools").doc(SCHOOL_A).collection("payments").doc("pay_1").update({
      amountFcfa: 90000,
      updatedAt: tsNow(),
    })
  );

  // Parent can read linked child's student, grade, payment; cannot write grades or payments
  await assertSucceeds(parentADb.collection("schools").doc(SCHOOL_A).collection("students").doc("stu_1").get());
  await assertSucceeds(
    parentADb.collection("schools").doc(SCHOOL_A).collection("grades").where("readerUids", "array-contains", PARENT_A).get()
  );
  await assertFails(
    parentADb.collection("schools").doc(SCHOOL_A).collection("grades").doc("grd_hack").set({
      schoolId: SCHOOL_A,
      studentId: "stu_1",
      studentName: "Amina Ndi",
      classId: "cls_1",
      className: "5ème A",
      subjectId: "sub_math",
      subjectName: "Mathématiques",
      coefficient: 4,
      term: "TRIMESTRE_1",
      evaluationType: "DEVOIR",
      score: 20,
      maxScore: 20,
      teacherName: "Parent",
      readerUids: [PARENT_A],
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("ADMIN_ETABLISSEMENT governs school tenant and invitations without bypassing Super Admin SaaS controls", async () => {
  const SCHOOL_ADMIN_A = "school_admin_a_uid";
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("schools").doc(SCHOOL_A).set({
      ...schoolPayload(DIRECTOR_A, "Institut Polyvalent Bafoussam"),
      memberUids: [DIRECTOR_A, SCHOOL_ADMIN_A],
    });
    await db.collection("schools").doc(SCHOOL_A).collection("members").doc(SCHOOL_ADMIN_A).set(
      memberPayload(SCHOOL_ADMIN_A, SCHOOL_A, "admin@bafoussam.cm", "ADMIN_ETABLISSEMENT", [DIRECTOR_A, SCHOOL_ADMIN_A])
    );
  });

  const adminSchoolDb = testEnv.authenticatedContext(SCHOOL_ADMIN_A, {
    email: "admin@bafoussam.cm",
    email_verified: true,
  }).firestore();

  const now = tsNow();
  // Can invite a secretary and update school integrations
  await assertSucceeds(
    adminSchoolDb.collection("invitations").doc("inv_sec_1").set({
      schoolId: SCHOOL_A,
      schoolName: "Institut Polyvalent Bafoussam",
      invitedEmail: "sec@bafoussam.cm",
      role: "SECRETAIRE",
      linkedStudentIds: [],
      assignedClassIds: [],
      status: "PENDING",
      invitedByUid: SCHOOL_ADMIN_A,
      readerUids: [DIRECTOR_A, SCHOOL_ADMIN_A],
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adminSchoolDb.collection("schools").doc(SCHOOL_A).update({
      momoConfigured: true,
      momoMerchantCode: "MOMO-237-99",
      updatedAt: tsNow(),
    })
  );

  // Cannot self-activate subscriptionStatus or modify platform saas_plans
  await assertFails(
    adminSchoolDb.collection("schools").doc(SCHOOL_A).update({
      subscriptionStatus: "ACTIF",
      updatedAt: tsNow(),
    })
  );
  await assertFails(
    adminSchoolDb.collection("saas_plans").doc("STARTER").set({
      code: "STARTER",
      name: "Starter Free",
      monthlyPriceFcfa: 0,
      trialDays: 30,
      gracePeriodDays: 10,
      maxStudents: 1000,
      features: ["Test"],
      active: true,
      updatedAt: tsNow(),
    })
  );
});

test("ELEVE role requires verified email to accept invitation and can only read linked student records", async () => {
  const ELEVE_A = "eleve_a_uid";
  const now = tsNow();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("schools").doc(SCHOOL_A).set({
      ...schoolPayload(DIRECTOR_A, "Lycée Général Leclerc"),
      memberUids: [DIRECTOR_A],
    });
    await db.collection("schools").doc(SCHOOL_A).collection("members").doc(DIRECTOR_A).set(
      memberPayload(DIRECTOR_A, SCHOOL_A, "dir@leclerc.cm", "DIRECTEUR", [DIRECTOR_A])
    );
    await db.collection("schools").doc(SCHOOL_A).collection("students").doc("stu_eleve_1").set({
      schoolId: SCHOOL_A,
      matricule: "LGL-2026-001",
      firstName: "Samuel",
      lastName: "Eto",
      gender: "M",
      birthDate: "2009-04-12",
      classId: "cls_1ere",
      className: "1ère C",
      level: "1ère",
      academicYear: "2026-2027",
      campus: "Campus Principal",
      status: "INSCRIT",
      guardianName: "Pierre Eto",
      guardianPhone: "+237699112233",
      tuitionTotalFcfa: 180000,
      historyNotes: ["Inscrit"],
      readerUids: [DIRECTOR_A, ELEVE_A],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("schools").doc(SCHOOL_A).collection("students").doc("stu_other").set({
      schoolId: SCHOOL_A,
      matricule: "LGL-2026-002",
      firstName: "Claire",
      lastName: "Foka",
      gender: "F",
      birthDate: "2009-08-22",
      classId: "cls_1ere",
      className: "1ère C",
      level: "1ère",
      academicYear: "2026-2027",
      campus: "Campus Principal",
      status: "INSCRIT",
      guardianName: "Jeanne Foka",
      guardianPhone: "+237677112233",
      tuitionTotalFcfa: 180000,
      historyNotes: ["Inscrit"],
      readerUids: [DIRECTOR_A],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("invitations").doc("inv_eleve_1").set({
      schoolId: SCHOOL_A,
      schoolName: "Lycée Général Leclerc",
      invitedEmail: "samuel.eto@student.cm",
      role: "ELEVE",
      linkedStudentIds: ["stu_eleve_1"],
      assignedClassIds: [],
      status: "PENDING",
      invitedByUid: DIRECTOR_A,
      readerUids: [DIRECTOR_A],
      createdAt: now,
      updatedAt: now,
    });
  });

  // Unverified email cannot read or accept invitation
  const unverifiedEleveDb = testEnv.authenticatedContext(ELEVE_A, {
    email: "samuel.eto@student.cm",
    email_verified: false,
  }).firestore();
  await assertFails(unverifiedEleveDb.collection("invitations").doc("inv_eleve_1").get());

  // Verified email can read invitation and accept via member creation
  const eleveDb = testEnv.authenticatedContext(ELEVE_A, {
    email: "samuel.eto@student.cm",
    email_verified: true,
  }).firestore();
  await assertSucceeds(eleveDb.collection("invitations").doc("inv_eleve_1").get());
  await assertSucceeds(
    eleveDb.collection("schools").doc(SCHOOL_A).collection("members").doc(ELEVE_A).set({
      uid: ELEVE_A,
      schoolId: SCHOOL_A,
      email: "samuel.eto@student.cm",
      displayName: "Samuel Eto",
      role: "ELEVE",
      linkedStudentIds: ["stu_eleve_1"],
      assignedClassIds: [],
      invitedViaId: "inv_eleve_1",
      active: true,
      readerUids: [DIRECTOR_A, ELEVE_A],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Can read own student record, CANNOT read another student's record or write grades
  await assertSucceeds(eleveDb.collection("schools").doc(SCHOOL_A).collection("students").doc("stu_eleve_1").get());
  await assertFails(eleveDb.collection("schools").doc(SCHOOL_A).collection("students").doc("stu_other").get());
  await assertFails(
    eleveDb.collection("schools").doc(SCHOOL_A).collection("grades").doc("grd_hack").set({
      schoolId: SCHOOL_A,
      studentId: "stu_eleve_1",
      studentName: "Samuel Eto",
      classId: "cls_1ere",
      className: "1ère C",
      subjectId: "sub_math",
      subjectName: "Maths",
      coefficient: 4,
      term: "TRIMESTRE_1",
      evaluationType: "DEVOIR",
      score: 20,
      maxScore: 20,
      teacherName: "Hack",
      readerUids: [ELEVE_A],
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Annual results respect confidentiality, scheduled publication, and director-only validation", async () => {
  const ELEVE_A = "eleve_res_a";
  const ELEVE_OTHER = "eleve_res_b";
  const now = tsNow();

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("schools").doc(SCHOOL_A).set({
      ...schoolPayload(DIRECTOR_A, "Université de Yaoundé I"),
      category: "UNIVERSITE_INSTITUT",
      schoolType: "Université / Institut",
      faculties: ["Faculté des Sciences"],
      departments: ["Informatique"],
      programs: ["Génie Logiciel"],
      passingAverage: 10.0,
      creditPassThreshold: 10.0,
      annualCreditsRequired: 60,
      lmdCompensation: true,
      termMode: "SEMESTRE",
      memberUids: [DIRECTOR_A, TEACHER_A, PARENT_A, ELEVE_A, ELEVE_OTHER],
    });
    await db.collection("schools").doc(SCHOOL_A).collection("members").doc(DIRECTOR_A).set(
      memberPayload(DIRECTOR_A, SCHOOL_A, "dir@uy1.cm", "DIRECTEUR", [DIRECTOR_A])
    );
    await db.collection("schools").doc(SCHOOL_A).collection("members").doc(TEACHER_A).set(
      memberPayload(TEACHER_A, SCHOOL_A, "prof@uy1.cm", "ENSEIGNANT", [DIRECTOR_A, TEACHER_A])
    );
    await db.collection("schools").doc(SCHOOL_A).collection("members").doc(PARENT_A).set(
      memberPayload(PARENT_A, SCHOOL_A, "parent@uy1.cm", "PARENT", [DIRECTOR_A, PARENT_A], ["stu_1"])
    );
    await db.collection("schools").doc(SCHOOL_A).collection("members").doc(ELEVE_A).set(
      memberPayload(ELEVE_A, SCHOOL_A, "eleve1@uy1.cm", "ELEVE", [DIRECTOR_A, ELEVE_A], ["stu_1"])
    );
    await db.collection("schools").doc(SCHOOL_A).collection("members").doc(ELEVE_OTHER).set(
      memberPayload(ELEVE_OTHER, SCHOOL_A, "eleve2@uy1.cm", "ELEVE", [DIRECTOR_A, ELEVE_OTHER], ["stu_2"])
    );
  });

  const dirDb = testEnv.authenticatedContext(DIRECTOR_A, { email: "dir@uy1.cm", email_verified: true }).firestore();
  const teacherDb = testEnv.authenticatedContext(TEACHER_A, { email: "prof@uy1.cm", email_verified: true }).firestore();
  const parentDb = testEnv.authenticatedContext(PARENT_A, { email: "parent@uy1.cm", email_verified: true }).firestore();
  const eleveDb = testEnv.authenticatedContext(ELEVE_A, { email: "eleve1@uy1.cm", email_verified: true }).firestore();
  const otherEleveDb = testEnv.authenticatedContext(ELEVE_OTHER, { email: "eleve2@uy1.cm", email_verified: true }).firestore();

  const baseResult = {
    schoolId: SCHOOL_A,
    schoolName: "Université de Yaoundé I",
    category: "UNIVERSITE_INSTITUT",
    studentId: "stu_1",
    matricule: "UY1-UN-2026-0001",
    studentFullName: "Atangana Paul",
    birthDate: "2004-05-12",
    classId: "cls_l3_gl",
    className: "L3 Génie Logiciel",
    level: "Licence 3",
    faculty: "Faculté des Sciences",
    department: "Informatique",
    program: "Génie Logiciel",
    academicYear: "2026-2027",
    periodLabel: "Résultats annuels 2026-2027",
    average: 13.8,
    semester1Average: 13.5,
    semester2Average: 14.1,
    annualAverage: 13.8,
    rank: 2,
    classSize: 45,
    creditsObtained: 60,
    creditsRequired: 60,
    decision: "VALIDE_LMD",
    mention: "Assez Bien",
    appreciation: "Parcours validé (60/60 crédits).",
    subjectLinesJson: "[]",
    status: "BROUILLON",
    scheduledPublishAtMillis: 0,
    publishedAtMillis: 0,
    revisionCount: 0,
    verificationHash: "abc123hash",
    readerUids: [DIRECTOR_A, TEACHER_A],
    createdAt: now,
    updatedAt: now,
  };

  // Teacher can create BROUILLON, CANNOT directly create PUBLIE
  await assertSucceeds(
    teacherDb.collection("schools").doc(SCHOOL_A).collection("annual_results").doc("res_1").set(baseResult)
  );
  await assertFails(
    teacherDb.collection("schools").doc(SCHOOL_A).collection("annual_results").doc("res_hack").set({
      ...baseResult,
      status: "PUBLIE",
    })
  );

  // Parent & student CANNOT read BROUILLON
  await assertFails(
    parentDb.collection("schools").doc(SCHOOL_A).collection("annual_results").doc("res_1").get()
  );
  await assertFails(
    eleveDb.collection("schools").doc(SCHOOL_A).collection("annual_results").doc("res_1").get()
  );

  // Director schedules for future -> still blocked for ELEVE/PARENT via get
  await assertSucceeds(
    dirDb.collection("schools").doc(SCHOOL_A).collection("annual_results").doc("res_1").update({
      status: "PROGRAMME",
      scheduledPublishAtMillis: Date.now() + 86400000,
      updatedAt: tsNow(),
    })
  );
  await assertFails(
    eleveDb.collection("schools").doc(SCHOOL_A).collection("annual_results").doc("res_1").get()
  );

  // Director publishes -> linked student and parent can read; teacher cannot alter after publication; unlinked student blocked
  await assertSucceeds(
    dirDb.collection("schools").doc(SCHOOL_A).collection("annual_results").doc("res_1").update({
      status: "PUBLIE",
      publishedAtMillis: Date.now(),
      publishedByUid: DIRECTOR_A,
      publishedByName: "Doyen UY1",
      readerUids: [DIRECTOR_A, TEACHER_A, PARENT_A, ELEVE_A],
      updatedAt: tsNow(),
    })
  );
  await assertSucceeds(
    eleveDb.collection("schools").doc(SCHOOL_A).collection("annual_results").doc("res_1").get()
  );
  await assertSucceeds(
    parentDb.collection("schools").doc(SCHOOL_A).collection("annual_results").doc("res_1").get()
  );
  await assertFails(
    otherEleveDb.collection("schools").doc(SCHOOL_A).collection("annual_results").doc("res_1").get()
  );
  await assertFails(
    teacherDb.collection("schools").doc(SCHOOL_A).collection("annual_results").doc("res_1").update({
      annualAverage: 19.0,
      updatedAt: tsNow(),
    })
  );

  // Director can correct post-publication with revisionCount & reason
  await assertSucceeds(
    dirDb.collection("schools").doc(SCHOOL_A).collection("annual_results").doc("res_1").update({
      annualAverage: 14.2,
      average: 14.2,
      revisionCount: 1,
      lastModifiedReason: "Recalcul UE Algorithmique",
      updatedAt: tsNow(),
    })
  );
});

test("Invitation query and full 5-doc acceptInvitation batch succeed atomically", async () => {
  const INVITEE_UID = "invitee_teacher_uid";
  const now = tsNow();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("schools").doc(SCHOOL_A).set({
      ...schoolPayload(DIRECTOR_A, "Collège Libermann"),
      memberUids: [DIRECTOR_A],
    });
    await db.collection("schools").doc(SCHOOL_A).collection("members").doc(DIRECTOR_A).set(
      memberPayload(DIRECTOR_A, SCHOOL_A, "dir@libermann.cm", "DIRECTEUR", [DIRECTOR_A])
    );
    await db.collection("invitations").doc("inv_prof_1").set({
      schoolId: SCHOOL_A,
      schoolName: "Collège Libermann",
      invitedEmail: "prof.lib@cm",
      role: "ENSEIGNANT",
      linkedStudentIds: [],
      assignedClassIds: ["cls_1"],
      status: "PENDING",
      invitedByUid: DIRECTOR_A,
      readerUids: [DIRECTOR_A],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("users").doc(INVITEE_UID).set({
      uid: INVITEE_UID,
      email: "prof.lib@cm",
      displayName: "Prof Libermann",
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("users").doc(INVITEE_UID).collection("school_links").doc(SCHOOL_A).set({
      userId: INVITEE_UID,
      schoolId: SCHOOL_A,
      schoolName: "Collège Libermann",
      role: "PARENT",
      createdAt: now,
      updatedAt: now,
    });
  });

  const inviteeDb = testEnv.authenticatedContext(INVITEE_UID, {
    email: "prof.lib@cm",
    email_verified: true,
  }).firestore();

  // Matches observePendingInvitationsForUser
  const invitesSnap = await assertSucceeds(
    inviteeDb.collection("invitations").where("invitedEmail", "==", "prof.lib@cm").get()
  );
  if (invitesSnap.size !== 1) {
    throw new Error(`Expected 1 invitation, got ${invitesSnap.size}`);
  }

  // Matches SchoolRepository.acceptInvitation 5-write batch (including updating existing school_links + memberUids join)
  const batch = inviteeDb.batch();
  batch.set(
    inviteeDb.collection("schools").doc(SCHOOL_A).collection("members").doc(INVITEE_UID),
    {
      uid: INVITEE_UID,
      schoolId: SCHOOL_A,
      email: "prof.lib@cm",
      displayName: "Prof Libermann",
      role: "ENSEIGNANT",
      linkedStudentIds: [],
      assignedClassIds: ["cls_1"],
      invitedViaId: "inv_prof_1",
      active: true,
      readerUids: [DIRECTOR_A, INVITEE_UID],
      createdAt: now,
      updatedAt: now,
    }
  );
  batch.update(inviteeDb.collection("invitations").doc("inv_prof_1"), {
    status: "ACCEPTED",
    updatedAt: tsNow(),
  });
  batch.update(inviteeDb.collection("schools").doc(SCHOOL_A), {
    memberUids: [DIRECTOR_A, INVITEE_UID],
    updatedAt: tsNow(),
  });
  batch.update(
    inviteeDb.collection("users").doc(INVITEE_UID).collection("school_links").doc(SCHOOL_A),
    {
      schoolName: "Collège Libermann",
      role: "ENSEIGNANT",
      updatedAt: tsNow(),
    }
  );
  batch.update(inviteeDb.collection("users").doc(INVITEE_UID), {
    activeSchoolId: SCHOOL_A,
    updatedAt: tsNow(),
  });
  await assertSucceeds(batch.commit());
});

test("Director syncMemberReaderAccess, Super Admin cross-tenant actions, and partial updates succeed cleanly", async () => {
  const now = tsNow();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("schools").doc(SCHOOL_A).set({
      ...schoolPayload(DIRECTOR_A, "Lycée Joss"),
      memberUids: [DIRECTOR_A, TEACHER_A, PARENT_A],
    });
    await db.collection("schools").doc(SCHOOL_A).collection("members").doc(DIRECTOR_A).set(
      memberPayload(DIRECTOR_A, SCHOOL_A, "dir@joss.cm", "DIRECTEUR", [DIRECTOR_A])
    );
    await db.collection("schools").doc(SCHOOL_A).collection("members").doc(TEACHER_A).set(
      memberPayload(TEACHER_A, SCHOOL_A, "prof@joss.cm", "ENSEIGNANT", [DIRECTOR_A, TEACHER_A])
    );
    await db.collection("schools").doc(SCHOOL_A).collection("classes").doc("cls_1").set({
      schoolId: SCHOOL_A,
      name: "Tle C",
      level: "Terminale",
      campus: "Campus Principal",
      capacity: 45,
      tuitionFeeFcfa: 150000,
      registrationFeeFcfa: 25000,
      academicYear: "2026-2027",
      readerUids: [DIRECTOR_A],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("schools").doc(SCHOOL_A).collection("students").doc("stu_1").set({
      schoolId: SCHOOL_A,
      matricule: "LJ-2026-001",
      firstName: "Nadia",
      lastName: "Tchamba",
      gender: "F",
      birthDate: "2008-02-19",
      classId: "cls_1",
      className: "Tle C",
      level: "Terminale",
      academicYear: "2026-2027",
      campus: "Campus Principal",
      status: "INSCRIT",
      guardianName: "M. Tchamba",
      guardianPhone: "+237699000111",
      tuitionTotalFcfa: 150000,
      historyNotes: ["Inscrite"],
      readerUids: [DIRECTOR_A],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("schools").doc(SCHOOL_A).collection("payments").doc("pay_1").set({
      schoolId: SCHOOL_A,
      receiptNumber: "REC-001",
      studentId: "stu_1",
      studentName: "Nadia Tchamba",
      studentMatricule: "LJ-2026-001",
      classId: "cls_1",
      className: "Tle C",
      tranche: "INSCRIPTION",
      amountFcfa: 25000,
      method: "ESPECES",
      providerStatus: "CONFIRME",
      transactionRef: "REC-001",
      recordedByUid: DIRECTOR_A,
      recordedByName: "Directeur",
      readerUids: [DIRECTOR_A],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("schools").doc(SCHOOL_A).collection("audit_logs").doc("log_1").set({
      schoolId: SCHOOL_A,
      actorUid: DIRECTOR_A,
      actorName: "Directeur",
      actorRole: "DIRECTEUR",
      action: "INIT",
      entityType: "School",
      entityId: SCHOOL_A,
      details: "Init",
      readerUids: [DIRECTOR_A],
      createdAt: now,
      updatedAt: now,
    });
  });

  const dirDb = testEnv.authenticatedContext(DIRECTOR_A, { email: "dir@joss.cm", email_verified: true }).firestore();
  const teacherDb = testEnv.authenticatedContext(TEACHER_A, { email: "prof@joss.cm", email_verified: true }).firestore();
  const outsiderDb = testEnv.authenticatedContext("outsider_uid", { email: "out@cm", email_verified: true }).firestore();
  const saDb = testEnv.authenticatedContext(SUPER_ADMIN_UID, { email: SUPER_ADMIN_EMAIL, email_verified: true }).firestore();

  // Director unfiltered list + syncMemberReaderAccess batch updates succeed across subcollections
  await assertSucceeds(dirDb.collection("schools").doc(SCHOOL_A).collection("classes").get());
  await assertSucceeds(dirDb.collection("schools").doc(SCHOOL_A).collection("students").get());
  await assertSucceeds(dirDb.collection("schools").doc(SCHOOL_A).collection("payments").get());
  await assertSucceeds(dirDb.collection("schools").doc(SCHOOL_A).collection("audit_logs").get());

  // Teacher & outsider unfiltered list must fail
  await assertFails(teacherDb.collection("schools").doc(SCHOOL_A).collection("payments").get());
  await assertFails(teacherDb.collection("schools").doc(SCHOOL_A).collection("audit_logs").get());
  await assertFails(outsiderDb.collection("schools").doc(SCHOOL_A).collection("classes").get());

  const syncBatch = dirDb.batch();
  syncBatch.update(dirDb.collection("schools").doc(SCHOOL_A).collection("classes").doc("cls_1"), {
    readerUids: [DIRECTOR_A, TEACHER_A],
    updatedAt: tsNow(),
  });
  syncBatch.update(dirDb.collection("schools").doc(SCHOOL_A).collection("payments").doc("pay_1"), {
    readerUids: [DIRECTOR_A, PARENT_A],
    updatedAt: tsNow(),
  });
  await assertSucceeds(syncBatch.commit());

  // Partial update on class and student without touching createdAt (AcademicRepository.upsertDoc)
  await assertSucceeds(
    dirDb.collection("schools").doc(SCHOOL_A).collection("classes").doc("cls_1").update({
      schoolId: SCHOOL_A,
      name: "Tle C1",
      level: "Terminale",
      campus: "Campus Principal",
      capacity: 50,
      tuitionFeeFcfa: 160000,
      registrationFeeFcfa: 25000,
      academicYear: "2026-2027",
      readerUids: [DIRECTOR_A, TEACHER_A],
      updatedAt: tsNow(),
    })
  );
  await assertSucceeds(
    dirDb.collection("schools").doc(SCHOOL_A).collection("students").doc("stu_1").update({
      schoolId: SCHOOL_A,
      matricule: "LJ-2026-001",
      firstName: "Nadia",
      lastName: "Tchamba",
      gender: "F",
      birthDate: "2008-02-19",
      classId: "cls_1",
      className: "Tle C1",
      level: "Terminale",
      academicYear: "2026-2027",
      campus: "Campus Principal",
      status: "REINSCRIT",
      guardianName: "M. Tchamba",
      guardianPhone: "+237699000111",
      tuitionTotalFcfa: 160000,
      historyNotes: ["Inscrite", "Réinscrite Tle C1"],
      readerUids: [DIRECTOR_A, TEACHER_A],
      updatedAt: tsNow(),
    })
  );

  // Director partial update on school settings/LMD rules
  await assertSucceeds(
    dirDb.collection("schools").doc(SCHOOL_A).update({
      passingAverage: 11.5,
      termMode: "TRIMESTRE",
      whatsappConfigured: true,
      whatsappApiUrl: "https://api.school.cm/wa",
      updatedAt: tsNow(),
    })
  );

  // Super Admin cross-tenant subscription activation + invoice + audit_log (not in SCHOOL_A members)
  const saBatch = saDb.batch();
  saBatch.update(saDb.collection("schools").doc(SCHOOL_A), {
    subscriptionStatus: "ACTIF",
    planCode: "ENTERPRISE",
    monthlyPriceFcfa: 40000,
    gracePeriodDays: 10,
    graceEndsAtMillis: 0,
    nextBillingAtMillis: Date.now() + 30 * 86400000,
    updatedAt: tsNow(),
  });
  saBatch.set(saDb.collection("schools").doc(SCHOOL_A).collection("subscription_invoices").doc("inv_sa_1"), {
    schoolId: SCHOOL_A,
    invoiceNumber: "FAC-SAAS-999001",
    planCode: "ENTERPRISE",
    amountFcfa: 40000,
    periodLabel: "Abonnement mensuel (Enterprise)",
    status: "PAYEE",
    dueDateMillis: Date.now(),
    paidAtMillis: Date.now(),
    readerUids: [DIRECTOR_A, TEACHER_A, PARENT_A],
    createdAt: now,
    updatedAt: tsNow(),
  });
  await assertSucceeds(saBatch.commit());

  await assertSucceeds(
    saDb.collection("schools").doc(SCHOOL_A).collection("audit_logs").doc("log_sa_2").set({
      schoolId: SCHOOL_A,
      actorUid: SUPER_ADMIN_UID,
      actorName: "Super Admin",
      actorRole: "SUPER_ADMIN",
      action: "ACTIVATION_ABONNEMENT",
      entityType: "School",
      entityId: SCHOOL_A,
      details: "Activation Enterprise par Super Admin",
      readerUids: [DIRECTOR_A, SUPER_ADMIN_UID],
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );
});



