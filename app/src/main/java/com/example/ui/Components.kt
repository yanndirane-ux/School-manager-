package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.MonthlyMetric
import com.example.model.PlanCode
import com.example.ui.theme.*
import com.example.util.Formatters

@Composable
fun KpiMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color = SkyBluePrimary,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StatusPillBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    actionTestTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (actionLabel != null && onActionClick != null) {
            FilledTonalButton(
                onClick = onActionClick,
                modifier = if (actionTestTag != null) Modifier.testTag(actionTestTag) else Modifier
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_state),
                contentDescription = title,
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun PlanGateBanner(
    requiredPlan: PlanCode,
    currentPlan: PlanCode,
    featureName: String,
    onUpgradeClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = IceBlueContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, SkyBlueBright.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MidnightNavy),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, contentDescription = "Formule requise", tint = PureWhite)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "$featureName — Formule ${requiredPlan.label} requise",
                    style = MaterialTheme.typography.titleSmall,
                    color = MidnightNavy
                )
                Text(
                    "Votre formule actuelle est ${currentPlan.label}. Passez à ${requiredPlan.label} (${Formatters.fcfa(requiredPlan.defaultPriceFcfa.toLong())}/mois) pour débloquer ce module.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepRoyalBlue
                )
            }
            OutlinedButton(onClick = onUpgradeClick) {
                Text("Voir offres")
            }
        }
    }
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirmer",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = if (isDestructive) CoralError else AmberWarning
            )
        },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (isDestructive) {
                    ButtonDefaults.buttonColors(containerColor = CoralError, contentColor = PureWhite)
                } else ButtonDefaults.buttonColors()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
fun GlobalSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Rechercher un élève, matricule, classe...",
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .testTag("global_search_input"),
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Rechercher") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun InteractiveEnrollmentChart(
    series: List<MonthlyMetric>,
    modifier: Modifier = Modifier
) {
    var selectedIdx by remember(series) { mutableIntStateOf((series.size - 1).coerceAtLeast(0)) }
    val selected = series.getOrNull(selectedIdx)

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Évolution des inscriptions", style = MaterialTheme.typography.titleMedium)
                    Text(
                        selected?.let { "${it.label} : ${it.value.toInt()} élève(s)" } ?: "Aucune donnée",
                        style = MaterialTheme.typography.bodySmall,
                        color = SkyBluePrimary
                    )
                }
                StatusPillBadge("Dynamique", IceBlueContainer, MidnightNavy)
            }

            if (series.isEmpty()) {
                Text("Inscrivez des élèves pour afficher la courbe.", style = MaterialTheme.typography.bodyMedium)
            } else {
                val maxVal = (series.maxOfOrNull { it.value } ?: 1f).coerceAtLeast(1f)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    val count = series.size
                    val padX = 24f
                    val padY = 20f
                    val usableW = (size.width - padX * 2).coerceAtLeast(1f)
                    val usableH = (size.height - padY * 2).coerceAtLeast(1f)
                    val stepX = if (count <= 1) 0f else usableW / (count - 1)

                    val pts = series.mapIndexed { i, item ->
                        val x = if (count == 1) size.width / 2f else padX + i * stepX
                        val y = padY + usableH * (1f - (item.value / maxVal))
                        Offset(x, y)
                    }

                    // Grille horizontale
                    for (g in 0..2) {
                        val gy = padY + (usableH / 2f) * g
                        drawLine(
                            color = SlateBorder,
                            start = Offset(padX, gy),
                            end = Offset(size.width - padX, gy),
                            strokeWidth = 1.5f
                        )
                    }

                    if (pts.size > 1) {
                        val areaPath = Path().apply {
                            moveTo(pts.first().x, size.height - padY)
                            pts.forEach { lineTo(it.x, it.y) }
                            lineTo(pts.last().x, size.height - padY)
                            close()
                        }
                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(SkyBlueBright.copy(alpha = 0.28f), Color.Transparent)
                            )
                        )
                        val linePath = Path().apply {
                            moveTo(pts.first().x, pts.first().y)
                            pts.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path = linePath,
                            color = SkyBluePrimary,
                            style = Stroke(width = 5f, cap = StrokeCap.Round)
                        )
                    }

                    pts.forEachIndexed { idx, pt ->
                        val active = idx == selectedIdx
                        drawCircle(
                            color = if (active) MidnightNavy else PureWhite,
                            radius = if (active) 11f else 8f,
                            center = pt
                        )
                        drawCircle(
                            color = SkyBluePrimary,
                            radius = if (active) 11f else 8f,
                            center = pt,
                            style = Stroke(width = 3.5f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    series.forEachIndexed { idx, item ->
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (idx == selectedIdx) FontWeight.Bold else FontWeight.Normal,
                            color = if (idx == selectedIdx) SkyBluePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedIdx = idx }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveFinanceBarChart(
    collectedFcfa: Long,
    remainingFcfa: Long,
    collectionRatePercent: Float,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Situation financière", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Taux de recouvrement : ${collectionRatePercent.toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldSuccess
                    )
                }
                StatusPillBadge("${collectionRatePercent.toInt()}%", EmeraldContainer, EmeraldSuccess)
            }

            val maxVal = maxOf(collectedFcfa, remainingFcfa, 1L).toFloat()
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                val barH = 26f
                val maxBarW = (size.width - 20f).coerceAtLeast(1f)
                val collectedW = (collectedFcfa.toFloat() / maxVal) * maxBarW
                val remainingW = (remainingFcfa.toFloat() / maxVal) * maxBarW

                // Fond barre 1
                drawRoundRect(
                    color = SlateBorder,
                    topLeft = Offset(0f, 16f),
                    size = Size(maxBarW, barH),
                    cornerRadius = CornerRadius(12f, 12f)
                )
                drawRoundRect(
                    color = EmeraldSuccess,
                    topLeft = Offset(0f, 16f),
                    size = Size(collectedW.coerceAtLeast(8f), barH),
                    cornerRadius = CornerRadius(12f, 12f)
                )

                // Fond barre 2
                drawRoundRect(
                    color = SlateBorder,
                    topLeft = Offset(0f, 64f),
                    size = Size(maxBarW, barH),
                    cornerRadius = CornerRadius(12f, 12f)
                )
                drawRoundRect(
                    color = AmberWarning,
                    topLeft = Offset(0f, 64f),
                    size = Size(remainingW.coerceAtLeast(8f), barH),
                    cornerRadius = CornerRadius(12f, 12f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(EmeraldSuccess))
                    Text("Encaissé : ${Formatters.fcfa(collectedFcfa)}", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(AmberWarning))
                    Text("À recouvrer : ${Formatters.fcfa(remainingFcfa)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun AttendanceGaugeCard(
    attendanceRatePercent: Float,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 14f
                    drawArc(
                        color = SlateBorder,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = SkyBluePrimary,
                        startAngle = -90f,
                        sweepAngle = (attendanceRatePercent / 100f) * 360f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Text(
                    "${attendanceRatePercent.toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Assiduité & Présences", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Calculé dynamiquement à partir des appels enregistrés par les enseignants et le secrétariat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
