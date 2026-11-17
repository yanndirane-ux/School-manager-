package com.example.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.theme.*
import com.example.util.Formatters

@Composable
fun TeachersScreen(
    school: School,
    role: UserRole,
    teachers: List<Teacher>,
    subjects: List<Subject>,
    classes: List<SchoolClass>,
    onSaveTeacher: (Teacher) -> Unit,
    onRecordTeacherAbsence: (Teacher) -> Unit,
    onUpgradeClick: () -> Unit
) {
    val planCode = PlanCode.fromRaw(school.planCode)
    val canManage = role in listOf(UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR, UserRole.SECRETAIRE)
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Teacher?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (planCode.rank < PlanCode.PROFESSIONAL.rank) {
            item {
                PlanGateBanner(
                    requiredPlan = PlanCode.PROFESSIONAL,
                    currentPlan = planCode,
                    featureName = "Gestion avancée des enseignants",
                    onUpgradeClick = onUpgradeClick
                )
            }
        }

        item {
            SectionHeader(
                title = "Enseignants & Personnel",
                subtitle = "${teachers.size} enseignant(s) enregistré(s)",
                actionLabel = if (canManage) "+ Ajouter" else null,
                onActionClick = if (canManage) {
                    { editing = null; showDialog = true }
                } else null,
                actionTestTag = "add_teacher_button"
            )
        }

        if (teachers.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Aucun enseignant enregistré",
                    message = "Ajoutez vos enseignants, leurs spécialités, contrats et affectations de matières.",
                    actionLabel = if (canManage) "Ajouter un enseignant" else null,
                    onAction = if (canManage) { { editing = null; showDialog = true } } else null
                )
            }
        } else {
            items(teachers, key = { it.id }) { t ->
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(t.fullName, style = MaterialTheme.typography.titleMedium)
                                Text("${t.employeeCode} • ${t.specialty}", style = MaterialTheme.typography.bodySmall, color = SkyBluePrimary)
                            }
                            StatusPillBadge(t.contractType, IceBlueContainer, MidnightNavy)
                        }
                        Text("Tél : ${t.phone} • Embauche : ${t.hireDate}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                        if (t.assignedClasses.isNotEmpty() || t.assignedSubjects.isNotEmpty()) {
                            Text(
                                "Classes : ${t.assignedClasses.joinToString()} • Matières : ${t.assignedSubjects.joinToString()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Absences : ${t.absencesCount} • Salaire : ${Formatters.fcfa(t.monthlySalaryFcfa)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (t.absencesCount > 0) AmberWarning else EmeraldSuccess
                            )
                            if (canManage) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { onRecordTeacherAbsence(t) }) {
                                        Text("+1 Absence")
                                    }
                                    TextButton(onClick = { editing = t; showDialog = true }) {
                                        Text("Modifier")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        TeacherFormDialog(
            school = school,
            existing = editing,
            classes = classes,
            subjects = subjects,
            onDismiss = { showDialog = false },
            onSave = {
                onSaveTeacher(it)
                showDialog = false
            }
        )
    }
}

