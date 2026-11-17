package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole(val label: String) {
    SUPER_ADMIN("Super Administrateur"),
    ADMIN_ETABLISSEMENT("Administrateur d'établissement"),
    DIRECTEUR("Directeur"),
    SECRETAIRE("Secrétaire"),
    COMPTABLE("Comptable"),
    ENSEIGNANT("Enseignant"),
    ELEVE("Élève"),
    PARENT("Parent");

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.name == raw } ?: PARENT
    }
}

enum class PlanCode(val label: String, val defaultPriceFcfa: Int, val rank: Int) {
    STARTER("Starter", 10_000, 1),
    PROFESSIONAL("Professional", 20_000, 2),
    ENTERPRISE("Enterprise", 40_000, 3);

    companion object {
        fun fromRaw(raw: String?): PlanCode =
            entries.firstOrNull { it.name == raw } ?: STARTER
    }
}

enum class SubscriptionStatus(val label: String) {
    ESSAI("Essai gratuit (14j)"),
    ACTIF("Actif"),
    PERIODE_GRACE("Période de grâce"),
    SUSPENDU("Suspendu");

    companion object {
        fun fromRaw(raw: String?): SubscriptionStatus =
            entries.firstOrNull { it.name == raw } ?: ESSAI
    }
}

enum class StudentStatus(val label: String) {
    INSCRIT("Inscrit"),
    REINSCRIT("Réinscrit"),
    SUSPENDU("Suspendu"),
    ARCHIVE("Archivé");

    companion object {
        fun fromRaw(raw: String?): StudentStatus =
            entries.firstOrNull { it.name == raw } ?: INSCRIT
    }
}

enum class TermPeriod(val label: String) {
    TRIMESTRE_1("1er Trimestre"),
    TRIMESTRE_2("2e Trimestre"),
    TRIMESTRE_3("3e Trimestre"),
    SEMESTRE_1("1er Semestre"),
    SEMESTRE_2("2e Semestre");

    companion object {
        fun fromRaw(raw: String?): TermPeriod =
            entries.firstOrNull { it.name == raw } ?: TRIMESTRE_1
    }
}

enum class EvaluationType(val label: String) {
    DEVOIR("Devoir"),
    COMPOSITION("Composition"),
    INTERROGATION("Interrogation");

    companion object {
        fun fromRaw(raw: String?): EvaluationType =
            entries.firstOrNull { it.name == raw } ?: DEVOIR
    }
}

enum class PaymentMethod(val label: String) {
    ESPECES("Espèces"),
    VIREMENT("Virement bancaire"),
    MTN_MOMO("MTN MoMo"),
    ORANGE_MONEY("Orange Money");

    companion object {
        fun fromRaw(raw: String?): PaymentMethod =
            entries.firstOrNull { it.name == raw } ?: ESPECES
    }
}

enum class PaymentTranche(val label: String) {
    INSCRIPTION("Frais d'inscription"),
    TRANCHE_1("1ère Tranche"),
    TRANCHE_2("2ème Tranche"),
    TRANCHE_3("3ème Tranche"),
    AUTRE("Autre frais");

    companion object {
        fun fromRaw(raw: String?): PaymentTranche =
            entries.firstOrNull { it.name == raw } ?: TRANCHE_1
    }
}

enum class ProviderStatus(val label: String) {
    CONFIRME("Confirmé"),
    EN_ATTENTE("En attente"),
    ECHOUE("Échoué");

    companion object {
        fun fromRaw(raw: String?): ProviderStatus =
            entries.firstOrNull { it.name == raw } ?: CONFIRME
    }
}

enum class AttendanceStatus(val label: String) {
    PRESENT("Présent"),
    ABSENT("Absent"),
    RETARD("En retard");

    companion object {
        fun fromRaw(raw: String?): AttendanceStatus =
            entries.firstOrNull { it.name == raw } ?: PRESENT
    }
}

enum class SchoolCategory(
    val label: String,
    val shortLabel: String,
    val defaultTermMode: String
) {
    PRIMAIRE_MATERNELLE("Écoles primaires et maternelles", "Primaire / Maternelle", "TRIMESTRE"),
    COLLEGE_LYCEE("Collèges et lycées", "Collège / Lycée", "TRIMESTRE"),
    UNIVERSITE_INSTITUT("Universités et instituts supérieurs", "Université / Institut", "SEMESTRE");

    companion object {
        fun fromRaw(raw: String?): SchoolCategory =
            entries.firstOrNull { it.name == raw } ?: COLLEGE_LYCEE
    }
}

