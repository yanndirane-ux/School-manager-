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
fun FinanceScreen(
    school: School,
    role: UserRole,
    students: List<Student>,
    payments: List<Payment>,
    balances: List<StudentBalance>,
    stats: DashboardStats,
    isProcessing: Boolean,
    onRecordPayment: (Student, PaymentTranche, Long, PaymentMethod, String, String, String) -> Unit,
    onGenerateReceiptPdf: (Payment, StudentBalance?) -> Unit,
    onOpenGatewaySettings: () -> Unit
) {
    val canCollect = role in listOf(UserRole.SUPER_ADMIN, UserRole.ADMIN_ETABLISSEMENT, UserRole.DIRECTEUR, UserRole.COMPTABLE)
    var tab by remember { mutableIntStateOf(0) }
    var showPayDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Transactions") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Impayés") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Rapports") })
            Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Mobile Money") })
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
                            title = "Paiements & Scolarité",
                            subtitle = "Encaissé : ${Formatters.fcfa(stats.totalCollectedFcfa)}",
                            actionLabel = if (canCollect) "+ Encaisser" else null,
                            onActionClick = if (canCollect) { { showPayDialog = true } } else null,
                            actionTestTag = "add_payment_button"
                        )
                    }
                    if (payments.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "Aucun paiement enregistré",
                                message = "Enregistrez un paiement complet ou partiel en espèces, virement ou Mobile Money (si configuré).",
                                actionLabel = if (canCollect && students.isNotEmpty()) "Enregistrer un paiement" else null,
                                onAction = if (canCollect && students.isNotEmpty()) { { showPayDialog = true } } else null
                            )
                        }
                    } else {
                        items(payments.sortedByDescending { it.createdAt?.seconds ?: 0L }, key = { it.id }) { pay ->
                            val method = PaymentMethod.fromRaw(pay.method)
                            val provStatus = ProviderStatus.fromRaw(pay.providerStatus)
                            val bal = balances.firstOrNull { it.student.id == pay.studentId }
                            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(pay.studentName, style = MaterialTheme.typography.titleMedium)
                                            Text("${pay.receiptNumber} • ${PaymentTranche.fromRaw(pay.tranche).label}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                        }
                                        Text(
                                            Formatters.fcfa(pay.amountFcfa),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldSuccess
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${method.label} • Réf : ${pay.transactionRef}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                        val badge = when (provStatus) {
                                            ProviderStatus.CONFIRME -> EmeraldContainer to EmeraldSuccess
                                            ProviderStatus.EN_ATTENTE -> AmberContainer to AmberWarning
                                            ProviderStatus.ECHOUE -> CoralContainer to CoralError
                                        }
                                        StatusPillBadge(provStatus.label, badge.first, badge.second)
                                    }
                                    if (!pay.notes.isNullOrBlank()) {
                                        Text(pay.notes, style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                    }
                                    OutlinedButton(
                                        onClick = { onGenerateReceiptPdf(pay, bal) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Télécharger Reçu PDF")
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    item {
                        SectionHeader(
                            title = "Étudiants avec impayés",
                            subtitle = "${stats.unpaidStudents.size} dossier(s) • Total dû : ${Formatters.fcfa(stats.remainingToCollectFcfa)}"
                        )
                    }
                    if (stats.unpaidStudents.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "Aucun impayé en cours",
                                message = "Tous les élèves inscrits sont à jour de leurs frais de scolarité."
                            )
                        }
                    } else {
                        items(stats.unpaidStudents, key = { it.student.id }) { bal ->
                            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(bal.student.fullName, style = MaterialTheme.typography.titleMedium)
                                        Text("${bal.student.className} • Tuteur : ${bal.student.guardianPhone}", style = MaterialTheme.typography.bodySmall, color = SlateMuted)
                                        Text("Payé : ${Formatters.fcfa(bal.paidFcfa)} / ${Formatters.fcfa(bal.expectedFcfa)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    StatusPillBadge("Dû : ${Formatters.fcfa(bal.remainingFcfa)}", CoralContainer, CoralError)
                                }
                            }
                        }
                    }
                }
                2 -> {
                    item {
                        InteractiveFinanceBarChart(
                            collectedFcfa = stats.totalCollectedFcfa,
                            remainingFcfa = stats.remainingToCollectFcfa,
                            collectionRatePercent = stats.collectionRatePercent
                        )
                    }
                    item {
                        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Ventilation par mode de paiement", style = MaterialTheme.typography.titleMedium)
                                PaymentMethod.entries.forEach { m ->
                                    val amt = stats.paymentByMethod[m] ?: 0L
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(m.label, style = MaterialTheme.typography.bodyMedium)
                                        Text(Formatters.fcfa(amt), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    item {
                        SectionHeader(
                            title = "Passerelles Mobile Money",
                            subtitle = "Intégration API directe (aucune simulation)"
                        )
                    }
                    item {
                        GatewayStatusCard(
                            title = "MTN Mobile Money (MTN MoMo)",
                            configured = school.momoConfigured && !school.momoApiBaseUrl.isNullOrBlank(),
                            endpoint = school.momoApiBaseUrl,
                            merchant = school.momoMerchantCode,
                            canConfigure = role in listOf(UserRole.SUPER_ADMIN, UserRole.DIRECTEUR),
                            onConfigure = onOpenGatewaySettings
                        )
                    }
                    item {
                        GatewayStatusCard(
                            title = "Orange Money Cameroun / Zone CFA",
                            configured = school.orangeConfigured && !school.orangeApiBaseUrl.isNullOrBlank(),
                            endpoint = school.orangeApiBaseUrl,
                            merchant = school.orangeMerchantCode,
                            canConfigure = role in listOf(UserRole.SUPER_ADMIN, UserRole.DIRECTEUR),
                            onConfigure = onOpenGatewaySettings
                        )
                    }
                }
            }
        }
    }

    if (showPayDialog) {
        var pickedStudent by remember { mutableStateOf(students.firstOrNull()) }
        var tranche by remember { mutableStateOf(PaymentTranche.TRANCHE_1) }
        var method by remember { mutableStateOf(PaymentMethod.ESPECES) }
        var amountText by remember { mutableStateOf("50000") }
        var payerPhone by remember { mutableStateOf(pickedStudent?.guardianPhone ?: "+237 ") }
        var manualRef by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }

        val momoReady = school.momoConfigured && !school.momoApiBaseUrl.isNullOrBlank()
        val orangeReady = school.orangeConfigured && !school.orangeApiBaseUrl.isNullOrBlank()

        AlertDialog(
            onDismissRequest = { showPayDialog = false },
            title = { Text("Encaisser un paiement") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (students.isNotEmpty()) {
                        Text("Étudiant :", style = MaterialTheme.typography.labelMedium)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            students.forEach { st ->
                                FilterChip(
                                    selected = pickedStudent?.id == st.id,
                                    onClick = {
                                        pickedStudent = st
                                        payerPhone = st.guardianPhone
                                    },
                                    label = { Text(st.fullName) }
                                )
                            }
                        }
                    }
                    Text("Tranche :", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PaymentTranche.entries.forEach { tr ->
                            FilterChip(selected = tranche == tr, onClick = { tranche = tr }, label = { Text(tr.label) })
                        }
                    }
                    Text("Mode de paiement :", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PaymentMethod.entries.forEach { m ->
                            FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m.label) })
                        }
                    }
                    if ((method == PaymentMethod.MTN_MOMO && !momoReady) || (method == PaymentMethod.ORANGE_MONEY && !orangeReady)) {
                        Surface(color = AmberContainer, shape = MaterialTheme.shapes.small) {
                            Text(
                                "Attention : API ${method.label} en attente de configuration. Le paiement sera enregistré au statut « En attente » jusqu'à configuration de l'API dans Paramètres.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AmberWarning,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter(Char::isDigit) },
                        label = { Text("Montant encaissé (FCFA) *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("payment_amount_input")
                    )
                    if (method == PaymentMethod.MTN_MOMO || method == PaymentMethod.ORANGE_MONEY) {
                        OutlinedTextField(value = payerPhone, onValueChange = { payerPhone = it }, label = { Text("Numéro Mobile Money") }, singleLine = true)
                    } else {
                        OutlinedTextField(value = manualRef, onValueChange = { manualRef = it }, label = { Text("Référence bordereau / reçu") }, singleLine = true)
                    }
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Observation") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val st = pickedStudent ?: return@Button
                        val amt = amountText.toLongOrNull() ?: 0L
                        onRecordPayment(st, tranche, amt, method, payerPhone, manualRef, notes)
                        showPayDialog = false
                    },
                    enabled = !isProcessing && pickedStudent != null && (amountText.toLongOrNull() ?: 0L) > 0L,
                    modifier = Modifier.testTag("payment_submit_button")
                ) { Text("Valider") }
            },
            dismissButton = { TextButton(onClick = { showPayDialog = false }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun GatewayStatusCard(
    title: String,
    configured: Boolean,
    endpoint: String?,
    merchant: String?,
    canConfigure: Boolean,
    onConfigure: () -> Unit
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusPillBadge(
                    text = if (configured) "API Configurée" else "En attente de configuration",
                    containerColor = if (configured) EmeraldContainer else AmberContainer,
                    contentColor = if (configured) EmeraldSuccess else AmberWarning
                )
            }
            Text(
                if (configured) "Endpoint : $endpoint • Marchand : ${merchant ?: "—"}"
                else "Aucune clé ou URL d'API renseignée. L'application n'exécute pas de paiement simulé.",
                style = MaterialTheme.typography.bodySmall,
                color = SlateMuted
            )
            if (canConfigure) {
                OutlinedButton(onClick = onConfigure) {
                    Text(if (configured) "Modifier la configuration" else "Configurer l'API")
                }
            }
        }
    }
}
