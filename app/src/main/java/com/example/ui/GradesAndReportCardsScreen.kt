package com.example.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
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
import java.util.Locale

@Composable
fun GradesAndReportCardsScreen(
    school: School,
    role: UserRole,
    classes: List<SchoolClass>,
    subjects: List<Subject>,
    students: List<Student>,
    grades: List<Grade>,
    onSaveGrade: (Grade) -> Unit,
    onGenerateBulletinPdf: (StudentTermReport) -> Unit,
    onUpgradeClick: () -> Unit
) {
    val planCode = PlanCode.fromRaw(school.planCode)
    val canGrade = role in listOf(UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR, UserRole.SECRETAIRE, UserRole.ENSEIGNANT)
    var tab by remember { mutableIntStateOf(if (canGrade) 0 else 1) }
    var selectedClass by remember(classes) { mutableStateOf(classes.firstOrNull()) }
    var selectedTerm by remember { mutableStateOf(TermPeriod.TRIMESTRE_1) }
    var showGradeDialog by remember { mutableStateOf(false) }

    val reports = remember(students, grades, selectedClass, selectedTerm) {
        val cid = selectedClass?.id ?: ""
        if (cid.isBlank()) emptyList() else Formatters.computeTermReports(students, grades, cid, selectedTerm)
    }

    val passCount = reports.count { it.weightedAverage >= 10.0 }
    val passRate = if (reports.isNotEmpty()) (passCount * 100f / reports.size) else 0f
    val classAvg = if (reports.isNotEmpty()) reports.map { it.weightedAverage }.average() else 0.0

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            if (canGrade) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Saisie des notes") })
            }
            Tab(selected = tab == (if (canGrade) 1 else 0), onClick = { tab = if (canGrade) 1 else 0 }, text = { Text("Classement & Bulletins") })
        }

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
                        featureName = "Notes & Bulletins PDF",
                        onUpgradeClick = onUpgradeClick
                    )
                }
            }

            // Sélecteurs de période & classe
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TermPeriod.entries.forEach { t ->
                            FilterChip(selected = selectedTerm == t, onClick = { selectedTerm = t }, label = { Text(t.label) })
                        }
                    }
                    if (classes.isNotEmpty()) {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            classes.forEach { c ->
                                FilterChip(selected = selectedClass?.id == c.id, onClick = { selectedClass = c }, label = { Text(c.name) })
                            }
                        }
                    }
                }
            }

            if (tab == 0 && canGrade) {
                item {
                    SectionHeader(
                        title = "Évaluations (${selectedTerm.label})",
                        subtitle = "${grades.count { it.term == selectedTerm.name }} note(s) enregistrée(s)",
                        actionLabel = "+ Saisir note",
                        onActionClick = { showGradeDialog = true },
                        actionTestTag = "add_grade_button"
                    )
                }
                val termGrades = grades.filter {
                    it.term == selectedTerm.name && (selectedClass == null || it.classId == selectedClass?.id)
                }
                if (termGrades.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "Aucune note saisie",
                            message = "Enregistrez les notes de devoirs, interrogations ou compositions (/20) pour calculer automatiquement les moyennes pondérées.",
                            actionLabel = if (students.isNotEmpty()) "Saisir une note" else null,
                            onAction = if (students.isNotEmpty()) { { showGradeDialog = true } } else null
                        )
                    }
                } else {
                    items(termGrades, key = { it.id }) { g ->
                        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(g.studentName, style = MaterialTheme.typography.titleMedium)
                                    Text("${g.subjectName} (Coeff ${g.coefficient}) • ${EvaluationType.fromRaw(g.evaluationType).label}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                    if (!g.comment.isNullOrBlank()) Text(g.comment, style = MaterialTheme.typography.bodySmall)
                                }
                                StatusPillBadge(
                                    "${String.format(Locale.FRENCH, "%.2f", g.score)} / 20",
                                    if (g.score >= 10.0) EmeraldContainer else CoralContainer,
                                    if (g.score >= 10.0) EmeraldSuccess else CoralError
                                )
                            }
                        }
                    }
                }
            } else {
                // Classement & Bulletins PDF
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpiMetricCard(
                            title = "Moyenne classe",
                            value = String.format(Locale.FRENCH, "%.2f / 20", classAvg),
                            subtitle = selectedClass?.name ?: "Aucune classe",
                            icon = Icons.Default.PictureAsPdf,
                            accentColor = SkyBluePrimary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = "Taux de réussite",
                            value = "${passRate.toInt()}%",
                            subtitle = "$passCount / ${reports.size} >= 10/20",
                            icon = Icons.Default.PictureAsPdf,
                            accentColor = EmeraldSuccess,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (reports.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "Aucun bulletin disponible",
                            message = "Sélectionnez une classe contenant des élèves et saisissez des notes pour générer le classement et les bulletins PDF."
                        )
                    }
                } else {
                    items(reports, key = { it.student.id }) { rep ->
                        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${rep.rank}e — ${rep.student.fullName}", style = MaterialTheme.typography.titleMedium)
                                        Text("${rep.student.matricule} • Mention : ${rep.mention}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                    }
                                    StatusPillBadge(
                                        "${String.format(Locale.FRENCH, "%.2f", rep.weightedAverage)}/20",
                                        if (rep.weightedAverage >= 10.0) EmeraldContainer else CoralContainer,
                                        if (rep.weightedAverage >= 10.0) EmeraldSuccess else CoralError
                                    )
                                }
                                rep.subjectAverages.forEach { sa ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${sa.subjectName} (×${sa.coefficient})", style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "${String.format(Locale.FRENCH, "%.2f", sa.averageOn20)} / 20",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { onGenerateBulletinPdf(rep) },
                                    modifier = Modifier.fillMaxWidth().testTag("pdf_bulletin_${rep.student.id}")
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Générer Bulletin PDF")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showGradeDialog) {
        val classStudents = students.filter { selectedClass == null || it.classId == selectedClass?.id }
        val classSubjects = subjects.filter { selectedClass == null || it.classId == selectedClass?.id }
        var pickedStudent by remember { mutableStateOf(classStudents.firstOrNull() ?: students.firstOrNull()) }
        var pickedSubject by remember { mutableStateOf(classSubjects.firstOrNull() ?: subjects.firstOrNull()) }
        var customSubjName by remember { mutableStateOf("Mathématiques") }
        var customCoeff by remember { mutableStateOf("4") }
        var evalType by remember { mutableStateOf(EvaluationType.DEVOIR) }
        var scoreText by remember { mutableStateOf("14.5") }
        var teacherName by remember { mutableStateOf("Enseignant") }
        var comment by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showGradeDialog = false },
            title = { Text("Saisir une note (/20)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (classStudents.isNotEmpty()) {
                        Text("Élève :", style = MaterialTheme.typography.labelMedium)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            classStudents.forEach { st ->
                                FilterChip(selected = pickedStudent?.id == st.id, onClick = { pickedStudent = st }, label = { Text(st.fullName) })
                            }
                        }
                    }
                    if (classSubjects.isNotEmpty()) {
                        Text("Matière :", style = MaterialTheme.typography.labelMedium)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            classSubjects.forEach { sub ->
                                FilterChip(selected = pickedSubject?.id == sub.id, onClick = { pickedSubject = sub }, label = { Text("${sub.name} (×${sub.coefficient})") })
                            }
                        }
                    } else {
                        OutlinedTextField(value = customSubjName, onValueChange = { customSubjName = it }, label = { Text("Matière") }, singleLine = true)
                        OutlinedTextField(value = customCoeff, onValueChange = { customCoeff = it.filter(Char::isDigit) }, label = { Text("Coefficient") }, singleLine = true)
                    }
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EvaluationType.entries.forEach { ev ->
                            FilterChip(selected = evalType == ev, onClick = { evalType = ev }, label = { Text(ev.label) })
                        }
                    }
                    OutlinedTextField(
                        value = scoreText,
                        onValueChange = { scoreText = it },
                        label = { Text("Note sur 20 *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("grade_score_input")
                    )
                    OutlinedTextField(value = teacherName, onValueChange = { teacherName = it }, label = { Text("Enseignant") }, singleLine = true)
                    OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Appréciation") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val st = pickedStudent ?: return@Button
                        val parsedScore = scoreText.replace(",", ".").toDoubleOrNull()?.coerceIn(0.0, 20.0) ?: 0.0
                        onSaveGrade(
                            Grade(
                                schoolId = school.id,
                                studentId = st.id,
                                studentName = st.fullName,
                                classId = st.classId,
                                className = st.className,
                                subjectId = pickedSubject?.id ?: customSubjName.lowercase().replace(" ", "_"),
                                subjectName = pickedSubject?.name ?: customSubjName,
                                coefficient = pickedSubject?.coefficient ?: (customCoeff.toLongOrNull() ?: 1L),
                                term = selectedTerm.name,
                                evaluationType = evalType.name,
                                score = parsedScore,
                                maxScore = 20.0,
                                teacherName = teacherName.ifBlank { "Enseignant" },
                                comment = comment
                            )
                        )
                        showGradeDialog = false
                    },
                    enabled = pickedStudent != null && scoreText.replace(",", ".").toDoubleOrNull() != null
                ) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { showGradeDialog = false }) { Text("Annuler") } }
        )
    }
}
