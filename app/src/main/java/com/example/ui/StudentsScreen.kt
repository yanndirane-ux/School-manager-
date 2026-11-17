package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.*
import com.example.ui.theme.*
import com.example.util.Formatters
import com.example.util.PdfAndCsvExporter

@Composable
fun StudentsScreen(
    school: School,
    role: UserRole,
    students: List<Student>,
    classes: List<SchoolClass>,
    balances: List<StudentBalance>,
    globalSearch: String,
    onSaveStudent: (Student) -> Unit,
    onDeleteStudent: (Student) -> Unit,
    onImportCsv: (Uri, SchoolClass?) -> Unit,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit,
    onGenerateCertificate: (Student, DocumentType) -> Unit
) {
    val context = LocalContext.current
    val canEdit = role in listOf(UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR, UserRole.SECRETAIRE)
    val canDelete = role in listOf(UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR)

    var searchQuery by remember(globalSearch) { mutableStateOf(globalSearch) }
    var selectedClassFilter by remember { mutableStateOf("TOUTES") }
    var selectedStatusFilter by remember { mutableStateOf("TOUS") }
    var showFormDialog by remember { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var detailStudent by remember { mutableStateOf<Student?>(null) }
    var pendingDelete by remember { mutableStateOf<Student?>(null) }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportCsv(uri, classes.firstOrNull())
        }
    }

    if (detailStudent != null) {
        val current = students.firstOrNull { it.id == detailStudent?.id } ?: detailStudent!!
        BackHandler { detailStudent = null }
        StudentDetailView(
            student = current,
            balance = balances.firstOrNull { it.student.id == current.id },
            canEdit = canEdit,
            canDelete = canDelete,
            onBack = { detailStudent = null },
            onEdit = {
                editingStudent = current
                showFormDialog = true
            },
            onReenroll = {
                val updated = current.copy(
                    status = StudentStatus.REINSCRIT.name,
                    academicYear = school.academicYear,
                    historyNotes = (current.historyNotes + "Réinscription ${school.academicYear} (${current.className}) le ${Formatters.todayIso()}").takeLast(20)
                )
                onSaveStudent(updated)
            },
            onGenerateCertificate = { docType -> onGenerateCertificate(current, docType) },
            onDelete = { pendingDelete = current }
        )
    } else {
        val filtered = remember(students, searchQuery, selectedClassFilter, selectedStatusFilter) {
            students.filter { st ->
                val matchesSearch = searchQuery.isBlank() ||
                    st.fullName.contains(searchQuery, ignoreCase = true) ||
                    st.matricule.contains(searchQuery, ignoreCase = true) ||
                    st.guardianName.contains(searchQuery, ignoreCase = true)
                val matchesClass = selectedClassFilter == "TOUTES" || st.classId == selectedClassFilter
                val matchesStatus = selectedStatusFilter == "TOUS" || st.status == selectedStatusFilter
                matchesSearch && matchesClass && matchesStatus
            }.sortedBy { it.lastName }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionHeader(
                    title = when (role) {
                        UserRole.PARENT -> "Mes enfants inscrits"
                        UserRole.ELEVE -> "Mon dossier scolaire"
                        else -> "Gestion des étudiants"
                    },
                    subtitle = "${filtered.size} dossier(s) affiché(s) sur ${students.size}",
                    actionLabel = if (canEdit) "+ Inscrire" else null,
                    onActionClick = if (canEdit) {
                        {
                            editingStudent = null
                            showFormDialog = true
                        }
                    } else null,
                    actionTestTag = "add_student_button"
                )
            }

            item {
                GlobalSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Nom, prénom, matricule ou tuteur..."
                )
            }

            if (canEdit) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { csvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) },
                            modifier = Modifier.testTag("import_csv_button")
                        ) {
                            Icon(Icons.Default.UploadFile, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Importer CSV")
                        }
                        OutlinedButton(
                            onClick = onExportCsv,
                            modifier = Modifier.testTag("export_csv_button")
                        ) {
                            Icon(Icons.Default.TableView, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Exporter Excel/CSV")
                        }
                        OutlinedButton(
                            onClick = onExportPdf,
                            modifier = Modifier.testTag("export_students_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Exporter PDF")
                        }
                    }
                }
            }

            // Filtres par classe
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedClassFilter == "TOUTES",
                        onClick = { selectedClassFilter = "TOUTES" },
                        label = { Text("Toutes classes") }
                    )
                    classes.forEach { cls ->
                        FilterChip(
                            selected = selectedClassFilter == cls.id,
                            onClick = { selectedClassFilter = cls.id },
                            label = { Text(cls.name) }
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "Aucun étudiant trouvé",
                        message = if (classes.isEmpty()) {
                            "Commencez par créer vos classes dans le module Classes & Cours, puis inscrivez vos élèves."
                        } else {
                            "Aucun dossier ne correspond à vos critères actuels."
                        },
                        actionLabel = if (canEdit) "Inscrire un étudiant" else null,
                        onAction = if (canEdit) {
                            {
                                editingStudent = null
                                showFormDialog = true
                            }
                        } else null
                    )
                }
            } else {
                items(filtered, key = { it.id }) { st ->
                    val bal = balances.firstOrNull { it.student.id == st.id }
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { detailStudent = st }
                            .testTag("student_card_${st.id}"),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!st.photoUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = st.photoUri,
                                    contentDescription = st.fullName,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(IceBlueContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        st.firstName.take(1).uppercase() + st.lastName.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = MidnightNavy
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(st.fullName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${st.matricule} • ${st.className} (${st.campus})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateMuted
                                )
                                Text(
                                    "Tuteur : ${st.guardianName} (${st.guardianPhone})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateMuted
                                )
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                StatusPillBadge(
                                    StudentStatus.fromRaw(st.status).label,
                                    IceBlueContainer,
                                    MidnightNavy
                                )
                                if (bal != null) {
                                    Text(
                                        if (bal.isPaidInFull) "Soldé" else "Reste ${Formatters.fcfa(bal.remainingFcfa)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (bal.isPaidInFull) EmeraldSuccess else AmberWarning
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFormDialog) {
        StudentFormDialog(
            school = school,
            existing = editingStudent,
            classes = classes,
            onDismiss = { showFormDialog = false },
            onConfirm = { student ->
                onSaveStudent(student)
                showFormDialog = false
            }
        )
    }

    if (pendingDelete != null) {
        ConfirmActionDialog(
            title = "Supprimer le dossier ?",
            message = "Voulez-vous vraiment supprimer le dossier de ${pendingDelete!!.fullName} (${pendingDelete!!.matricule}) ? Cette action est journalisée.",
            confirmLabel = "Supprimer",
            isDestructive = true,
            onConfirm = {
                onDeleteStudent(pendingDelete!!)
                pendingDelete = null
                detailStudent = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun StudentDetailView(
    student: Student,
    balance: StudentBalance?,
    canEdit: Boolean,
    canDelete: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onReenroll: () -> Unit,
    onGenerateCertificate: (DocumentType) -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
            }
            Text("Fiche individuelle", style = MaterialTheme.typography.headlineSmall)
        }

        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (!student.photoUri.isNullOrBlank()) {
                        AsyncImage(
                            model = student.photoUri,
                            contentDescription = student.fullName,
                            modifier = Modifier.size(64.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(64.dp).clip(CircleShape).background(MidnightNavy),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                student.firstName.take(1) + student.lastName.take(1),
                                color = PureWhite,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(student.fullName, style = MaterialTheme.typography.titleLarge)
                        Text("Matricule : ${student.matricule}", style = MaterialTheme.typography.labelLarge, color = SkyBluePrimary)
                        Text("${student.className} • ${student.level} • ${student.campus}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider()
                InfoRow("Sexe / Naissance", "${if (student.gender == "F") "Féminin" else "Masculin"} • Né(e) le ${student.birthDate} ${student.birthPlace.orEmpty()}")
                InfoRow("Année scolaire", student.academicYear)
                if (!student.faculty.isNullOrBlank()) InfoRow("Faculté / École", student.faculty)
                if (!student.department.isNullOrBlank()) InfoRow("Département", student.department)
                if (!student.program.isNullOrBlank()) InfoRow("Filière", student.program)
                InfoRow("Tuteur / Parent", "${student.guardianName} • ${student.guardianPhone}")
                if (!student.guardianEmail.isNullOrBlank()) InfoRow("E-mail tuteur", student.guardianEmail)
                if (!student.address.isNullOrBlank()) InfoRow("Adresse", student.address)
                if (!student.accessCodeDisplay.isNullOrBlank()) {
                    var showPin by remember(student.id) { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Code confidentiel (Portail résultats)", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                            Text(
                                if (showPin) "${student.accessCodeDisplay} (À communiquer à l'apprenant/parent)" else "•••••• (Masqué)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MidnightNavy
                            )
                        }
                        TextButton(onClick = { showPin = !showPin }) {
                            Text(if (showPin) "Masquer" else "Afficher")
                        }
                    }
                }
                if (balance != null) {
                    InfoRow("Scolarité", "Attendu : ${Formatters.fcfa(balance.expectedFcfa)} • Payé : ${Formatters.fcfa(balance.paidFcfa)} • Solde : ${Formatters.fcfa(balance.remainingFcfa)}")
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Documents officiels PDF", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = { onGenerateCertificate(DocumentType.CERTIFICAT_SCOLARITE) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Certificat scolarité")
                    }
                    FilledTonalButton(
                        onClick = { onGenerateCertificate(DocumentType.ATTESTATION_PRESENCE) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Attestation présence")
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Historique du matricule & Parcours", style = MaterialTheme.typography.titleMedium)
                if (student.matriculeHistory.isNotEmpty()) {
                    student.matriculeHistory.forEach { m ->
                        Text("• $m", style = MaterialTheme.typography.bodySmall, color = SkyBluePrimary)
                    }
                }
                student.historyNotes.forEach { note ->
                    Text("• $note", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (canEdit) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Text("Modifier")
                }
                OutlinedButton(onClick = onReenroll, modifier = Modifier.weight(1f)) {
                    Text("Réinscrire")
                }
            }
        }
        if (canDelete) {
            TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = CoralError)) {
                Icon(Icons.Default.Delete, null)
                Spacer(Modifier.width(6.dp))
                Text("Supprimer le dossier")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = SlateMuted, modifier = Modifier.weight(0.38f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.62f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentFormDialog(
    school: School,
    existing: Student?,
    classes: List<SchoolClass>,
    onDismiss: () -> Unit,
    onConfirm: (Student) -> Unit
) {
    var firstName by remember { mutableStateOf(existing?.firstName ?: "") }
    var lastName by remember { mutableStateOf(existing?.lastName ?: "") }
    var gender by remember { mutableStateOf(existing?.gender ?: "M") }
    var birthDate by remember { mutableStateOf(existing?.birthDate ?: "2012-01-15") }
    var birthPlace by remember { mutableStateOf(existing?.birthPlace ?: school.city) }
    var selectedClass by remember {
        mutableStateOf(classes.firstOrNull { it.id == existing?.classId } ?: classes.firstOrNull())
    }
    val isUniversity = SchoolCategory.fromRaw(school.category) == SchoolCategory.UNIVERSITE_INSTITUT
    var faculty by remember { mutableStateOf(existing?.faculty ?: selectedClass?.faculty ?: "") }
    var department by remember { mutableStateOf(existing?.department ?: selectedClass?.department ?: "") }
    var program by remember { mutableStateOf(existing?.program ?: selectedClass?.program ?: "") }
    var guardianName by remember { mutableStateOf(existing?.guardianName ?: "") }
    var guardianPhone by remember { mutableStateOf(existing?.guardianPhone ?: "+237 ") }
    var guardianEmail by remember { mutableStateOf(existing?.guardianEmail ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var photoUri by remember { mutableStateOf(existing?.photoUri) }
    var tuitionText by remember {
        mutableStateOf(
            (existing?.tuitionTotalFcfa
                ?: ((selectedClass?.tuitionFeeFcfa ?: 150_000L) + (selectedClass?.registrationFeeFcfa ?: 25_000L))).toString()
        )
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) photoUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nouvelle inscription" else "Modifier l'étudiant") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ) {
                        Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (photoUri == null) "Photo (optionnel)" else "Photo sélectionnée")
                    }
                }
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Nom *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("student_lastname_input")
                )
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("Prénom *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("student_firstname_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = gender == "M", onClick = { gender = "M" }, label = { Text("Garçon (M)") })
                    FilterChip(selected = gender == "F", onClick = { gender = "F" }, label = { Text("Fille (F)") })
                }
                OutlinedTextField(
                    value = birthDate,
                    onValueChange = { birthDate = it },
                    label = { Text("Date de naissance (AAAA-MM-JJ)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = birthPlace,
                    onValueChange = { birthPlace = it },
                    label = { Text("Lieu de naissance") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (classes.isNotEmpty()) {
                    Text(if (isUniversity) "Promotion / Filière :" else "Classe d'affectation :", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        classes.forEach { cls ->
                            FilterChip(
                                selected = selectedClass?.id == cls.id,
                                onClick = {
                                    selectedClass = cls
                                    if (!cls.faculty.isNullOrBlank()) faculty = cls.faculty
                                    if (!cls.department.isNullOrBlank()) department = cls.department
                                    if (!cls.program.isNullOrBlank()) program = cls.program
                                    if (existing == null) {
                                        tuitionText = (cls.tuitionFeeFcfa + cls.registrationFeeFcfa).toString()
                                    }
                                },
                                label = { Text(cls.name) }
                            )
                        }
                    }
                }
                if (isUniversity) {
                    OutlinedTextField(value = faculty, onValueChange = { faculty = it }, label = { Text("Faculté / École") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = department, onValueChange = { department = it }, label = { Text("Département") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = program, onValueChange = { program = it }, label = { Text("Filière / Spécialité") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(
                    value = guardianName,
                    onValueChange = { guardianName = it },
                    label = { Text("Nom du tuteur / parent *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("student_guardian_input")
                )
                OutlinedTextField(
                    value = guardianPhone,
                    onValueChange = { guardianPhone = it },
                    label = { Text("Téléphone du tuteur *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("student_phone_input")
                )
                OutlinedTextField(
                    value = guardianEmail,
                    onValueChange = { guardianEmail = it },
                    label = { Text("E-mail du tuteur (optionnel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tuitionText,
                    onValueChange = { tuitionText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Total scolarité + inscription (FCFA)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cls = selectedClass
                    onConfirm(
                        (existing ?: Student()).copy(
                            schoolId = school.id,
                            firstName = firstName,
                            lastName = lastName,
                            gender = gender,
                            birthDate = birthDate,
                            birthPlace = birthPlace,
                            classId = cls?.id ?: "non_assignee",
                            className = cls?.name ?: "Non assignée",
                            level = cls?.level ?: "Général",
                            faculty = faculty.ifBlank { null },
                            department = department.ifBlank { null },
                            program = program.ifBlank { null },
                            campus = cls?.campus ?: school.campuses.firstOrNull() ?: "Campus Principal",
                            academicYear = school.academicYear,
                            photoUri = photoUri,
                            address = address,
                            guardianName = guardianName,
                            guardianPhone = guardianPhone,
                            guardianEmail = guardianEmail,
                            tuitionTotalFcfa = tuitionText.toLongOrNull() ?: 0L
                        )
                    )
                },
                enabled = lastName.isNotBlank() && firstName.isNotBlank() && guardianName.isNotBlank() && guardianPhone.isNotBlank(),
                modifier = Modifier.testTag("student_save_button")
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
