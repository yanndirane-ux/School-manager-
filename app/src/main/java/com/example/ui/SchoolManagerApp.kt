package com.example.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.model.*
import com.example.ui.theme.*
import com.example.util.Formatters
import com.example.util.PdfAndCsvExporter
import com.example.viewmodel.SchoolOperationsViewModel
import com.example.viewmodel.WorkspaceViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import java.util.Locale

private data class NavModule(
    val route: String,
    val label: String,
    val shortLabel: String,
    val icon: ImageVector,
    val allowedRoles: Set<UserRole>
)

private val ALL_MODULES = listOf(
    NavModule(
        route = "dashboard",
        label = "Tableau de bord",
        shortLabel = "Accueil",
        icon = Icons.Default.Dashboard,
        allowedRoles = UserRole.entries.toSet()
    ),
    NavModule(
        route = "students",
        label = "Étudiants",
        shortLabel = "Élèves",
        icon = Icons.Default.People,
        allowedRoles = setOf(
            UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR, UserRole.SECRETAIRE,
            UserRole.COMPTABLE, UserRole.ENSEIGNANT, UserRole.ELEVE, UserRole.PARENT
        )
    ),
    NavModule(
        route = "classes",
        label = "Classes & Cours",
        shortLabel = "Classes",
        icon = Icons.Default.Class,
        allowedRoles = setOf(
            UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR, UserRole.SECRETAIRE,
            UserRole.ENSEIGNANT, UserRole.ELEVE
        )
    ),
    NavModule(
        route = "teachers",
        label = "Enseignants",
        shortLabel = "Profs",
        icon = Icons.Default.Badge,
        allowedRoles = setOf(UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR, UserRole.SECRETAIRE)
    ),
    NavModule(
        route = "grades",
        label = "Notes & Bulletins",
        shortLabel = "Notes",
        icon = Icons.Default.Grade,
        allowedRoles = setOf(
            UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR, UserRole.SECRETAIRE,
            UserRole.ENSEIGNANT, UserRole.ELEVE, UserRole.PARENT
        )
    ),
    NavModule(
        route = "results_admin",
        label = "Résultats annuels",
        shortLabel = "Résultats",
        icon = Icons.Default.Assessment,
        allowedRoles = setOf(
            UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR, UserRole.SECRETAIRE, UserRole.ENSEIGNANT
        )
    ),
    NavModule(
        route = "results_portal",
        label = "Consulter mes résultats",
        shortLabel = "Portail",
        icon = Icons.Default.Verified,
        allowedRoles = UserRole.entries.toSet()
    ),
    NavModule(
        route = "finance",
        label = "Finances & FCFA",
        shortLabel = "Finances",
        icon = Icons.Default.Payments,
        allowedRoles = setOf(
            UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR, UserRole.COMPTABLE,
            UserRole.SECRETAIRE, UserRole.PARENT
        )
    ),
    NavModule(
        route = "attendance",
        label = "Présences & Absences",
        shortLabel = "Présences",
        icon = Icons.Default.FactCheck,
        allowedRoles = setOf(
            UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR, UserRole.SECRETAIRE,
            UserRole.ENSEIGNANT, UserRole.ELEVE, UserRole.PARENT
        )
    ),
    NavModule(
        route = "comms",
        label = "Comms & Documents",
        shortLabel = "Docs",
        icon = Icons.Default.Campaign,
        allowedRoles = UserRole.entries.toSet()
    ),
    NavModule(
        route = "saas_settings",
        label = "SaaS & Paramètres",
        shortLabel = "SaaS",
        icon = Icons.Default.Settings,
        allowedRoles = setOf(
            UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR, UserRole.SECRETAIRE, UserRole.COMPTABLE
        )
    )
)

@Composable
fun SchoolManagerApp() {
    val context = LocalContext.current
    val credentialManager = remember { CredentialManager.create(context) }
    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    var checkingAutoSignIn by remember { mutableStateOf(Firebase.auth.currentUser == null) }

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            AuthManager.attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { checkingAutoSignIn = false },
                onUnauthenticated = { checkingAutoSignIn = false },
                scope = this
            )
        } else {
            checkingAutoSignIn = false
        }
    }

    if (checkingAutoSignIn && currentUser == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MidnightNavy),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(color = SkyBlueLight)
                Text(
                    "SchoolManager",
                    style = MaterialTheme.typography.headlineMedium,
                    color = PureWhite
                )
                Text(
                    "Vérification de votre session...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IceBlueContainer
                )
            }
        }
        return
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { checkingAutoSignIn = false })
        return
    }

    val scope = rememberCoroutineScope()
    key(user.uid) {
        AuthenticatedWorkspace(
            context = context,
            uid = user.uid,
            email = user.email.orEmpty(),
            displayName = user.displayName?.ifBlank { null } ?: user.email?.substringBefore("@") ?: "Utilisateur",
            initialEmailVerified = user.isEmailVerified,
            onSignOut = {
                AuthManager.signOut(credentialManager = credentialManager, scope = scope)
            }
        )
    }
}

