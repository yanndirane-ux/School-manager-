package com.example.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
fun DashboardScreen(
    school: School,
    role: UserRole,
    stats: DashboardStats,
    allSchools: List<School> = emptyList(),
    members: List<SchoolMember> = emptyList(),
    invitations: List<Invitation> = emptyList(),
    auditLogs: List<AuditLog> = emptyList(),
    students: List<Student> = emptyList(),
    balances: List<StudentBalance> = emptyList(),
    grades: List<Grade> = emptyList(),
    subjects: List<Subject> = emptyList(),
    schedules: List<ScheduleEntry> = emptyList(),
    attendance: List<AttendanceRecord> = emptyList(),
    announcements: List<Announcement> = emptyList(),
    onNavigateModule: (String) -> Unit
) {
    val subStatus = SubscriptionStatus.fromRaw(school.subscriptionStatus)
    val planCode = PlanCode.fromRaw(school.planCode)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Bannière statut abonnement & rôle actif
        item {
            SubscriptionHeaderBanner(school = school, role = role, subStatus = subStatus, planCode = planCode)
        }

        // Contenu adapté strictement au rôle connecté
        when (role) {
            UserRole.SUPER_ADMIN -> {
                val activeCount = allSchools.count { it.subscriptionStatus == SubscriptionStatus.ACTIF.name }
                val trialCount = allSchools.count { it.subscriptionStatus == SubscriptionStatus.ESSAI.name }
                val suspendedCount = allSchools.count { it.subscriptionStatus == SubscriptionStatus.SUSPENDU.name }
                val estimatedMrr = allSchools.filter { it.subscriptionStatus == SubscriptionStatus.ACTIF.name }.sumOf { it.monthlyPriceFcfa }

                item {
                    SectionHeader(
                        title = "Cockpit Super Administrateur SaaS",
                        subtitle = "Pilotage multi-établissements & abonnements FCFA",
                        actionLabel = "Console SaaS",
                        onActionClick = { onNavigateModule("saas_settings") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpiMetricCard(
                            title = "Établissements",
                            value = "${allSchools.size}",
                            subtitle = "$activeCount actif(s) • $trialCount essai",
                            icon = Icons.Default.Apartment,
                            accentColor = SkyBluePrimary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = "Revenu mensuel (MRR)",
                            value = Formatters.fcfa(estimatedMrr),
                            subtitle = "$suspendedCount suspendu(s)",
                            icon = Icons.Default.WorkspacePremium,
                            accentColor = EmeraldSuccess,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    SchoolKpiGrid(school = school, stats = stats)
                }
                item { QuickActionsRow(role = role, onNavigateModule = onNavigateModule) }
                item { InteractiveEnrollmentChart(series = stats.enrollmentSeries) }
                item {
                    InteractiveFinanceBarChart(
                        collectedFcfa = stats.totalCollectedFcfa,
                        remainingFcfa = stats.remainingToCollectFcfa,
                        collectionRatePercent = stats.collectionRatePercent
                    )
                }
            }

            UserRole.ADMIN_ETABLISSEMENT -> {
                val pendingInvites = invitations.count { it.status == "PENDING" }
                val configuredApis = listOf(
                    school.momoConfigured,
                    school.orangeConfigured,
                    school.whatsappConfigured,
                    school.smsConfigured
                ).count { it }

                item {
                    SectionHeader(
                        title = "Gouvernance & Sécurité de l'établissement",
                        subtitle = "Administration RBAC, intégrations API & traçabilité",
                        actionLabel = "Équipe & API",
                        onActionClick = { onNavigateModule("saas_settings") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiMetricCard(
                                title = "Membres actifs",
                                value = "${members.count { it.active }}",
                                subtitle = "$pendingInvites invitation(s) en attente",
                                icon = Icons.Default.AdminPanelSettings,
                                accentColor = SkyBluePrimary,
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricCard(
                                title = "Passerelles API",
                                value = "$configuredApis / 4",
                                subtitle = "MoMo, Orange, WhatsApp, SMS",
                                icon = Icons.Default.Hub,
                                accentColor = if (configuredApis > 0) EmeraldSuccess else AmberWarning,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiMetricCard(
                                title = "Étudiants & Campus",
                                value = "${stats.totalStudents}",
                                subtitle = "${school.campuses.size} campus • ${stats.totalClasses} classes",
                                icon = Icons.Default.School,
                                accentColor = DeepRoyalBlue,
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricCard(
                                title = "Audit sécurité",
                                value = "${auditLogs.size}",
                                subtitle = "Conservation ${school.retentionMonths} mois",
                                icon = Icons.Default.Security,
                                accentColor = EmeraldSuccess,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                item { QuickActionsRow(role = role, onNavigateModule = onNavigateModule) }
                item {
                    SectionHeader("Derniers journaux d'audit", "Traçabilité immuable des actions")
                }
                if (auditLogs.isEmpty()) {
                    item { EmptyStateCard("Aucun événement d'audit", "Les actions administratives apparaîtront ici.") }
                } else {
                    items(auditLogs.take(5), key = { it.id }) { log ->
                        AuditPreviewCard(log)
                    }
                }
            }

            UserRole.DIRECTEUR -> {
                item {
                    SectionHeader(
                        title = "Tableau de bord Direction",
                        subtitle = "Pilotage global • Année ${school.academicYear}"
                    )
                    Spacer(Modifier.height(8.dp))
                    SchoolKpiGrid(school = school, stats = stats)
                }
                item { QuickActionsRow(role = role, onNavigateModule = onNavigateModule) }
                item { InteractiveEnrollmentChart(series = stats.enrollmentSeries) }
                item {
                    InteractiveFinanceBarChart(
                        collectedFcfa = stats.totalCollectedFcfa,
                        remainingFcfa = stats.remainingToCollectFcfa,
                        collectionRatePercent = stats.collectionRatePercent
                    )
                }
                item { AttendanceGaugeCard(attendanceRatePercent = stats.attendanceRatePercent) }
            }

            UserRole.SECRETAIRE -> {
                val missingPhotoCount = students.count { it.photoUri.isNullOrBlank() }
                val todayAbsences = attendance.count {
                    it.dateIso == Formatters.todayIso() && it.status != AttendanceStatus.PRESENT.name
                }
                item {
                    SectionHeader(
                        title = "Accueil Secrétariat & Scolarité",
                        subtitle = "Inscriptions, dossiers élèves & certificats officiels"
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiMetricCard(
                                title = "Élèves inscrits",
                                value = "${stats.totalStudents}",
                                subtitle = "$missingPhotoCount sans photo",
                                icon = Icons.Default.People,
                                accentColor = SkyBluePrimary,
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricCard(
                                title = "Classes ouvertes",
                                value = "${stats.totalClasses}",
                                subtitle = "${subjects.size} matières",
                                icon = Icons.Default.Class,
                                accentColor = DeepRoyalBlue,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiMetricCard(
                                title = "Taux d'assiduité",
                                value = "${stats.attendanceRatePercent.toInt()}%",
                                subtitle = "$todayAbsences incident(s) ce jour",
                                icon = Icons.Default.FactCheck,
                                accentColor = EmeraldSuccess,
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricCard(
                                title = "Corps enseignant",
                                value = "${stats.totalTeachers}",
                                subtitle = "Fiches actives",
                                icon = Icons.Default.Badge,
                                accentColor = SkyBlueBright,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                item { QuickActionsRow(role = role, onNavigateModule = onNavigateModule) }
                item { InteractiveEnrollmentChart(series = stats.enrollmentSeries) }
            }

            UserRole.COMPTABLE -> {
                item {
                    SectionHeader(
                        title = "Cockpit Comptabilité & Scolarité",
                        subtitle = "Suivi des encaissements en FCFA & relances"
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiMetricCard(
                                title = "Frais encaissés",
                                value = Formatters.fcfa(stats.totalCollectedFcfa),
                                subtitle = "Recouvrement ${stats.collectionRatePercent.toInt()}%",
                                icon = Icons.Default.Payments,
                                accentColor = EmeraldSuccess,
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricCard(
                                title = "Reste à recouvrer",
                                value = Formatters.fcfa(stats.remainingToCollectFcfa),
                                subtitle = "${stats.unpaidStudents.size} élève(s) en solde",
                                icon = Icons.Default.AccountBalanceWallet,
                                accentColor = AmberWarning,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val momoTotal = stats.paymentByMethod[PaymentMethod.MTN_MOMO] ?: 0L
                            val omTotal = stats.paymentByMethod[PaymentMethod.ORANGE_MONEY] ?: 0L
                            KpiMetricCard(
                                title = "MTN MoMo",
                                value = Formatters.fcfa(momoTotal),
                                subtitle = if (school.momoConfigured) "API active" else "En attente config API",
                                icon = Icons.Default.PhoneAndroid,
                                accentColor = SkyBluePrimary,
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricCard(
                                title = "Orange Money",
                                value = Formatters.fcfa(omTotal),
                                subtitle = if (school.orangeConfigured) "API active" else "En attente config API",
                                icon = Icons.Default.Smartphone,
                                accentColor = AmberWarning,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                item { QuickActionsRow(role = role, onNavigateModule = onNavigateModule) }
                item {
                    InteractiveFinanceBarChart(
                        collectedFcfa = stats.totalCollectedFcfa,
                        remainingFcfa = stats.remainingToCollectFcfa,
                        collectionRatePercent = stats.collectionRatePercent
                    )
                }
                if (stats.unpaidStudents.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Dossiers impayés prioritaires",
                            subtitle = "Relances de scolarité",
                            actionLabel = "Tout voir",
                            onActionClick = { onNavigateModule("finance") }
                        )
                    }
                    items(stats.unpaidStudents.take(5), key = { it.student.id }) { bal ->
                        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(bal.student.fullName, style = MaterialTheme.typography.titleSmall)
                                    Text("${bal.student.className} • ${bal.student.guardianPhone}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                }
                                Text(Formatters.fcfa(bal.remainingFcfa), color = CoralError, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            UserRole.ENSEIGNANT -> {
                item {
                    SectionHeader(
                        title = "Espace Pédagogique Enseignant",
                        subtitle = "Notes, évaluations, présences & emplois du temps"
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiMetricCard(
                                title = "Classes suivies",
                                value = "${stats.totalClasses}",
                                subtitle = "${stats.totalStudents} élève(s)",
                                icon = Icons.Default.Class,
                                accentColor = SkyBluePrimary,
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricCard(
                                title = "Matières",
                                value = "${subjects.size}",
                                subtitle = "${schedules.size} créneau(x) horaire(s)",
                                icon = Icons.Default.MenuBook,
                                accentColor = DeepRoyalBlue,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KpiMetricCard(
                                title = "Notes saisies",
                                value = "${grades.size}",
                                subtitle = "Calcul moyennes auto",
                                icon = Icons.Default.Grade,
                                accentColor = EmeraldSuccess,
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricCard(
                                title = "Assiduité",
                                value = "${stats.attendanceRatePercent.toInt()}%",
                                subtitle = "Appels enregistrés",
                                icon = Icons.Default.FactCheck,
                                accentColor = SkyBlueBright,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                item { QuickActionsRow(role = role, onNavigateModule = onNavigateModule) }
                if (schedules.isNotEmpty()) {
                    item { SectionHeader("Créneaux d'emploi du temps", "Aperçu hebdomadaire") }
                    items(schedules.take(5), key = { it.id }) { sc ->
                        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${sc.subjectName} (${sc.className})", style = MaterialTheme.typography.titleSmall)
                                    Text("Salle ${sc.roomName}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                }
                                StatusPillBadge("${sc.dayOfWeek} ${sc.startTime}-${sc.endTime}", IceBlueContainer, MidnightNavy)
                            }
                        }
                    }
                }
            }

            UserRole.ELEVE -> {
                val myStudent = students.firstOrNull()
                val myAvg = if (grades.isNotEmpty()) grades.map { (it.score / it.maxScore) * 20.0 }.average() else null
                val mySchedules = if (myStudent == null) emptyList() else schedules.filter { it.classId == myStudent.classId }

                item {
                    SectionHeader(
                        title = "Espace Élève — Suivi Scolaire",
                        subtitle = myStudent?.let { "${it.fullName} • ${it.className} (${it.matricule})" } ?: "Compte élève actif"
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpiMetricCard(
                            title = "Moyenne générale",
                            value = myAvg?.let { String.format(Locale.FRENCH, "%.2f/20", it) } ?: "—",
                            subtitle = "${grades.size} note(s) publiée(s)",
                            icon = Icons.Default.Grade,
                            accentColor = SkyBluePrimary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = "Assiduité",
                            value = "${stats.attendanceRatePercent.toInt()}%",
                            subtitle = "${attendance.count { it.status == AttendanceStatus.ABSENT.name }} absence(s)",
                            icon = Icons.Default.FactCheck,
                            accentColor = EmeraldSuccess,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item { QuickActionsRow(role = role, onNavigateModule = onNavigateModule) }
                if (myStudent == null) {
                    item {
                        EmptyStateCard(
                            title = "Dossier élève non encore rattaché",
                            message = "Demandez à l'Administrateur ou au Directeur de votre établissement de lier votre compte Élève à votre fiche matricule dans Équipe & Rôles."
                        )
                    }
                } else {
                    item {
                        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(myStudent.fullName, style = MaterialTheme.typography.titleMedium, color = MidnightNavy)
                                    StatusPillBadge(myStudent.className, IceBlueContainer, MidnightNavy)
                                }
                                Text("Matricule : ${myStudent.matricule} • Année ${myStudent.academicYear}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                Text("Campus : ${myStudent.campus} • Statut : ${StudentStatus.fromRaw(myStudent.status).label}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                            }
                        }
                    }
                    if (mySchedules.isNotEmpty()) {
                        item { SectionHeader("Mon emploi du temps", "Cours planifiés pour ${myStudent.className}") }
                        items(mySchedules.take(5), key = { it.id }) { sc ->
                            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(sc.subjectName, style = MaterialTheme.typography.titleSmall)
                                        Text("${sc.teacherName} • Salle ${sc.roomName}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                    }
                                    StatusPillBadge("${sc.dayOfWeek} ${sc.startTime}", IceBlueContainer, MidnightNavy)
                                }
                            }
                        }
                    }
                }
                if (announcements.isNotEmpty()) {
                    item { SectionHeader("Annonces de l'établissement", "Informations scolaires") }
                    items(announcements.take(3), key = { it.id }) { ann ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(ann.title, style = MaterialTheme.typography.titleSmall)
                                Text(ann.message, style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                            }
                        }
                    }
                }
            }

            UserRole.PARENT -> {
                val totalPaid = balances.sumOf { it.paidFcfa }
                val totalRemaining = balances.sumOf { it.remainingFcfa }
                item {
                    SectionHeader(
                        title = "Espace Parent & Suivi Élèves",
                        subtitle = "Résultats scolaires, présences et scolarité de vos enfants"
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpiMetricCard(
                            title = "Enfant(s) suivi(s)",
                            value = "${students.size}",
                            subtitle = "Année ${school.academicYear}",
                            icon = Icons.Default.FamilyRestroom,
                            accentColor = SkyBluePrimary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = "Reste scolarité",
                            value = Formatters.fcfa(totalRemaining),
                            subtitle = "Payé : ${Formatters.fcfa(totalPaid)}",
                            icon = Icons.Default.Payments,
                            accentColor = if (totalRemaining == 0L) EmeraldSuccess else AmberWarning,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item { QuickActionsRow(role = role, onNavigateModule = onNavigateModule) }
                if (students.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "Aucun élève rattaché à votre compte",
                            message = "Demandez au Directeur ou à l'Administrateur de votre établissement de lier votre compte Parent au dossier de votre enfant dans Équipe & Rôles."
                        )
                    }
                } else {
                    items(students, key = { it.id }) { child ->
                        val childBal = balances.firstOrNull { it.student.id == child.id }
                        val childGrades = grades.filter { it.studentId == child.id }
                        val avg = if (childGrades.isNotEmpty()) childGrades.map { (it.score / it.maxScore) * 20.0 }.average() else null
                        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(child.fullName, style = MaterialTheme.typography.titleMedium, color = MidnightNavy)
                                    StatusPillBadge(child.className, IceBlueContainer, MidnightNavy)
                                }
                                Text("Matricule : ${child.matricule} • ${StudentStatus.fromRaw(child.status).label}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                Text(
                                    if (avg != null) "Moyenne notes saisies : ${String.format(Locale.FRENCH, "%.2f", avg)} / 20 (${childGrades.size} note(s))"
                                    else "Aucune note publiée pour le moment",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SkyBluePrimary
                                )
                                if (childBal != null) {
                                    Text(
                                        "Scolarité : Payé ${Formatters.fcfa(childBal.paidFcfa)} • Reste ${Formatters.fcfa(childBal.remainingFcfa)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (childBal.isPaidInFull) EmeraldSuccess else AmberWarning
                                    )
                                }
                            }
                        }
                    }
                }
                if (announcements.isNotEmpty()) {
                    item { SectionHeader("Dernières annonces de l'école", "Communication officielle") }
                    items(announcements.take(3), key = { it.id }) { ann ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(ann.title, style = MaterialTheme.typography.titleSmall)
                                Text(ann.message, style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                            }
                        }
                    }
                }
            }
        }

        // Activités récentes communes pour les profils opérationnels
        if (role != UserRole.PARENT && role != UserRole.ELEVE && role != UserRole.ADMIN_ETABLISSEMENT) {
            item {
                SectionHeader(title = "Activités récentes", subtitle = "Journal en direct de l'établissement")
            }
            if (stats.recentActivities.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "Aucune activité récente",
                        message = "Créez vos classes, inscrivez des élèves ou enregistrez un paiement pour alimenter le tableau de bord.",
                        actionLabel = if (role in setOf(UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR, UserRole.SECRETAIRE)) "Créer une classe" else null,
                        onAction = { onNavigateModule("classes") }
                    )
                }
            } else {
                items(stats.recentActivities) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = when (item.category) {
                                "FINANCE" -> Icons.Default.Payments
                                "PRESENCE" -> Icons.Default.EventBusy
                                else -> Icons.Default.School
                            }
                            Icon(icon, contentDescription = null, tint = SkyBluePrimary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                            }
                            Text(item.timeLabel, style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionHeaderBanner(
    school: School,
    role: UserRole,
    subStatus: SubscriptionStatus,
    planCode: PlanCode
) {
    val statusColor = when (subStatus) {
        SubscriptionStatus.ACTIF -> EmeraldContainer to EmeraldSuccess
        SubscriptionStatus.ESSAI -> IceBlueContainer to SkyBluePrimary
        SubscriptionStatus.PERIODE_GRACE -> AmberContainer to AmberWarning
        SubscriptionStatus.SUSPENDU -> CoralContainer to CoralError
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = statusColor.first),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${school.name} • ${SchoolCategory.fromRaw(school.category).shortLabel} • ${planCode.label}", style = MaterialTheme.typography.titleMedium, color = MidnightNavy)
                Text(
                    when (subStatus) {
                        SubscriptionStatus.ESSAI ->
                            "Essai actif jusqu'au ${Formatters.formatDateMillis(school.trialEndsAtMillis)} • Profil : ${role.label}"
                        SubscriptionStatus.ACTIF ->
                            "Abonnement actif (${Formatters.fcfa(school.monthlyPriceFcfa)}/mois) • Profil : ${role.label}"
                        SubscriptionStatus.PERIODE_GRACE ->
                            "Période de grâce (${school.gracePeriodDays}j) — Renouvellement requis"
                        SubscriptionStatus.SUSPENDU ->
                            "Abonnement suspendu pour impayé — Contactez le Super Administrateur"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = CharcoalInk
                )
            }
            StatusPillBadge(subStatus.label, PureWhite, statusColor.second)
        }
    }
}

@Composable
private fun SchoolKpiGrid(school: School, stats: DashboardStats) {
    BoxWithConstraints {
        val twoCols = maxWidth < 700.dp
        if (twoCols) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiMetricCard("Étudiants", "${stats.totalStudents}", "Inscrits actifs", Icons.Default.People, SkyBluePrimary, Modifier.weight(1f))
                    KpiMetricCard("Enseignants", "${stats.totalTeachers}", "Personnel pédagogique", Icons.Default.Badge, DeepRoyalBlue, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiMetricCard("Classes", "${stats.totalClasses}", "${school.campuses.size} campus", Icons.Default.Class, SkyBlueBright, Modifier.weight(1f))
                    KpiMetricCard("Taux présence", "${stats.attendanceRatePercent.toInt()}%", "Appels enregistrés", Icons.Default.FactCheck, EmeraldSuccess, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiMetricCard("Frais encaissés", Formatters.fcfa(stats.totalCollectedFcfa), "Recouvrement ${stats.collectionRatePercent.toInt()}%", Icons.Default.Payments, EmeraldSuccess, Modifier.weight(1f))
                    KpiMetricCard("Reste à recouvrer", Formatters.fcfa(stats.remainingToCollectFcfa), "${stats.unpaidStudents.size} impayé(s)", Icons.Default.AccountBalanceWallet, AmberWarning, Modifier.weight(1f))
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiMetricCard("Étudiants", "${stats.totalStudents}", "Inscrits actifs", Icons.Default.People, SkyBluePrimary, Modifier.weight(1f))
                    KpiMetricCard("Enseignants", "${stats.totalTeachers}", "Personnel", Icons.Default.Badge, DeepRoyalBlue, Modifier.weight(1f))
                    KpiMetricCard("Classes", "${stats.totalClasses}", "${school.campuses.size} campus", Icons.Default.Class, SkyBlueBright, Modifier.weight(1f))
                    KpiMetricCard("Présence", "${stats.attendanceRatePercent.toInt()}%", "Assiduité", Icons.Default.FactCheck, EmeraldSuccess, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiMetricCard("Frais encaissés", Formatters.fcfa(stats.totalCollectedFcfa), "Recouvrement ${stats.collectionRatePercent.toInt()}%", Icons.Default.Payments, EmeraldSuccess, Modifier.weight(1f))
                    KpiMetricCard("Reste à recouvrer", Formatters.fcfa(stats.remainingToCollectFcfa), "${stats.unpaidStudents.size} impayé(s)", Icons.Default.AccountBalanceWallet, AmberWarning, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickActionsRow(role: UserRole, onNavigateModule: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (role in listOf(UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR, UserRole.SECRETAIRE)) {
            FilledTonalButton(
                onClick = { onNavigateModule("students") },
                modifier = Modifier.testTag("quick_students_btn")
            ) {
                Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Élèves", maxLines = 1)
            }
        }
        if (role in listOf(UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR, UserRole.COMPTABLE, UserRole.PARENT)) {
            FilledTonalButton(
                onClick = { onNavigateModule("finance") },
                modifier = Modifier.testTag("quick_finance_btn")
            ) {
                Icon(Icons.Default.ReceiptLong, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (role == UserRole.PARENT) "Scolarité" else "Finances", maxLines = 1)
            }
        }
        if (role in listOf(UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR, UserRole.ENSEIGNANT, UserRole.ELEVE, UserRole.PARENT)) {
            FilledTonalButton(
                onClick = { onNavigateModule("grades") },
                modifier = Modifier.testTag("quick_grades_btn")
            ) {
                Icon(Icons.Default.Grade, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (role == UserRole.PARENT || role == UserRole.ELEVE) "Bulletins" else "Notes", maxLines = 1)
            }
        }
        if (role in listOf(UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR, UserRole.SECRETAIRE, UserRole.ENSEIGNANT)) {
            FilledTonalButton(
                onClick = { onNavigateModule("results_admin") },
                modifier = Modifier.testTag("quick_results_admin_btn")
            ) {
                Icon(Icons.Default.Assessment, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Résultats", maxLines = 1)
            }
        }
        FilledTonalButton(
            onClick = { onNavigateModule("results_portal") },
            modifier = Modifier.testTag("quick_results_portal_btn")
        ) {
            Icon(Icons.Default.Verified, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Consulter mes résultats", maxLines = 1)
        }
        if (role == UserRole.ELEVE) {
            FilledTonalButton(
                onClick = { onNavigateModule("classes") },
                modifier = Modifier.testTag("quick_classes_btn")
            ) {
                Icon(Icons.Default.Class, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cours", maxLines = 1)
            }
        }
        if (role in listOf(UserRole.SECRETAIRE, UserRole.ENSEIGNANT, UserRole.ELEVE)) {
            FilledTonalButton(
                onClick = { onNavigateModule("attendance") },
                modifier = Modifier.testTag("quick_attendance_btn")
            ) {
                Icon(Icons.Default.FactCheck, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (role == UserRole.ELEVE) "Présences" else "Appel", maxLines = 1)
            }
        }
        if (role == UserRole.ADMIN_ETABLISSEMENT) {
            FilledTonalButton(
                onClick = { onNavigateModule("saas_settings") },
                modifier = Modifier.testTag("quick_settings_btn")
            ) {
                Icon(Icons.Default.Settings, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("RBAC", maxLines = 1)
            }
        }
    }
}

@Composable
private fun AuditPreviewCard(log: AuditLog) {
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
