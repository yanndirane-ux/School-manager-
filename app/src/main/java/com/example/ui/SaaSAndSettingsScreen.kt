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
fun SaaSAndSettingsScreen(
    school: School?,
    allSchools: List<School>,
    plans: List<SaasPlan>,
    role: UserRole,
    isSuperAdmin: Boolean,
    members: List<SchoolMember>,
    invitations: List<Invitation>,
    invoices: List<SubscriptionInvoice>,
    auditLogs: List<AuditLog>,
    students: List<Student>,
    onSelectSchool: (String) -> Unit,
    onCreateNewSchool: () -> Unit,
    onInviteMember: (String, UserRole, List<String>) -> Unit,
    onUpdateMemberRole: (SchoolMember, UserRole, List<String>, Boolean) -> Unit,
    onSyncMemberAccess: () -> Unit,
    onUpdateSchoolSettings: (Map<String, Any>, String) -> Unit,
    onSuperAdminUpdatePlan: (PlanCode, String, Long, Long, Long, Long, List<String>) -> Unit,
    onSuperAdminManageSchool: (School, SubscriptionStatus, PlanCode, Long, Long, Boolean) -> Unit
) {
    var tab by remember { mutableIntStateOf(if (isSuperAdmin) 0 else 1) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<SchoolMember?>(null) }
    var editingPlan by remember { mutableStateOf<SaasPlan?>(null) }
    var managingSchool by remember { mutableStateOf<School?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
            if (isSuperAdmin) Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Super Admin SaaS") })
            if (school != null) {
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Équipe & Rôles") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Intégrations & Campus") })
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Sécurité & Audit") })
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (tab == 0 && isSuperAdmin) {
                item {
                    SectionHeader(
                        title = "Console Super Administrateur",
                        subtitle = "${allSchools.size} établissement(s) actif(s) • Tarifs et fonctionnalités modifiables",
                        actionLabel = "+ École",
                        onActionClick = onCreateNewSchool
                    )
                }
                item { Text("Formules commerciales SaaS", style = MaterialTheme.typography.titleMedium) }
                items(plans, key = { it.code }) { plan ->
                    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("${plan.name} (${Formatters.fcfa(plan.monthlyPriceFcfa)}/mois)", style = MaterialTheme.typography.titleMedium)
                                OutlinedButton(onClick = { editingPlan = plan }) { Text("Modifier tarif") }
                            }
                            Text("Essai : ${plan.trialDays}j • Grâce : ${plan.gracePeriodDays}j • Max ${plan.maxStudents} élèves", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                            plan.features.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                item { Text("Établissements hébergés (Multi-tenant)", style = MaterialTheme.typography.titleMedium) }
                items(allSchools, key = { it.id }) { sch ->
                    val st = SubscriptionStatus.fromRaw(sch.subscriptionStatus)
                    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(sch.name, style = MaterialTheme.typography.titleMedium)
                                    Text("${sch.city}, ${sch.country} • ${PlanCode.fromRaw(sch.planCode).label} (${Formatters.fcfa(sch.monthlyPriceFcfa)})", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                }
                                StatusPillBadge(st.label, IceBlueContainer, MidnightNavy)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onSelectSchool(sch.id) }) { Text("Ouvrir espace") }
                                Button(onClick = { managingSchool = sch }) { Text("Gérer abonnement") }
                            }
                        }
                    }
                }
            } else if (school != null && tab == 1) {
                val canManageTeam = role in listOf(UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR)
                item {
                    SectionHeader(
                        title = "Équipe & Contrôle d'accès (RBAC)",
                        subtitle = "${members.size} membre(s) • ${invitations.count { it.status == "PENDING" }} invitation(s) en attente",
                        actionLabel = if (canManageTeam) "+ Inviter" else null,
                        onActionClick = if (canManageTeam) { { showInviteDialog = true } } else null,
                        actionTestTag = "invite_member_button"
                    )
                }
                if (canManageTeam) {
                    item {
                        OutlinedButton(
                            onClick = onSyncMemberAccess,
                            modifier = Modifier.fillMaxWidth().testTag("sync_member_access_button")
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Synchroniser les droits d'accès sur les dossiers existants")
                        }
                    }
                }
                items(members, key = { it.uid }) { m ->
                    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(m.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(m.email, style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                    if (!m.active) {
                                        Text("Compte suspendu", style = MaterialTheme.typography.labelSmall, color = CoralError)
                                    } else if (m.role == UserRole.PARENT.name || m.role == UserRole.ELEVE.name) {
                                        Text("${m.linkedStudentIds.size} dossier(s) élève rattaché(s)", style = MaterialTheme.typography.labelSmall, color = SkyBluePrimary)
                                    }
                                }
                                StatusPillBadge(UserRole.fromRaw(m.role).label, IceBlueContainer, MidnightNavy)
                            }
                            if (canManageTeam) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { editingMember = m }) {
                                        Text("Gérer rôle & accès")
                                    }
                                }
                            }
                        }
                    }
                }
                if (invitations.isNotEmpty()) {
                    item { Text("Invitations envoyées", style = MaterialTheme.typography.titleMedium) }
                    items(invitations, key = { it.id }) { inv ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${inv.invitedEmail} (${UserRole.fromRaw(inv.role).label})", style = MaterialTheme.typography.bodyMedium)
                                StatusPillBadge(inv.status, IceBlueContainer, MidnightNavy)
                            }
                        }
                    }
                }
                if (invoices.isNotEmpty()) {
                    item { Text("Factures d'abonnement SaaS", style = MaterialTheme.typography.titleMedium) }
                    items(invoices, key = { it.id }) { inv ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("${inv.invoiceNumber} • ${inv.periodLabel}", style = MaterialTheme.typography.titleSmall)
                                    Text("Échéance : ${Formatters.formatDateMillis(inv.dueDateMillis)}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                }
                                StatusPillBadge("${Formatters.fcfa(inv.amountFcfa)} (${inv.status})", EmeraldContainer, EmeraldSuccess)
                            }
                        }
                    }
                }
            } else if (school != null && tab == 2) {
                item {
                    SchoolIntegrationsEditor(
                        school = school,
                        canEdit = role in listOf(UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR),
                        onSave = onUpdateSchoolSettings
                    )
                }
            } else if (school != null && tab == 3) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = IceBlueContainer)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Protection des données des mineurs & Conservation", style = MaterialTheme.typography.titleMedium, color = MidnightNavy)
                            Text(
                                "• Isolation stricte multi-tenant : chaque requête est cloisonnée à l'établissement ${school.name}.\n" +
                                    "• Politique de conservation active : ${school.retentionMonths} mois après départ.\n" +
                                    "• Toutes les opérations sensibles sont journalisées de façon immuable ci-dessous.",
                                style = MaterialTheme.typography.bodySmall,
                                color = CharcoalInk
                            )
                        }
                    }
                }
                item { SectionHeader("Journal d'audit sécurité (${auditLogs.size})") }
                items(auditLogs.take(30), key = { it.id }) { log ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(log.action, style = MaterialTheme.typography.labelLarge, color = SkyBluePrimary)
                                Text(Formatters.formatTimestamp(log.createdAt), style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                            }
                            Text(log.details, style = MaterialTheme.typography.bodySmall)
                            Text("Par ${log.actorName} (${UserRole.fromRaw(log.actorRole).label})", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                        }
                    }
                }
            }
        }
    }

    if (showInviteDialog && school != null) {
        var email by remember { mutableStateOf("") }
        var pickedRole by remember { mutableStateOf(UserRole.ENSEIGNANT) }
        val linkedIds = remember { mutableStateListOf<String>() }
        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            title = { Text("Inviter un utilisateur") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("E-mail Google de l'utilisateur *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Profil / Rôle :", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            UserRole.ADMIN_ETABLISSEMENT,
                            UserRole.DIRECTEUR,
                            UserRole.SECRETAIRE,
                            UserRole.COMPTABLE,
                            UserRole.ENSEIGNANT,
                            UserRole.ELEVE,
                            UserRole.PARENT
                        ).forEach { r ->
                            FilterChip(selected = pickedRole == r, onClick = { pickedRole = r }, label = { Text(r.label) })
                        }
                    }
                    if ((pickedRole == UserRole.PARENT || pickedRole == UserRole.ELEVE) && students.isNotEmpty()) {
                        Text(
                            if (pickedRole == UserRole.ELEVE) "Dossier élève à rattacher :" else "Enfants autorisés à consulter :",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            students.forEach { st ->
                                val sel = st.id in linkedIds
                                FilterChip(selected = sel, onClick = { if (sel) linkedIds.remove(st.id) else linkedIds.add(st.id) }, label = { Text(st.fullName) })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { onInviteMember(email, pickedRole, linkedIds.toList()); showInviteDialog = false }, enabled = email.contains("@")) {
                    Text("Envoyer")
                }
            },
            dismissButton = { TextButton(onClick = { showInviteDialog = false }) { Text("Annuler") } }
        )
    }

    if (editingMember != null && school != null) {
        val m = editingMember!!
        var pickedRole by remember(m.uid) { mutableStateOf(UserRole.fromRaw(m.role)) }
        var isActive by remember(m.uid) { mutableStateOf(m.active) }
        val linkedIds = remember(m.uid) { mutableStateListOf<String>().apply { addAll(m.linkedStudentIds) } }
        AlertDialog(
            onDismissRequest = { editingMember = null },
            title = { Text("Gérer ${m.displayName}") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(m.email, style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                    Text("Rôle dans l'établissement :", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            UserRole.ADMIN_ETABLISSEMENT,
                            UserRole.DIRECTEUR,
                            UserRole.SECRETAIRE,
                            UserRole.COMPTABLE,
                            UserRole.ENSEIGNANT,
                            UserRole.ELEVE,
                            UserRole.PARENT
                        ).forEach { r ->
                            FilterChip(selected = pickedRole == r, onClick = { pickedRole = r }, label = { Text(r.label) })
                        }
                    }
                    if ((pickedRole == UserRole.PARENT || pickedRole == UserRole.ELEVE) && students.isNotEmpty()) {
                        Text(
                            if (pickedRole == UserRole.ELEVE) "Dossier élève rattaché :" else "Enfants rattachés :",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            students.forEach { st ->
                                val sel = st.id in linkedIds
                                FilterChip(selected = sel, onClick = { if (sel) linkedIds.remove(st.id) else linkedIds.add(st.id) }, label = { Text(st.fullName) })
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isActive, onCheckedChange = { isActive = it })
                        Text("Accès actif à l'établissement", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateMemberRole(m, pickedRole, linkedIds.toList(), isActive)
                    editingMember = null
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { editingMember = null }) { Text("Annuler") } }
        )
    }

    if (editingPlan != null) {
        val p = editingPlan!!
        var price by remember { mutableStateOf(p.monthlyPriceFcfa.toString()) }
        var trial by remember { mutableStateOf(p.trialDays.toString()) }
        var grace by remember { mutableStateOf(p.gracePeriodDays.toString()) }
        var maxStu by remember { mutableStateOf(p.maxStudents.toString()) }
        var featuresText by remember { mutableStateOf(p.features.joinToString("\n")) }
        AlertDialog(
            onDismissRequest = { editingPlan = null },
            title = { Text("Éditer formule ${p.name}") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = price, onValueChange = { price = it.filter(Char::isDigit) }, label = { Text("Prix mensuel (FCFA)") }, singleLine = true)
                    OutlinedTextField(value = trial, onValueChange = { trial = it.filter(Char::isDigit) }, label = { Text("Jours d'essai") }, singleLine = true)
                    OutlinedTextField(value = grace, onValueChange = { grace = it.filter(Char::isDigit) }, label = { Text("Période de grâce (jours)") }, singleLine = true)
                    OutlinedTextField(value = maxStu, onValueChange = { maxStu = it.filter(Char::isDigit) }, label = { Text("Plafond étudiants") }, singleLine = true)
                    OutlinedTextField(value = featuresText, onValueChange = { featuresText = it }, label = { Text("Fonctionnalités (1 par ligne)") }, minLines = 4)
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSuperAdminUpdatePlan(
                        PlanCode.fromRaw(p.code),
                        p.name,
                        price.toLongOrNull() ?: p.monthlyPriceFcfa,
                        trial.toLongOrNull() ?: 14L,
                        grace.toLongOrNull() ?: 7L,
                        maxStu.toLongOrNull() ?: 500L,
                        featuresText.lines().map { it.trim() }.filter { it.isNotBlank() }
                    )
                    editingPlan = null
                }) { Text("Mettre à jour") }
            },
            dismissButton = { TextButton(onClick = { editingPlan = null }) { Text("Annuler") } }
        )
    }

    if (managingSchool != null) {
        val sch = managingSchool!!
        var status by remember { mutableStateOf(SubscriptionStatus.fromRaw(sch.subscriptionStatus)) }
        var planCode by remember { mutableStateOf(PlanCode.fromRaw(sch.planCode)) }
        var grace by remember { mutableStateOf(sch.gracePeriodDays.toString()) }
        var genInvoice by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { managingSchool = null },
            title = { Text("Abonnement : ${sch.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Statut :", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SubscriptionStatus.entries.forEach { s -> FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s.label) }) }
                    }
                    Text("Formule :", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PlanCode.entries.forEach { c -> FilterChip(selected = planCode == c, onClick = { planCode = c }, label = { Text(c.label) }) }
                    }
                    OutlinedTextField(value = grace, onValueChange = { grace = it.filter(Char::isDigit) }, label = { Text("Jours de grâce") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = genInvoice, onCheckedChange = { genInvoice = it })
                        Text("Émettre une facture SaaS", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val planPrice = plans.firstOrNull { it.code == planCode.name }?.monthlyPriceFcfa ?: planCode.defaultPriceFcfa.toLong()
                    onSuperAdminManageSchool(sch, status, planCode, planPrice, grace.toLongOrNull() ?: 7L, genInvoice)
                    managingSchool = null
                }) { Text("Appliquer") }
            },
            dismissButton = { TextButton(onClick = { managingSchool = null }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun SchoolIntegrationsEditor(
    school: School,
    canEdit: Boolean,
    onSave: (Map<String, Any>, String) -> Unit
) {
    var category by remember(school.id) { mutableStateOf(SchoolCategory.fromRaw(school.category)) }
    var termMode by remember(school.id) { mutableStateOf(school.termMode.ifBlank { category.defaultTermMode }) }
    var passingAvgText by remember(school.id) { mutableStateOf(school.passingAverage.toString()) }
    var creditThresholdText by remember(school.id) { mutableStateOf(school.creditPassThreshold.toString()) }
    var annualCreditsText by remember(school.id) { mutableStateOf(school.annualCreditsRequired.toString()) }
    var lmdCompensation by remember(school.id) { mutableStateOf(school.lmdCompensation) }
    var facultiesText by remember(school.id) { mutableStateOf(school.faculties.joinToString(", ")) }
    var departmentsText by remember(school.id) { mutableStateOf(school.departments.joinToString(", ")) }
    var programsText by remember(school.id) { mutableStateOf(school.programs.joinToString(", ")) }
    var campusesText by remember(school.id) { mutableStateOf(school.campuses.joinToString(", ")) }
    var momoUrl by remember(school.id) { mutableStateOf(school.momoApiBaseUrl ?: "") }
    var momoMerchant by remember(school.id) { mutableStateOf(school.momoMerchantCode ?: "") }
    var orangeUrl by remember(school.id) { mutableStateOf(school.orangeApiBaseUrl ?: "") }
    var orangeMerchant by remember(school.id) { mutableStateOf(school.orangeMerchantCode ?: "") }
    var whatsappUrl by remember(school.id) { mutableStateOf(school.whatsappApiUrl ?: "") }
    var smsUrl by remember(school.id) { mutableStateOf(school.smsApiUrl ?: "") }
    var smsSender by remember(school.id) { mutableStateOf(school.smsSenderId ?: school.name.take(11)) }

    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Catégorie, Règles de Calcul & Campus", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SchoolCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { if (canEdit) { category = cat; termMode = cat.defaultTermMode } },
                        label = { Text(cat.shortLabel) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = termMode == "TRIMESTRE",
                    onClick = { if (canEdit) termMode = "TRIMESTRE" },
                    label = { Text("Régime Trimestriel") }
                )
                FilterChip(
                    selected = termMode == "SEMESTRE",
                    onClick = { if (canEdit) termMode = "SEMESTRE" },
                    label = { Text("Régime Semestriel") }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = passingAvgText,
                    onValueChange = { passingAvgText = it },
                    label = { Text("Moyenne admission /20") },
                    enabled = canEdit,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = creditThresholdText,
                    onValueChange = { creditThresholdText = it },
                    label = { Text("Seuil UE /20") },
                    enabled = canEdit,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            if (category == SchoolCategory.UNIVERSITE_INSTITUT) {
                OutlinedTextField(
                    value = annualCreditsText,
                    onValueChange = { annualCreditsText = it.filter(Char::isDigit) },
                    label = { Text("Crédits annuels requis (LMD)") },
                    enabled = canEdit,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = lmdCompensation, onCheckedChange = { if (canEdit) lmdCompensation = it })
                    Text("Activer la compensation LMD entre semestres/UE", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(value = facultiesText, onValueChange = { facultiesText = it }, label = { Text("Facultés / Écoles (virgule)") }, enabled = canEdit, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = departmentsText, onValueChange = { departmentsText = it }, label = { Text("Départements (virgule)") }, enabled = canEdit, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = programsText, onValueChange = { programsText = it }, label = { Text("Filières (virgule)") }, enabled = canEdit, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(value = campusesText, onValueChange = { campusesText = it }, label = { Text("Campus (séparés par virgule)") }, enabled = canEdit, modifier = Modifier.fillMaxWidth())
            HorizontalDivider()
            Text("Passerelles API réelles (Mobile Money & Messagerie)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Renseignez les endpoints HTTP de vos prestataires MTN MoMo, Orange Money, WhatsApp Cloud ou SMS pour activer l'envoi réel.",
                style = MaterialTheme.typography.bodySmall,
                color = SlateMuted
            )
            OutlinedTextField(value = momoUrl, onValueChange = { momoUrl = it }, label = { Text("URL API MTN MoMo (Collection)") }, enabled = canEdit, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = momoMerchant, onValueChange = { momoMerchant = it }, label = { Text("Code Marchand MTN") }, enabled = canEdit, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = orangeUrl, onValueChange = { orangeUrl = it }, label = { Text("URL API Orange Money") }, enabled = canEdit, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = orangeMerchant, onValueChange = { orangeMerchant = it }, label = { Text("Code Marchand Orange") }, enabled = canEdit, singleLine = true, modifier = Modifier.fillMaxWidth())
            HorizontalDivider()
            OutlinedTextField(value = whatsappUrl, onValueChange = { whatsappUrl = it }, label = { Text("URL Webhook / API WhatsApp Business") }, enabled = canEdit, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = smsUrl, onValueChange = { smsUrl = it }, label = { Text("URL Passerelle SMS") }, enabled = canEdit, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = smsSender, onValueChange = { smsSender = it }, label = { Text("Sender ID SMS") }, enabled = canEdit, singleLine = true, modifier = Modifier.fillMaxWidth())

            if (canEdit) {
                Button(
                    onClick = {
                        val campuses = campusesText.split(",").map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { listOf("Campus Principal") }
                        val faculties = facultiesText.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(25)
                        val departments = departmentsText.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(40)
                        val programs = programsText.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(50)
                        val map = buildMap<String, Any> {
                            put("category", category.name)
                            put("termMode", termMode)
                            put("passingAverage", passingAvgText.replace(",", ".").toDoubleOrNull()?.coerceIn(0.0, 20.0) ?: 10.0)
                            put("creditPassThreshold", creditThresholdText.replace(",", ".").toDoubleOrNull()?.coerceIn(0.0, 20.0) ?: 10.0)
                            put("annualCreditsRequired", (annualCreditsText.toLongOrNull() ?: 60L).coerceIn(1L, 300L))
                            put("lmdCompensation", lmdCompensation)
                            put("faculties", faculties)
                            put("departments", departments)
                            put("programs", programs)
                            put("campuses", campuses)
                            put("momoConfigured", momoUrl.isNotBlank())
                            if (momoUrl.isNotBlank()) put("momoApiBaseUrl", momoUrl.trim())
                            if (momoMerchant.isNotBlank()) put("momoMerchantCode", momoMerchant.trim())
                            put("orangeConfigured", orangeUrl.isNotBlank())
                            if (orangeUrl.isNotBlank()) put("orangeApiBaseUrl", orangeUrl.trim())
                            if (orangeMerchant.isNotBlank()) put("orangeMerchantCode", orangeMerchant.trim())
                            put("whatsappConfigured", whatsappUrl.isNotBlank())
                            if (whatsappUrl.isNotBlank()) put("whatsappApiUrl", whatsappUrl.trim())
                            put("smsConfigured", smsUrl.isNotBlank())
                            if (smsUrl.isNotBlank()) put("smsApiUrl", smsUrl.trim())
                            if (smsSender.isNotBlank()) put("smsSenderId", smsSender.trim())
                        }
                        onSave(map, "Paramètres académiques, règles et intégrations enregistrés.")
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_integrations_button")
                ) {
                    Text("Enregistrer la configuration")
                }
            }
        }
    }
}

