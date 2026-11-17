package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.theme.*
import com.example.util.Formatters
import java.util.Locale

@Composable
fun ResultsPortalScreen(
    activeSchool: School,
    allSchools: List<School>,
    onSelectSchool: (String) -> Unit,
    onVerifyAndConsult: (String, SchoolCategory, String, String, String) -> Result<AnnualResult>,
    onDownloadPdf: (AnnualResult, DocumentType) -> Unit
) {
    var selectedSchoolId by remember(activeSchool.id) { mutableStateOf(activeSchool.id) }
    val targetSchool = allSchools.firstOrNull { it.id == selectedSchoolId } ?: activeSchool
    var selectedCategory by remember(targetSchool.id) {
        mutableStateOf(SchoolCategory.fromRaw(targetSchool.category))
    }
    var matricule by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }
    var accessPin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }

    var consultError by remember { mutableStateOf<String?>(null) }
    var verifiedResult by remember { mutableStateOf<AnnualResult?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("results_portal_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // En-tête officiel du portail
            Card(
                colors = CardDefaults.cardColors(containerColor = MidnightNavy)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = SkyBlueLight,
                        modifier = Modifier.size(36.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Consulter mes résultats",
                            style = MaterialTheme.typography.titleLarge,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Portail sécurisé de vérification et de téléchargement des résultats officiels de fin d'année.",
                            style = MaterialTheme.typography.bodySmall,
                            color = IceBlueContainer
                        )
                    }
                }
            }

            // Formulaire de vérification d'identité
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("1. Établissement", style = MaterialTheme.typography.titleSmall, color = MidnightNavy)
                    if (allSchools.size > 1) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            allSchools.forEach { sch ->
                                FilterChip(
                                    selected = sch.id == targetSchool.id,
                                    onClick = {
                                        selectedSchoolId = sch.id
                                        selectedCategory = SchoolCategory.fromRaw(sch.category)
                                        verifiedResult = null
                                        consultError = null
                                        onSelectSchool(sch.id)
                                    },
                                    label = { Text(sch.name) }
                                )
                            }
                        }
                    } else {
                        Surface(
                            color = IceBlueContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "${targetSchool.name} (${targetSchool.city}) — ${targetSchool.academicYear}",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.titleSmall,
                                color = MidnightNavy
                            )
                        }
                    }

                    Text("2. Catégorie d'établissement", style = MaterialTheme.typography.titleSmall, color = MidnightNavy)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SchoolCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = {
                                    selectedCategory = cat
                                    verifiedResult = null
                                    consultError = null
                                },
                                label = { Text(cat.shortLabel) }
                            )
                        }
                    }

                    Text("3. Identification sécurisée de l'apprenant", style = MaterialTheme.typography.titleSmall, color = MidnightNavy)
                    OutlinedTextField(
                        value = matricule,
                        onValueChange = { matricule = it; consultError = null },
                        label = { Text("Numéro de matricule *") },
                        placeholder = { Text("Ex: LYC-SC-2026-0001") },
                        leadingIcon = { Icon(Icons.Default.Badge, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("portal_matricule_input")
                    )

                    OutlinedTextField(
                        value = birthDate,
                        onValueChange = { birthDate = it; consultError = null },
                        label = { Text("Date de naissance (AAAA-MM-JJ) *") },
                        placeholder = { Text("Ex: 2010-05-14") },
                        leadingIcon = { Icon(Icons.Default.Cake, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("portal_birthdate_input")
                    )

                    OutlinedTextField(
                        value = accessPin,
                        onValueChange = { accessPin = it; consultError = null },
                        label = { Text("Code d'accès confidentiel (6 chiffres) *") },
                        placeholder = { Text("Remis sur votre fiche élève/étudiant") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { showPin = !showPin }) {
                                Icon(
                                    if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPin) "Masquer le code" else "Afficher le code"
                                )
                            }
                        },
                        visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("portal_pin_input")
                    )

                    Surface(color = IceBlueContainer.copy(alpha = 0.55f), shape = RoundedCornerShape(10.dp)) {
                        Text(
                            "Confidentialité garantie : le matricule seul ne donne aucun accès. Vos résultats s'affichent uniquement après validation de la date de naissance, du code confidentiel et de la publication officielle par la Direction.",
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = CharcoalInk
                        )
                    }

                    if (!consultError.isNullOrBlank()) {
                        Surface(
                            color = CoralContainer,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CoralError.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, null, tint = CoralError)
                                Text(consultError!!, color = CoralError, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                val res = onVerifyAndConsult(
                                    targetSchool.id,
                                    selectedCategory,
                                    matricule,
                                    birthDate,
                                    accessPin
                                )
                                res.onSuccess {
                                    verifiedResult = it
                                    consultError = null
                                }.onFailure { err ->
                                    verifiedResult = null
                                    consultError = err.message ?: "Consultation impossible."
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("portal_consult_button")
                        ) {
                            Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Consulter mes résultats")
                        }
                        if (verifiedResult != null) {
                            OutlinedButton(
                                onClick = {
                                    verifiedResult = null
                                    consultError = null
                                },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Nouvelle recherche")
                            }
                        }
                    }
                }
            }

            // Affichage sécurisé des résultats uniquement après vérification réussie
            verifiedResult?.let { result ->
                VerifiedAnnualResultView(
                    result = result,
                    onDownloadPdf = { docType -> onDownloadPdf(result, docType) }
                )
            }
        }
    }
}

