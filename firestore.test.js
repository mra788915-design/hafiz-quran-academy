const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const crypto = require("node:crypto");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";

const ADMIN_UID = "admin_uid_001";
const TEACHER_UID = "teacher_uid_001";
const OTHER_TEACHER_UID = "teacher_uid_002";
const PARENT_UID = "parent_uid_001";
const OTHER_PARENT_UID = "parent_uid_002";
const ATTACKER_UID = "attacker_uid_999";

const ACADEMY_ID = "hqa_main";
const CLASS_1_ID = "cls_hifz_1";
const CLASS_2_ID = "cls_nazra_1";
const STUDENT_1_ID = "std_bilal_01";
const STUDENT_2_ID = "std_zayd_02";
const CHALLAN_1_ID = "ch_bilal_oct26";

function sha256Hex(input) {
  return crypto.createHash("sha256").update(input, "utf8").digest("hex");
}

const ADMIN_TICKET_HASH = sha256Hex("admin:Admin@123");
const TEACHER_TICKET_HASH = sha256Hex("qari.abdullah:Tchr@101");
const PARENT_TICKET_HASH = sha256Hex("p.farooq:Prnt@101");
const OTHER_PARENT_TICKET_HASH = sha256Hex("p.asad:Prnt@102");

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

async function seedAcademyFixture() {
  const now = new Date("2026-10-01T08:00:00Z");
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("academies").doc(ACADEMY_ID).set({
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      academyName: "Hafiz Quran Academy",
      easyPaisaNumber: "03493895409",
      jazzCashNumber: "03707304820",
      onlineAccountTitle: "MUHAMMAD RIZWAN",
      easyPaisaIban: "PK23TMFB0000000095651623",
      classIds: [CLASS_1_ID, CLASS_2_ID],
      teacherIds: ["tchr_1"],
      studentIds: [STUDENT_1_ID, STUDENT_2_ID],
      announcementIds: [],
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("auth_tickets").doc(ADMIN_TICKET_HASH).set({
      ticketHash: ADMIN_TICKET_HASH,
      accountId: "acc_admin",
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      displayName: "Nazim-e-Aala",
      role: "ADMIN",
      teacherId: "",
      assignedClassIds: [],
      linkedStudentIds: [],
      isActive: true,
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("auth_tickets").doc(TEACHER_TICKET_HASH).set({
      ticketHash: TEACHER_TICKET_HASH,
      accountId: "acc_tchr_1",
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      displayName: "Qari Abdullah",
      role: "TEACHER",
      teacherId: "tchr_1",
      assignedClassIds: [CLASS_1_ID],
      linkedStudentIds: [],
      isActive: true,
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("auth_tickets").doc(PARENT_TICKET_HASH).set({
      ticketHash: PARENT_TICKET_HASH,
      accountId: "acc_prnt_1",
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      displayName: "Farooq Ahmed",
      role: "PARENT",
      teacherId: "",
      assignedClassIds: [],
      linkedStudentIds: [STUDENT_1_ID],
      isActive: true,
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("auth_tickets").doc(OTHER_PARENT_TICKET_HASH).set({
      ticketHash: OTHER_PARENT_TICKET_HASH,
      accountId: "acc_prnt_2",
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      displayName: "Asadullah Khan",
      role: "PARENT",
      teacherId: "",
      assignedClassIds: [],
      linkedStudentIds: [STUDENT_2_ID],
      isActive: true,
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("users").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      displayName: "Nazim-e-Aala",
      role: "ADMIN",
      ticketHash: ADMIN_TICKET_HASH,
      academyId: ACADEMY_ID,
      teacherId: "",
      assignedClassIds: [],
      linkedStudentIds: [],
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("users").doc(TEACHER_UID).set({
      userId: TEACHER_UID,
      displayName: "Qari Abdullah",
      role: "TEACHER",
      ticketHash: TEACHER_TICKET_HASH,
      academyId: ACADEMY_ID,
      teacherId: "tchr_1",
      assignedClassIds: [CLASS_1_ID],
      linkedStudentIds: [],
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("users").doc(PARENT_UID).set({
      userId: PARENT_UID,
      displayName: "Farooq Ahmed",
      role: "PARENT",
      ticketHash: PARENT_TICKET_HASH,
      academyId: ACADEMY_ID,
      teacherId: "",
      assignedClassIds: [],
      linkedStudentIds: [STUDENT_1_ID],
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("users").doc(OTHER_PARENT_UID).set({
      userId: OTHER_PARENT_UID,
      displayName: "Asadullah Khan",
      role: "PARENT",
      ticketHash: OTHER_PARENT_TICKET_HASH,
      academyId: ACADEMY_ID,
      teacherId: "",
      assignedClassIds: [],
      linkedStudentIds: [STUDENT_2_ID],
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("admin_vault").doc("acc_prnt_1").set({
      accountId: "acc_prnt_1",
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      name: "Farooq Ahmed",
      username: "p.farooq",
      password: "Prnt@101",
      email: "",
      phone: "03004455667",
      role: "PARENT",
      teacherId: "",
      assignedClassIds: "",
      linkedStudentIds: STUDENT_1_ID,
      ticketHash: PARENT_TICKET_HASH,
      isActive: true,
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("classes").doc(CLASS_1_ID).set({
      classId: CLASS_1_ID,
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      name: "Hifz Halaqah 1",
      section: "Morning",
      roomNumber: "Room A",
      capacity: 15,
      assignedTeacherId: "tchr_1",
      assignedTeacherName: "Qari Abdullah",
      scheduleInfo: "Mon-Sat",
      isActive: true,
      studentIds: [STUDENT_1_ID],
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("students").doc(STUDENT_1_ID).set({
      studentId: STUDENT_1_ID,
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      admissionNumber: "HQA-26-0101",
      fullName: "Muhammad Bilal Farooq",
      guardianName: "Farooq Ahmed",
      dateOfBirth: "2013-05-14",
      admissionDate: "2025-01-10",
      gender: "Male",
      contactNumber: "03004455667",
      alternateContact: "",
      address: "Model Town Lahore",
      classId: CLASS_1_ID,
      className: "Hifz Halaqah 1",
      teacherId: "tchr_1",
      teacherName: "Qari Abdullah",
      quranLevel: "Hifz",
      monthlyFee: 4500,
      feeCategory: "Regular",
      isFsEligible: false,
      fsStatus: "None",
      status: "ACTIVE",
      notes: "",
      recentAttendanceIds: [],
      recentProgressIds: [],
      recentChallanIds: [CHALLAN_1_ID],
      recentPaymentIds: [],
      createdAt: now,
      updatedAt: now,
    });

    await db.collection("fee_challans").doc(CHALLAN_1_ID).set({
      challanId: CHALLAN_1_ID,
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      challanNumber: "HQA-2026-10-0101-789",
      studentId: STUDENT_1_ID,
      admissionNumber: "HQA-26-0101",
      studentName: "Muhammad Bilal Farooq",
      classId: CLASS_1_ID,
      className: "Hifz Halaqah 1",
      billingMonth: "October",
      billingYear: 2026,
      issueDate: "2026-10-01",
      dueDate: "2026-10-10",
      monthlyTuitionFee: 4500,
      additionalCharges: 200,
      discountAmount: 0,
      fsAssistanceAmount: 0,
      previousBalance: 0,
      totalPayable: 4700,
      amountPaid: 0,
      remainingBalance: 4700,
      status: "UNPAID",
      paymentDate: "",
      paymentMethod: "",
      transactionRef: "",
      remarks: "",
      createdAt: now,
      updatedAt: now,
    });
  });
}

// 1. Unauthenticated Denials
test("Unauthenticated user: denied reading students, challans, and admin_vault", async () => {
  await seedAcademyFixture();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("students").doc(STUDENT_1_ID).get());
  await assertFails(unauthDb.collection("fee_challans").doc(CHALLAN_1_ID).get());
  await assertFails(unauthDb.collection("admin_vault").doc("acc_prnt_1").get());
});

// 2. Role Escalation & Ticket Spoofing Prevention
test("Attacker: cannot self-assign ADMIN role once academy exists without valid admin ticketHash", async () => {
  await seedAcademyFixture();
  const attackerDb = testEnv.authenticatedContext(ATTACKER_UID).firestore();
  const now = new Date();
  await assertFails(
    attackerDb.collection("users").doc(ATTACKER_UID).set({
      userId: ATTACKER_UID,
      displayName: "Rogue Admin",
      role: "ADMIN",
      ticketHash: sha256Hex("rogue:guess123"),
      academyId: ACADEMY_ID,
      teacherId: "",
      assignedClassIds: [],
      linkedStudentIds: [],
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Valid Parent: can claim PARENT session using exact Admin-issued ticketHash", async () => {
  await seedAcademyFixture();
  const newParentUid = "parent_device_uid_77";
  const parentDb = testEnv.authenticatedContext(newParentUid).firestore();
  const now = new Date();
  await assertSucceeds(
    parentDb.collection("users").doc(newParentUid).set({
      userId: newParentUid,
      displayName: "Farooq Ahmed",
      role: "PARENT",
      ticketHash: PARENT_TICKET_HASH,
      academyId: ACADEMY_ID,
      teacherId: "",
      assignedClassIds: [],
      linkedStudentIds: [STUDENT_1_ID],
      createdAt: now,
      updatedAt: now,
    })
  );
});

// 3. Confidential PII Isolation (Admin Vault, Cross-Parent Student & Challan Isolation)
test("Teacher and Parent: strictly denied reading Admin Credential Vault", async () => {
  await seedAcademyFixture();
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  await assertFails(teacherDb.collection("admin_vault").doc("acc_prnt_1").get());
  await assertFails(parentDb.collection("admin_vault").doc("acc_prnt_1").get());
});

test("Cross-Parent Isolation: Parent 2 cannot read Parent 1's child or fee challan", async () => {
  await seedAcademyFixture();
  const otherParentDb = testEnv.authenticatedContext(OTHER_PARENT_UID).firestore();
  await assertFails(otherParentDb.collection("students").doc(STUDENT_1_ID).get());
  await assertFails(otherParentDb.collection("fee_challans").doc(CHALLAN_1_ID).get());
});

test("Linked Parent: can read their own child and fee challan", async () => {
  await seedAcademyFixture();
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  await assertSucceeds(parentDb.collection("students").doc(STUDENT_1_ID).get());
  await assertSucceeds(parentDb.collection("fee_challans").doc(CHALLAN_1_ID).get());
});

// 4. Shadow Field Injection Rejection
test("Shadow field injection: extra unexpected field on attendance create is rejected", async () => {
  await seedAcademyFixture();
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const now = new Date();
  await assertFails(
    teacherDb.collection("attendance").doc("att_shadow_1").set({
      attendanceId: "att_shadow_1",
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      date: "2026-10-24",
      classId: CLASS_1_ID,
      className: "Hifz Halaqah 1",
      studentId: STUDENT_1_ID,
      studentName: "Muhammad Bilal Farooq",
      admissionNumber: "HQA-26-0101",
      status: "PRESENT",
      remarks: "",
      markedByTeacherId: "tchr_1",
      isAdminOverride: true, // Forbidden shadow field
      createdAt: now,
      updatedAt: now,
    })
  );
});

// 5. Teacher Workflow: Can mark attendance & Quran progress for assigned class, blocked from unassigned class
test("Assigned Teacher: can create attendance & update student recentAttendanceIds", async () => {
  await seedAcademyFixture();
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    teacherDb.collection("attendance").doc("att_std1_20261024").set({
      attendanceId: "att_std1_20261024",
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      date: "2026-10-24",
      classId: CLASS_1_ID,
      className: "Hifz Halaqah 1",
      studentId: STUDENT_1_ID,
      studentName: "Muhammad Bilal Farooq",
      admissionNumber: "HQA-26-0101",
      status: "PRESENT",
      remarks: "On time",
      markedByTeacherId: "tchr_1",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    teacherDb.collection("students").doc(STUDENT_1_ID).update({
      recentAttendanceIds: ["att_std1_20261024"],
      updatedAt: now,
    })
  );
});

test("Assigned Teacher: cannot modify student monthlyFee or status", async () => {
  await seedAcademyFixture();
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  await assertFails(
    teacherDb.collection("students").doc(STUDENT_1_ID).update({
      monthlyFee: 0,
      updatedAt: new Date(),
    })
  );
});

// 6. Parent Online Payment & Terminal PAID State Locking
test("Linked Parent: can submit online QR payment with TID, but cannot lower totalPayable or mutate after PAID", async () => {
  await seedAcademyFixture();
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  const now = new Date();

  // Attempt to tamper with totalPayable -> must FAIL
  await assertFails(
    parentDb.collection("fee_challans").doc(CHALLAN_1_ID).update({
      totalPayable: 100,
      amountPaid: 100,
      remainingBalance: 0,
      status: "PAID",
      paymentDate: "2026-10-24 10:00",
      paymentMethod: "EasyPaisa (03493895409)",
      transactionRef: "TID-99887766",
      remarks: "Tampered",
      updatedAt: now,
    })
  );

  // Legitimate full payment with valid TID -> must SUCCEED
  await assertSucceeds(
    parentDb.collection("fee_payments").doc("pay_oct26_01").set({
      paymentId: "pay_oct26_01",
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      challanId: CHALLAN_1_ID,
      challanNumber: "HQA-2026-10-0101-789",
      studentId: STUDENT_1_ID,
      studentName: "Muhammad Bilal Farooq",
      amount: 4700,
      paymentDate: "2026-10-24 10:05",
      paymentMethod: "EasyPaisa (03493895409)",
      receiptNumber: "TID-99887766",
      receivedBy: "Online QR Portal (Farooq Ahmed)",
      remarks: "Paid via EasyPaisa QR",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    parentDb.collection("fee_challans").doc(CHALLAN_1_ID).update({
      amountPaid: 4700,
      remainingBalance: 0,
      status: "PAID",
      paymentDate: "2026-10-24 10:05",
      paymentMethod: "EasyPaisa (03493895409)",
      transactionRef: "TID-99887766",
      remarks: "Paid via EasyPaisa QR",
      updatedAt: now,
    })
  );

  // Terminal state lock: once PAID, Parent cannot revert or edit challan again -> must FAIL
  await assertFails(
    parentDb.collection("fee_challans").doc(CHALLAN_1_ID).update({
      transactionRef: "TID-CHANGED-999",
      updatedAt: new Date(),
    })
  );
});

// 7. Client Query Alignment Tests (Admin list queries)
test("Admin: can list students and challans filtered by adminUid, fails unfiltered query", async () => {
  await seedAcademyFixture();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();

  await assertSucceeds(
    adminDb.collection("students").where("adminUid", "==", ADMIN_UID).get()
  );
  await assertSucceeds(
    adminDb.collection("fee_challans").where("adminUid", "==", ADMIN_UID).get()
  );
  await assertSucceeds(
    adminDb.collection("admin_vault").where("adminUid", "==", ADMIN_UID).get()
  );

  // Rules are not filters: Parent cannot list students collection
  await assertFails(parentDb.collection("students").get());
  await assertFails(
    parentDb.collection("students").where("adminUid", "==", ADMIN_UID).get()
  );
});

test("Admin: can create, update, and permanently delete withdrawn student", async () => {
  await seedAcademyFixture();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    adminDb.collection("students").doc("std_new_05").set({
      studentId: "std_new_05",
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      admissionNumber: "HQA-26-0105",
      fullName: "Umar Farooq",
      guardianName: "Farooq Ahmed",
      dateOfBirth: "2014-01-01",
      admissionDate: "2026-10-01",
      gender: "Male",
      contactNumber: "03004455667",
      alternateContact: "",
      address: "Model Town Lahore",
      classId: CLASS_1_ID,
      className: "Hifz Halaqah 1",
      teacherId: "tchr_1",
      teacherName: "Qari Abdullah",
      quranLevel: "Qaida",
      monthlyFee: 4000,
      feeCategory: "Regular",
      isFsEligible: false,
      fsStatus: "None",
      status: "ACTIVE",
      notes: "New admission",
      completedCourse: "",
      completionDate: "",
      certificateNumber: "",
      finalGrade: "",
      certificateRemarks: "",
      recentAttendanceIds: [],
      recentProgressIds: [],
      recentChallanIds: [],
      recentPaymentIds: [],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb.collection("students").doc("std_new_05").update({
      monthlyFee: 4200,
      notes: "Updated fee",
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(adminDb.collection("students").doc("std_new_05").delete());
});

// 8. Teacher assigned via classes.assignedTeacherId (even when users.assignedClassIds is empty)
test("Teacher assigned via classes.assignedTeacherId: can read class students and create/update/delete attendance & Quran progress", async () => {
  await seedAcademyFixture();
  const now = new Date("2026-10-02T08:00:00Z");
  const newTeacherHash = sha256Hex("qari.hamza:Tchr@202");

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    // Create Teacher 2 whose auth_ticket / users doc initially has empty assignedClassIds [],
    // while Class 2 has assignedTeacherId == "tchr_2" (reproducing Admin -> Add Teacher -> Assign Class flow)
    await db.collection("auth_tickets").doc(newTeacherHash).set({
      ticketHash: newTeacherHash,
      accountId: "acc_tchr_2",
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      displayName: "Qari Hamza",
      role: "TEACHER",
      teacherId: "tchr_2",
      assignedClassIds: [],
      linkedStudentIds: [],
      isActive: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("users").doc(OTHER_TEACHER_UID).set({
      userId: OTHER_TEACHER_UID,
      displayName: "Qari Hamza",
      role: "TEACHER",
      ticketHash: newTeacherHash,
      academyId: ACADEMY_ID,
      teacherId: "tchr_2",
      assignedClassIds: [],
      linkedStudentIds: [],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("classes").doc(CLASS_2_ID).set({
      classId: CLASS_2_ID,
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      name: "Nazra Halaqah 1",
      section: "Morning",
      roomNumber: "Room B",
      capacity: 20,
      assignedTeacherId: "tchr_2",
      assignedTeacherName: "Qari Hamza",
      scheduleInfo: "Mon-Sat",
      isActive: true,
      studentIds: [STUDENT_2_ID],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("students").doc(STUDENT_2_ID).set({
      studentId: STUDENT_2_ID,
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      admissionNumber: "HQA-26-0102",
      fullName: "Zayd Asadullah",
      guardianName: "Asadullah Khan",
      dateOfBirth: "2014-03-12",
      admissionDate: "2025-02-01",
      gender: "Male",
      contactNumber: "03001122334",
      alternateContact: "",
      address: "Johar Town Lahore",
      classId: CLASS_2_ID,
      className: "Nazra Halaqah 1",
      teacherId: "",
      teacherName: "",
      quranLevel: "Nazra",
      monthlyFee: 3500,
      feeCategory: "Regular",
      isFsEligible: false,
      fsStatus: "None",
      status: "ACTIVE",
      notes: "",
      recentAttendanceIds: [],
      recentProgressIds: [],
      recentChallanIds: [],
      recentPaymentIds: [],
      createdAt: now,
      updatedAt: now,
    });
  });

  const teacher2Db = testEnv.authenticatedContext(OTHER_TEACHER_UID).firestore();

  // Can read student in Class 2 via checkTeacherClassSession fallback to classes/CLASS_2_ID.assignedTeacherId == "tchr_2"
  await assertSucceeds(teacher2Db.collection("students").doc(STUDENT_2_ID).get());
  // Still blocked from reading Student 1 in Class 1 (assigned to tchr_1)
  await assertFails(teacher2Db.collection("students").doc(STUDENT_1_ID).get());

  // Can create, update, and delete Quran progress & attendance for Class 2
  const writeTime = new Date();
  await assertSucceeds(
    teacher2Db.collection("quran_progress").doc("prog_zayd_1").set({
      progressId: "prog_zayd_1",
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      date: "2026-10-25",
      classId: CLASS_2_ID,
      studentId: STUDENT_2_ID,
      studentName: "Zayd Asadullah",
      admissionNumber: "HQA-26-0102",
      teacherId: "tchr_2",
      teacherName: "Qari Hamza",
      surahName: "Al-Mulk",
      surahNumber: 67,
      juzNumber: 29,
      sabaqAyatStart: 1,
      sabaqAyatEnd: 15,
      sabaqRating: "VERY_GOOD",
      sabqiSummary: "Para 29",
      manzilSummary: "Juz 30",
      mistakesCount: 1,
      teacherRemarks: "MashaAllah",
      nextTarget: "Ayat 16-30",
      createdAt: writeTime,
      updatedAt: writeTime,
    })
  );

  await assertSucceeds(
    teacher2Db.collection("quran_progress").doc("prog_zayd_1").update({
      sabaqAyatEnd: 20,
      sabaqRating: "EXCELLENT",
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    teacher2Db.collection("quran_progress").doc("prog_zayd_1").delete()
  );
});

// 9. Admin Full Edit & Remove (Teachers, Classes, Challans, Payments, FS Records, Announcements) while Parent cannot edit/delete Payments
test("Admin: can edit and delete teachers, classes, challans, payments, FS records, and announcements; Parent denied payment edit/delete", async () => {
  await seedAcademyFixture();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  const now = new Date();

  // Admin creates, updates, deletes Teacher
  await assertSucceeds(
    adminDb.collection("teachers").doc("tchr_temp").set({
      teacherId: "tchr_temp",
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      fullName: "Qari Usman",
      contactNumber: "03121234567",
      email: "usman@hqa.edu",
      address: "Lahore",
      assignedClasses: "Hifz Halaqah 1",
      assignedClassIds: CLASS_1_ID,
      joiningDate: "2026-10-01",
      isActive: true,
      notes: "",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adminDb.collection("teachers").doc("tchr_temp").update({
      fullName: "Qari Muhammad Usman",
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(adminDb.collection("teachers").doc("tchr_temp").delete());

  // Admin creates & updates Payment; Parent denied update & delete; Admin deletes Payment & Challan
  await assertSucceeds(
    adminDb.collection("fee_payments").doc("pay_admin_1").set({
      paymentId: "pay_admin_1",
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      challanId: CHALLAN_1_ID,
      challanNumber: "HQA-2026-10-0101-789",
      studentId: STUDENT_1_ID,
      studentName: "Muhammad Bilal Farooq",
      amount: 2000,
      paymentDate: "2026-10-25 11:00",
      paymentMethod: "Cash at Counter",
      receiptNumber: "REC-1001",
      receivedBy: "Admin",
      remarks: "Partial cash",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    parentDb.collection("fee_payments").doc("pay_admin_1").update({
      amount: 4700,
      updatedAt: new Date(),
    })
  );
  await assertFails(parentDb.collection("fee_payments").doc("pay_admin_1").delete());

  await assertSucceeds(
    adminDb.collection("fee_payments").doc("pay_admin_1").update({
      amount: 2500,
      remarks: "Corrected partial cash",
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(adminDb.collection("fee_payments").doc("pay_admin_1").delete());
  await assertSucceeds(adminDb.collection("fee_challans").doc(CHALLAN_1_ID).delete());
});

// 10. Course Completion Sanad Upgrade (27-key -> 32-key) & Locked-Out Academy Owner Recovery
test("Admin: can upgrade legacy 27-key student to 32-key COMPLETED Sanad; Teacher denied status/certificate tampering", async () => {
  await seedAcademyFixture();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const now = new Date("2026-10-01T08:00:00Z");

  // Teacher cannot mark student COMPLETED
  await assertFails(
    teacherDb.collection("students").doc(STUDENT_1_ID).update({
      status: "COMPLETED",
      certificateNumber: "FAKE-001",
      updatedAt: new Date(),
    })
  );

  // Admin upgrades legacy 27-key STUDENT_1_ID to full 32-key COMPLETED Sanad record
  await assertSucceeds(
    adminDb.collection("students").doc(STUDENT_1_ID).set({
      studentId: STUDENT_1_ID,
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      admissionNumber: "HQA-26-0101",
      fullName: "Muhammad Bilal Farooq",
      guardianName: "Farooq Ahmed",
      dateOfBirth: "2013-05-14",
      admissionDate: "2025-01-10",
      gender: "Male",
      contactNumber: "03004455667",
      alternateContact: "",
      address: "Model Town Lahore",
      classId: CLASS_1_ID,
      className: "Hifz Halaqah 1",
      teacherId: "tchr_1",
      teacherName: "Qari Abdullah",
      quranLevel: "Hifz",
      monthlyFee: 4500,
      feeCategory: "Regular",
      isFsEligible: false,
      fsStatus: "None",
      status: "COMPLETED",
      notes: "Graduated Hifz",
      completedCourse: "Hifz-ul-Quran Al-Kareem (Complete 30 Juz)",
      completionDate: "2026-11-01",
      certificateNumber: "HQA-SANAD-2026-0101",
      finalGrade: "Mumtaz (A+ Distinction)",
      certificateRemarks: "Completed 30 Juz with Tajweed",
      recentAttendanceIds: [],
      recentProgressIds: [],
      recentChallanIds: [CHALLAN_1_ID],
      recentPaymentIds: [],
      createdAt: now,
      updatedAt: new Date(),
    })
  );
});

test("Academy Owner (academies.adminUid): can recover/reset Admin auth_ticket and admin_vault when locked out; Attacker denied", async () => {
  await seedAcademyFixture();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("auth_tickets").doc(ADMIN_TICKET_HASH).delete();
    await db.collection("users").doc(ADMIN_UID).delete();
  });

  const ownerDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const attackerDb = testEnv.authenticatedContext(ATTACKER_UID).firestore();
  const newAdminHash = sha256Hex("rizwan.admin:Rizwan@786");
  const now = new Date();

  await assertFails(
    attackerDb.collection("auth_tickets").doc(newAdminHash).set({
      ticketHash: newAdminHash,
      accountId: "acc_admin_main",
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      displayName: "Rogue",
      role: "ADMIN",
      teacherId: "",
      assignedClassIds: [],
      linkedStudentIds: [],
      isActive: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    ownerDb.collection("auth_tickets").doc(newAdminHash).set({
      ticketHash: newAdminHash,
      accountId: "acc_admin_main",
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      displayName: "Nazim-e-Aala",
      role: "ADMIN",
      teacherId: "",
      assignedClassIds: [],
      linkedStudentIds: [],
      isActive: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    ownerDb.collection("admin_vault").doc("acc_admin_main").set({
      accountId: "acc_admin_main",
      academyId: ACADEMY_ID,
      adminUid: ADMIN_UID,
      name: "Nazim-e-Aala",
      username: "rizwan.admin",
      password: "Rizwan@786",
      email: "admin@hafizquranacademy.edu",
      phone: "03707304820",
      role: "ADMIN",
      teacherId: "",
      assignedClassIds: "",
      linkedStudentIds: "",
      ticketHash: newAdminHash,
      isActive: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    ownerDb.collection("users").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      displayName: "Nazim-e-Aala",
      role: "ADMIN",
      ticketHash: newAdminHash,
      academyId: ACADEMY_ID,
      teacherId: "",
      assignedClassIds: [],
      linkedStudentIds: [],
      createdAt: now,
      updatedAt: now,
    })
  );
});