@Composable
private fun TeacherFormDialog(
    school: School,
    existing: Teacher?,
    classes: List<SchoolClass>,
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onSave: (Teacher) -> Unit
) {
    var lastName by remember { mutableStateOf(existing?.lastName ?: "") }
    var firstName by remember { mutableStateOf(existing?.firstName ?: "") }
    var specialty by remember { mutableStateOf(existing?.specialty ?: "Mathématiques") }
    var phone by remember { mutableStateOf(existing?.phone ?: "+237 ") }
    var email by remember { mutableStateOf(existing?.email ?: "") }
    var contract by remember { mutableStateOf(existing?.contractType ?: "CDI") }
    var salary by remember { mutableStateOf((existing?.monthlySalaryFcfa ?: 180_000L).toString()) }
    val selectedClasses = remember { mutableStateListOf<String>().apply { addAll(existing?.assignedClasses ?: emptyList()) } }
    val selectedSubjects = remember { mutableStateListOf<String>().apply { addAll(existing?.assignedSubjects ?: emptyList()) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nouvel enseignant" else "Modifier l'enseignant") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text("Nom *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text("Prénom *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = specialty, onValueChange = { specialty = it }, label = { Text("Spécialité *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Téléphone *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("E-mail") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = salary, onValueChange = { salary = it.filter(Char::isDigit) }, label = { Text("Salaire mensuel (FCFA)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CDI", "VACATAIRE", "STAGIAIRE").forEach { c ->
                        FilterChip(selected = contract == c, onClick = { contract = c }, label = { Text(c) })
                    }
                }
                if (classes.isNotEmpty()) {
                    Text("Classes affectées :", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        classes.forEach { cls ->
                            val picked = cls.name in selectedClasses
                            FilterChip(
                                selected = picked,
                                onClick = { if (picked) selectedClasses.remove(cls.name) else selectedClasses.add(cls.name) },
                                label = { Text(cls.name) }
                            )
                        }
                    }
                }
                if (subjects.isNotEmpty()) {
                    Text("Matières :", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        subjects.distinctBy { it.name }.forEach { sub ->
                            val picked = sub.name in selectedSubjects
                            FilterChip(
                                selected = picked,
                                onClick = { if (picked) selectedSubjects.remove(sub.name) else selectedSubjects.add(sub.name) },
                                label = { Text(sub.name) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        (existing ?: Teacher()).copy(
                            schoolId = school.id,
                            firstName = firstName,
                            lastName = lastName,
                            specialty = specialty,
                            phone = phone,
                            email = email,
                            hireDate = existing?.hireDate?.ifBlank { Formatters.todayIso() } ?: Formatters.todayIso(),
                            contractType = contract,
                            monthlySalaryFcfa = salary.toLongOrNull() ?: 0L,
                            assignedClasses = selectedClasses.toList(),
                            assignedSubjects = selectedSubjects.toList()
                        )
                    )
                },
                enabled = lastName.isNotBlank() && firstName.isNotBlank() && phone.isNotBlank()
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun ClassesAndCoursesScreen(
    school: School,
    role: UserRole,
    classes: List<SchoolClass>,
    subjects: List<Subject>,
    rooms: List<Classroom>,
    schedules: List<ScheduleEntry>,
    students: List<Student>,
    teachers: List<Teacher>,
    onSaveClass: (SchoolClass) -> Unit,
    onSaveSubject: (Subject) -> Unit,
    onSaveRoom: (Classroom) -> Unit,
    onSaveSchedule: (ScheduleEntry) -> Unit
) {
    val isUniversity = SchoolCategory.fromRaw(school.category) == SchoolCategory.UNIVERSITE_INSTITUT
    val canEdit = role in listOf(UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR, UserRole.SECRETAIRE)
    var tab by remember { mutableIntStateOf(0) }
    var showClassDialog by remember { mutableStateOf(false) }
    var showSubjectDialog by remember { mutableStateOf(false) }
    var showRoomDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(if (isUniversity) "Filières & LMD" else "Classes") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(if (isUniversity) "UE & Matières" else "Matières") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Cours & Examens") })
            Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Salles") })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (tab) {
                0 -> {
                    item {
                        SectionHeader(
                            title = if (isUniversity) "Filières, Facultés & Niveaux LMD" else "Classes & Niveaux",
                            subtitle = "${classes.size} parcours/classe(s) • ${SchoolCategory.fromRaw(school.category).shortLabel}",
                            actionLabel = if (canEdit) (if (isUniversity) "+ Parcours" else "+ Classe") else null,
                            onActionClick = if (canEdit) { { showClassDialog = true } } else null,
                            actionTestTag = "add_class_button"
                        )
                    }
                    if (classes.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = if (isUniversity) "Aucune filière/promotion LMD" else "Aucune classe créée",
                                message = if (isUniversity) {
                                    "Créez vos promotions universitaires (ex: L1 Informatique, Master 1 Finance) avec faculté, département et droits universitaires."
                                } else {
                                    "Créez vos niveaux et classes (ex: 6ème A, Terminale C, CM2) avec leurs frais de scolarité en FCFA."
                                },
                                actionLabel = if (canEdit) (if (isUniversity) "Créer une filière" else "Créer une classe") else null,
                                onAction = if (canEdit) { { showClassDialog = true } } else null
                            )
                        }
                    } else {
                        items(classes, key = { it.id }) { cls ->
                            val enrolled = students.count { it.classId == cls.id }
                            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("${cls.name} (${cls.level})", style = MaterialTheme.typography.titleMedium)
                                        if (!cls.faculty.isNullOrBlank() || !cls.program.isNullOrBlank()) {
                                            Text(
                                                listOfNotNull(cls.faculty, cls.department, cls.program).joinToString(" • "),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MidnightNavy
                                            )
                                        }
                                        Text("${cls.campus} • Effectif : $enrolled / ${cls.capacity}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                        Text("Scolarité : ${Formatters.fcfa(cls.tuitionFeeFcfa)} + Inscription ${Formatters.fcfa(cls.registrationFeeFcfa)}", style = MaterialTheme.typography.bodySmall, color = SkyBluePrimary)
                                    }
                                    StatusPillBadge("$enrolled inscrits", IceBlueContainer, MidnightNavy)
                                }
                            }
                        }
                    }
                }
                1 -> {
                    item {
                        SectionHeader(
                            title = if (isUniversity) "Unités d'Enseignement (UE) & Crédits" else "Matières & Coefficients",
                            subtitle = "${subjects.size} ${if (isUniversity) "UE(s)" else "matière(s)"}",
                            actionLabel = if (canEdit) (if (isUniversity) "+ UE" else "+ Matière") else null,
                            onActionClick = if (canEdit) { { showSubjectDialog = true } } else null,
                            actionTestTag = "add_subject_button"
                        )
                    }
                    if (subjects.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = if (isUniversity) "Aucune UE configurée" else "Aucune matière configurée",
                                message = if (isUniversity) {
                                    "Ajoutez vos Unités d'Enseignement avec crédits LMD, semestre (S1/S2) et coefficients."
                                } else {
                                    "Ajoutez vos matières avec leurs coefficients (1 à 10) pour le calcul pondéré des bulletins."
                                },
                                actionLabel = if (canEdit && classes.isNotEmpty()) "Ajouter" else null,
                                onAction = if (canEdit && classes.isNotEmpty()) { { showSubjectDialog = true } } else null
                            )
                        }
                    } else {
                        items(subjects, key = { it.id }) { sub ->
                            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${sub.name} (${sub.code})", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            buildString {
                                                append("Classe : ${sub.className} • ${sub.weeklyHours}h/sem")
                                                if (!sub.semester.isNullOrBlank()) append(" • ${sub.semester}")
                                                if (!sub.teacherName.isNullOrBlank()) append(" • ${sub.teacherName}")
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SlateMuted
                                        )
                                    }
                                    StatusPillBadge(
                                        if (isUniversity || sub.universityUe) "${sub.credits} créd. (×${sub.coefficient})" else "Coeff ${sub.coefficient}",
                                        IceBlueContainer,
                                        MidnightNavy
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> {
                    item {
                        SectionHeader(
                            title = "Emplois du temps & Examens",
                            subtitle = "${schedules.size} créneau(x) planifié(s)",
                            actionLabel = if (canEdit) "+ Créneau" else null,
                            onActionClick = if (canEdit) { { showScheduleDialog = true } } else null
                        )
                    }
                    if (schedules.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "Aucun cours ou examen planifié",
                                message = "Planifiez les créneaux horaires de cours et les sessions d'examens par classe, matière, enseignant et salle."
                            )
                        }
                    } else {
                        items(schedules, key = { it.id }) { sc ->
                            val isExam = sc.entryType == ScheduleEntryType.EXAMEN.name
                            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            if (isExam && !sc.examDate.isNullOrBlank()) "EXAMEN (${sc.examDate}) • ${sc.startTime} - ${sc.endTime}"
                                            else "${sc.dayOfWeek} • ${sc.startTime} - ${sc.endTime}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (isExam) CoralError else SkyBluePrimary
                                        )
                                        Text("${sc.subjectName} — ${sc.className}", style = MaterialTheme.typography.titleMedium)
                                        Text("Prof : ${sc.teacherName} • Salle : ${sc.roomName}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                    }
                                    StatusPillBadge(
                                        ScheduleEntryType.fromRaw(sc.entryType).label,
                                        if (isExam) CoralContainer else IceBlueContainer,
                                        if (isExam) CoralError else MidnightNavy
                                    )
                                }
                            }
                        }
                    }
                }
                3 -> {
                    item {
                        SectionHeader(
                            title = "Salles de cours & Amphithéâtres",
                            subtitle = "${rooms.size} salle(s)",
                            actionLabel = if (canEdit) "+ Salle" else null,
                            onActionClick = if (canEdit) { { showRoomDialog = true } } else null
                        )
                    }
                    if (rooms.isEmpty()) {
                        item { EmptyStateCard("Aucune salle enregistrée", "Ajoutez vos salles de cours, laboratoires ou amphithéâtres.") }
                    } else {
                        items(rooms, key = { it.id }) { rm ->
                            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(rm.name, style = MaterialTheme.typography.titleMedium)
                                        Text("${rm.building} (${rm.campus}) • ${rm.roomType}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                    }
                                    StatusPillBadge("${rm.capacity} places", IceBlueContainer, MidnightNavy)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClassDialog) {
        var name by remember { mutableStateOf("") }
        var level by remember {
            mutableStateOf(
                when (SchoolCategory.fromRaw(school.category)) {
                    SchoolCategory.PRIMAIRE_MATERNELLE -> "CM2"
                    SchoolCategory.COLLEGE_LYCEE -> "6ème"
                    SchoolCategory.UNIVERSITE_INSTITUT -> UniversityLevel.LICENCE_1.label
                }
            )
        }
        var faculty by remember { mutableStateOf(school.faculties.firstOrNull() ?: "") }
        var department by remember { mutableStateOf(school.departments.firstOrNull() ?: "") }
        var program by remember { mutableStateOf(school.programs.firstOrNull() ?: "") }
        var cycleLevel by remember { mutableStateOf(if (isUniversity) UniversityLevel.LICENCE_1.code else "") }
        var campus by remember { mutableStateOf(school.campuses.firstOrNull() ?: "Campus Principal") }
        var capacity by remember { mutableStateOf(if (isUniversity) "120" else "50") }
        var tuition by remember { mutableStateOf(if (isUniversity) "350000" else "150000") }
        var regFee by remember { mutableStateOf(if (isUniversity) "50000" else "25000") }
        AlertDialog(
            onDismissRequest = { showClassDialog = false },
            title = { Text(if (isUniversity) "Créer une filière / promotion LMD" else "Créer une classe") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(if (isUniversity) "Nom promotion * (ex: L1 Informatique)" else "Nom de la classe * (ex: 6ème A)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("class_name_input")
                    )
                    if (isUniversity) {
                        Text("Niveau LMD :", style = MaterialTheme.typography.labelMedium)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            UniversityLevel.entries.forEach { ul ->
                                FilterChip(
                                    selected = level == ul.label,
                                    onClick = { level = ul.label; cycleLevel = ul.code },
                                    label = { Text(ul.label) }
                                )
                            }
                        }
                        OutlinedTextField(value = faculty, onValueChange = { faculty = it }, label = { Text("Faculté / École") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = department, onValueChange = { department = it }, label = { Text("Département") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = program, onValueChange = { program = it }, label = { Text("Filière / Spécialité") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    } else {
                        OutlinedTextField(value = level, onValueChange = { level = it }, label = { Text("Niveau (ex: 6ème, Terminale C, CM2)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(value = campus, onValueChange = { campus = it }, label = { Text("Campus") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = capacity, onValueChange = { capacity = it.filter(Char::isDigit) }, label = { Text("Capacité max") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = tuition, onValueChange = { tuition = it.filter(Char::isDigit) }, label = { Text("Frais de scolarité (FCFA)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = regFee, onValueChange = { regFee = it.filter(Char::isDigit) }, label = { Text("Frais d'inscription (FCFA)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveClass(
                            SchoolClass(
                                schoolId = school.id,
                                name = name,
                                level = level,
                                campus = campus,
                                faculty = faculty.ifBlank { null },
                                department = department.ifBlank { null },
                                program = program.ifBlank { null },
                                cycleLevel = cycleLevel.ifBlank { null },
                                capacity = capacity.toLongOrNull() ?: 50L,
                                tuitionFeeFcfa = tuition.toLongOrNull() ?: 0L,
                                registrationFeeFcfa = regFee.toLongOrNull() ?: 0L,
                                academicYear = school.academicYear
                            )
                        )
                        showClassDialog = false
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.testTag("class_save_button")
                ) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { showClassDialog = false }) { Text("Annuler") } }
        )
    }

    if (showSubjectDialog) {
        var name by remember { mutableStateOf("") }
        var code by remember { mutableStateOf(if (isUniversity) "INF101" else "MATH") }
        var coeff by remember { mutableStateOf("4") }
        var credits by remember { mutableStateOf("6") }
        var semester by remember { mutableStateOf("S1") }
        var hours by remember { mutableStateOf("5") }
        var pickedClass by remember { mutableStateOf(classes.firstOrNull()) }
        var teacherName by remember { mutableStateOf(teachers.firstOrNull()?.fullName ?: "") }
        AlertDialog(
            onDismissRequest = { showSubjectDialog = false },
            title = { Text(if (isUniversity) "Nouvelle Unité d'Enseignement (UE)" else "Nouvelle matière") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(if (isUniversity) "Intitulé de l'UE *" else "Nom de la matière *") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("subject_name_input"))
                    OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Code (ex: MATH, INF101)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = coeff, onValueChange = { coeff = it.filter(Char::isDigit) }, label = { Text("Coefficient *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (isUniversity) {
                        OutlinedTextField(value = credits, onValueChange = { credits = it.filter(Char::isDigit) }, label = { Text("Crédits LMD *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("S1", "S2").forEach { sem ->
                                FilterChip(selected = semester == sem, onClick = { semester = sem }, label = { Text("Semestre $sem") })
                            }
                        }
                    }
                    OutlinedTextField(value = hours, onValueChange = { hours = it.filter(Char::isDigit) }, label = { Text("Heures / semaine") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = teacherName, onValueChange = { teacherName = it }, label = { Text("Enseignant (optionnel)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (classes.isNotEmpty()) {
                        Text(if (isUniversity) "Filière / Promotion :" else "Classe :", style = MaterialTheme.typography.labelMedium)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            classes.forEach { cls ->
                                FilterChip(selected = pickedClass?.id == cls.id, onClick = { pickedClass = cls }, label = { Text(cls.name) })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveSubject(
                            Subject(
                                schoolId = school.id,
                                name = name,
                                code = code,
                                coefficient = (coeff.toLongOrNull() ?: 1L).coerceIn(1L, 20L),
                                credits = if (isUniversity) (credits.toLongOrNull() ?: 3L).coerceIn(0L, 30L) else 0L,
                                semester = if (isUniversity) semester else null,
                                faculty = pickedClass?.faculty,
                                department = pickedClass?.department,
                                universityUe = isUniversity,
                                classId = pickedClass?.id ?: "general",
                                className = pickedClass?.name ?: "Général",
                                teacherName = teacherName,
                                weeklyHours = (hours.toLongOrNull() ?: 2L).coerceIn(1L, 40L)
                            )
                        )
                        showSubjectDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { showSubjectDialog = false }) { Text("Annuler") } }
        )
    }

    if (showRoomDialog) {
        var name by remember { mutableStateOf("") }
        var building by remember { mutableStateOf("Bâtiment A") }
        var capacity by remember { mutableStateOf("50") }
        var type by remember { mutableStateOf(if (isUniversity) "Amphithéâtre" else "Salle de classe") }
        AlertDialog(
            onDismissRequest = { showRoomDialog = false },
            title = { Text("Ajouter une salle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom de la salle *") }, singleLine = true)
                    OutlinedTextField(value = building, onValueChange = { building = it }, label = { Text("Bâtiment") }, singleLine = true)
                    OutlinedTextField(value = capacity, onValueChange = { capacity = it.filter(Char::isDigit) }, label = { Text("Capacité") }, singleLine = true)
                    OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type (Amphi, Labo, Salle...)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveRoom(
                            Classroom(
                                schoolId = school.id, name = name, building = building,
                                campus = school.campuses.firstOrNull() ?: "Campus Principal",
                                capacity = capacity.toLongOrNull() ?: 50L, roomType = type
                            )
                        )
                        showRoomDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { showRoomDialog = false }) { Text("Annuler") } }
        )
    }

    if (showScheduleDialog) {
        var pickedClass by remember { mutableStateOf(classes.firstOrNull()) }
        var pickedSubject by remember { mutableStateOf(subjects.firstOrNull()) }
        var teacherName by remember { mutableStateOf(teachers.firstOrNull()?.fullName ?: "Enseignant") }
        var roomName by remember { mutableStateOf(rooms.firstOrNull()?.name ?: "Salle 101") }
        var entryType by remember { mutableStateOf(ScheduleEntryType.COURS) }
        var examDate by remember { mutableStateOf(Formatters.todayIso()) }
        var day by remember { mutableStateOf("Lundi") }
        var start by remember { mutableStateOf("08:00") }
        var end by remember { mutableStateOf("10:00") }
        AlertDialog(
            onDismissRequest = { showScheduleDialog = false },
            title = { Text("Planifier un créneau") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScheduleEntryType.entries.forEach { et ->
                            FilterChip(selected = entryType == et, onClick = { entryType = et }, label = { Text(et.label) })
                        }
                    }
                    if (entryType == ScheduleEntryType.EXAMEN) {
                        OutlinedTextField(value = examDate, onValueChange = { examDate = it }, label = { Text("Date examen (AAAA-MM-JJ)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi").forEach { d ->
                            FilterChip(selected = day == d, onClick = { day = d }, label = { Text(d) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Début") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("Fin") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    if (classes.isNotEmpty()) {
                        Text("Classe / Filière :", style = MaterialTheme.typography.labelMedium)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            classes.forEach { c -> FilterChip(selected = pickedClass?.id == c.id, onClick = { pickedClass = c }, label = { Text(c.name) }) }
                        }
                    }
                    if (subjects.isNotEmpty()) {
                        Text(if (isUniversity) "UE / Matière :" else "Matière :", style = MaterialTheme.typography.labelMedium)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            subjects.forEach { s -> FilterChip(selected = pickedSubject?.id == s.id, onClick = { pickedSubject = s }, label = { Text(s.name) }) }
                        }
                    }
                    OutlinedTextField(value = teacherName, onValueChange = { teacherName = it }, label = { Text("Enseignant / Surveillant") }, singleLine = true)
                    OutlinedTextField(value = roomName, onValueChange = { roomName = it }, label = { Text("Salle / Amphi") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveSchedule(
                            ScheduleEntry(
                                schoolId = school.id,
                                classId = pickedClass?.id ?: "general",
                                className = pickedClass?.name ?: "Classe",
                                subjectId = pickedSubject?.id ?: "sub",
                                subjectName = pickedSubject?.name ?: "Matière",
                                teacherName = teacherName,
                                roomName = roomName,
                                dayOfWeek = day,
                                startTime = start,
                                endTime = end,
                                entryType = entryType.name,
                                examDate = if (entryType == ScheduleEntryType.EXAMEN) examDate else null
                            )
                        )
                        showScheduleDialog = false
                    },
                    enabled = teacherName.isNotBlank() && roomName.isNotBlank()
                ) { Text("Ajouter") }
            },
            dismissButton = { TextButton(onClick = { showScheduleDialog = false }) { Text("Annuler") } }
        )
    }
}
