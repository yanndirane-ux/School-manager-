package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.util.Locale

@Composable
fun ResultsAdminScreen(
    school: School,
    role: UserRole,
    classes: List<SchoolClass>,
    subjects: List<Subject>,
    students: List<Student>,
    grades: List<Grade>,
    annualResults: List<AnnualResult>,
    isProcessing: Boolean,
    onGenerateDrafts: (String?, String) -> Unit,
    onSaveOrCorrectResult: (AnnualResult, String?) -> Unit,
    onPublishOrSchedule: (List<AnnualResult>, ResultStatus, Long) -> Unit,
    onImportGradesCsv: (Uri, TermPeriod) -> Unit,
    onDownloadPdf: (AnnualResult, DocumentType) -> Unit,
    onExportStatsPdf: (List<AnnualResult>) -> Unit,
    onExportStatsCsv: (List<AnnualResult>) -> Unit
) {
    val category = SchoolCategory.fromRaw(school.category)
    val isUniv = category == SchoolCategory.UNIVERSITE_INSTITUT
    val canComputeEdit = role in setOf(
        UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR,
        UserRole.SECRETAIRE, UserRole.ENSEIGNANT
    )
    val canPublishOrCorrect = role in setOf(
        UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR
    )

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedClassId by remember { mutableStateOf<String?>(null) }
    var periodLabel by remember(school.id, school.academicYear) {
        mutableStateOf("Résultats annuels ${school.academicYear}")
    }
    var importTerm by remember(school.termMode) {
        mutableStateOf(
            if (school.termMode == "SEMESTRE" || isUniv) TermPeriod.SEMESTRE_1 else TermPeriod.TRIMESTRE_3
        )
    }

    var editingResult by remember { mutableStateOf<AnnualResult?>(null) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showScheduleDialog by remember { mutableStateOf(false) }

    val filteredResults = remember(annualResults, selectedClassId) {
        annualResults.filter { selectedClassId.isNullOrBlank() || it.classId == selectedClassId }
            .sortedByDescending { it.annualAverage }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportGradesCsv(uri, importTerm)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("results_admin_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = "Gestion des résultats annuels",
            subtitle = "${category.shortLabel} • ${school.academicYear} • ${annualResults.size} dossier(s)"
        )

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Calcul & Brouillons", maxLines = 1) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Publication", maxLines = 1) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Statistiques", maxLines = 1) }
            )
        }

        // Filtre classes
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedClassId == null,
                onClick = { selectedClassId = null },
                label = { Text("Toutes classes (${annualResults.size})") }
            )
            classes.forEach { cls ->
                val count = annualResults.count { it.classId == cls.id }
                FilterChip(
                    selected = selectedClassId == cls.id,
                    onClick = { selectedClassId = cls.id },
                    label = { Text("${cls.name} ($count)") }
                )
            }
        }

        when (selectedTab) {
            0 -> {
                Card(colors = CardDefaults.cardColors(containerColor = IceBlueContainer.copy(alpha = 0.7f))) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Règles de délibération actives", style = MaterialTheme.typography.titleSmall, color = MidnightNavy)
                        Text(
                            if (isUniv) {
                                "Seuil validation UE : ${school.creditPassThreshold}/20 • Crédits annuels : ${school.annualCreditsRequired} • Compensation LMD : ${if (school.lmdCompensation) "Active" else "Désactivée"}"
                            } else {
                                "Seuil passage / admission : ${school.passingAverage}/20 • Mode : ${school.termMode} • ${students.size} élève(s), ${grades.size} note(s), ${subjects.size} matière(s)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = CharcoalInk
                        )

                        OutlinedTextField(
                            value = periodLabel,
                            onValueChange = { periodLabel = it },
                            label = { Text("Libellé officiel de la session / période") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        if (canComputeEdit) {
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val stacked = maxWidth < 480.dp
                                if (stacked) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { onGenerateDrafts(selectedClassId, periodLabel) },
                                            enabled = !isProcessing,
                                            modifier = Modifier.fillMaxWidth().testTag("compute_results_button")
                                        ) {
                                            Icon(Icons.Default.Calculate, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Calculer depuis notes")
                                        }
                                        OutlinedButton(
                                            onClick = { csvLauncher.launch(arrayOf("text/*", "text/csv", "application/vnd.ms-excel")) },
                                            enabled = !isProcessing,
                                            modifier = Modifier.fillMaxWidth().testTag("import_grades_csv_button")
                                        ) {
                                            Icon(Icons.Default.UploadFile, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Importer CSV notes (${importTerm.label})")
                                        }
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = { onGenerateDrafts(selectedClassId, periodLabel) },
                                            enabled = !isProcessing,
                                            modifier = Modifier.weight(1f).testTag("compute_results_button")
                                        ) {
                                            Icon(Icons.Default.Calculate, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Calculer depuis notes")
                                        }
                                        OutlinedButton(
                                            onClick = { csvLauncher.launch(arrayOf("text/*", "text/csv", "application/vnd.ms-excel")) },
                                            enabled = !isProcessing,
                                            modifier = Modifier.weight(1f).testTag("import_grades_csv_button")
                                        ) {
                                            Icon(Icons.Default.UploadFile, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Importer CSV notes")
                                        }
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Période import CSV :", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                                val terms = if (isUniv) listOf(TermPeriod.SEMESTRE_1, TermPeriod.SEMESTRE_2)
                                else listOf(TermPeriod.TRIMESTRE_1, TermPeriod.TRIMESTRE_2, TermPeriod.TRIMESTRE_3)
                                terms.forEach { t ->
                                    FilterChip(
                                        selected = importTerm == t,
                                        onClick = { importTerm = t },
                                        label = { Text(t.label) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (filteredResults.isEmpty()) {
                    EmptyStateCard(
                        title = "Aucun résultat calculé",
                        message = "Saisissez ou importez les notes puis appuyez sur « Calculer depuis notes » pour générer les brouillons de résultats annuels."
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(filteredResults, key = { it.id }) { res ->
                            AnnualResultAdminCard(
                                result = res,
                                isUniv = isUniv,
                                canEdit = canComputeEdit && (res.status == ResultStatus.BROUILLON.name || res.status == ResultStatus.VALIDE.name || canPublishOrCorrect),
                                onEdit = { editingResult = res },
                                onDownloadPdf = {
                                    val docType = if (isUniv) DocumentType.RELEVE_UNIVERSITAIRE else DocumentType.BULLETIN
                                    onDownloadPdf(res, docType)
                                }
                            )
                        }
                    }
                }
            }

            1 -> {
                if (!canPublishOrCorrect) {
                    Card(colors = CardDefaults.cardColors(containerColor = AmberContainer)) {
                        Text(
                            "Seuls le Directeur ou l'Administrateur de l'établissement peuvent valider, programmer ou publier officiellement les résultats.",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MidnightNavy
                        )
                    }
                } else {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${selectedIds.size} sélectionné(s) / ${filteredResults.size}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MidnightNavy
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { selectedIds = filteredResults.map { it.id }.toSet() }) {
                                        Text("Tout cocher")
                                    }
                                    TextButton(onClick = { selectedIds = emptySet() }) {
                                        Text("Effacer")
                                    }
                                }
                            }
                            val targetBatch = remember(filteredResults, selectedIds) {
                                if (selectedIds.isEmpty()) filteredResults
                                else filteredResults.filter { it.id in selectedIds }
                            }
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val stacked = maxWidth < 500.dp
                                if (stacked) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { onPublishOrSchedule(targetBatch, ResultStatus.VALIDE, 0L) },
                                            enabled = !isProcessing && targetBatch.isNotEmpty(),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("1. Valider sélection (${targetBatch.size})")
                                        }
                                        Button(
                                            onClick = { onPublishOrSchedule(targetBatch, ResultStatus.PUBLIE, 0L) },
                                            enabled = !isProcessing && targetBatch.isNotEmpty(),
                                            modifier = Modifier.fillMaxWidth().testTag("publish_results_now_button")
                                        ) {
                                            Icon(Icons.Default.Publish, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("2. Publier maintenant")
                                        }
                                        FilledTonalButton(
                                            onClick = { showScheduleDialog = true },
                                            enabled = !isProcessing && targetBatch.isNotEmpty(),
                                            modifier = Modifier.fillMaxWidth().testTag("schedule_publish_button")
                                        ) {
                                            Icon(Icons.Default.ScheduleSend, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Programmer une date")
                                        }
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { onPublishOrSchedule(targetBatch, ResultStatus.VALIDE, 0L) },
                                            enabled = !isProcessing && targetBatch.isNotEmpty(),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Valider (${targetBatch.size})", maxLines = 1)
                                        }
                                        Button(
                                            onClick = { onPublishOrSchedule(targetBatch, ResultStatus.PUBLIE, 0L) },
                                            enabled = !isProcessing && targetBatch.isNotEmpty(),
                                            modifier = Modifier.weight(1f).testTag("publish_results_now_button")
                                        ) {
                                            Text("Publier maintenant", maxLines = 1)
                                        }
                                        FilledTonalButton(
                                            onClick = { showScheduleDialog = true },
                                            enabled = !isProcessing && targetBatch.isNotEmpty(),
                                            modifier = Modifier.weight(1f).testTag("schedule_publish_button")
                                        ) {
                                            Text("Programmer", maxLines = 1)
                                        }
                                    }
                                }
                            }
                            Text(
                                "Confidentialité : les brouillons, résultats validés et publications programmées futures restent strictement invisibles aux comptes Élève et Parent jusqu'à l'heure officielle.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateMuted
                            )
                        }
                    }
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredResults, key = { it.id }) { res ->
                        val checked = res.id in selectedIds
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (checked) IceBlueContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (canPublishOrCorrect) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { c ->
                                            selectedIds = if (c) selectedIds + res.id else selectedIds - res.id
                                        }
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${res.studentFullName} (${res.matricule})", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${res.className} • Moy: ${String.format(Locale.FRENCH, "%.2f", res.annualAverage)}/20 • ${ResultDecision.fromRaw(res.decision).label}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateMuted
                                    )
                                    if (res.status == ResultStatus.PROGRAMME.name && res.scheduledPublishAtMillis > 0L) {
                                        Text(
                                            "Prévu le ${Formatters.formatDateTimeMillis(res.scheduledPublishAtMillis)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SkyBluePrimary
                                        )
                                    }
                                }
                                ResultStatusPill(ResultStatus.fromRaw(res.status))
                            }
                        }
                    }
                }
            }

            2 -> {
                val total = filteredResults.size
                val admis = filteredResults.count {
                    it.decision == ResultDecision.ADMIS.name || it.decision == ResultDecision.VALIDE_LMD.name
                }
                val ajournes = filteredResults.count {
                    it.decision == ResultDecision.AJOURNE.name || it.decision == ResultDecision.AJOURNE_LMD.name
                }
                val redoublants = filteredResults.count { it.decision == ResultDecision.REDOUBLEMENT.name }
                val rate = if (total > 0) (admis * 100.0 / total) else 0.0
                val avgAll = if (total > 0) filteredResults.map { it.annualAverage }.average() else 0.0

                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiMetricCard(
                                title = "Taux de réussite",
                                value = "${String.format(Locale.FRENCH, "%.1f", rate)}%",
                                subtitle = "$admis admis / $total évalués",
                                icon = Icons.Default.EmojiEvents,
                                accentColor = EmeraldSuccess,
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricCard(
                                title = "Moyenne globale",
                                value = "${String.format(Locale.FRENCH, "%.2f", avgAll)}/20",
                                subtitle = "$ajournes ajourné(s) • $redoublants red.",
                                icon = Icons.Default.Insights,
                                accentColor = SkyBluePrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onExportStatsPdf(filteredResults) },
                                enabled = filteredResults.isNotEmpty(),
                                modifier = Modifier.weight(1f).testTag("export_stats_pdf_button")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Rapport PDF")
                            }
                            OutlinedButton(
                                onClick = { onExportStatsCsv(filteredResults) },
                                enabled = filteredResults.isNotEmpty(),
                                modifier = Modifier.weight(1f).testTag("export_stats_csv_button")
                            ) {
                                Icon(Icons.Default.TableChart, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Export CSV/Excel")
                            }
                        }
                    }
                    items(filteredResults, key = { "stat_${it.id}" }) { res ->
                        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${res.rank}e — ${res.studentFullName}", style = MaterialTheme.typography.titleSmall)
                                        Text("${res.matricule} • ${res.className}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                    }
                                    Text(
                                        "${String.format(Locale.FRENCH, "%.2f", res.annualAverage)}/20",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = SkyBluePrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isUniv) {
                                        FilledTonalButton(onClick = { onDownloadPdf(res, DocumentType.RELEVE_UNIVERSITAIRE) }) {
                                            Text("Relevé LMD PDF")
                                        }
                                    } else {
                                        FilledTonalButton(onClick = { onDownloadPdf(res, DocumentType.BULLETIN) }) {
                                            Text("Bulletin PDF")
                                        }
                                    }
                                    OutlinedButton(onClick = { onDownloadPdf(res, DocumentType.ATTESTATION_RESULTATS) }) {
                                        Text("Attestation PDF")
                                    }
                                    OutlinedButton(onClick = { onDownloadPdf(res, DocumentType.DECISION_PASSAGE) }) {
                                        Text("Décision PDF")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingResult?.let { current ->
        AnnualResultEditDialog(
            result = current,
            isUniv = isUniv,
            onDismiss = { editingResult = null },
            onSave = { updated, reason ->
                onSaveOrCorrectResult(updated, reason)
                editingResult = null
            }
        )
    }

    if (showScheduleDialog) {
        val targetBatch = if (selectedIds.isEmpty()) filteredResults else filteredResults.filter { it.id in selectedIds }
        SchedulePublishDialog(
            count = targetBatch.size,
            onDismiss = { showScheduleDialog = false },
            onConfirm = { millis ->
                onPublishOrSchedule(targetBatch, ResultStatus.PROGRAMME, millis)
                showScheduleDialog = false
            }
        )
    }
}

@Composable
private fun AnnualResultAdminCard(
    result: AnnualResult,
    isUniv: Boolean,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDownloadPdf: () -> Unit
) {
    val dec = ResultDecision.fromRaw(result.decision)
    val st = ResultStatus.fromRaw(result.status)
    val isLockedPublished = st == ResultStatus.PUBLIE || st == ResultStatus.PROGRAMME

    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(result.studentFullName, style = MaterialTheme.typography.titleMedium, color = MidnightNavy)
                    Text("${result.matricule} • ${result.className} (${result.level})", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                }
                ResultStatusPill(st)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Moyenne : ${String.format(Locale.FRENCH, "%.2f", result.annualAverage)} / 20 (${result.mention})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SkyBluePrimary
                )
                Text(
                    if (isUniv) "${result.creditsObtained}/${result.creditsRequired} crédits"
                    else "Rang ${result.rank}/${result.classSize}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MidnightNavy
                )
            }

            if (isUniv && (result.semester1Average != null || result.semester2Average != null)) {
                Text(
                    "S1 : ${result.semester1Average?.let { String.format(Locale.FRENCH, "%.2f", it) } ?: "—"}  •  S2 : ${result.semester2Average?.let { String.format(Locale.FRENCH, "%.2f", it) } ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateMuted
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DecisionPill(dec)
                if (result.revisionCount > 0L) {
                    StatusPillBadge("Corrigé x${result.revisionCount}", AmberContainer, AmberWarning)
                }
            }

            Text(result.appreciation, style = MaterialTheme.typography.bodySmall, color = CharcoalInk)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canEdit) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isLockedPublished) "Corriger" else "Modifier")
                    }
                }
                FilledTonalButton(onClick = onDownloadPdf, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PictureAsPdf, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isUniv) "Relevé PDF" else "Bulletin PDF")
                }
            }
        }
    }
}