@Composable
private fun VerifiedAnnualResultView(
    result: AnnualResult,
    onDownloadPdf: (DocumentType) -> Unit
) {
    val isUniv = result.category == SchoolCategory.UNIVERSITE_INSTITUT.name
    val decision = ResultDecision.fromRaw(result.decision)
    val lines = remember(result.subjectLinesJson) { Formatters.parseSubjectLines(result.subjectLinesJson) }
    val (decBg, decFg) = when (decision) {
        ResultDecision.ADMIS, ResultDecision.VALIDE_LMD -> EmeraldContainer to EmeraldSuccess
        ResultDecision.AJOURNE, ResultDecision.AJOURNE_LMD -> AmberContainer to AmberWarning
        ResultDecision.REDOUBLEMENT -> CoralContainer to CoralError
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, SkyBluePrimary.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(result.studentFullName, style = MaterialTheme.typography.headlineSmall, color = MidnightNavy)
                    Text("Matricule : ${result.matricule}", style = MaterialTheme.typography.titleSmall, color = SkyBluePrimary)
                }
                StatusPillBadge(decision.label, decBg, decFg)
            }

            Surface(color = IceBlueContainer.copy(alpha = 0.5f), shape = RoundedCornerShape(10.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Établissement : ${result.schoolName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("Catégorie : ${SchoolCategory.fromRaw(result.category).label}", style = MaterialTheme.typography.bodySmall)
                    if (isUniv) {
                        Text(
                            "Faculté : ${result.faculty ?: "—"} • Département : ${result.department ?: "—"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Filière : ${result.program ?: result.className} • Niveau : ${result.level}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text("Classe / Niveau : ${result.className} (${result.level})", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Année académique : ${result.academicYear} • ${result.periodLabel}", style = MaterialTheme.typography.bodySmall)
                }
            }

            // Synthèse des moyennes & crédits
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiMetricCard(
                    title = "Moyenne annuelle",
                    value = "${String.format(Locale.FRENCH, "%.2f", result.annualAverage)} / 20",
                    subtitle = "Mention : ${result.mention}",
                    icon = Icons.Default.Grade,
                    accentColor = SkyBluePrimary,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = if (isUniv) "Crédits LMD" else "Classement",
                    value = if (isUniv) "${result.creditsObtained} / ${result.creditsRequired}" else "${result.rank}e / ${result.classSize}",
                    subtitle = if (isUniv) {
                        "S1: ${result.semester1Average?.let { String.format(Locale.FRENCH, "%.2f", it) } ?: "—"} • S2: ${result.semester2Average?.let { String.format(Locale.FRENCH, "%.2f", it) } ?: "—"}"
                    } else {
                        decision.label
                    },
                    icon = Icons.Default.EmojiEvents,
                    accentColor = decFg,
                    modifier = Modifier.weight(1f)
                )
            }

            Text("Appréciation officielle : ${result.appreciation}", style = MaterialTheme.typography.bodyMedium, color = CharcoalInk)

            // Détail matières ou UE
            if (lines.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    if (isUniv) "Unités d'Enseignement (UE)" else "Détail des notes par matière",
                    style = MaterialTheme.typography.titleSmall,
                    color = MidnightNavy
                )
                lines.forEach { line ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (line.code.isNotBlank()) "${line.code} — ${line.name}" else line.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (isUniv) {
                                        "${line.semester.ifBlank { "S1" }} • ${line.credits} crédits • ${if (line.validated) "Validée" else "Non validée"}"
                                    } else {
                                        "Coeff ${line.coefficient} • ${line.appreciation}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateMuted
                                )
                            }
                            Text(
                                "${String.format(Locale.FRENCH, "%.2f", line.scoreOn20)} / 20",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (line.validated) EmeraldSuccess else CoralError,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            HorizontalDivider()
            Text("Documents officiels téléchargeables (PDF)", style = MaterialTheme.typography.titleSmall, color = MidnightNavy)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isUniv) {
                        Button(
                            onClick = { onDownloadPdf(DocumentType.RELEVE_UNIVERSITAIRE) },
                            modifier = Modifier.weight(1f).testTag("download_transcript_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Relevé universitaire", maxLines = 1)
                        }
                    } else {
                        Button(
                            onClick = { onDownloadPdf(DocumentType.BULLETIN) },
                            modifier = Modifier.weight(1f).testTag("download_bulletin_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Bulletin de notes", maxLines = 1)
                        }
                    }
                    OutlinedButton(
                        onClick = { onDownloadPdf(DocumentType.ATTESTATION_RESULTATS) },
                        modifier = Modifier.weight(1f).testTag("download_attestation_pdf_button")
                    ) {
                        Icon(Icons.Default.Verified, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Attestation", maxLines = 1)
                    }
                }
                OutlinedButton(
                    onClick = { onDownloadPdf(DocumentType.DECISION_PASSAGE) },
                    modifier = Modifier.fillMaxWidth().testTag("download_decision_pdf_button")
                ) {
                    Icon(Icons.Default.Gavel, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Télécharger la décision de passage / validation (PDF)")
                }
            }
        }
    }
}
