package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.model.*
import com.example.util.Formatters
import com.example.util.PdfAndCsvExporter
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class SchoolOperationsViewModel(
    private val academicRepo: AcademicRepository,
    private val financeRepo: FinanceAndCommsRepository,
    private val schoolRepo: SchoolRepository,
    private val currentUserId: String,
    private val actorName: String,
    private val activeSchoolFlow: StateFlow<School?>,
    private val activeRoleFlow: StateFlow<UserRole>,
    private val activeMemberFlow: StateFlow<SchoolMember?>,
    private val schoolMembersFlow: StateFlow<List<SchoolMember>> = MutableStateFlow(emptyList())
) : ViewModel() {

    private val _globalSearch = MutableStateFlow("")
    val globalSearch: StateFlow<String> = _globalSearch.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private fun <T> tenantFlow(loader: (String) -> Flow<List<T>>): StateFlow<List<T>> =
        activeSchoolFlow.flatMapLatest { school ->
            if (school == null) flowOf(emptyList())
            else loader(school.id).catch { err ->
                val firestoreError = generateSequence(err) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && err is CancellationException) throw err
                _statusBanner.value = extractFirestoreErrorMessage(err, "Erreur de lecture Firestore.")
                emit(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val classes: StateFlow<List<SchoolClass>> = tenantFlow { academicRepo.observeClasses(it, currentUserId) }
    val subjects: StateFlow<List<Subject>> = tenantFlow { academicRepo.observeSubjects(it, currentUserId) }
    val rooms: StateFlow<List<Classroom>> = tenantFlow { academicRepo.observeRooms(it, currentUserId) }
    val schedules: StateFlow<List<ScheduleEntry>> = tenantFlow { academicRepo.observeSchedules(it, currentUserId) }
    val allStudents: StateFlow<List<Student>> = tenantFlow { academicRepo.observeStudents(it, currentUserId) }
    val teachers: StateFlow<List<Teacher>> = tenantFlow { academicRepo.observeTeachers(it, currentUserId) }
    val allGrades: StateFlow<List<Grade>> = tenantFlow { academicRepo.observeGrades(it, currentUserId) }
    val allPayments: StateFlow<List<Payment>> = tenantFlow { financeRepo.observePayments(it, currentUserId) }
    private val rawAttendance: StateFlow<List<AttendanceRecord>> = tenantFlow { academicRepo.observeAttendance(it, currentUserId) }
    val announcements: StateFlow<List<Announcement>> = tenantFlow { financeRepo.observeAnnouncements(it, currentUserId) }
    private val rawDocuments: StateFlow<List<SchoolDocument>> = tenantFlow { financeRepo.observeDocuments(it, currentUserId) }
    val allAnnualResults: StateFlow<List<AnnualResult>> = tenantFlow { academicRepo.observeAnnualResults(it, currentUserId) }

    init {
        viewModelScope.launch {
            combine(activeSchoolFlow, activeRoleFlow, allAnnualResults, schoolMembersFlow) { sch, role, list, members ->
                Triple(sch, role, list to members)
            }.collect { (sch, role, pair) ->
                if (sch != null && role in setOf(UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR)) {
                    val now = System.currentTimeMillis()
                    val due = pair.first.filter {
                        it.status == ResultStatus.PROGRAMME.name && it.scheduledPublishAtMillis in 1L..now
                    }
                    if (due.isNotEmpty()) {
                        val promoted = due.map {
                            it.copy(
                                status = ResultStatus.PUBLIE.name,
                                publishedAtMillis = now,
                                publishedByUid = currentUserId,
                                publishedByName = actorName
                            )
                        }
                        academicRepo.saveAnnualResultsBatch(sch, promoted, pair.second)
                    }
                }
            }
        }
    }

    // Filtrage rôle Parent / Élève : ne voit que ses dossiers rattachés
    val visibleStudents: StateFlow<List<Student>> = combine(allStudents, activeRoleFlow, activeMemberFlow) { list, role, member ->
        if (role == UserRole.PARENT || role == UserRole.ELEVE) {
            val linked = member?.linkedStudentIds ?: emptyList()
            list.filter { it.id in linked }
        } else list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val visibleGrades: StateFlow<List<Grade>> = combine(allGrades, activeRoleFlow, activeMemberFlow) { list, role, member ->
        if (role == UserRole.PARENT || role == UserRole.ELEVE) {
            val linked = member?.linkedStudentIds ?: emptyList()
            list.filter { it.studentId in linked }
        } else list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val visiblePayments: StateFlow<List<Payment>> = combine(allPayments, activeRoleFlow, activeMemberFlow) { list, role, member ->
        if (role == UserRole.PARENT || role == UserRole.ELEVE) {
            val linked = member?.linkedStudentIds ?: emptyList()
            list.filter { it.studentId in linked }
        } else list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val attendance: StateFlow<List<AttendanceRecord>> = combine(rawAttendance, activeRoleFlow, activeMemberFlow) { list, role, member ->
        if (role == UserRole.PARENT || role == UserRole.ELEVE) {
            val linked = member?.linkedStudentIds ?: emptyList()
            list.filter { it.targetType == "ETUDIANT" && it.targetId in linked }
        } else list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val documents: StateFlow<List<SchoolDocument>> = combine(rawDocuments, activeRoleFlow, activeMemberFlow) { list, role, member ->
        if (role == UserRole.PARENT || role == UserRole.ELEVE) {
            val linked = member?.linkedStudentIds ?: emptyList()
            list.filter { !it.studentId.isNullOrBlank() && it.studentId in linked }
        } else list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val visibleAnnualResults: StateFlow<List<AnnualResult>> = combine(allAnnualResults, activeRoleFlow, activeMemberFlow) { list, role, member ->
        if (role == UserRole.PARENT || role == UserRole.ELEVE) {
            val linked = member?.linkedStudentIds ?: emptyList()
            list.filter { it.studentId in linked && Formatters.isResultVisibleToLearner(it) }
        } else list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val studentBalances: StateFlow<List<StudentBalance>> = combine(visibleStudents, visiblePayments) { stus, pays ->
        Formatters.computeStudentBalances(stus, pays)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val dashboardStats: StateFlow<DashboardStats> = combine(
        visibleStudents,
        teachers,
        classes,
        visiblePayments,
        attendance
    ) { stus, tchs, cls, pays, att ->
        val confirmedPays = pays.filter { it.providerStatus == ProviderStatus.CONFIRME.name }
        val totalCollected = confirmedPays.sumOf { it.amountFcfa }
        val totalExpected = stus.sumOf { it.tuitionTotalFcfa }
        val remaining = (totalExpected - totalCollected).coerceAtLeast(0L)
        val collectionRate = if (totalExpected > 0L) (totalCollected.toFloat() / totalExpected.toFloat()) * 100f else 0f

        val stuAtt = att.filter { it.targetType == "ETUDIANT" }
        val presentCount = stuAtt.count { it.status == AttendanceStatus.PRESENT.name || it.status == AttendanceStatus.RETARD.name }
        val attendanceRate = if (stuAtt.isNotEmpty()) (presentCount.toFloat() / stuAtt.size.toFloat()) * 100f else 100f

        val monthFmt = SimpleDateFormat("MMM", Locale.FRENCH)
        val groupedByMonth = stus.groupBy { st ->
            st.createdAt?.toDate()?.let { monthFmt.format(it).replaceFirstChar { c -> c.uppercase() } } ?: "Actuel"
        }
        val enrollmentSeries = if (groupedByMonth.isEmpty()) {
            listOf(MonthlyMetric("Actuel", 0f))
        } else {
            groupedByMonth.entries.map { MonthlyMetric(it.key, it.value.size.toFloat()) }
        }

        val byMethod = PaymentMethod.entries.associateWith { m ->
            confirmedPays.filter { it.method == m.name }.sumOf { it.amountFcfa }
        }

        val balances = Formatters.computeStudentBalances(stus, pays)
        val unpaid = balances.filter { it.remainingFcfa > 0L }

        val recent = buildList {
            confirmedPays.sortedByDescending { it.createdAt?.seconds ?: 0L }.take(3).forEach { p ->
                add(
                    ActivityFeedItem(
                        title = "Paiement ${Formatters.fcfa(p.amountFcfa)}",
                        subtitle = "${p.studentName} • ${PaymentMethod.fromRaw(p.method).label}",
                        timeLabel = Formatters.formatTimestamp(p.createdAt),
                        category = "FINANCE"
                    )
                )
            }
            stus.sortedByDescending { it.createdAt?.seconds ?: 0L }.take(3).forEach { s ->
                add(
                    ActivityFeedItem(
                        title = "Inscription ${s.fullName}",
                        subtitle = "${s.className} • ${s.matricule}",
                        timeLabel = Formatters.formatTimestamp(s.createdAt),
                        category = "ETUDIANT"
                    )
                )
            }
            att.filter { it.status != AttendanceStatus.PRESENT.name }
                .sortedByDescending { it.createdAt?.seconds ?: 0L }.take(2).forEach { a ->
                    add(
                        ActivityFeedItem(
                            title = "${AttendanceStatus.fromRaw(a.status).label} : ${a.targetName}",
                            subtitle = "${a.className} • ${a.dateIso}",
                            timeLabel = Formatters.formatTimestamp(a.createdAt),
                            category = "PRESENCE"
                        )
                    )
                }
        }

        DashboardStats(
            totalStudents = stus.size,
            totalTeachers = tchs.size,
            totalClasses = cls.size,
            totalCollectedFcfa = totalCollected,
            totalExpectedFcfa = totalExpected,
            remainingToCollectFcfa = remaining,
            collectionRatePercent = collectionRate.coerceIn(0f, 100f),
            attendanceRatePercent = attendanceRate.coerceIn(0f, 100f),
            enrollmentSeries = enrollmentSeries,
            paymentByMethod = byMethod,
            unpaidStudents = unpaid,
            recentActivities = recent
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DashboardStats())

    fun setGlobalSearch(query: String) {
        _globalSearch.value = query
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    private fun activeSchool(): School? {
        val s = activeSchoolFlow.value
        if (s == null) _statusBanner.value = "Aucun établissement actif sélectionné."
        return s
    }

    private fun logAction(school: School, action: String, entityType: String, entityId: String, details: String) {
        viewModelScope.launch {
            schoolRepo.recordAuditLog(school, actorName, activeRoleFlow.value, action, entityType, entityId, details)
        }
    }

    // --- CLASSES, MATIERES, SALLES, EMPLOIS DU TEMPS ---
    fun saveClass(item: SchoolClass) {
        val school = activeSchool() ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            val res = academicRepo.saveClass(school, item)
            _isProcessing.value = false
            res.onSuccess { id ->
                logAction(school, "ENREGISTREMENT_CLASSE", "SchoolClass", id, "Classe ${item.name} (${Formatters.fcfa(item.tuitionFeeFcfa)})")
                _statusBanner.value = "Classe « ${item.name} » enregistrée."
            }.onFailure { _statusBanner.value = it.localizedMessage ?: "Erreur lors de l'enregistrement." }
        }
    }

    fun saveSubject(item: Subject) {
        val school = activeSchool() ?: return
        viewModelScope.launch {
            academicRepo.saveSubject(school, item)
                .onSuccess { _statusBanner.value = "Matière « ${item.name} » (Coeff ${item.coefficient}) enregistrée." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Erreur matière." }
        }
    }

    fun saveClassroom(item: Classroom) {
        val school = activeSchool() ?: return
        viewModelScope.launch {
            academicRepo.saveClassroom(school, item)
                .onSuccess { _statusBanner.value = "Salle « ${item.name} » enregistrée." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Erreur salle." }
        }
    }

    fun saveSchedule(item: ScheduleEntry) {
        val school = activeSchool() ?: return
        viewModelScope.launch {
            academicRepo.saveSchedule(school, item)
                .onSuccess { _statusBanner.value = "Créneau ${item.dayOfWeek} ${item.startTime} ajouté." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Erreur emploi du temps." }
        }
    }

    // --- ETUDIANTS ---
    fun saveStudent(item: Student) {
        val school = activeSchool() ?: return
        val isNew = item.id.isBlank()
        val otherMatricules = allStudents.value
            .filter { it.id != item.id }
            .map { it.matricule.trim().uppercase(Locale.ROOT) }
            .toSet()
        val year = item.academicYear.ifBlank { school.academicYear }
        val finalMatricule = if (
            item.matricule.isBlank() ||
            item.matricule.trim().uppercase(Locale.ROOT) in otherMatricules
        ) {
            Formatters.generateUniqueMatricule(
                schoolName = school.name,
                category = SchoolCategory.fromRaw(school.category),
                academicYear = year,
                existingMatricules = otherMatricules
            )
        } else {
            item.matricule.trim()
        }

        val matEntry = "$finalMatricule • ${item.className} • $year (${StudentStatus.fromRaw(item.status).label})"
        val matHistory = if (item.matriculeHistory.isEmpty() || item.matriculeHistory.lastOrNull()?.startsWith("$finalMatricule • ${item.className} • $year") != true) {
            (item.matriculeHistory + matEntry).takeLast(25)
        } else item.matriculeHistory

        val pin = item.accessCodeDisplay?.takeIf { it.isNotBlank() }
            ?: Formatters.generateStudentAccessPin("${school.id}|$finalMatricule|${System.nanoTime()}")
        val hash = Formatters.computeStudentVerificationHash(school.id, finalMatricule, item.birthDate, pin)

        val history = if (item.historyNotes.isEmpty()) {
            listOf("${StudentStatus.fromRaw(item.status).label} en ${item.className} ($year) le ${Formatters.todayIso()}")
        } else item.historyNotes

        val prepared = item.copy(
            matricule = finalMatricule,
            academicYear = year,
            historyNotes = history,
            matriculeHistory = matHistory,
            accessCodeDisplay = pin,
            accessCodeHash = hash
        )

        viewModelScope.launch {
            _isProcessing.value = true
            val res = academicRepo.saveStudent(school, prepared)
            _isProcessing.value = false
            res.onSuccess { id ->
                logAction(
                    school,
                    if (isNew) "INSCRIPTION_ETUDIANT" else "MISE_A_JOUR_ETUDIANT",
                    "Student",
                    id,
                    "${prepared.fullName} ($finalMatricule) — ${prepared.className}"
                )
                _statusBanner.value = "${prepared.fullName} ($finalMatricule) enregistré. Code portail : $pin"
            }.onFailure { _statusBanner.value = it.localizedMessage ?: "Erreur étudiant." }
        }
    }

    fun deleteStudent(student: Student) {
        val school = activeSchool() ?: return
        viewModelScope.launch {
            academicRepo.deleteStudent(school.id, student.id)
                .onSuccess {
                    logAction(school, "SUPPRESSION_ETUDIANT", "Student", student.id, "Suppression ${student.fullName} (${student.matricule})")
                    _statusBanner.value = "Dossier de ${student.fullName} supprimé."
                }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Suppression refusée (réservé au Directeur)." }
        }
    }

    fun importStudentsCsv(context: Context, uri: Uri, defaultClass: SchoolClass?) {
        val school = activeSchool() ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            val parsed = PdfAndCsvExporter.parseStudentsFromCsvUri(
                context, uri, defaultClass, school, allStudents.value.size
            )
            val usedMatricules = allStudents.value.map { it.matricule.trim().uppercase(Locale.ROOT) }.toMutableSet()
            var ok = 0
            parsed.forEach { st ->
                val uniqueMat = Formatters.generateUniqueMatricule(
                    school.name,
                    SchoolCategory.fromRaw(school.category),
                    st.academicYear,
                    usedMatricules
                )
                usedMatricules.add(uniqueMat.uppercase(Locale.ROOT))
                val pin = Formatters.generateStudentAccessPin("${school.id}|$uniqueMat|${System.nanoTime()}")
                val hash = Formatters.computeStudentVerificationHash(school.id, uniqueMat, st.birthDate, pin)
                val ready = st.copy(
                    matricule = uniqueMat,
                    matriculeHistory = listOf("$uniqueMat • ${st.className} • ${st.academicYear} (Importé)"),
                    accessCodeDisplay = pin,
                    accessCodeHash = hash
                )
                if (academicRepo.saveStudent(school, ready).isSuccess) ok++
            }
            _isProcessing.value = false
            logAction(school, "IMPORT_CSV_ETUDIANTS", "Student", "batch", "$ok étudiant(s) importé(s)")
            _statusBanner.value = if (ok > 0) "$ok étudiant(s) importé(s) avec matricule unique et code confidentiel." else "Aucune ligne valide trouvée dans le CSV."
        }
    }

    // --- ENSEIGNANTS ---
    fun saveTeacher(item: Teacher) {
        val school = activeSchool() ?: return
        val code = item.employeeCode.ifBlank { Formatters.generateEmployeeCode(teachers.value.size) }
        val prepared = item.copy(employeeCode = code)
        viewModelScope.launch {
            academicRepo.saveTeacher(school, prepared)
                .onSuccess { id ->
                    logAction(school, "ENREGISTREMENT_ENSEIGNANT", "Teacher", id, "${prepared.fullName} ($code)")
                    _statusBanner.value = "Enseignant ${prepared.fullName} ($code) enregistré."
                }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Erreur enseignant." }
        }
    }

    fun incrementTeacherAbsence(teacher: Teacher) {
        val school = activeSchool() ?: return
        viewModelScope.launch {
            val updated = teacher.copy(absencesCount = teacher.absencesCount + 1L)
            academicRepo.saveTeacher(school, updated).onSuccess {
                academicRepo.saveAttendance(
                    school,
                    AttendanceRecord(
                        schoolId = school.id,
                        targetType = "ENSEIGNANT",
                        targetId = teacher.id,
                        targetName = teacher.fullName,
                        classId = "STAFF",
                        className = "Corps enseignant",
                        dateIso = Formatters.todayIso(),
                        status = AttendanceStatus.ABSENT.name,
                        justified = false,
                        reason = "Absence signalée"
                    )
                )
                _statusBanner.value = "Absence enregistrée pour ${teacher.fullName}."
            }
        }
    }

    // --- NOTES & BULLETINS ---
    fun saveGrade(item: Grade) {
        val school = activeSchool() ?: return
        viewModelScope.launch {
            academicRepo.saveGrade(school, item)
                .onSuccess { id ->
                    logAction(school, "SAISIE_NOTE", "Grade", id, "${item.studentName} • ${item.subjectName}: ${item.score}/20")
                    _statusBanner.value = "Note ${item.score}/20 enregistrée pour ${item.studentName}."
                }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Erreur saisie de note." }
        }
    }

    fun importGradesCsv(context: Context, uri: Uri, defaultTerm: TermPeriod) {
        val school = activeSchool() ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            val parsed = PdfAndCsvExporter.parseGradesFromCsvUri(
                context = context,
                uri = uri,
                students = allStudents.value,
                subjects = subjects.value,
                defaultTerm = defaultTerm,
                teacherName = actorName
            )
            var ok = 0
            parsed.forEach { g ->
                if (academicRepo.saveGrade(school, g).isSuccess) ok++
            }
            _isProcessing.value = false
            logAction(school, "IMPORT_CSV_NOTES", "Grade", "batch", "$ok note(s) importée(s)")
            _statusBanner.value = if (ok > 0) "$ok note(s) importée(s) avec succès." else "Aucune note valide trouvée (format : Matricule;Matiere;Note;Coeff)."
        }
    }

    // --- RESULTATS DE FIN D'ANNEE & PORTAIL ---
    fun generateAnnualResultsDrafts(targetClassId: String?, periodLabel: String) {
        val school = activeSchool() ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            val members = schoolMembersFlow.value
            val staffUids = (members.filter {
                it.active && it.role != UserRole.PARENT.name && it.role != UserRole.ELEVE.name
            }.map { it.uid } + school.ownerUid + currentUserId).distinct()

            val computed = Formatters.computeAnnualResults(
                school = school,
                students = allStudents.value,
                subjects = subjects.value,
                grades = allGrades.value,
                existingResults = allAnnualResults.value,
                targetClassId = targetClassId,
                periodLabel = periodLabel.ifBlank { "Résultats annuels ${school.academicYear}" },
                staffReaderUids = staffUids
            )
            val editable = computed.filter { it.status != ResultStatus.PUBLIE.name && it.status != ResultStatus.PROGRAMME.name }
            if (editable.isEmpty()) {
                _isProcessing.value = false
                _statusBanner.value = if (computed.isEmpty()) "Aucun élève trouvé pour ce périmètre." else "Tous les résultats concernés sont déjà publiés (utilisez 'Corriger')."
                return@launch
            }
            val res = academicRepo.saveAnnualResultsBatch(school, editable, members)
            _isProcessing.value = false
            res.onSuccess { count ->
                val skipped = computed.size - editable.size
                logAction(school, "CALCUL_RESULTATS_ANNUELS", "AnnualResult", targetClassId ?: "all", "$count brouillon(s) généré(s)")
                _statusBanner.value = if (skipped > 0) {
                    "$count brouillon(s) calculé(s) ($skipped déjà publié(s) préservé(s))."
                } else {
                    "$count résultat(s) calculé(s) en brouillon."
                }
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Erreur lors du calcul des résultats."
            }
        }
    }

    fun saveOrCorrectAnnualResult(result: AnnualResult, correctionReason: String?) {
        val school = activeSchool() ?: return
        val wasPublishedOrScheduled = result.status == ResultStatus.PUBLIE.name || result.status == ResultStatus.PROGRAMME.name
        if (wasPublishedOrScheduled && correctionReason.isNullOrBlank()) {
            _statusBanner.value = "Un motif est obligatoire pour corriger un résultat publié ou programmé."
            return
        }
        viewModelScope.launch {
            _isProcessing.value = true
            val stu = allStudents.value.firstOrNull { it.id == result.studentId }
            val freshHash = if (stu != null && !stu.accessCodeHash.isNullOrBlank()) stu.accessCodeHash!! else result.verificationHash
            val updated = result.copy(
                verificationHash = freshHash,
                revisionCount = if (!correctionReason.isNullOrBlank() || wasPublishedOrScheduled) result.revisionCount + 1L else result.revisionCount,
                lastModifiedReason = correctionReason?.trim()?.ifBlank { null } ?: result.lastModifiedReason
            )
            val res = academicRepo.saveAnnualResult(school, updated, schoolMembersFlow.value)
            _isProcessing.value = false
            res.onSuccess { id ->
                logAction(
                    school,
                    if (wasPublishedOrScheduled) "CORRECTION_RESULTAT_PUBLIE" else "MODIFICATION_BROUILLON_RESULTAT",
                    "AnnualResult",
                    id,
                    "${updated.studentFullName} (${updated.matricule}) : ${updated.annualAverage}/20 — ${updated.decision}" +
                        (correctionReason?.let { " | Motif : $it" } ?: "")
                )
                _statusBanner.value = "Résultat de ${updated.studentFullName} enregistré."
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Erreur lors de la sauvegarde du résultat."
            }
        }
    }

    fun publishOrScheduleResults(
        results: List<AnnualResult>,
        targetStatus: ResultStatus,
        scheduledAtMillis: Long = 0L
    ) {
        val school = activeSchool() ?: return
        if (results.isEmpty()) {
            _statusBanner.value = "Aucun résultat sélectionné."
            return
        }
        viewModelScope.launch {
            _isProcessing.value = true
            val now = System.currentTimeMillis()
            val effectiveStatus = if (targetStatus == ResultStatus.PROGRAMME && scheduledAtMillis <= now) {
                ResultStatus.PUBLIE
            } else targetStatus

            val updatedList = results.map { item ->
                item.copy(
                    status = effectiveStatus.name,
                    scheduledPublishAtMillis = if (effectiveStatus == ResultStatus.PROGRAMME) scheduledAtMillis else 0L,
                    publishedAtMillis = if (effectiveStatus == ResultStatus.PUBLIE) now else item.publishedAtMillis,
                    publishedByUid = if (effectiveStatus == ResultStatus.PUBLIE || effectiveStatus == ResultStatus.VALIDE) currentUserId else item.publishedByUid,
                    publishedByName = if (effectiveStatus == ResultStatus.PUBLIE || effectiveStatus == ResultStatus.VALIDE) actorName else item.publishedByName
                )
            }
            val res = academicRepo.saveAnnualResultsBatch(school, updatedList, schoolMembersFlow.value)
            if (res.isSuccess && effectiveStatus == ResultStatus.PUBLIE) {
                financeRepo.publishAnnouncement(
                    school = school,
                    title = "Résultats publiés — ${school.academicYear}",
                    message = "${updatedList.size} résultat(s) officiel(s) sont désormais consultables dans « Consulter mes résultats » avec votre matricule et code confidentiel.",
                    category = "RESULTATS",
                    channel = ChannelType.INTERNE,
                    targetAudience = "TOUS",
                    authorName = actorName
                )
            }
            _isProcessing.value = false
            res.onSuccess { count ->
                logAction(school, "STATUT_RESULTATS_${effectiveStatus.name}", "AnnualResult", "batch", "$count résultat(s) -> ${effectiveStatus.label}")
                _statusBanner.value = when (effectiveStatus) {
                    ResultStatus.VALIDE -> "$count résultat(s) validé(s) avant publication."
                    ResultStatus.PROGRAMME -> "$count résultat(s) programmé(s) pour le ${Formatters.formatDateTimeMillis(scheduledAtMillis)}."
                    ResultStatus.PUBLIE -> "$count résultat(s) publié(s) et disponibles sur le portail."
                    ResultStatus.BROUILLON -> "$count résultat(s) repassé(s) en brouillon."
                }
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Action refusée (réservée à la Direction)."
            }
        }
    }

    fun verifyAndConsultResult(
        schoolId: String,
        category: SchoolCategory,
        matricule: String,
        birthDate: String,
        accessPin: String
    ): Result<AnnualResult> {
        val cleanMat = matricule.trim()
        val cleanBirth = birthDate.trim()
        val cleanPin = accessPin.trim()
        if (cleanMat.isBlank() || cleanBirth.isBlank() || cleanPin.isBlank()) {
            return Result.failure(IllegalArgumentException("Veuillez saisir le matricule, la date de naissance et votre code confidentiel."))
        }

        val candidate = allAnnualResults.value.firstOrNull {
            it.schoolId == schoolId &&
                it.category == category.name &&
                it.matricule.equals(cleanMat, ignoreCase = true)
        } ?: return Result.failure(
            SecurityException("Aucun résultat publié trouvé pour ces identifiants dans cette catégorie.")
        )

        val computedHash = Formatters.computeStudentVerificationHash(
            schoolId = schoolId,
            matricule = candidate.matricule,
            birthDate = cleanBirth,
            pin = cleanPin
        )
        if (computedHash != candidate.verificationHash) {
            return Result.failure(
                SecurityException("Vérification échouée : matricule, date de naissance ou code d'accès confidentiel invalide.")
            )
        }
        if (!Formatters.isResultVisibleToLearner(candidate)) {
            val schedLabel = if (candidate.status == ResultStatus.PROGRAMME.name && candidate.scheduledPublishAtMillis > 0L) {
                " Publication prévue le ${Formatters.formatDateTimeMillis(candidate.scheduledPublishAtMillis)}."
            } else ""
            return Result.failure(
                IllegalStateException("Ces résultats ne sont pas encore publiés.$schedLabel")
            )
        }
        return Result.success(candidate)
    }

    // --- FINANCES & MOBILE MONEY ---
    fun recordPayment(
        student: Student,
        tranche: PaymentTranche,
        amountFcfa: Long,
        method: PaymentMethod,
        payerPhone: String,
        manualRef: String,
        notes: String
    ) {
        val school = activeSchool() ?: return
        if (amountFcfa <= 0L) {
            _statusBanner.value = "Le montant doit être supérieur à 0 FCFA."
            return
        }
        viewModelScope.launch {
            _isProcessing.value = true
            val receiptNum = Formatters.generateReceiptNumber(allPayments.value.size)
            val extRef = manualRef.ifBlank { receiptNum }

            val dispatch = if (method == PaymentMethod.MTN_MOMO || method == PaymentMethod.ORANGE_MONEY) {
                financeRepo.initiateMobileMoneyRequest(school, method, payerPhone, amountFcfa, extRef)
            } else {
                ExternalDispatchResult(
                    status = ProviderStatus.CONFIRME.name,
                    reference = extRef,
                    message = "Paiement ${method.label} enregistré."
                )
            }

            val payment = Payment(
                schoolId = school.id,
                receiptNumber = receiptNum,
                studentId = student.id,
                studentName = student.fullName,
                studentMatricule = student.matricule,
                classId = student.classId,
                className = student.className,
                tranche = tranche.name,
                amountFcfa = amountFcfa,
                method = method.name,
                providerStatus = dispatch.status,
                transactionRef = dispatch.reference,
                recordedByUid = currentUserId,
                recordedByName = actorName,
                notes = notes.ifBlank { dispatch.message }
            )

            val res = financeRepo.recordPayment(school, payment)
            _isProcessing.value = false
            res.onSuccess { id ->
                logAction(
                    school, "ENCAISSEMENT_SCOLARITE", "Payment", id,
                    "$receiptNum : ${Formatters.fcfa(amountFcfa)} (${method.label} - ${dispatch.status})"
                )
                _statusBanner.value = dispatch.message
            }.onFailure { _statusBanner.value = it.localizedMessage ?: "Erreur paiement." }
        }
    }

    // --- PRESENCES ---
    fun recordAttendance(
        student: Student,
        status: AttendanceStatus,
        justified: Boolean,
        reason: String,
        dateIso: String = Formatters.todayIso()
    ) {
        val school = activeSchool() ?: return
        viewModelScope.launch {
            val record = AttendanceRecord(
                schoolId = school.id,
                targetType = "ETUDIANT",
                targetId = student.id,
                targetName = student.fullName,
                classId = student.classId,
                className = student.className,
                dateIso = dateIso,
                status = status.name,
                justified = justified,
                reason = reason.ifBlank { null },
                recordedByUid = currentUserId
            )
            academicRepo.saveAttendance(school, record)
                .onSuccess { _statusBanner.value = "${student.fullName} marqué(e) ${status.label}." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Erreur présence." }
        }
    }

    fun justifyAttendance(record: AttendanceRecord, reason: String) {
        val school = activeSchool() ?: return
        viewModelScope.launch {
            academicRepo.saveAttendance(school, record.copy(justified = true, reason = reason))
                .onSuccess { _statusBanner.value = "Absence justifiée pour ${record.targetName}." }
        }
    }

    // --- COMMUNICATION & DOCUMENTS PDF ---
    fun publishAnnouncement(
        title: String,
        message: String,
        category: String,
        channel: ChannelType,
        targetAudience: String
    ) {
        val school = activeSchool() ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            val res = financeRepo.publishAnnouncement(
                school, title, message, category, channel, targetAudience, actorName
            )
            _isProcessing.value = false
            res.onSuccess { delivery ->
                _statusBanner.value = when (delivery) {
                    "ENVOYE_API" -> "Message diffusé via ${channel.label}."
                    "EN_ATTENTE_CONFIG_API" -> "Annonce enregistrée (API ${channel.label} en attente de configuration)."
                    "ECHEC_API" -> "Annonce enregistrée mais échec d'envoi API ${channel.label}."
                    else -> "Annonce publiée."
                }
            }.onFailure { _statusBanner.value = it.localizedMessage ?: "Erreur publication." }
        }
    }

    fun generateAndOpenPdf(context: Context, build: (School) -> Pair<SchoolDocument, File>) {
        val school = activeSchool() ?: return
        val isLearnerOrParent = activeRoleFlow.value == UserRole.PARENT || activeRoleFlow.value == UserRole.ELEVE
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val (docMeta, file) = build(school)
                if (!isLearnerOrParent) {
                    financeRepo.archiveDocument(school, docMeta)
                    logAction(school, "GENERATION_DOCUMENT", "SchoolDocument", docMeta.referenceNumber, docMeta.title)
                }
                _isProcessing.value = false
                PdfAndCsvExporter.openOrShareFile(context, file, "application/pdf", docMeta.title)
                _statusBanner.value = if (isLearnerOrParent) {
                    "${docMeta.title} prêt au téléchargement."
                } else {
                    "${docMeta.title} archivé et prêt."
                }
            } catch (e: Exception) {
                _isProcessing.value = false
                _statusBanner.value = "Erreur PDF : ${e.localizedMessage ?: "Impossible de générer"}"
            }
        }
    }

    fun exportStudentsCsvAndOpen(context: Context) {
        val school = activeSchool() ?: return
        val file = PdfAndCsvExporter.exportStudentsCsv(context, visibleStudents.value)
        logAction(school, "EXPORT_CSV_ETUDIANTS", "SchoolDocument", "CSV", "${visibleStudents.value.size} étudiants")
        PdfAndCsvExporter.openOrShareFile(context, file, "text/csv", "Exporter les étudiants (CSV)")
        _statusBanner.value = "Fichier CSV exporté (${visibleStudents.value.size} élèves)."
    }

    fun exportResultsStatsCsvAndOpen(context: Context, list: List<AnnualResult>) {
        val school = activeSchool() ?: return
        val file = PdfAndCsvExporter.exportResultsStatisticsCsv(context, list)
        logAction(school, "EXPORT_STATS_RESULTATS_CSV", "AnnualResult", "CSV", "${list.size} résultat(s)")
        PdfAndCsvExporter.openOrShareFile(context, file, "text/csv", "Exporter statistiques résultats (CSV)")
        _statusBanner.value = "Statistiques exportées en CSV (${list.size} dossier(s))."
    }
}