enum class UniversityLevel(val label: String, val code: String, val annualCredits: Long) {
    LICENCE_1("Licence 1", "L1", 60L),
    LICENCE_2("Licence 2", "L2", 60L),
    LICENCE_3("Licence 3", "L3", 60L),
    MASTER_1("Master 1", "M1", 60L),
    MASTER_2("Master 2", "M2", 60L),
    DOCTORAT("Doctorat", "DOC", 180L);

    companion object {
        fun fromRaw(raw: String?): UniversityLevel =
            entries.firstOrNull { it.name == raw || it.label.equals(raw, ignoreCase = true) } ?: LICENCE_1
    }
}

enum class ResultStatus(val label: String) {
    BROUILLON("Brouillon"),
    VALIDE("Validé"),
    PROGRAMME("Publication programmée"),
    PUBLIE("Publié");

    companion object {
        fun fromRaw(raw: String?): ResultStatus =
            entries.firstOrNull { it.name == raw } ?: BROUILLON
    }
}

enum class ResultDecision(val label: String) {
    ADMIS("Admis(e)"),
    AJOURNE("Ajourné(e)"),
    REDOUBLEMENT("Redoublement"),
    VALIDE_LMD("Semestre / Année Validé(e) (LMD)"),
    AJOURNE_LMD("Ajourné(e) / Crédits à valider");

    companion object {
        fun fromRaw(raw: String?): ResultDecision =
            entries.firstOrNull { it.name == raw } ?: AJOURNE
    }
}

enum class ScheduleEntryType(val label: String) {
    COURS("Cours"),
    EXAMEN("Examen / Session");

    companion object {
        fun fromRaw(raw: String?): ScheduleEntryType =
            entries.firstOrNull { it.name == raw } ?: COURS
    }
}

enum class DocumentType(val label: String) {
    CERTIFICAT_SCOLARITE("Certificat de scolarité"),
    ATTESTATION_PRESENCE("Attestation de présence"),
    RELEVE_NOTES("Relevé de notes"),
    RELEVE_UNIVERSITAIRE("Relevé de notes universitaire"),
    ATTESTATION_RESULTATS("Attestation de résultats"),
    DECISION_PASSAGE("Décision de passage / validation"),
    BULLETIN("Bulletin scolaire"),
    RECU_PAIEMENT("Reçu de paiement"),
    LISTE_ETUDIANTS("Liste des étudiants");

    companion object {
        fun fromRaw(raw: String?): DocumentType =
            entries.firstOrNull { it.name == raw } ?: CERTIFICAT_SCOLARITE
    }
}

enum class ChannelType(val label: String) {
    INTERNE("Interne (App)"),
    WHATSAPP("WhatsApp API"),
    SMS("SMS API");