@Composable
private fun AuthenticatedWorkspace(
    context: Context,
    uid: String,
    email: String,
    displayName: String,
    initialEmailVerified: Boolean,
    onSignOut: () -> Unit
) {
    val workspaceVm: WorkspaceViewModel = viewModel(
        key = "workspace_$uid",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return WorkspaceViewModel(
                    schoolRepo = SchoolRepository(context),
                    currentUserId = uid,
                    userEmail = email,
                    userDisplayName = displayName
                ) as T
            }
        }
    )

    val opsVm: SchoolOperationsViewModel = viewModel(
        key = "ops_$uid",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SchoolOperationsViewModel(
                    academicRepo = AcademicRepository(context),
                    financeRepo = FinanceAndCommsRepository(context),
                    schoolRepo = SchoolRepository(context),
                    currentUserId = uid,
                    actorName = displayName,
                    activeSchoolFlow = workspaceVm.activeSchool,
                    activeRoleFlow = workspaceVm.effectiveRole,
                    activeMemberFlow = workspaceVm.activeMember,
                    schoolMembersFlow = workspaceVm.schoolMembers
                ) as T
            }
        }
    )

    val schoolsState by workspaceVm.schoolsState.collectAsStateWithLifecycle()
    val saasPlans by workspaceVm.saasPlans.collectAsStateWithLifecycle()
    val pendingInvitations by workspaceVm.pendingInvitations.collectAsStateWithLifecycle()
    val activeSchool by workspaceVm.activeSchool.collectAsStateWithLifecycle()
    val effectiveRole by workspaceVm.effectiveRole.collectAsStateWithLifecycle()
    val schoolMembers by workspaceVm.schoolMembers.collectAsStateWithLifecycle()
    val schoolInvitations by workspaceVm.schoolInvitations.collectAsStateWithLifecycle()
    val subscriptionInvoices by workspaceVm.subscriptionInvoices.collectAsStateWithLifecycle()
    val auditLogs by workspaceVm.auditLogs.collectAsStateWithLifecycle()
    val isWorkspaceBusy by workspaceVm.isBusy.collectAsStateWithLifecycle()
    val workspaceFeedback by workspaceVm.feedbackMessage.collectAsStateWithLifecycle()

    val classes by opsVm.classes.collectAsStateWithLifecycle()
    val subjects by opsVm.subjects.collectAsStateWithLifecycle()
    val rooms by opsVm.rooms.collectAsStateWithLifecycle()
    val schedules by opsVm.schedules.collectAsStateWithLifecycle()
    val visibleStudents by opsVm.visibleStudents.collectAsStateWithLifecycle()
    val teachers by opsVm.teachers.collectAsStateWithLifecycle()
    val visibleGrades by opsVm.visibleGrades.collectAsStateWithLifecycle()
    val visibleAnnualResults by opsVm.visibleAnnualResults.collectAsStateWithLifecycle()
    val visiblePayments by opsVm.visiblePayments.collectAsStateWithLifecycle()
    val studentBalances by opsVm.studentBalances.collectAsStateWithLifecycle()
    val attendance by opsVm.attendance.collectAsStateWithLifecycle()
    val announcements by opsVm.announcements.collectAsStateWithLifecycle()
    val documents by opsVm.documents.collectAsStateWithLifecycle()
    val dashboardStats by opsVm.dashboardStats.collectAsStateWithLifecycle()
    val globalSearch by opsVm.globalSearch.collectAsStateWithLifecycle()
    val opsBanner by opsVm.statusBanner.collectAsStateWithLifecycle()
    val isOpsProcessing by opsVm.isProcessing.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val authScope = rememberCoroutineScope()
    var isEmailVerified by remember(uid, initialEmailVerified) { mutableStateOf(initialEmailVerified) }
    var openSuperAdminConsoleWithoutSchool by remember { mutableStateOf(false) }
    var createAdditionalSchool by remember { mutableStateOf(false) }

    LaunchedEffect(workspaceFeedback) {
        val msg = workspaceFeedback ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        workspaceVm.clearFeedback()
    }

    LaunchedEffect(opsBanner) {
        val msg = opsBanner ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        opsVm.clearBanner()
    }

    val schoolsList = (schoolsState as? UiState.Success)?.data ?: emptyList()
    var lastSchoolCount by remember { mutableIntStateOf(schoolsList.size) }
    LaunchedEffect(schoolsList.size) {
        if (schoolsList.size > lastSchoolCount) {
            createAdditionalSchool = false
        }
        lastSchoolCount = schoolsList.size
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { outerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(outerPadding)
        ) {
            when (val state = schoolsState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = SkyBluePrimary)
                            Text(
                                "Chargement de votre espace SchoolManager...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SlateMuted
                            )
                        }
                    }
                }

                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CoralContainer),
                            modifier = Modifier.widthIn(max = 460.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    "Erreur d'accès à l'espace",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CoralError
                                )
                                Text(state.message, style = MaterialTheme.typography.bodyMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = onSignOut) {
                                        Text("Déconnexion")
                                    }
                                }
                            }
                        }
                    }
                }

                is UiState.Success -> {
                    if ((schoolsList.isEmpty() && !openSuperAdminConsoleWithoutSchool) || createAdditionalSchool) {
                        if (createAdditionalSchool) {
                            BackHandler { createAdditionalSchool = false }
                        }
                        OnboardingScreen(
                            userDisplayName = displayName,
                            userEmail = email,
                            isEmailVerified = isEmailVerified,
                            isSuperAdmin = workspaceVm.isSuperAdmin,
                            plans = saasPlans,
                            invitations = pendingInvitations,
                            isBusy = isWorkspaceBusy,
                            onCreateSchool = { name, slogan, cat, type, city, country, year, phone, schEmail, plan, creatorRole, facs, depts, progs, passingAvg, termMode ->
                                workspaceVm.createSchool(
                                    name = name,
                                    slogan = slogan,
                                    category = cat,
                                    schoolType = type,
                                    city = city,
                                    country = country,
                                    academicYear = year,
                                    phone = phone,
                                    email = schEmail,
                                    planCode = plan,
                                    creatorRole = creatorRole,
                                    faculties = facs,
                                    departments = depts,
                                    programs = progs,
                                    passingAverage = passingAvg,
                                    termMode = termMode,
                                    onSuccess = {
                                        createAdditionalSchool = false
                                        openSuperAdminConsoleWithoutSchool = false
                                    }
                                )
                            },
                            onAcceptInvitation = { inv ->
                                workspaceVm.acceptInvitation(inv) {
                                    createAdditionalSchool = false
                                    openSuperAdminConsoleWithoutSchool = false
                                }
                            },
                            onRefreshVerification = {
                                authScope.launch {
                                    AuthManager.reloadCurrentUser().onSuccess { verified ->
                                        isEmailVerified = verified
                                        workspaceVm.refreshEmailVerificationStatus(verified)
                                        snackbarHostState.showSnackbar(
                                            if (verified) "Adresse e-mail vérifiée." else "E-mail toujours en attente de vérification."
                                        )
                                    }
                                }
                            },
                            onSendVerificationEmail = {
                                authScope.launch {
                                    AuthManager.sendEmailVerification()
                                        .onSuccess { snackbarHostState.showSnackbar("E-mail de vérification envoyé à $email.") }
                                        .onFailure { snackbarHostState.showSnackbar(it.localizedMessage ?: "Envoi impossible.") }
                                }
                            },
                            onOpenSuperAdmin = {
                                createAdditionalSchool = false
                                openSuperAdminConsoleWithoutSchool = true
                            },
                            onSignOut = {
                                if (createAdditionalSchool) {
                                    createAdditionalSchool = false
                                } else {
                                    onSignOut()
                                }
                            }
                        )
                    } else {
                        val visibleModules = remember(effectiveRole, activeSchool) {
                            if (activeSchool == null && workspaceVm.isSuperAdmin) {
                                listOf(ALL_MODULES.last())
                            } else {
                                ALL_MODULES.filter { effectiveRole in it.allowedRoles }
                            }
                        }
                        var currentRoute by remember(activeSchool?.id) {
                            mutableStateOf(
                                if (activeSchool == null && workspaceVm.isSuperAdmin) "saas_settings"
                                else "dashboard"
                            )
                        }
                        LaunchedEffect(visibleModules) {
                            if (visibleModules.none { it.route == currentRoute }) {
                                currentRoute = visibleModules.firstOrNull()?.route ?: "dashboard"
                            }
                        }

                        if (currentRoute != "dashboard" && activeSchool != null) {
                            BackHandler { currentRoute = "dashboard" }
                        } else if (activeSchool == null && openSuperAdminConsoleWithoutSchool) {
                            BackHandler { openSuperAdminConsoleWithoutSchool = false }
                        }

                        AppShell(
                            displayName = displayName,
                            email = email,
                            isEmailVerified = isEmailVerified,
                            activeSchool = activeSchool,
                            allSchools = schoolsList,
                            role = effectiveRole,
                            isSuperAdmin = workspaceVm.isSuperAdmin,
                            modules = visibleModules,
                            currentRoute = currentRoute,
                            globalSearch = globalSearch,
                            onSearchChange = { q ->
                                opsVm.setGlobalSearch(q)
                                if (q.isNotBlank() && currentRoute == "dashboard" && visibleModules.any { it.route == "students" }) {
                                    currentRoute = "students"
                                }
                            },
                            onNavigate = { currentRoute = it },
                            onSelectSchool = { id ->
                                workspaceVm.selectSchool(id)
                                openSuperAdminConsoleWithoutSchool = false
                            },
                            onCreateNewSchool = { createAdditionalSchool = true },
                            onSignOut = onSignOut
                        ) {
                            val school = activeSchool
                            val isSuspended = school != null &&
                                SubscriptionStatus.fromRaw(school.subscriptionStatus) == SubscriptionStatus.SUSPENDU &&
                                !workspaceVm.isSuperAdmin

                            if (isSuspended && currentRoute != "saas_settings") {
                                SuspendedSchoolNotice(
                                    school = school!!,
                                    canManageSettings = effectiveRole in setOf(UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR, UserRole.COMPTABLE),
                                    onOpenSettings = { currentRoute = "saas_settings" }
                                )
                            } else if (school == null && currentRoute != "saas_settings") {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    if (schoolsList.isNotEmpty()) {
                                        CircularProgressIndicator(color = SkyBluePrimary)
                                    } else {
                                        Text("Sélectionnez ou créez un établissement.")
                                    }
                                }
                            } else {
                                when (currentRoute) {
                                    "dashboard" -> DashboardScreen(
                                        school = school!!,
                                        role = effectiveRole,
                                        stats = dashboardStats,
                                        allSchools = schoolsList,
                                        members = schoolMembers,
                                        invitations = schoolInvitations,
                                        auditLogs = auditLogs,
                                        students = visibleStudents,
                                        balances = studentBalances,
                                        grades = visibleGrades,
                                        subjects = subjects,
                                        schedules = schedules,
                                        attendance = attendance,
                                        announcements = announcements,
                                        onNavigateModule = { currentRoute = it }
                                    )

                                    "students" -> StudentsScreen(
                                        school = school!!,
                                        role = effectiveRole,
                                        students = visibleStudents,
                                        classes = classes,
                                        balances = studentBalances,
                                        globalSearch = globalSearch,
                                        onSaveStudent = { opsVm.saveStudent(it) },
                                        onDeleteStudent = { opsVm.deleteStudent(it) },
                                        onImportCsv = { uri, cls -> opsVm.importStudentsCsv(context, uri, cls) },
                                        onExportCsv = { opsVm.exportStudentsCsvAndOpen(context) },
                                        onExportPdf = {
                                            opsVm.generateAndOpenPdf(context) { sch ->
                                                val ref = Formatters.generateDocRef("REG", documents.size)
                                                val file = PdfAndCsvExporter.exportStudentsPdf(context, sch, visibleStudents)
                                                SchoolDocument(
                                                    schoolId = sch.id,
                                                    docType = DocumentType.LISTE_ETUDIANTS.name,
                                                    referenceNumber = ref,
                                                    title = "Registre des étudiants (${visibleStudents.size})",
                                                    generatedByName = displayName,
                                                    summaryText = "Export PDF officiel des effectifs"
                                                ) to file
                                            }
                                        },
                                        onGenerateCertificate = { student, docType ->
                                            opsVm.generateAndOpenPdf(context) { sch ->
                                                val ref = Formatters.generateDocRef("DOC", documents.size)
                                                val file = PdfAndCsvExporter.generateOfficialCertificatePdf(
                                                    context, sch, student, docType, ref, displayName
                                                )
                                                SchoolDocument(
                                                    schoolId = sch.id,
                                                    docType = docType.name,
                                                    referenceNumber = ref,
                                                    title = "${docType.label} — ${student.fullName}",
                                                    studentId = student.id,
                                                    studentName = student.fullName,
                                                    generatedByName = displayName,
                                                    summaryText = "${student.className} (${student.academicYear})"
                                                ) to file
                                            }
                                        }
                                    )

                                    "classes" -> ClassesAndCoursesScreen(
                                        school = school!!,
                                        role = effectiveRole,
                                        classes = classes,
                                        subjects = subjects,
                                        rooms = rooms,
                                        schedules = schedules,
                                        students = visibleStudents,
                                        teachers = teachers,
                                        onSaveClass = { opsVm.saveClass(it) },
                                        onSaveSubject = { opsVm.saveSubject(it) },
                                        onSaveRoom = { opsVm.saveClassroom(it) },
                                        onSaveSchedule = { opsVm.saveSchedule(it) }
                                    )

                                    "teachers" -> TeachersScreen(
                                        school = school!!,
                                        role = effectiveRole,
                                        teachers = teachers,
                                        subjects = subjects,
                                        classes = classes,
                                        onSaveTeacher = { opsVm.saveTeacher(it) },
                                        onRecordTeacherAbsence = { opsVm.incrementTeacherAbsence(it) },
                                        onUpgradeClick = { currentRoute = "saas_settings" }
                                    )

                                    "grades" -> GradesAndReportCardsScreen(
                                        school = school!!,
                                        role = effectiveRole,
                                        classes = classes,
                                        subjects = subjects,
                                        students = visibleStudents,
                                        grades = visibleGrades,
                                        onSaveGrade = { opsVm.saveGrade(it) },
                                        onGenerateBulletinPdf = { report ->
                                            opsVm.generateAndOpenPdf(context) { sch ->
                                                val ref = Formatters.generateDocRef("BUL", documents.size)
                                                val file = PdfAndCsvExporter.generateReportCardPdf(context, sch, report, ref)
                                                SchoolDocument(
                                                    schoolId = sch.id,
                                                    docType = DocumentType.BULLETIN.name,
                                                    referenceNumber = ref,
                                                    title = "Bulletin ${report.term.label} — ${report.student.fullName}",
                                                    studentId = report.student.id,
                                                    studentName = report.student.fullName,
                                                    generatedByName = displayName,
                                                    summaryText = "Moyenne ${String.format(Locale.FRENCH, "%.2f", report.weightedAverage)}/20 • Rang ${report.rank}/${report.classSize}"
                                                ) to file
                                            }
                                        },
                                        onUpgradeClick = { currentRoute = "saas_settings" }
                                    )

                                    "results_admin" -> ResultsAdminScreen(
                                        school = school!!,
                                        role = effectiveRole,
                                        classes = classes,
                                        subjects = subjects,
                                        students = visibleStudents,
                                        grades = visibleGrades,
                                        annualResults = visibleAnnualResults,
                                        isProcessing = isOpsProcessing,
                                        onGenerateDrafts = { clsId, periodLabel ->
                                            opsVm.generateAnnualResultsDrafts(clsId, periodLabel)
                                        },
                                        onSaveOrCorrectResult = { res, reason ->
                                            opsVm.saveOrCorrectAnnualResult(res, reason)
                                        },
                                        onPublishOrSchedule = { list, status, millis ->
                                            opsVm.publishOrScheduleResults(list, status, millis)
                                        },
                                        onImportGradesCsv = { uri, term ->
                                            opsVm.importGradesCsv(context, uri, term)
                                        },
                                        onDownloadPdf = { res, docType ->
                                            opsVm.generateAndOpenPdf(context) { sch ->
                                                val ref = Formatters.generateDocRef("RES", documents.size)
                                                val file = when (docType) {
                                                    DocumentType.RELEVE_UNIVERSITAIRE ->
                                                        PdfAndCsvExporter.generateUniversityTranscriptPdf(context, sch, res, ref)
                                                    DocumentType.ATTESTATION_RESULTATS ->
                                                        PdfAndCsvExporter.generateResultAttestationPdf(context, sch, res, ref, displayName)
                                                    DocumentType.DECISION_PASSAGE ->
                                                        PdfAndCsvExporter.generateDecisionCertificatePdf(context, sch, res, ref, displayName)
                                                    else ->
                                                        PdfAndCsvExporter.generateAnnualBulletinPdf(context, sch, res, ref)
                                                }
                                                SchoolDocument(
                                                    schoolId = sch.id,
                                                    docType = docType.name,
                                                    referenceNumber = ref,
                                                    title = "${docType.label} — ${res.studentFullName}",
                                                    studentId = res.studentId,
                                                    studentName = res.studentFullName,
                                                    generatedByName = displayName,
                                                    summaryText = "${res.matricule} • ${String.format(Locale.FRENCH, "%.2f", res.annualAverage)}/20 (${ResultDecision.fromRaw(res.decision).label})"
                                                ) to file
                                            }
                                        },
                                        onExportStatsPdf = { list ->
                                            opsVm.generateAndOpenPdf(context) { sch ->
                                                val ref = Formatters.generateDocRef("STAT", documents.size)
                                                val file = PdfAndCsvExporter.exportResultsStatisticsPdf(context, sch, list)
                                                SchoolDocument(
                                                    schoolId = sch.id,
                                                    docType = DocumentType.RELEVE_NOTES.name,
                                                    referenceNumber = ref,
                                                    title = "Statistiques résultats (${list.size})",
                                                    generatedByName = displayName,
                                                    summaryText = "Rapport statistique ${sch.academicYear}"
                                                ) to file
                                            }
                                        },
                                        onExportStatsCsv = { list ->
                                            opsVm.exportResultsStatsCsvAndOpen(context, list)
                                        }
                                    )

                                    "results_portal" -> ResultsPortalScreen(
                                        activeSchool = school!!,
                                        allSchools = schoolsList,
                                        onSelectSchool = { id -> workspaceVm.selectSchool(id) },
                                        onVerifyAndConsult = { schId, cat, mat, birth, pin ->
                                            opsVm.verifyAndConsultResult(schId, cat, mat, birth, pin)
                                        },
                                        onDownloadPdf = { res, docType ->
                                            opsVm.generateAndOpenPdf(context) { sch ->
                                                val ref = Formatters.generateDocRef("POR", documents.size)
                                                val file = when (docType) {
                                                    DocumentType.RELEVE_UNIVERSITAIRE ->
                                                        PdfAndCsvExporter.generateUniversityTranscriptPdf(context, sch, res, ref)
                                                    DocumentType.ATTESTATION_RESULTATS ->
                                                        PdfAndCsvExporter.generateResultAttestationPdf(context, sch, res, ref, "La Direction")
                                                    DocumentType.DECISION_PASSAGE ->
                                                        PdfAndCsvExporter.generateDecisionCertificatePdf(context, sch, res, ref, "La Direction")
                                                    else ->
                                                        PdfAndCsvExporter.generateAnnualBulletinPdf(context, sch, res, ref)
                                                }
                                                SchoolDocument(
                                                    schoolId = sch.id,
                                                    docType = docType.name,
                                                    referenceNumber = ref,
                                                    title = "${docType.label} — ${res.studentFullName}",
                                                    studentId = res.studentId,
                                                    studentName = res.studentFullName,
                                                    generatedByName = displayName,
                                                    summaryText = "${res.matricule} • ${res.academicYear}"
                                                ) to file
                                            }
                                        }
                                    )

                                    "finance" -> FinanceScreen(
                                        school = school!!,
                                        role = effectiveRole,
                                        students = visibleStudents,
                                        payments = visiblePayments,
                                        balances = studentBalances,
                                        stats = dashboardStats,
                                        isProcessing = isOpsProcessing,
                                        onRecordPayment = { student, tranche, amount, method, phone, ref, notes ->
                                            opsVm.recordPayment(student, tranche, amount, method, phone, ref, notes)
                                        },
                                        onGenerateReceiptPdf = { payment, balance ->
                                            opsVm.generateAndOpenPdf(context) { sch ->
                                                val file = PdfAndCsvExporter.generatePaymentReceiptPdf(context, sch, payment, balance)
                                                SchoolDocument(
                                                    schoolId = sch.id,
                                                    docType = DocumentType.RECU_PAIEMENT.name,
                                                    referenceNumber = payment.receiptNumber,
                                                    title = "Reçu ${payment.receiptNumber} — ${payment.studentName}",
                                                    studentId = payment.studentId,
                                                    studentName = payment.studentName,
                                                    generatedByName = displayName,
                                                    summaryText = "${Formatters.fcfa(payment.amountFcfa)} (${PaymentMethod.fromRaw(payment.method).label})"
                                                ) to file
                                            }
                                        },
                                        onOpenGatewaySettings = { currentRoute = "saas_settings" }
                                    )

                                    "attendance" -> AttendanceScreen(
                                        school = school!!,
                                        role = effectiveRole,
                                        classes = classes,
                                        students = visibleStudents,
                                        attendance = attendance,
                                        onRecordAttendance = { st, status, justified, reason ->
                                            opsVm.recordAttendance(st, status, justified, reason)
                                        },
                                        onJustifyAttendance = { rec, reason ->
                                            opsVm.justifyAttendance(rec, reason)
                                        },
                                        onUpgradeClick = { currentRoute = "saas_settings" }
                                    )

                                    "comms" -> CommsAndDocsScreen(
                                        school = school!!,
                                        role = effectiveRole,
                                        students = visibleStudents,
                                        announcements = announcements,
                                        documents = documents,
                                        onPublishAnnouncement = { title, message, cat, channel, target ->
                                            opsVm.publishAnnouncement(title, message, cat, channel, target)
                                        },
                                        onGenerateStudentDoc = { student, docType ->
                                            opsVm.generateAndOpenPdf(context) { sch ->
                                                val ref = Formatters.generateDocRef("DOC", documents.size)
                                                val file = PdfAndCsvExporter.generateOfficialCertificatePdf(
                                                    context, sch, student, docType, ref, displayName
                                                )
                                                SchoolDocument(
                                                    schoolId = sch.id,
                                                    docType = docType.name,
                                                    referenceNumber = ref,
                                                    title = "${docType.label} — ${student.fullName}",
                                                    studentId = student.id,
                                                    studentName = student.fullName,
                                                    generatedByName = displayName,
                                                    summaryText = "${student.className} (${student.academicYear})"
                                                ) to file
                                            }
                                        },
                                        onOpenGatewaySettings = { currentRoute = "saas_settings" },
                                        onUpgradeClick = { currentRoute = "saas_settings" }
                                    )

                                    "saas_settings" -> SaaSAndSettingsScreen(
                                        school = school,
                                        allSchools = schoolsList,
                                        plans = saasPlans,
                                        role = effectiveRole,
                                        isSuperAdmin = workspaceVm.isSuperAdmin,
                                        members = schoolMembers,
                                        invitations = schoolInvitations,
                                        invoices = subscriptionInvoices,
                                        auditLogs = auditLogs,
                                        students = visibleStudents,
                                        onSelectSchool = { id ->
                                            workspaceVm.selectSchool(id)
                                            currentRoute = "dashboard"
                                        },
                                        onCreateNewSchool = { createAdditionalSchool = true },
                                        onInviteMember = { invEmail, invRole, linked ->
                                            workspaceVm.inviteMember(invEmail, invRole, linked)
                                        },
                                        onUpdateMemberRole = { member, newRole, linked, active ->
                                            workspaceVm.updateMemberRole(member, newRole, linked, active)
                                        },
                                        onSyncMemberAccess = {
                                            workspaceVm.syncMemberReaderAccess()
                                        },
                                        onUpdateSchoolSettings = { updates, msg ->
                                            workspaceVm.updateSchoolProfileAndIntegrations(updates, msg)
                                        },
                                        onSuperAdminUpdatePlan = { code, name, price, trial, grace, maxSt, feats ->
                                            workspaceVm.superAdminUpdatePlan(code, name, price, trial, grace, maxSt, feats)
                                        },
                                        onSuperAdminManageSchool = { sch, status, plan, price, grace, genInv ->
                                            workspaceVm.superAdminManageSubscription(sch, status, plan, price, grace, genInv)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppShell(
    displayName: String,
    email: String,
    isEmailVerified: Boolean,
    activeSchool: School?,
    allSchools: List<School>,
    role: UserRole,
    isSuperAdmin: Boolean,
    modules: List<NavModule>,
    currentRoute: String,
    globalSearch: String,
    onSearchChange: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onSelectSchool: (String) -> Unit,
    onCreateNewSchool: () -> Unit,
    onSignOut: () -> Unit,
    content: @Composable () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSchoolSwitcher by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp

        if (isWideScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                SidebarContent(
                    displayName = displayName,
                    email = email,
                    isEmailVerified = isEmailVerified,
                    activeSchool = activeSchool,
                    role = role,
                    isSuperAdmin = isSuperAdmin,
                    modules = modules,
                    currentRoute = currentRoute,
                    onNavigate = onNavigate,
                    onOpenSchoolSwitcher = { showSchoolSwitcher = true },
                    onCreateNewSchool = onCreateNewSchool,
                    onSignOut = onSignOut,
                    modifier = Modifier
                        .width(268.dp)
                        .fillMaxHeight()
                )
                Scaffold(
                    modifier = Modifier.weight(1f),
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    modules.firstOrNull { it.route == currentRoute }?.label ?: "SchoolManager",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MidnightNavy
                            ),
                            actions = {
                                if (activeSchool != null) {
                                    Box(modifier = Modifier.width(260.dp).padding(end = 8.dp)) {
                                        GlobalSearchField(
                                            query = globalSearch,
                                            onQueryChange = onSearchChange,
                                            placeholder = "Rechercher élève, matricule..."
                                        )
                                    }
                                }
                                StatusPillBadge(role.label, IceBlueContainer, MidnightNavy)
                                Spacer(Modifier.width(12.dp))
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        content()
                    }
                }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = MidnightNavy,
                        drawerContentColor = PureWhite,
                        modifier = Modifier.width(290.dp)
                    ) {
                        SidebarContent(
                            displayName = displayName,
                            email = email,
                            isEmailVerified = isEmailVerified,
                            activeSchool = activeSchool,
                            role = role,
                            isSuperAdmin = isSuperAdmin,
                            modules = modules,
                            currentRoute = currentRoute,
                            onNavigate = {
                                onNavigate(it)
                                scope.launch { drawerState.close() }
                            },
                            onOpenSchoolSwitcher = {
                                scope.launch { drawerState.close() }
                                showSchoolSwitcher = true
                            },
                            onCreateNewSchool = {
                                scope.launch { drawerState.close() }
                                onCreateNewSchool()
                            },
                            onSignOut = onSignOut,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.testTag("drawer_menu_button")
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu principal")
                                }
                            },
                            title = {
                                Column {
                                    Text(
                                        activeSchool?.name ?: "SchoolManager",
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${modules.firstOrNull { it.route == currentRoute }?.shortLabel ?: ""} • ${role.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = IceBlueContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MidnightNavy,
                                titleContentColor = PureWhite,
                                navigationIconContentColor = PureWhite,
                                actionIconContentColor = PureWhite
                            ),
                            actions = {
                                if (activeSchool != null) {
                                    IconButton(onClick = { showSearchBar = !showSearchBar }) {
                                        Icon(Icons.Default.Search, contentDescription = "Recherche rapide")
                                    }
                                }
                                if (allSchools.size > 1 || isSuperAdmin) {
                                    IconButton(onClick = { showSchoolSwitcher = true }) {
                                        Icon(Icons.Default.SwapHoriz, contentDescription = "Changer d'établissement")
                                    }
                                }
                            }
                        )
                    },
                    bottomBar = {
                        val bottomTabs = modules.take(5)
                        if (bottomTabs.size > 1) {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                                bottomTabs.forEach { mod ->
                                    NavigationBarItem(
                                        selected = currentRoute == mod.route,
                                        onClick = { onNavigate(mod.route) },
                                        icon = { Icon(mod.icon, contentDescription = mod.label) },
                                        label = {
                                            Text(
                                                mod.shortLabel,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        if (showSearchBar && activeSchool != null) {
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                GlobalSearchField(
                                    query = globalSearch,
                                    onQueryChange = onSearchChange
                                )
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            content()
                        }
                    }
                }
            }
        }
    }

    if (showSchoolSwitcher) {
        AlertDialog(
            onDismissRequest = { showSchoolSwitcher = false },
            title = { Text("Mes établissements (${allSchools.size})") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allSchools.forEach { sch ->
                        val selected = sch.id == activeSchool?.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectSchool(sch.id)
                                    showSchoolSwitcher = false
                                },
                            color = if (selected) IceBlueContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(sch.name, fontWeight = FontWeight.SemiBold, color = MidnightNavy)
                                    Text("${sch.city} • ${PlanCode.fromRaw(sch.planCode).label}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (selected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Actif", tint = SkyBluePrimary)
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    TextButton(
                        onClick = {
                            showSchoolSwitcher = false
                            onCreateNewSchool()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AddBusiness, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Créer un nouvel établissement")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSchoolSwitcher = false }) {
                    Text("Fermer")
                }
            }
        )
    }
}

@Composable
private fun SidebarContent(
    displayName: String,
    email: String,
    isEmailVerified: Boolean,
    activeSchool: School?,
    role: UserRole,
    isSuperAdmin: Boolean,
    modules: List<NavModule>,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onOpenSchoolSwitcher: () -> Unit,
    onCreateNewSchool: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MidnightNavy)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Identité SchoolManager
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
            Text(
                "SchoolManager",
                style = MaterialTheme.typography.headlineSmall,
                color = PureWhite,
                fontWeight = FontWeight.Bold
            )
            Text(
                "« Votre établissement, mieux organisé. »",
                style = MaterialTheme.typography.labelSmall,
                color = IceBlueContainer
            )
        }

        // Carte Établissement actif
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenSchoolSwitcher),
            color = DeepRoyalBlue
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Apartment, contentDescription = null, tint = SkyBlueLight)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        activeSchool?.name ?: if (isSuperAdmin) "Console Plateforme" else "Aucun établissement",
                        style = MaterialTheme.typography.titleSmall,
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (activeSchool != null) "${PlanCode.fromRaw(activeSchool.planCode).label} • ${role.label}" else role.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = IceBlueContainer,
                        maxLines = 1
                    )
                }
                Icon(Icons.Default.UnfoldMore, contentDescription = "Changer d'école", tint = IceBlueContainer)
            }
        }

        HorizontalDivider(color = PureWhite.copy(alpha = 0.14f))

        // Liste modules
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            modules.forEach { mod ->
                val selected = currentRoute == mod.route
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onNavigate(mod.route) }
                        .testTag("nav_${mod.route}"),
                    color = if (selected) SkyBluePrimary else MidnightNavy
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            mod.icon,
                            contentDescription = mod.label,
                            tint = if (selected) PureWhite else IceBlueContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            mod.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) PureWhite else IceBlueContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = PureWhite.copy(alpha = 0.14f))

        TextButton(
            onClick = onCreateNewSchool,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = SkyBlueLight)
        ) {
            Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Nouvel établissement")
        }

        // Profil utilisateur & déconnexion
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    email,
                    style = MaterialTheme.typography.labelSmall,
                    color = IceBlueContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (isEmailVerified) "Session active • E-mail vérifié" else "E-mail en attente de vérification",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isEmailVerified) EmeraldContainer else AmberContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onSignOut,
                modifier = Modifier.testTag("sign_out_button")
            ) {
                Icon(Icons.Default.Logout, contentDescription = "Se déconnecter", tint = CoralContainer)
            }
        }
    }
}

@Composable
private fun SuspendedSchoolNotice(
    school: School,
    canManageSettings: Boolean,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.widthIn(max = 500.dp),
            colors = CardDefaults.cardColors(containerColor = CoralContainer)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Abonnement suspendu",
                    tint = CoralError,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    "Abonnement suspendu — ${school.name}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MidnightNavy
                )
                Text(
                    "L'accès aux opérations scolaires est temporairement restreint suite à une suspension d'abonnement. Régularisez vos factures auprès du Super Administrateur pour réactiver immédiatement l'établissement.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CharcoalInk
                )
                if (canManageSettings) {
                    Button(onClick = onOpenSettings) {
                        Text("Voir facturation & paramètres")
                    }
                }
            }
        }
    }
}