@Composable
private fun ResultStatusPill(status: ResultStatus) {
    val (bg, fg) = when (status) {
        ResultStatus.BROUILLON -> SlateBorder to CharcoalInk
        ResultStatus.VALIDE -> IceBlueContainer to SkyBluePrimary
        ResultStatus.PROGRAMME -> AmberContainer to AmberWarning
        ResultStatus.PUBLIE -> EmeraldContainer to EmeraldSuccess
    }
    StatusPillBadge(status.label, bg, fg)
}

@Composable
private fun DecisionPill(decision: ResultDecision) {
    val (bg, fg) = when (decision) {
        ResultDecision.ADMIS, ResultDecision.VALIDE_LMD -> EmeraldContainer to EmeraldSuccess
        ResultDecision.AJOURNE, ResultDecision.AJOURNE_LMD -> AmberContainer to AmberWarning
        ResultDecision.REDOUBLEMENT -> CoralContainer to CoralError
    }
    StatusPillBadge(decision.label, bg, fg)
}

@Composable
private fun AnnualResultEditDialog(
    result: AnnualResult,
    isUniv: Boolean,
    onDismiss: () -> Unit,
    onSave: (AnnualResult, String?) -> Unit
) {
    val needsReason = result.status == ResultStatus.PUBLIE.name || result.status == ResultStatus.PROGRAMME.name
    var annualAvgText by remember { mutableStateOf(String.format(Locale.US, "%.2f", result.annualAverage)) }
    var s1Text by remember { mutableStateOf(result.semester1Average?.let { String.format(Locale.US, "%.2f", it) } ?: "") }
    var s2Text by remember { mutableStateOf(result.semester2Average?.let { String.format(Locale.US, "%.2f", it) } ?: "") }
    var creditsObtText by remember { mutableStateOf(result.creditsObtained.toString()) }
    var decision by remember { mutableStateOf(ResultDecision.fromRaw(result.decision)) }
    var mention by remember { mutableStateOf(result.mention) }
    var appreciation by remember { mutableStateOf(result.appreciation) }
    var correctionReason by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf(Formatters.parseSubjectLines(result.subjectLinesJson)) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (needsReason) "Corriger résultat publié" else "Modifier brouillon — ${result.studentFullName}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = annualAvgText,
                    onValueChange = {
                        annualAvgText = it
                        it.replace(",", ".").toDoubleOrNull()?.let { v ->
                            mention = Formatters.mentionForAverage(v)
                        }
                    },
                    label = { Text("Moyenne annuelle / 20") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isUniv) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = s1Text,
                            onValueChange = { s1Text = it },
                            label = { Text("Moy. S1") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = s2Text,
                            onValueChange = { s2Text = it },
                            label = { Text("Moy. S2") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = creditsObtText,
                        onValueChange = { creditsObtText = it },
                        label = { Text("Crédits obtenus / ${result.creditsRequired}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text("Décision du jury :", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val options = if (isUniv) {
                        listOf(ResultDecision.VALIDE_LMD, ResultDecision.AJOURNE_LMD)
                    } else {
                        listOf(ResultDecision.ADMIS, ResultDecision.AJOURNE, ResultDecision.REDOUBLEMENT)
                    }
                    options.forEach { d ->
                        FilterChip(
                            selected = decision == d,
                            onClick = { decision = d },
                            label = { Text(d.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = mention,
                    onValueChange = { mention = it },
                    label = { Text("Mention") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = appreciation,
                    onValueChange = { appreciation = it },
                    label = { Text("Appréciation") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (lines.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Notes détaillées (${lines.size}) :", style = MaterialTheme.typography.labelLarge)
                    lines.forEachIndexed { idx, line ->
                        var lineScore by remember(idx) { mutableStateOf(String.format(Locale.US, "%.2f", line.scoreOn20)) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(line.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = lineScore,
                                onValueChange = { raw ->
                                    lineScore = raw
                                    raw.replace(",", ".").toDoubleOrNull()?.let { sc ->
                                        val clamped = sc.coerceIn(0.0, 20.0)
                                        lines = lines.toMutableList().also { mut ->
                                            mut[idx] = line.copy(scoreOn20 = clamped, validated = clamped >= 10.0)
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.width(86.dp)
                            )
                        }
                    }
                }

                if (needsReason) {
                    OutlinedTextField(
                        value = correctionReason,
                        onValueChange = { correctionReason = it; errorMsg = null },
                        label = { Text("Motif de correction post-publication *") },
                        placeholder = { Text("Ex: Erreur de report note Mathématiques") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (!errorMsg.isNullOrBlank()) {
                    Text(errorMsg!!, color = CoralError, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val avg = annualAvgText.replace(",", ".").toDoubleOrNull()?.coerceIn(0.0, 20.0)
                    if (avg == null) {
                        errorMsg = "Moyenne annuelle invalide."
                        return@Button
                    }
                    if (needsReason && correctionReason.isBlank()) {
                        errorMsg = "Veuillez saisir un motif de correction."
                        return@Button
                    }
                    val updated = result.copy(
                        average = avg,
                        annualAverage = avg,
                        semester1Average = s1Text.replace(",", ".").toDoubleOrNull()?.coerceIn(0.0, 20.0),
                        semester2Average = s2Text.replace(",", ".").toDoubleOrNull()?.coerceIn(0.0, 20.0),
                        creditsObtained = creditsObtText.toLongOrNull()?.coerceAtLeast(0L) ?: result.creditsObtained,
                        decision = decision.name,
                        mention = mention.ifBlank { Formatters.mentionForAverage(avg) },
                        appreciation = appreciation,
                        subjectLinesJson = Formatters.serializeSubjectLines(lines)
                    )
                    onSave(updated, correctionReason.ifBlank { null })
                }
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun SchedulePublishDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var dateIso by remember { mutableStateOf(Formatters.todayIso()) }
    var timeHm by remember { mutableStateOf("18:00") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Programmer la publication ($count dossier(s))") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Les résultats resteront masqués aux élèves et parents jusqu'à la date et l'heure indiquées.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateMuted
                )
                OutlinedTextField(
                    value = dateIso,
                    onValueChange = { dateIso = it; error = null },
                    label = { Text("Date de publication (AAAA-MM-JJ)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = timeHm,
                    onValueChange = { timeHm = it; error = null },
                    label = { Text("Heure (HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!error.isNullOrBlank()) {
                    Text(error!!, color = CoralError, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val millis = Formatters.parseDateTimeMillis(dateIso, timeHm)
                    if (millis == null) {
                        error = "Format attendu : AAAA-MM-JJ et HH:mm (ex: 2026-06-30 et 18:00)."
                    } else {
                        onConfirm(millis)
                    }
                }
            ) {
                Text("Programmer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