    companion object {
        fun fromRaw(raw: String?): ChannelType =
            entries.firstOrNull { it.name == raw } ?: INTERNE
    }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class SaasPlan(
    @DocumentId val id: String = "",
    val code: String = PlanCode.STARTER.name,
    val name: String = "Starter",
    val monthlyPriceFcfa: Long = 10_000L,
    val trialDays: Long = 14L,
    val gracePeriodDays: Long = 7L,
    val maxStudents: Long = 300L,
    val features: List<String> = emptyList(),
    val active: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val phone: String? = null,
    val activeSchoolId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SchoolLink(
    @DocumentId val id: String = "",
    val userId: String = "",
    val schoolId: String = "",
    val schoolName: String = "",
    val role: String = UserRole.DIRECTEUR.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Invitation(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolName: String = "",
    val invitedEmail: String = "",
    val role: String = UserRole.ENSEIGNANT.name,
    val linkedStudentIds: List<String> = emptyList(),
    val assignedClassIds: List<String> = emptyList(),
    val status: String = "PENDING",
    val invitedByUid: String = "",
    val readerUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class School(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val name: String = "",
    val slogan: String = "Votre établissement, mieux organisé.",
    val category: String = SchoolCategory.COLLEGE_LYCEE.name,
    val schoolType: String = "Lycée / Collège",
    val city: String = "Yaoundé",
    val country: String = "Cameroun",
    val address: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val academicYear: String = "2026-2027",
    val logoUri: String? = null,
    val campuses: List<String> = listOf("Campus Principal"),
    val faculties: List<String> = emptyList(),
    val departments: List<String> = emptyList(),
    val programs: List<String> = emptyList(),
    val passingAverage: Double = 10.0,
    val creditPassThreshold: Double = 10.0,
    val annualCreditsRequired: Long = 60L,
    val lmdCompensation: Boolean = true,
    val termMode: String = "TRIMESTRE",
    val planCode: String = PlanCode.STARTER.name,
    val subscriptionStatus: String = SubscriptionStatus.ESSAI.name,
    val monthlyPriceFcfa: Long = 10_000L,
    val trialEndsAtMillis: Long = 0L,
    val nextBillingAtMillis: Long = 0L,
    val gracePeriodDays: Long = 7L,
    val graceEndsAtMillis: Long = 0L,
    val memberUids: List<String> = emptyList(),
    val momoConfigured: Boolean = false,
    val momoProvider: String? = "MTN_MOMO_CAMEROUN",
    val momoApiBaseUrl: String? = null,
    val momoMerchantCode: String? = null,
    val orangeConfigured: Boolean = false,
    val orangeApiBaseUrl: String? = null,
    val orangeMerchantCode: String? = null,
    val whatsappConfigured: Boolean = false,
    val whatsappApiUrl: String? = null,
    val smsConfigured: Boolean = false,
    val smsApiUrl: String? = null,
    val smsSenderId: String? = null,
    val retentionMonths: Long = 36L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SchoolMember(
    @DocumentId val id: String = "",
    val uid: String = "",
    val schoolId: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = UserRole.DIRECTEUR.name,
    val linkedStudentIds: List<String> = emptyList(),
    val assignedClassIds: List<String> = emptyList(),
    val invitedViaId: String? = null,
    val active: Boolean = true,
    val readerUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SchoolClass(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val name: String = "",
    val level: String = "",
    val campus: String = "Campus Principal",
    val faculty: String? = null,
    val department: String? = null,
    val program: String? = null,
    val cycleLevel: String? = null,
    val capacity: Long = 45L,
    val tuitionFeeFcfa: Long = 150_000L,
    val registrationFeeFcfa: Long = 25_000L,
    val academicYear: String = "2026-2027",
    val mainTeacherName: String? = null,
    val readerUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Subject(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val name: String = "",
    val code: String = "",
    val coefficient: Long = 2L,
    val credits: Long = 3L,
    val semester: String? = null,
    val faculty: String? = null,
    val department: String? = null,
    val universityUe: Boolean = false,
    val classId: String = "",
    val className: String = "",
    val teacherId: String? = null,
    val teacherName: String? = null,
    val weeklyHours: Long = 4L,
    val readerUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Classroom(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val name: String = "",
    val building: String = "Bâtiment A",
    val campus: String = "Campus Principal",
    val capacity: Long = 50L,
    val roomType: String = "Salle standard",
    val readerUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ScheduleEntry(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val classId: String = "",
    val className: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val teacherId: String? = null,
    val teacherName: String = "",
    val roomId: String? = null,
    val roomName: String = "",
    val dayOfWeek: String = "Lundi",
    val startTime: String = "08:00",
    val endTime: String = "10:00",
    val entryType: String = ScheduleEntryType.COURS.name,
    val examDate: String? = null,
    val readerUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Student(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val matricule: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val gender: String = "M",
    val birthDate: String = "",
    val birthPlace: String? = null,
    val classId: String = "",
    val className: String = "",
    val level: String = "",
    val faculty: String? = null,
    val department: String? = null,
    val program: String? = null,
    val academicYear: String = "2026-2027",
    val campus: String = "Campus Principal",
    val status: String = StudentStatus.INSCRIT.name,
    val photoUri: String? = null,
    val address: String? = null,
    val guardianName: String = "",
    val guardianPhone: String = "",
    val guardianEmail: String? = null,
    val tuitionTotalFcfa: Long = 0L,
    val historyNotes: List<String> = emptyList(),
    val matriculeHistory: List<String> = emptyList(),
    val accessCodeDisplay: String? = null,
    val accessCodeHash: String? = null,
    val readerUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val fullName: String get() = "$lastName $firstName".trim()
}

@Keep
@IgnoreExtraProperties
data class Teacher(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val employeeCode: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val specialty: String = "",
    val phone: String = "",
    val email: String? = null,
    val hireDate: String = "",
    val contractType: String = "CDI",
    val monthlySalaryFcfa: Long = 0L,
    val assignedSubjects: List<String> = emptyList(),
    val assignedClasses: List<String> = emptyList(),
    val absencesCount: Long = 0L,
    val status: String = "ACTIF",
    val readerUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val fullName: String get() = "$lastName $firstName".trim()
}

@Keep
@IgnoreExtraProperties
data class Grade(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val classId: String = "",
    val className: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val coefficient: Long = 1L,
    val term: String = TermPeriod.TRIMESTRE_1.name,
    val evaluationType: String = EvaluationType.DEVOIR.name,
    val score: Double = 0.0,
    val maxScore: Double = 20.0,
    val teacherName: String = "",
    val comment: String? = null,
    val readerUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Payment(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val receiptNumber: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val studentMatricule: String = "",
    val classId: String = "",
    val className: String = "",
    val tranche: String = PaymentTranche.TRANCHE_1.name,
    val amountFcfa: Long = 0L,
    val method: String = PaymentMethod.ESPECES.name,
    val providerStatus: String = ProviderStatus.CONFIRME.name,
    val transactionRef: String = "",
    val recordedByUid: String = "",
    val recordedByName: String = "",
    val notes: String? = null,
    val readerUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AttendanceRecord(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val targetType: String = "ETUDIANT",
    val targetId: String = "",
    val targetName: String = "",
    val classId: String = "",
    val className: String = "",
    val dateIso: String = "",
    val status: String = AttendanceStatus.PRESENT.name,
    val justified: Boolean = false,
    val reason: String? = null,
    val recordedByUid: String = "",
    val readerUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Announcement(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val title: String = "",
    val message: String = "",
    val category: String = "ANNONCE",
    val channel: String = ChannelType.INTERNE.name,
    val deliveryStatus: String = "PUBLIE",
    val targetAudience: String = "TOUS",
    val authorUid: String = "",
    val authorName: String = "",
    val readerUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SchoolDocument(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val docType: String = DocumentType.CERTIFICAT_SCOLARITE.name,
    val referenceNumber: String = "",
    val title: String = "",
    val studentId: String? = null,
    val studentName: String? = null,
    val generatedByName: String = "",
    val summaryText: String = "",
    val readerUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SubscriptionInvoice(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val invoiceNumber: String = "",
    val planCode: String = PlanCode.STARTER.name,
    val amountFcfa: Long = 10_000L,
    val periodLabel: String = "",
    val status: String = "EN_ATTENTE",
    val dueDateMillis: Long = 0L,
    val paidAtMillis: Long? = null,
    val readerUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val actorRole: String = "",
    val action: String = "",
    val entityType: String = "",
    val entityId: String = "",
    val details: String = "",
    val readerUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class ResultSubjectLine(
    val code: String = "",
    val name: String = "",
    val coefficient: Long = 1L,
    val credits: Long = 0L,
    val scoreOn20: Double = 0.0,
    val semester: String = "",
    val validated: Boolean = false,
    val appreciation: String = ""
)

@Keep
@IgnoreExtraProperties
data class AnnualResult(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolName: String = "",
    val category: String = SchoolCategory.COLLEGE_LYCEE.name,
    val studentId: String = "",
    val matricule: String = "",
    val studentFullName: String = "",
    val birthDate: String = "",
    val classId: String = "",
    val className: String = "",
    val level: String = "",
    val faculty: String? = null,
    val department: String? = null,
    val program: String? = null,
    val academicYear: String = "2026-2027",
    val periodLabel: String = "Résultats annuels 2026-2027",
    val average: Double = 0.0,
    val semester1Average: Double? = null,
    val semester2Average: Double? = null,
    val annualAverage: Double = 0.0,
    val rank: Long = 0L,
    val classSize: Long = 0L,
    val creditsObtained: Long = 0L,
    val creditsRequired: Long = 0L,
    val decision: String = ResultDecision.AJOURNE.name,
    val mention: String = "",
    val appreciation: String = "",
    val subjectLinesJson: String = "[]",
    val status: String = ResultStatus.BROUILLON.name,
    val scheduledPublishAtMillis: Long = 0L,
    val publishedAtMillis: Long = 0L,
    val publishedByUid: String? = null,
    val publishedByName: String? = null,
    val revisionCount: Long = 0L,
    val lastModifiedReason: String? = null,
    val verificationHash: String = "",
    val readerUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

