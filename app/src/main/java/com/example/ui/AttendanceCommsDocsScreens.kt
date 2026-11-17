package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.theme.*
import com.example.util.Formatters

@Composable
fun AttendanceScreen(
    school: School,
    role: UserRole,
    classes: List<SchoolClass>,
    students: List<Student>,
    attendance: List<AttendanceRecord>,
    onRecordAttendance: (Student, AttendanceStatus, Boolean, String) -> Unit,
    onJustifyAttendance: (AttendanceRecord, String) -> Unit,
    onUpgradeClick: () -> Unit
) {
    val planCode = PlanCode.fromRaw(school.planCode)
    val canMark = role in listOf(UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR, UserRole.SECRETAIRE, UserRole.ENSEIGNANT)
    var tab by remember { mutableIntStateOf(if (canMark) 0 else 1) }
    var selectedClass by remember(classes) { mutableStateOf(classes.firstOrNull()) }
    var justifyTarget by remember { mutableStateOf<AttendanceRecord?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            if (canMark) Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Appel du jour") })
            Tab(selected = tab == (if (canMark) 1 else 0), onClick = { tab = if (canMark) 1 else 0 }, text = { Text("Historique & Justifications") })
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
                        featureName = "Suivi avancé des absences",
                        onUpgradeClick = onUpgradeClick
                    )
                }
            }

            if (classes.isNotEmpty()) {
                item {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        classes.forEach { c ->
                            FilterChip(selected = selectedClass?.id == c.id, onClick = { selectedClass = c }, label = { Text(c.name) })
                        }
                    }
                }
            }

            if (tab == 0 && canMark) {
                val classStudents = students.filter { selectedClass == null || it.classId == selectedClass?.id }
                item {
                    SectionHeader(
                        title = "Appel du ${Formatters.todayIso()}",
                        subtitle = "${classStudents.size} élève(s) dans ${selectedClass?.name ?: "l'établissement"}"
                    )
                }
                if (classStudents.isEmpty()) {
                    item { EmptyStateCard("Aucun élève à appeler", "Sélectionnez une classe contenant des élèves inscrits.") }
                } else {
                    items(classStudents, key = { it.id }) { st ->
                        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${st.fullName} (${st.matricule})", style = MaterialTheme.typography.titleMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = { onRecordAttendance(st, AttendanceStatus.PRESENT, true, "") },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Présent") }
                                    OutlinedButton(
                                        onClick = { onRecordAttendance(st, AttendanceStatus.RETARD, false, "Retard constaté") },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Retard") }
                                    OutlinedButton(
                                        onClick = { onRecordAttendance(st, AttendanceStatus.ABSENT, false, "Absence non justifiée") },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralError),
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Absent") }
                                }
                            }
                        }
                    }
                }
            } else {
                val filteredAtt = attendance
                    .filter { selectedClass == null || it.classId == selectedClass?.id || it.classId == "STAFF" }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                item {
                    val absCount = filteredAtt.count { it.status == AttendanceStatus.ABSENT.name }
                    val lateCount = filteredAtt.count { it.status == AttendanceStatus.RETARD.name }
                    SectionHeader("Journal des présences", "$absCount absence(s) • $lateCount retard(s)")
                }
                if (filteredAtt.isEmpty()) {
                    item { EmptyStateCard("Aucun enregistrement", "Les appels effectués apparaîtront ici avec leurs justificatifs.") }
                } else {
                    items(filteredAtt, key = { it.id }) { rec ->
                        val st = AttendanceStatus.fromRaw(rec.status)
                        val colors = when (st) {
                            AttendanceStatus.PRESENT -> EmeraldContainer to EmeraldSuccess
                            AttendanceStatus.RETARD -> AmberContainer to AmberWarning
                            AttendanceStatus.ABSENT -> CoralContainer to CoralError
                        }
                        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rec.targetName, style = MaterialTheme.typography.titleMedium)
                                    Text("${rec.className} • ${rec.dateIso}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                    if (!rec.reason.isNullOrBlank()) Text("Motif : ${rec.reason}", style = MaterialTheme.typography.bodySmall)
                                }
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    StatusPillBadge(st.label, colors.first, colors.second)
                                    if (st != AttendanceStatus.PRESENT && !rec.justified && canMark) {
                                        TextButton(onClick = { justifyTarget = rec }) { Text("Justifier") }
                                    } else if (rec.justified && st != AttendanceStatus.PRESENT) {
                                        Text("Justifiée", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (justifyTarget != null) {
        var reason by remember { mutableStateOf("Certificat médical / Mot d'excuse parent") }
        AlertDialog(
            onDismissRequest = { justifyTarget = null },
            title = { Text("Justifier l'absence") },
            text = {
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Motif officiel *") }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = { onJustifyAttendance(justifyTarget!!, reason); justifyTarget = null }, enabled = reason.isNotBlank()) {
                    Text("Valider")
                }
            },
            dismissButton = { TextButton(onClick = { justifyTarget = null }) { Text("Annuler") } }
        )
    }
}

@Composable
fun CommsAndDocsScreen(
    school: School,
    role: UserRole,
    students: List<Student>,
    announcements: List<Announcement>,
    documents: List<SchoolDocument>,
    onPublishAnnouncement: (String, String, String, ChannelType, String) -> Unit,
    onGenerateStudentDoc: (Student, DocumentType) -> Unit,
    onOpenGatewaySettings: () -> Unit,
    onUpgradeClick: () -> Unit
) {
    val context = LocalContext.current
    val planCode = PlanCode.fromRaw(school.planCode)
    val canPublish = role in listOf(UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR, UserRole.SECRETAIRE, UserRole.COMPTABLE, UserRole.ENSEIGNANT)
    var tab by remember { mutableIntStateOf(0) }
    var showMsgDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Communication") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Documents PDF") })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (planCode.rank < PlanCode.ENTERPRISE.rank) {
                item {
                    PlanGateBanner(
                        requiredPlan = PlanCode.ENTERPRISE,
                        currentPlan = planCode,
                        featureName = "Notifications automatisées & Gestion documentaire avancée",
                        onUpgradeClick = onUpgradeClick
                    )
                }
            }

            if (tab == 0) {
                item {
                    SectionHeader(
                        title = "Annonces & Notifications",
                        subtitle = "WhatsApp API : ${if (school.whatsappConfigured) "Configurée" else "En attente"} • SMS API : ${if (school.smsConfigured) "Configurée" else "En attente"}",
                        actionLabel = if (canPublish) "+ Diffuser" else null,
                        onActionClick = if (canPublish) { { showMsgDialog = true } } else null,
                        actionTestTag = "add_announcement_button"
                    )
                }
                if (announcements.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "Aucune annonce publiée",
                            message = "Informez les parents des résultats, rappels d'échéances FCFA ou réunions.",
                            actionLabel = if (canPublish) "Publier une annonce" else null,
                            onAction = if (canPublish) { { showMsgDialog = true } } else null
                        )
                    }
                } else {
                    items(announcements.sortedByDescending { it.createdAt?.seconds ?: 0L }, key = { it.id }) { ann ->
                        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(ann.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    val statusBadge = when (ann.deliveryStatus) {
                                        "ENVOYE_API", "PUBLIE" -> EmeraldContainer to EmeraldSuccess
                                        "EN_ATTENTE_CONFIG_API" -> AmberContainer to AmberWarning
                                        else -> CoralContainer to CoralError
                                    }
                                    StatusPillBadge(
                                        if (ann.deliveryStatus == "EN_ATTENTE_CONFIG_API") "API en attente" else ann.deliveryStatus,
                                        statusBadge.first,
                                        statusBadge.second
                                    )
                                }
                                Text(ann.message, style = MaterialTheme.typography.bodyMedium)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("${ChannelType.fromRaw(ann.channel).label} • ${ann.authorName}", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                                    TextButton(onClick = {
                                        val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
                                            putExtra("sms_body", "${school.name} : ${ann.title} - ${ann.message}")
                                        }
                                        runCatching { context.startActivity(smsIntent) }
                                    }) { Text("Relayer par SMS appareil") }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    SectionHeader("Génération rapide de documents PDF", "Certificats & attestations archivés")
                }
                if (students.isNotEmpty() && canPublish) {
                    item {
                        var picked by remember { mutableStateOf(students.first()) }
                        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    students.forEach { st ->
                                        FilterChip(selected = picked.id == st.id, onClick = { picked = st }, label = { Text(st.fullName) })
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onGenerateStudentDoc(picked, DocumentType.CERTIFICAT_SCOLARITE) }, modifier = Modifier.weight(1f)) {
                                        Text("Certificat scolarité")
                                    }
                                    OutlinedButton(onClick = { onGenerateStudentDoc(picked, DocumentType.ATTESTATION_PRESENCE) }, modifier = Modifier.weight(1f)) {
                                        Text("Attestation présence")
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    SectionHeader("Archives numériques (${documents.size})")
                }
                if (documents.isEmpty()) {
                    item { EmptyStateCard("Archive vide", "Les certificats, attestations, bulletins et reçus générés apparaîtront ici.") }
                } else {
                    items(documents.sortedByDescending { it.createdAt?.seconds ?: 0L }, key = { it.id }) { doc ->
                        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Description, null, tint = SkyBluePrimary)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(doc.title, style = MaterialTheme.typography.titleSmall)
                                    Text("${doc.referenceNumber} • ${doc.summaryText}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                }
                                StatusPillBadge(DocumentType.fromRaw(doc.docType).label, IceBlueContainer, MidnightNavy)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMsgDialog) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("ANNONCE") }
        var channel by remember { mutableStateOf(ChannelType.INTERNE) }
        var audience by remember { mutableStateOf("TOUS") }
        AlertDialog(
            onDismissRequest = { showMsgDialog = false },
            title = { Text("Diffuser une notification") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Objet *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Message *") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ANNONCE", "PAIEMENT", "RESULTATS", "EVENEMENT").forEach { c ->
                            FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                        }
                    }
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChannelType.entries.forEach { ch ->
                            FilterChip(selected = channel == ch, onClick = { channel = ch }, label = { Text(ch.label) })
                        }
                    }
                    if ((channel == ChannelType.WHATSAPP && !school.whatsappConfigured) || (channel == ChannelType.SMS && !school.smsConfigured)) {
                        Surface(color = AmberContainer, shape = MaterialTheme.shapes.small) {
                            Text(
                                "API ${channel.label} non configurée : le message sera archivé dans l'app avec le statut « En attente de configuration API ».",
                                style = MaterialTheme.typography.bodySmall,
                                color = AmberWarning,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPublishAnnouncement(title, body, category, channel, audience)
                        showMsgDialog = false
                    },
                    enabled = title.isNotBlank() && body.isNotBlank()
                ) { Text("Publier") }
            },
            dismissButton = { TextButton(onClick = { showMsgDialog = false }) { Text("Annuler") } }
        )
    }
}
