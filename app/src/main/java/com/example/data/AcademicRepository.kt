package com.example.data

import android.content.Context
import com.example.R
import com.example.model.*
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AcademicRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Utilisateur non authentifié.")

    private fun isSuperAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        return user.isEmailVerified && user.email.equals(SchoolRepository.SUPER_ADMIN_EMAIL, ignoreCase = true)
    }

    private fun <T> observeTenantCollection(
        schoolId: String,
        collectionName: String,
        currentUserId: String,
        clazz: Class<T>
    ): Flow<List<T>> {
        val base = db.collection("schools").document(schoolId).collection(collectionName)
        val query = if (isSuperAdmin()) base else base.whereArrayContains("readerUids", currentUserId)
        return query.snapshots().map { snap ->
            snap.toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
    }

    fun observeClasses(schoolId: String, uid: String): Flow<List<SchoolClass>> =
        observeTenantCollection(schoolId, "classes", uid, SchoolClass::class.java)

    fun observeSubjects(schoolId: String, uid: String): Flow<List<Subject>> =
        observeTenantCollection(schoolId, "subjects", uid, Subject::class.java)

    fun observeRooms(schoolId: String, uid: String): Flow<List<Classroom>> =
        observeTenantCollection(schoolId, "rooms", uid, Classroom::class.java)

    fun observeSchedules(schoolId: String, uid: String): Flow<List<ScheduleEntry>> =
        observeTenantCollection(schoolId, "schedules", uid, ScheduleEntry::class.java)

    fun observeStudents(schoolId: String, uid: String): Flow<List<Student>> =
        observeTenantCollection(schoolId, "students", uid, Student::class.java)

    fun observeTeachers(schoolId: String, uid: String): Flow<List<Teacher>> =
        observeTenantCollection(schoolId, "teachers", uid, Teacher::class.java)

    fun observeGrades(schoolId: String, uid: String): Flow<List<Grade>> =
        observeTenantCollection(schoolId, "grades", uid, Grade::class.java)

    fun observeAttendance(schoolId: String, uid: String): Flow<List<AttendanceRecord>> =
        observeTenantCollection(schoolId, "attendance", uid, AttendanceRecord::class.java)

    fun observeAnnualResults(schoolId: String, uid: String): Flow<List<AnnualResult>> =
        observeTenantCollection(schoolId, "annual_results", uid, AnnualResult::class.java)

    private fun readerList(school: School): List<String> {
        val uid = requireUserId()
        return (school.memberUids + uid).distinct().take(60)
    }

    private fun staffReaders(school: School, members: List<SchoolMember>): List<String> {
        val uid = requireUserId()
        val staff = members.filter {
            it.active && it.role != UserRole.PARENT.name && it.role != UserRole.ELEVE.name
        }.map { it.uid }
        return (staff + school.ownerUid + uid).distinct().take(60)
    }

    private fun resultReaderList(
        school: School,
        members: List<SchoolMember>,
        studentId: String,
        status: String,
        scheduledPublishAtMillis: Long
    ): List<String> {
        val baseStaff = staffReaders(school, members)
        val now = System.currentTimeMillis()
        val allowFamily = status == ResultStatus.PUBLIE.name ||
            (status == ResultStatus.PROGRAMME.name && scheduledPublishAtMillis in 1L..now)
        if (!allowFamily) return baseStaff
        val family = members.filter {
            it.active &&
                (it.role == UserRole.PARENT.name || it.role == UserRole.ELEVE.name) &&
                studentId in it.linkedStudentIds
        }.map { it.uid }
        return (baseStaff + family).distinct().take(60)
    }

    private suspend fun upsertDoc(
        col: com.google.firebase.firestore.CollectionReference,
        existingId: String,
        generatedId: String,
        basePayload: Map<String, Any>
    ): String {
        val isUpdate = existingId.isNotBlank()
        val id = existingId.ifBlank { generatedId }
        val ref = col.document(id)
        if (isUpdate) {
            ref.update(
                basePayload.toMutableMap().apply {
                    put("updatedAt", FieldValue.serverTimestamp())
                }
            ).await()
        } else {
            ref.set(
                basePayload.toMutableMap().apply {
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
            ).await()
        }
        return id
    }

    suspend fun saveClass(school: School, item: SchoolClass): Result<String> = try {
        requireUserId()
        val payload = mapOf(
            "schoolId" to school.id,
            "name" to item.name.trim().ifBlank { "Classe" }.take(80),
            "level" to item.level.trim().ifBlank { "Général" }.take(60),
            "campus" to item.campus.trim().ifBlank { "Campus Principal" }.take(80),
            "faculty" to item.faculty?.trim()?.ifBlank { null }?.take(100),
            "department" to item.department?.trim()?.ifBlank { null }?.take(100),
            "program" to item.program?.trim()?.ifBlank { null }?.take(100),
            "cycleLevel" to item.cycleLevel?.trim()?.ifBlank { null }?.take(40),
            "capacity" to item.capacity.coerceIn(1L, 500L),
            "tuitionFeeFcfa" to item.tuitionFeeFcfa.coerceIn(0L, 20_000_000L),
            "registrationFeeFcfa" to item.registrationFeeFcfa.coerceIn(0L, 5_000_000L),
            "academicYear" to item.academicYear.ifBlank { school.academicYear }.ifBlank { "2026-2027" }.take(25),
            "mainTeacherName" to item.mainTeacherName?.trim()?.ifBlank { null }?.take(100),
            "readerUids" to readerList(school)
        ).filterValues { it != null } as Map<String, Any>
        val id = upsertDoc(
            db.collection("schools").document(school.id).collection("classes"),
            item.id,
            "cls_" + UUID.randomUUID().toString().replace("-", "").take(10),
            payload
        )
        Result.success(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "schools/${school.id}/classes")
        Result.failure(e)
    }

    suspend fun saveSubject(school: School, item: Subject): Result<String> = try {
        requireUserId()
        val cleanName = item.name.trim().ifBlank { "Matière" }.take(100)
        val cleanCode = item.code.trim().uppercase().ifBlank { cleanName.take(6).uppercase() }.ifBlank { "MAT" }.take(25)
        val payload = mapOf(
            "schoolId" to school.id,
            "name" to cleanName,
            "code" to cleanCode,
            "coefficient" to item.coefficient.coerceIn(1L, 20L),
            "credits" to item.credits.coerceIn(0L, 30L),
            "semester" to item.semester?.trim()?.ifBlank { null }?.take(30),
            "faculty" to item.faculty?.trim()?.ifBlank { null }?.take(100),
            "department" to item.department?.trim()?.ifBlank { null }?.take(100),
            "universityUe" to item.universityUe,
            "classId" to item.classId.take(128),
            "className" to item.className.trim().ifBlank { "Toutes classes" }.take(80),
            "teacherId" to item.teacherId?.ifBlank { null }?.take(128),
            "teacherName" to item.teacherName?.trim()?.ifBlank { null }?.take(100),
            "weeklyHours" to item.weeklyHours.coerceIn(1L, 40L),
            "readerUids" to readerList(school)
        ).filterValues { it != null } as Map<String, Any>
        val id = upsertDoc(
            db.collection("schools").document(school.id).collection("subjects"),
            item.id,
            "sub_" + UUID.randomUUID().toString().replace("-", "").take(10),
            payload
        )
        Result.success(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "schools/${school.id}/subjects")
        Result.failure(e)
    }

    suspend fun saveClassroom(school: School, item: Classroom): Result<String> = try {
        requireUserId()
        val payload = mapOf(
            "schoolId" to school.id,
            "name" to item.name.trim().ifBlank { "Salle" }.take(60),
            "building" to item.building.trim().ifBlank { "Bâtiment A" }.take(60),
            "campus" to item.campus.trim().ifBlank { "Campus Principal" }.take(80),
            "capacity" to item.capacity.coerceIn(1L, 1000L),
            "roomType" to item.roomType.trim().ifBlank { "Salle standard" }.take(60),
            "readerUids" to readerList(school)
        )
        val id = upsertDoc(
            db.collection("schools").document(school.id).collection("rooms"),
            item.id,
            "rom_" + UUID.randomUUID().toString().replace("-", "").take(10),
            payload
        )
        Result.success(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "schools/${school.id}/rooms")
        Result.failure(e)
    }

    suspend fun saveSchedule(school: School, item: ScheduleEntry): Result<String> = try {
        requireUserId()
        val payload = mapOf(
            "schoolId" to school.id,
            "classId" to item.classId.take(128),
            "className" to item.className.trim().ifBlank { "Classe" }.take(80),
            "subjectId" to item.subjectId.take(128),
            "subjectName" to item.subjectName.trim().ifBlank { "Matière" }.take(100),
            "teacherId" to item.teacherId?.ifBlank { null }?.take(128),
            "teacherName" to item.teacherName.trim().ifBlank { "Enseignant" }.take(100),
            "roomId" to item.roomId?.ifBlank { null }?.take(128),
            "roomName" to item.roomName.trim().ifBlank { "Salle principale" }.take(60),
            "dayOfWeek" to item.dayOfWeek.ifBlank { "Lundi" },
            "startTime" to item.startTime.trim().ifBlank { "08:00" }.take(10),
            "endTime" to item.endTime.trim().ifBlank { "10:00" }.take(10),
            "entryType" to (if (item.entryType == ScheduleEntryType.EXAMEN.name) ScheduleEntryType.EXAMEN.name else ScheduleEntryType.COURS.name),
            "examDate" to item.examDate?.trim()?.ifBlank { null }?.take(30),
            "readerUids" to readerList(school)
        ).filterValues { it != null } as Map<String, Any>
        val id = upsertDoc(
            db.collection("schools").document(school.id).collection("schedules"),
            item.id,
            "scd_" + UUID.randomUUID().toString().replace("-", "").take(10),
            payload
        )
        Result.success(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "schools/${school.id}/schedules")
        Result.failure(e)
    }

    suspend fun saveStudent(school: School, item: Student): Result<String> = try {
        requireUserId()
        val payload = mapOf(
            "schoolId" to school.id,
            "matricule" to item.matricule.trim().ifBlank { "SM-2026" }.take(40),
            "firstName" to item.firstName.trim().ifBlank { "Prénom" }.take(80),
            "lastName" to item.lastName.trim().ifBlank { "Nom" }.take(80),
            "gender" to (if (item.gender == "F") "F" else "M"),
            "birthDate" to item.birthDate.trim().ifBlank { "2010-01-01" }.take(30),
            "birthPlace" to item.birthPlace?.trim()?.ifBlank { null }?.take(80),
            "classId" to item.classId.take(128),
            "className" to item.className.ifBlank { "Non assignée" }.take(80),
            "level" to item.level.ifBlank { "Général" }.take(60),
            "faculty" to item.faculty?.trim()?.ifBlank { null }?.take(100),
            "department" to item.department?.trim()?.ifBlank { null }?.take(100),
            "program" to item.program?.trim()?.ifBlank { null }?.take(100),
            "academicYear" to item.academicYear.ifBlank { school.academicYear }.ifBlank { "2026-2027" }.take(25),
            "campus" to item.campus.ifBlank { "Campus Principal" }.take(80),
            "status" to item.status,
            "photoUri" to item.photoUri?.ifBlank { null }?.take(500),
            "address" to item.address?.trim()?.ifBlank { null }?.take(200),
            "guardianName" to item.guardianName.trim().ifBlank { "Tuteur légal" }.take(100),
            "guardianPhone" to item.guardianPhone.trim().ifBlank { "+237000000" }.take(30),
            "guardianEmail" to item.guardianEmail?.trim()?.ifBlank { null }?.take(160),
            "tuitionTotalFcfa" to item.tuitionTotalFcfa.coerceIn(0L, 25_000_000L),
            "historyNotes" to item.historyNotes.take(20),
            "matriculeHistory" to item.matriculeHistory.take(25),
            "accessCodeDisplay" to item.accessCodeDisplay?.trim()?.ifBlank { null }?.take(20),
            "accessCodeHash" to item.accessCodeHash?.trim()?.ifBlank { null }?.take(128),
            "readerUids" to readerList(school)
        ).filterValues { it != null } as Map<String, Any>
        val id = upsertDoc(
            db.collection("schools").document(school.id).collection("students"),
            item.id,
            "stu_" + UUID.randomUUID().toString().replace("-", "").take(10),
            payload
        )
        Result.success(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "schools/${school.id}/students")
        Result.failure(e)
    }

    suspend fun deleteStudent(schoolId: String, studentId: String): Result<Unit> = try {
        requireUserId()
        db.collection("schools").document(schoolId).collection("students").document(studentId).delete().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "schools/$schoolId/students/$studentId")
        Result.failure(e)
    }

    suspend fun saveTeacher(school: School, item: Teacher): Result<String> = try {
        requireUserId()
        val payload = mapOf(
            "schoolId" to school.id,
            "employeeCode" to item.employeeCode.trim().ifBlank { "ENS-01" }.take(40),
            "firstName" to item.firstName.trim().ifBlank { "Prénom" }.take(80),
            "lastName" to item.lastName.trim().ifBlank { "Nom" }.take(80),
            "specialty" to item.specialty.trim().ifBlank { "Enseignement général" }.take(80),
            "phone" to item.phone.trim().ifBlank { "+237000000" }.take(30),
            "email" to item.email?.trim()?.ifBlank { null }?.take(160),
            "hireDate" to item.hireDate.trim().ifBlank { "2026-09-01" }.take(30),
            "contractType" to item.contractType,
            "monthlySalaryFcfa" to item.monthlySalaryFcfa.coerceIn(0L, 20_000_000L),
            "assignedSubjects" to item.assignedSubjects.take(15),
            "assignedClasses" to item.assignedClasses.take(15),
            "absencesCount" to item.absencesCount.coerceIn(0L, 366L),
            "status" to item.status,
            "readerUids" to readerList(school)
        ).filterValues { it != null } as Map<String, Any>
        val id = upsertDoc(
            db.collection("schools").document(school.id).collection("teachers"),
            item.id,
            "tch_" + UUID.randomUUID().toString().replace("-", "").take(10),
            payload
        )
        Result.success(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "schools/${school.id}/teachers")
        Result.failure(e)
    }

    suspend fun saveGrade(school: School, item: Grade): Result<String> = try {
        requireUserId()
        val payload = mapOf(
            "schoolId" to school.id,
            "studentId" to item.studentId,
            "studentName" to item.studentName.trim().ifBlank { "Élève" }.take(160),
            "classId" to item.classId.take(128),
            "className" to item.className.trim().ifBlank { "Classe" }.take(60),
            "subjectId" to item.subjectId.take(128),
            "subjectName" to item.subjectName.trim().ifBlank { "Matière" }.take(80),
            "coefficient" to item.coefficient.coerceIn(1L, 20L),
            "term" to item.term,
            "evaluationType" to item.evaluationType,
            "score" to item.score.coerceIn(0.0, 20.0),
            "maxScore" to item.maxScore.coerceIn(1.0, 100.0),
            "teacherName" to item.teacherName.trim().ifBlank { "Enseignant" }.take(100),
            "comment" to item.comment?.trim()?.ifBlank { null }?.take(300),
            "readerUids" to readerList(school)
        ).filterValues { it != null } as Map<String, Any>
        val id = upsertDoc(
            db.collection("schools").document(school.id).collection("grades"),
            item.id,
            "grd_" + UUID.randomUUID().toString().replace("-", "").take(10),
            payload
        )
        Result.success(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "schools/${school.id}/grades")
        Result.failure(e)
    }

    suspend fun saveAttendance(school: School, item: AttendanceRecord): Result<String> = try {
        val uid = requireUserId()
        val payload = mapOf(
            "schoolId" to school.id,
            "targetType" to item.targetType,
            "targetId" to item.targetId,
            "targetName" to item.targetName.trim().ifBlank { "Apprenant" }.take(160),
            "classId" to item.classId.take(128),
            "className" to item.className.trim().ifBlank { "Classe" }.take(60),
            "dateIso" to item.dateIso.trim().ifBlank { "2026-09-01" }.take(20),
            "status" to item.status,
            "justified" to item.justified,
            "reason" to item.reason?.trim()?.ifBlank { null }?.take(300),
            "recordedByUid" to uid,
            "readerUids" to readerList(school)
        ).filterValues { it != null } as Map<String, Any>
        val id = upsertDoc(
            db.collection("schools").document(school.id).collection("attendance"),
            item.id,
            "att_" + UUID.randomUUID().toString().replace("-", "").take(10),
            payload
        )
        Result.success(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "schools/${school.id}/attendance")
        Result.failure(e)
    }

    private fun buildAnnualResultFields(
        school: School,
        item: AnnualResult,
        members: List<SchoolMember>
    ): Map<String, Any> {
        val readers = resultReaderList(school, members, item.studentId, item.status, item.scheduledPublishAtMillis)
        return mapOf(
            "schoolId" to school.id,
            "schoolName" to school.name.ifBlank { "Établissement" }.take(120),
            "category" to item.category,
            "studentId" to item.studentId,
            "matricule" to item.matricule.trim().ifBlank { "SM-2026" }.take(40),
            "studentFullName" to item.studentFullName.trim().ifBlank { "Apprenant" }.take(160),
            "birthDate" to item.birthDate.trim().ifBlank { "2010-01-01" }.take(30),
            "classId" to item.classId.take(128),
            "className" to item.className.ifBlank { "Non assignée" }.take(80),
            "level" to item.level.ifBlank { "Général" }.take(60),
            "faculty" to item.faculty?.trim()?.ifBlank { null }?.take(100),
            "department" to item.department?.trim()?.ifBlank { null }?.take(100),
            "program" to item.program?.trim()?.ifBlank { null }?.take(100),
            "academicYear" to item.academicYear.ifBlank { school.academicYear }.ifBlank { "2026-2027" }.take(25),
            "periodLabel" to item.periodLabel.ifBlank { "Résultats annuels" }.take(80),
            "average" to item.average.coerceIn(0.0, 20.0),
            "semester1Average" to item.semester1Average?.coerceIn(0.0, 20.0),
            "semester2Average" to item.semester2Average?.coerceIn(0.0, 20.0),
            "annualAverage" to item.annualAverage.coerceIn(0.0, 20.0),
            "rank" to item.rank.coerceIn(0L, 5000L),
            "classSize" to item.classSize.coerceIn(0L, 5000L),
            "creditsObtained" to item.creditsObtained.coerceIn(0L, 300L),
            "creditsRequired" to item.creditsRequired.coerceIn(0L, 300L),
            "decision" to item.decision,
            "mention" to item.mention.take(60),
            "appreciation" to item.appreciation.take(500),
            "subjectLinesJson" to item.subjectLinesJson.take(100_000),
            "status" to item.status,
            "scheduledPublishAtMillis" to item.scheduledPublishAtMillis.coerceAtLeast(0L),
            "publishedAtMillis" to item.publishedAtMillis.coerceAtLeast(0L),
            "publishedByUid" to item.publishedByUid?.ifBlank { null }?.take(128),
            "publishedByName" to item.publishedByName?.ifBlank { null }?.take(100),
            "revisionCount" to item.revisionCount.coerceIn(0L, 1000L),
            "lastModifiedReason" to item.lastModifiedReason?.trim()?.ifBlank { null }?.take(400),
            "verificationHash" to item.verificationHash.take(128),
            "readerUids" to readers
        ).filterValues { it != null } as Map<String, Any>
    }

    suspend fun saveAnnualResult(
        school: School,
        item: AnnualResult,
        members: List<SchoolMember>
    ): Result<String> = try {
        requireUserId()
        val id = upsertDoc(
            db.collection("schools").document(school.id).collection("annual_results"),
            item.id,
            "res_" + UUID.randomUUID().toString().replace("-", "").take(12),
            buildAnnualResultFields(school, item, members)
        )
        Result.success(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "schools/${school.id}/annual_results")
        Result.failure(e)
    }

    suspend fun saveAnnualResultsBatch(
        school: School,
        results: List<AnnualResult>,
        members: List<SchoolMember>
    ): Result<Int> = try {
        requireUserId()
        val col = db.collection("schools").document(school.id).collection("annual_results")
        results.chunked(350).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { res ->
                val base = buildAnnualResultFields(school, res, members).toMutableMap()
                if (res.id.isNotBlank()) {
                    base["updatedAt"] = FieldValue.serverTimestamp()
                    batch.update(col.document(res.id), base)
                } else {
                    val newId = "res_" + UUID.randomUUID().toString().replace("-", "").take(12)
                    base["createdAt"] = FieldValue.serverTimestamp()
                    base["updatedAt"] = FieldValue.serverTimestamp()
                    batch.set(col.document(newId), base)
                }
            }
            batch.commit().await()
        }
        Result.success(results.size)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "schools/${school.id}/annual_results")
        Result.failure(e)
    }
}
