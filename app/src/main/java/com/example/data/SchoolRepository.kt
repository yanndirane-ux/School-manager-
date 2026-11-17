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

class SchoolRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        const val SUPER_ADMIN_EMAIL = "yanndirane@gmail.com"
    }

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Utilisateur non authentifié.")

    fun isSuperAdminUser(): Boolean {
        val user = auth.currentUser ?: return false
        return user.isEmailVerified && user.email.equals(SUPER_ADMIN_EMAIL, ignoreCase = true)
    }

    fun isCurrentUserEmailVerified(): Boolean =
        auth.currentUser?.isEmailVerified == true

    fun observeSaasPlans(): Flow<List<SaasPlan>> {
        val query = if (isSuperAdminUser()) {
            db.collection("saas_plans")
        } else {
            db.collection("saas_plans").whereEqualTo("active", true)
        }
        return query.snapshots().map { snap ->
            snap.toObjects(SaasPlan::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedBy { PlanCode.fromRaw(it.code).rank }
        }
    }

    suspend fun seedDefaultPlansIfEmpty(): Result<Unit> {
        if (!isSuperAdminUser()) return Result.success(Unit)
        return try {
            val snap = db.collection("saas_plans").get().await()
            if (snap.isEmpty) {
                val batch = db.batch()
                val defaults = listOf(
                    mapOf(
                        "code" to "STARTER",
                        "name" to "Starter",
                        "monthlyPriceFcfa" to 10_000L,
                        "trialDays" to 14L,
                        "gracePeriodDays" to 7L,
                        "maxStudents" to 300L,
                        "features" to listOf(
                            "Gestion des étudiants",
                            "Gestion des classes",
                            "Gestion des paiements",
                            "Tableau de bord"
                        ),
                        "active" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    mapOf(
                        "code" to "PROFESSIONAL",
                        "name" to "Professional",
                        "monthlyPriceFcfa" to 20_000L,
                        "trialDays" to 14L,
                        "gracePeriodDays" to 7L,
                        "maxStudents" to 1500L,
                        "features" to listOf(
                            "Toutes les fonctionnalités Starter",
                            "Gestion des notes et bulletins PDF",
                            "Gestion des enseignants",
                            "Gestion des absences",
                            "Rapports avancés"
                        ),
                        "active" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    mapOf(
                        "code" to "ENTERPRISE",
                        "name" to "Enterprise",
                        "monthlyPriceFcfa" to 40_000L,
                        "trialDays" to 14L,
                        "gracePeriodDays" to 10L,
                        "maxStudents" to 10_000L,
                        "features" to listOf(
                            "Toutes les fonctionnalités Professional",
                            "Gestion documentaire avancée",
                            "Notifications automatisées WhatsApp/SMS",
                            "Gestion multi-campus",
                            "Assistance prioritaire"
                        ),
                        "active" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                defaults.forEach { plan ->
                    val code = plan["code"] as String
                    batch.set(db.collection("saas_plans").document(code), plan)
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "saas_plans")
            Result.failure(e)
        }
    }

    suspend fun upsertUserProfile(uid: String, email: String, displayName: String): Result<UserProfile?> = try {
        val ref = db.collection("users").document(uid)
        val existing = ref.get().await()
        if (!existing.exists()) {
            val payload = mapOf(
                "uid" to uid,
                "email" to email.ifBlank { "$uid@schoolmanager.local" },
                "displayName" to displayName.ifBlank { "Utilisateur" },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()
        } else {
            ref.update(
                mapOf(
                    "displayName" to displayName.ifBlank { existing.getString("displayName") ?: "Utilisateur" },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        val fresh = ref.get().await().toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        Result.success(fresh)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "users/$uid")
        Result.failure(e)
    }

    suspend fun setActiveSchoolId(uid: String, schoolId: String): Result<Unit> = try {
        db.collection("users").document(uid).update(
            mapOf(
                "activeSchoolId" to schoolId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
        Result.failure(e)
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> =
        db.collection("users").document(uid).snapshots().map {
            it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }

    fun observeMySchools(uid: String): Flow<List<School>> {
        val query = if (isSuperAdminUser()) {
            db.collection("schools")
        } else {
            db.collection("schools").whereArrayContains("memberUids", uid)
        }
        return query.snapshots().map { snap ->
            snap.toObjects(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedBy { it.name }
        }
    }

    fun observePendingInvitationsForUser(email: String): Flow<List<Invitation>> =
        db.collection("invitations")
            .whereEqualTo("invitedEmail", email.trim().lowercase())
            .snapshots()
            .map { snap ->
                snap.toObjects(Invitation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.status == "PENDING" }
            }

    fun observeSchoolMembers(schoolId: String, currentUserId: String): Flow<List<SchoolMember>> {
        val base = db.collection("schools").document(schoolId).collection("members")
        val query = if (isSuperAdminUser()) base else base.whereArrayContains("readerUids", currentUserId)
        return query.snapshots().map { snap ->
            snap.toObjects(SchoolMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
    }

    fun observeSchoolMember(schoolId: String, memberUid: String): Flow<SchoolMember?> =
        db.collection("schools").document(schoolId).collection("members").document(memberUid)
            .snapshots()
            .map { it.toObject(SchoolMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    fun observeSchoolInvitations(schoolId: String, currentUserId: String): Flow<List<Invitation>> {
        val query = if (isSuperAdminUser()) {
            db.collection("invitations").whereEqualTo("schoolId", schoolId)
        } else {
            db.collection("invitations").whereArrayContains("readerUids", currentUserId)
        }
        return query.snapshots().map { snap ->
            snap.toObjects(Invitation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .filter { it.schoolId == schoolId }
        }
    }

    fun observeSubscriptionInvoices(schoolId: String, currentUserId: String): Flow<List<SubscriptionInvoice>> {
        val base = db.collection("schools").document(schoolId).collection("subscription_invoices")
        val query = if (isSuperAdminUser()) base else base.whereArrayContains("readerUids", currentUserId)
        return query.snapshots().map { snap ->
            snap.toObjects(SubscriptionInvoice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.dueDateMillis }
        }
    }

    fun observeAuditLogs(schoolId: String, currentUserId: String): Flow<List<AuditLog>> {
        val base = db.collection("schools").document(schoolId).collection("audit_logs")
        val query = if (isSuperAdminUser()) base else base.whereArrayContains("readerUids", currentUserId)
        return query.snapshots().map { snap ->
            snap.toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
    }

    suspend fun createSchool(
        name: String,
        slogan: String,
        category: SchoolCategory,
        schoolType: String,
        city: String,
        country: String,
        academicYear: String,
        phone: String,
        email: String,
        plan: SaasPlan?,
        planCode: PlanCode,
        creatorRole: UserRole = UserRole.DIRECTEUR,
        faculties: List<String> = emptyList(),
        departments: List<String> = emptyList(),
        programs: List<String> = emptyList(),
        passingAverage: Double = 10.0,
        termMode: String = category.defaultTermMode
    ): Result<String> = try {
        val uid = requireUserId()
        val effectiveCreatorRole = if (creatorRole == UserRole.ADMIN_ETABLISSEMENT) UserRole.ADMIN_ETABLISSEMENT else UserRole.DIRECTEUR
        val userEmail = (auth.currentUser?.email ?: "$uid@schoolmanager.local").trim().take(160)
        val userName = (auth.currentUser?.displayName?.ifBlank { null } ?: effectiveCreatorRole.label).trim().take(100)
        val cleanName = name.trim().take(120)
        val cleanSlogan = slogan.trim().ifBlank { "Votre établissement, mieux organisé." }.take(160)
        val cleanSchoolType = schoolType.trim().ifBlank { category.shortLabel }.take(80)
        val cleanCity = city.trim().ifBlank { "Yaoundé" }.take(80)
        val cleanCountry = country.trim().ifBlank { "Cameroun" }.take(80)
        val cleanYear = academicYear.trim().ifBlank { "2026-2027" }.take(25)
        val schoolId = "sch_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val nowMs = System.currentTimeMillis()
        val trialDays = plan?.trialDays ?: 14L
        val graceDays = plan?.gracePeriodDays ?: 7L
        val price = plan?.monthlyPriceFcfa ?: planCode.defaultPriceFcfa.toLong()
        val trialEnds = nowMs + trialDays * 86_400_000L

        val cleanFaculties = faculties.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(25)
        val cleanDepartments = departments.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(40)
        val cleanPrograms = programs.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(50)

        val schoolPayload = mapOf(
            "ownerUid" to uid,
            "name" to cleanName,
            "slogan" to cleanSlogan,
            "category" to category.name,
            "schoolType" to cleanSchoolType,
            "city" to cleanCity,
            "country" to cleanCountry,
            "phone" to phone.trim().take(30).ifBlank { null },
            "email" to email.trim().take(160).ifBlank { null },
            "academicYear" to cleanYear,
            "campuses" to listOf("Campus Principal"),
            "faculties" to cleanFaculties,
            "departments" to cleanDepartments,
            "programs" to cleanPrograms,
            "passingAverage" to passingAverage.coerceIn(0.0, 20.0),
            "creditPassThreshold" to 10.0,
            "annualCreditsRequired" to 60L,
            "lmdCompensation" to true,
            "termMode" to (if (termMode == "SEMESTRE") "SEMESTRE" else "TRIMESTRE"),
            "planCode" to planCode.name,
            "subscriptionStatus" to SubscriptionStatus.ESSAI.name,
            "monthlyPriceFcfa" to price,
            "trialEndsAtMillis" to trialEnds,
            "nextBillingAtMillis" to trialEnds,
            "gracePeriodDays" to graceDays,
            "graceEndsAtMillis" to 0L,
            "memberUids" to listOf(uid),
            "momoConfigured" to false,
            "momoProvider" to "MTN_MOMO_CAMEROUN",
            "orangeConfigured" to false,
            "whatsappConfigured" to false,
            "smsConfigured" to false,
            "retentionMonths" to 36L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>

        val memberPayload = mapOf(
            "uid" to uid,
            "schoolId" to schoolId,
            "email" to userEmail,
            "displayName" to userName,
            "role" to effectiveCreatorRole.name,
            "linkedStudentIds" to emptyList<String>(),
            "assignedClassIds" to emptyList<String>(),
            "active" to true,
            "readerUids" to listOf(uid),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val linkPayload = mapOf(
            "userId" to uid,
            "schoolId" to schoolId,
            "schoolName" to cleanName,
            "role" to effectiveCreatorRole.name,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val logId = "log_" + UUID.randomUUID().toString().replace("-", "").take(10)
        val auditPayload = mapOf(
            "schoolId" to schoolId,
            "actorUid" to uid,
            "actorName" to userName,
            "actorRole" to effectiveCreatorRole.name,
            "action" to "CREATION_ETABLISSEMENT",
            "entityType" to "School",
            "entityId" to schoolId,
            "details" to "Création de l'établissement $cleanName (${category.shortLabel}, Formule ${planCode.label})".take(400),
            "readerUids" to listOf(uid),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val userRef = db.collection("users").document(uid)
        val userExists = runCatching { userRef.get().await().exists() }.getOrDefault(false)

        val batch = db.batch()
        val schoolRef = db.collection("schools").document(schoolId)
        batch.set(schoolRef, schoolPayload)
        batch.set(schoolRef.collection("members").document(uid), memberPayload)
        batch.set(userRef.collection("school_links").document(schoolId), linkPayload)
        if (userExists) {
            batch.update(userRef, mapOf("activeSchoolId" to schoolId, "updatedAt" to FieldValue.serverTimestamp()))
        } else {
            batch.set(
                userRef,
                mapOf(
                    "uid" to uid,
                    "email" to userEmail,
                    "displayName" to userName,
                    "activeSchoolId" to schoolId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.set(schoolRef.collection("audit_logs").document(logId), auditPayload)
        batch.commit().await()

        Result.success(schoolId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "schools")
        Result.failure(e)
    }

    suspend fun inviteMember(
        school: School,
        invitedEmail: String,
        role: UserRole,
        linkedStudentIds: List<String> = emptyList(),
        assignedClassIds: List<String> = emptyList()
    ): Result<Unit> = try {
        val uid = requireUserId()
        val inviteId = "inv_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val payload = mapOf(
            "schoolId" to school.id,
            "schoolName" to school.name.ifBlank { "Établissement" }.take(120),
            "invitedEmail" to invitedEmail.trim().lowercase(),
            "role" to role.name,
            "linkedStudentIds" to linkedStudentIds.take(20),
            "assignedClassIds" to assignedClassIds.take(30),
            "status" to "PENDING",
            "invitedByUid" to uid,
            "readerUids" to (school.memberUids + uid).distinct().take(60),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("invitations").document(inviteId).set(payload).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "invitations")
        Result.failure(e)
    }

    suspend fun acceptInvitation(invitation: Invitation): Result<String> = try {
        val uid = requireUserId()
        val userEmail = (auth.currentUser?.email ?: invitation.invitedEmail).trim().ifBlank { "$uid@schoolmanager.local" }.take(160)
        val displayName = (auth.currentUser?.displayName?.trim()?.ifBlank { null } ?: userEmail.substringBefore("@")).trim().ifBlank { "Membre" }.take(100)
        val schoolRef = db.collection("schools").document(invitation.schoolId)
        val userRef = db.collection("users").document(uid)
        val linkRef = userRef.collection("school_links").document(invitation.schoolId)
        val userExists = runCatching { userRef.get().await().exists() }.getOrDefault(false)
        val linkExists = runCatching { linkRef.get().await().exists() }.getOrDefault(false)

        val memberPayload = mapOf(
            "uid" to uid,
            "schoolId" to invitation.schoolId,
            "email" to userEmail,
            "displayName" to displayName,
            "role" to invitation.role,
            "linkedStudentIds" to invitation.linkedStudentIds.take(20),
            "assignedClassIds" to invitation.assignedClassIds.take(30),
            "invitedViaId" to invitation.id,
            "active" to true,
            "readerUids" to (invitation.readerUids + uid).distinct().take(60),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val batch = db.batch()
        batch.set(schoolRef.collection("members").document(uid), memberPayload)
        batch.update(
            db.collection("invitations").document(invitation.id),
            mapOf("status" to "ACCEPTED", "updatedAt" to FieldValue.serverTimestamp())
        )
        batch.update(
            schoolRef,
            mapOf(
                "memberUids" to FieldValue.arrayUnion(uid),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        if (linkExists) {
            batch.update(
                linkRef,
                mapOf(
                    "schoolName" to invitation.schoolName.ifBlank { "Établissement" }.take(120),
                    "role" to invitation.role,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        } else {
            batch.set(
                linkRef,
                mapOf(
                    "userId" to uid,
                    "schoolId" to invitation.schoolId,
                    "schoolName" to invitation.schoolName.ifBlank { "Établissement" }.take(120),
                    "role" to invitation.role,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        if (userExists) {
            batch.update(userRef, mapOf("activeSchoolId" to invitation.schoolId, "updatedAt" to FieldValue.serverTimestamp()))
        } else {
            batch.set(
                userRef,
                mapOf(
                    "uid" to uid,
                    "email" to userEmail,
                    "displayName" to displayName,
                    "activeSchoolId" to invitation.schoolId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()

        Result.success(invitation.schoolId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "invitations/${invitation.id}")
        Result.failure(e)
    }

    suspend fun updateMemberRoleAndLinks(
        schoolId: String,
        member: SchoolMember,
        newRole: UserRole,
        linkedStudentIds: List<String>,
        active: Boolean
    ): Result<Unit> = try {
        requireUserId()
        db.collection("schools").document(schoolId).collection("members").document(member.uid)
            .update(
                mapOf(
                    "role" to newRole.name,
                    "linkedStudentIds" to linkedStudentIds,
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "schools/$schoolId/members/${member.uid}")
        Result.failure(e)
    }

    suspend fun updateSchoolIntegrationsAndProfile(
        schoolId: String,
        updates: Map<String, Any>
    ): Result<Unit> = try {
        requireUserId()
        val payload = updates.toMutableMap().apply {
            put("updatedAt", FieldValue.serverTimestamp())
        }
        db.collection("schools").document(schoolId).update(payload).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "schools/$schoolId")
        Result.failure(e)
    }

    suspend fun superAdminUpdatePlan(
        planCode: PlanCode,
        name: String,
        monthlyPriceFcfa: Long,
        trialDays: Long,
        gracePeriodDays: Long,
        maxStudents: Long,
        features: List<String>
    ): Result<Unit> = try {
        requireUserId()
        db.collection("saas_plans").document(planCode.name).set(
            mapOf(
                "code" to planCode.name,
                "name" to name.trim(),
                "monthlyPriceFcfa" to monthlyPriceFcfa,
                "trialDays" to trialDays,
                "gracePeriodDays" to gracePeriodDays,
                "maxStudents" to maxStudents,
                "features" to features.take(20),
                "active" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "saas_plans/${planCode.name}")
        Result.failure(e)
    }

    suspend fun superAdminManageSchoolSubscription(
        school: School,
        newStatus: SubscriptionStatus,
        newPlanCode: PlanCode,
        monthlyPriceFcfa: Long,
        gracePeriodDays: Long,
        generatePaidInvoice: Boolean
    ): Result<Unit> = try {
        requireUserId()
        val nowMs = System.currentTimeMillis()
        val nextBilling = if (newStatus == SubscriptionStatus.ACTIF) nowMs + 30L * 86_400_000L else school.nextBillingAtMillis
        val graceEnds = if (newStatus == SubscriptionStatus.PERIODE_GRACE) nowMs + gracePeriodDays * 86_400_000L else 0L

        val batch = db.batch()
        val schoolRef = db.collection("schools").document(school.id)
        batch.update(
            schoolRef,
            mapOf(
                "subscriptionStatus" to newStatus.name,
                "planCode" to newPlanCode.name,
                "monthlyPriceFcfa" to monthlyPriceFcfa,
                "gracePeriodDays" to gracePeriodDays,
                "graceEndsAtMillis" to graceEnds,
                "nextBillingAtMillis" to nextBilling,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        if (generatePaidInvoice) {
            val invId = "inv_" + UUID.randomUUID().toString().replace("-", "").take(10)
            val invNum = "FAC-SAAS-${nowMs.toString().takeLast(6)}"
            batch.set(
                schoolRef.collection("subscription_invoices").document(invId),
                mapOf(
                    "schoolId" to school.id,
                    "invoiceNumber" to invNum,
                    "planCode" to newPlanCode.name,
                    "amountFcfa" to monthlyPriceFcfa,
                    "periodLabel" to "Abonnement mensuel (${newPlanCode.label})",
                    "status" to if (newStatus == SubscriptionStatus.ACTIF) "PAYEE" else "EN_ATTENTE",
                    "dueDateMillis" to nowMs,
                    "paidAtMillis" to if (newStatus == SubscriptionStatus.ACTIF) nowMs else null,
                    "readerUids" to school.memberUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
            )
        }
        batch.commit().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "schools/${school.id}")
        Result.failure(e)
    }

    suspend fun recordAuditLog(
        school: School,
        actorName: String,
        actorRole: UserRole,
        action: String,
        entityType: String,
        entityId: String,
        details: String
    ): Result<Unit> = try {
        val uid = requireUserId()
        val logId = "log_" + UUID.randomUUID().toString().replace("-", "").take(10)
        val payload = mapOf(
            "schoolId" to school.id,
            "actorUid" to uid,
            "actorName" to actorName.trim().ifBlank { "Utilisateur" }.take(100),
            "actorRole" to actorRole.name,
            "action" to action.trim().ifBlank { "ACTION" }.take(80),
            "entityType" to entityType.trim().ifBlank { "School" }.take(60),
            "entityId" to entityId.trim().ifBlank { school.id }.take(128),
            "details" to details.trim().ifBlank { action }.take(400),
            "readerUids" to (school.memberUids + uid).distinct().take(60),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("schools").document(school.id).collection("audit_logs").document(logId).set(payload).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "schools/${school.id}/audit_logs")
        Result.failure(e)
    }

    suspend fun syncMemberReaderAccess(school: School, members: List<SchoolMember>): Result<Int> = try {
        val uid = requireUserId()
        val activeMembers = members.filter { it.active }
        val allReaders = (school.memberUids + activeMembers.map { it.uid } + uid).distinct().take(60)
        val staffReaders = (activeMembers.filter {
            it.role != UserRole.PARENT.name && it.role != UserRole.ELEVE.name
        }.map { it.uid } + school.ownerUid + uid).distinct().take(60)
        val familyMembers = activeMembers.filter {
            it.role == UserRole.PARENT.name || it.role == UserRole.ELEVE.name
        }

        val schoolRef = db.collection("schools").document(school.id)
        val ops = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, List<String>>>()

        val generalCollections = listOf("members", "classes", "subjects", "rooms", "schedules", "announcements")
        for (col in generalCollections) {
            schoolRef.collection(col).get().await().documents.forEach { doc ->
                ops.add(doc.reference to allReaders)
            }
        }
        schoolRef.collection("teachers").get().await().documents.forEach { doc ->
            ops.add(doc.reference to staffReaders)
        }
        schoolRef.collection("students").get().await().documents.forEach { doc ->
            val linkedFamily = familyMembers.filter { doc.id in it.linkedStudentIds }.map { it.uid }
            ops.add(doc.reference to (staffReaders + linkedFamily).distinct().take(60))
        }
        for (col in listOf("grades", "payments", "documents")) {
            schoolRef.collection(col).get().await().documents.forEach { doc ->
                val studentId = doc.getString("studentId").orEmpty()
                val linkedFamily = if (studentId.isBlank()) emptyList() else {
                    familyMembers.filter { studentId in it.linkedStudentIds }.map { it.uid }
                }
                ops.add(doc.reference to (staffReaders + linkedFamily).distinct().take(60))
            }
        }
        schoolRef.collection("attendance").get().await().documents.forEach { doc ->
            val targetType = doc.getString("targetType").orEmpty()
            val targetId = doc.getString("targetId").orEmpty()
            val linkedFamily = if (targetType == "ETUDIANT" && targetId.isNotBlank()) {
                familyMembers.filter { targetId in it.linkedStudentIds }.map { it.uid }
            } else emptyList()
            ops.add(doc.reference to (staffReaders + linkedFamily).distinct().take(60))
        }
        val nowMs = System.currentTimeMillis()
        schoolRef.collection("annual_results").get().await().documents.forEach { doc ->
            val status = doc.getString("status").orEmpty()
            val schedMs = doc.getLong("scheduledPublishAtMillis") ?: 0L
            val studentId = doc.getString("studentId").orEmpty()
            val allowFamily = status == ResultStatus.PUBLIE.name ||
                (status == ResultStatus.PROGRAMME.name && schedMs in 1L..nowMs)
            val linkedFamily = if (allowFamily && studentId.isNotBlank()) {
                familyMembers.filter { studentId in it.linkedStudentIds }.map { it.uid }
            } else emptyList()
            ops.add(doc.reference to (staffReaders + linkedFamily).distinct().take(60))
        }

        ops.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, readers) ->
                batch.update(ref, mapOf("readerUids" to readers, "updatedAt" to FieldValue.serverTimestamp()))
            }
            batch.commit().await()
        }
        Result.success(ops.size)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "schools/${school.id}/sync")
        Result.failure(e)
    }
}
