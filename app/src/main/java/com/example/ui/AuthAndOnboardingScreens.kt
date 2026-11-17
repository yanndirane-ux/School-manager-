package com.example.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.AuthManager
import com.example.model.*
import com.example.ui.theme.*
import com.example.util.Formatters

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showHelp by remember { mutableStateOf(false) }

    AuthScreenContent(
        isLoading = isLoading,
        errorMessage = errorMessage,
        showHelp = showHelp,
        onToggleHelp = { showHelp = !showHelp },
        onAddGoogleAccountClick = {
            val addAccountIntent = Intent(Settings.ACTION_ADD_ACCOUNT).apply {
                putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
            }
            runCatching { context.startActivity(addAccountIntent) }
                .onFailure {
                    runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                }
        },
        onSignInClick = {
            errorMessage = null
            isLoading = true
            AuthManager.onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    isLoading = false
                    onAuthSuccess()
                },
                onAuthError = { msg ->
                    isLoading = false
                    errorMessage = msg
                    showHelp = true
                },
                scope = scope,
                onAuthCancelled = {
                    isLoading = false
                    errorMessage = "Connexion annulée. Vérifiez qu'un compte Google est bien ajouté sur l'appareil/émulateur."
                    showHelp = true
                }
            )
        }
    )
}

@Composable
fun AuthScreenContent(
    isLoading: Boolean,
    errorMessage: String?,
    showHelp: Boolean = false,
    onToggleHelp: () -> Unit = {},
    onAddGoogleAccountClick: () -> Unit = {},
    onSignInClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(MidnightNavy, DeepRoyalBlue, SoftSlateBackground)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier.widthIn(max = 520.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_school_hero),
                            contentDescription = "Campus SchoolManager",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MidnightNavy.copy(alpha = 0.25f),
                                            MidnightNavy.copy(alpha = 0.85f)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            StatusPillBadge("SaaS Éducation • Afrique Francophone", SkyBluePrimary, PureWhite)
                            Text(
                                "SchoolManager",
                                style = MaterialTheme.typography.displaySmall,
                                color = PureWhite
                            )
                            Text(
                                "« Votre établissement, mieux organisé. »",
                                style = MaterialTheme.typography.titleSmall,
                                color = IceBlueContainer
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            "Connexion & Inscription sécurisées",
                            style = MaterialTheme.typography.titleLarge,
                            color = MidnightNavy
                        )
                        Text(
                            "Accédez à votre espace Administrateur, Directeur, Enseignant, Élève ou Parent avec vérification d'adresse e-mail et redirection automatique vers votre tableau de bord.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateMuted
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FeatureBullet("Authentification Firebase & vérification e-mail automatique via Google")
                            FeatureBullet("Gestion persistante de session, pages privées protégées & déconnexion sécurisée")
                            FeatureBullet("Contrôle d'accès par rôle (RBAC) & isolation multi-établissements")
                        }

                        if (!errorMessage.isNullOrBlank()) {
                            Surface(
                                color = CoralContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = errorMessage,
                                    color = CoralError,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Button(
                            onClick = onSignInClick,
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("google_sign_in_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MidnightNavy,
                                contentColor = PureWhite
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = PureWhite,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null)
                                Spacer(Modifier.width(10.dp))
                                Text("Continuer avec Google", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        TextButton(
                            onClick = onToggleHelp,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .testTag("auth_help_toggle_button")
                        ) {
                            Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (showHelp) "Masquer l'aide à la connexion" else "Problème de connexion Google ?")
                        }

                        if (showHelp) {
                            Surface(
                                color = IceBlueContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "Dépannage connexion Google",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MidnightNavy
                                    )
                                    Text(
                                        "1. Sur l'émulateur ou un appareil neuf, vérifiez qu'un compte Google (ex. compte créateur) est connecté dans les paramètres Android.\n" +
                                            "2. Vérifiez que Google Play Services et la connexion Internet sont actifs.\n" +
                                            "3. Si la fenêtre se ferme sans choix, appuyez sur le bouton ci-dessous pour ajouter votre compte Google sur l'appareil.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CharcoalInk
                                    )
                                    OutlinedButton(
                                        onClick = onAddGoogleAccountClick,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("add_google_account_settings_button")
                                    ) {
                                        Icon(Icons.Default.ManageAccounts, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Ajouter un compte Google sur l'appareil")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthScreenPreview() {
    SchoolManagerTheme {
        AuthScreenContent(isLoading = false, errorMessage = null, onSignInClick = {})
    }
}

@Composable
private fun FeatureBullet(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = SkyBluePrimary,
            modifier = Modifier.size(18.dp)
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = CharcoalInk)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    userDisplayName: String,
    userEmail: String,
    isEmailVerified: Boolean,
    isSuperAdmin: Boolean,
    plans: List<SaasPlan>,
    invitations: List<Invitation>,
    isBusy: Boolean,
    onCreateSchool: (
        name: String,
        slogan: String,
        category: SchoolCategory,
        schoolType: String,
        city: String,
        country: String,
        academicYear: String,
        phone: String,
        email: String,
        planCode: PlanCode,
        creatorRole: UserRole,
        faculties: List<String>,
        departments: List<String>,
        programs: List<String>,
        passingAverage: Double,
        termMode: String
    ) -> Unit,
    onAcceptInvitation: (Invitation) -> Unit,
    onRefreshVerification: () -> Unit,
    onSendVerificationEmail: () -> Unit,
    onOpenSuperAdmin: () -> Unit,
    onSignOut: () -> Unit
) {
    var category by remember { mutableStateOf(SchoolCategory.COLLEGE_LYCEE) }
    var name by remember { mutableStateOf("") }
    var slogan by remember { mutableStateOf("Votre établissement, mieux organisé.") }
    var schoolType by remember { mutableStateOf("Lycée / Collège") }
    var typeEditedManually by remember { mutableStateOf(false) }
    var city by remember { mutableStateOf("Yaoundé") }
    var country by remember { mutableStateOf("Cameroun") }
    var academicYear by remember { mutableStateOf("2026-2027") }
    var phone by remember { mutableStateOf("+237 ") }
    var email by remember { mutableStateOf(userEmail) }
    var facultiesText by remember { mutableStateOf("Sciences et Technologies, Sciences Économiques") }
    var departmentsText by remember { mutableStateOf("Informatique, Gestion") }
    var programsText by remember { mutableStateOf("Génie Logiciel, Finance & Comptabilité") }
    var passingAvgText by remember { mutableStateOf("10.0") }
    var termMode by remember { mutableStateOf(SchoolCategory.COLLEGE_LYCEE.defaultTermMode) }
    var selectedPlan by remember { mutableStateOf(PlanCode.PROFESSIONAL) }
    var creatorRole by remember { mutableStateOf(UserRole.DIRECTEUR) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bienvenue, $userDisplayName") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MidnightNavy,
                    titleContentColor = PureWhite,
                    actionIconContentColor = PureWhite
                ),
                actions = {
                    if (isSuperAdmin) {
                        TextButton(onClick = onOpenSuperAdmin) {
                            Text("Console Super Admin", color = SkyBlueLight)
                        }
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.Default.Logout, contentDescription = "Déconnexion")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isEmailVerified) EmeraldContainer else AmberContainer
                    )
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        val compact = maxWidth < 460.dp
                        if (compact) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    if (isEmailVerified) "Adresse e-mail vérifiée" else "Vérification de l'e-mail requise",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isEmailVerified) EmeraldSuccess else AmberWarning
                                )
                                Text(userEmail, style = MaterialTheme.typography.bodySmall, color = CharcoalInk)
                                if (isEmailVerified) {
                                    StatusPillBadge("Vérifié", PureWhite, EmeraldSuccess)
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = onSendVerificationEmail, modifier = Modifier.weight(1f)) {
                                            Text("Renvoyer")
                                        }
                                        Button(onClick = onRefreshVerification, modifier = Modifier.weight(1f)) {
                                            Text("Actualiser")
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (isEmailVerified) "Adresse e-mail vérifiée" else "Vérification de l'e-mail requise",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isEmailVerified) EmeraldSuccess else AmberWarning
                                    )
                                    Text(userEmail, style = MaterialTheme.typography.bodySmall, color = CharcoalInk)
                                }
                                if (isEmailVerified) {
                                    StatusPillBadge("Vérifié", PureWhite, EmeraldSuccess)
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(onClick = onSendVerificationEmail) { Text("Renvoyer") }
                                        Button(onClick = onRefreshVerification) { Text("Actualiser") }
                                    }
                                }
                            }
                        }
                    }
                }

                if (invitations.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = IceBlueContainer),
                        border = BorderStroke(1.dp, SkyBluePrimary)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Invitations reçues (${invitations.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MidnightNavy
                            )
                            invitations.forEach { inv ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(inv.schoolName, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Rôle proposé : ${UserRole.fromRaw(inv.role).label}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Button(
                                        onClick = { onAcceptInvitation(inv) },
                                        enabled = !isBusy && isEmailVerified
                                    ) {
                                        Text("Rejoindre")
                                    }
                                }
                            }
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        val stackFields = maxWidth < 500.dp
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "Créer votre espace établissement",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MidnightNavy
                            )
                            Text(
                                "Choisissez la catégorie d'établissement pour adapter les classes, UEs LMD, matricules et règles de délibération. Essai gratuit 14 jours inclus.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SlateMuted
                            )

                            Text("Catégorie d'établissement *", style = MaterialTheme.typography.titleSmall, color = MidnightNavy)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SchoolCategory.entries.forEach { cat ->
                                    val selected = category == cat
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                category = cat
                                                termMode = cat.defaultTermMode
                                                if (!typeEditedManually) {
                                                    schoolType = cat.shortLabel
                                                }
                                            }
                                            .testTag("category_chip_${cat.name}"),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) IceBlueContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(
                                            if (selected) 2.dp else 1.dp,
                                            if (selected) SkyBluePrimary else SlateBorder
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(cat.label, style = MaterialTheme.typography.titleSmall, color = MidnightNavy)
                                                Text(
                                                    when (cat) {
                                                        SchoolCategory.PRIMAIRE_MATERNELLE -> "Notes, moyennes trimestrielles, appréciations & passage"
                                                        SchoolCategory.COLLEGE_LYCEE -> "Matières à coefficients, moyennes pondérées & classement"
                                                        SchoolCategory.UNIVERSITE_INSTITUT -> "Système LMD : Facultés, filières, UE, crédits & semestres"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = SlateMuted
                                                )
                                            }
                                            if (selected) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SkyBluePrimary)
                                            }
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it; validationError = null },
                                label = { Text("Nom de l'établissement *") },
                                placeholder = {
                                    Text(
                                        when (category) {
                                            SchoolCategory.PRIMAIRE_MATERNELLE -> "Ex: Groupe Scolaire Les Hirondelles"
                                            SchoolCategory.COLLEGE_LYCEE -> "Ex: Collège Bilingue de Douala"
                                            SchoolCategory.UNIVERSITE_INSTITUT -> "Ex: Institut Supérieur Polytechnique"
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("create_school_name_input"),
                                singleLine = true
                            )

                            if (stackFields) {
                                OutlinedTextField(
                                    value = schoolType,
                                    onValueChange = { schoolType = it; typeEditedManually = true },
                                    label = { Text("Sous-type (Lycée, IUT, École...)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = academicYear,
                                    onValueChange = { academicYear = it; validationError = null },
                                    label = { Text("Année académique *") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = city,
                                    onValueChange = { city = it; validationError = null },
                                    label = { Text("Ville *") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = country,
                                    onValueChange = { country = it; validationError = null },
                                    label = { Text("Pays *") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = phone,
                                    onValueChange = { phone = it },
                                    label = { Text("Téléphone") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text("E-mail établissement") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = schoolType,
                                        onValueChange = { schoolType = it; typeEditedManually = true },
                                        label = { Text("Sous-type (Lycée, IUT...)") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = academicYear,
                                        onValueChange = { academicYear = it; validationError = null },
                                        label = { Text("Année académique *") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = city,
                                        onValueChange = { city = it; validationError = null },
                                        label = { Text("Ville *") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = country,
                                        onValueChange = { country = it; validationError = null },
                                        label = { Text("Pays *") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = phone,
                                        onValueChange = { phone = it },
                                        label = { Text("Téléphone") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = email,
                                        onValueChange = { email = it },
                                        label = { Text("E-mail établissement") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = slogan,
                                onValueChange = { slogan = it },
                                label = { Text("Devise / Slogan") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            if (category == SchoolCategory.UNIVERSITE_INSTITUT) {
                                Surface(
                                    color = IceBlueContainer.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Structure Universitaire (LMD)", style = MaterialTheme.typography.titleSmall, color = MidnightNavy)
                                        OutlinedTextField(
                                            value = facultiesText,
                                            onValueChange = { facultiesText = it },
                                            label = { Text("Facultés / Écoles (séparées par virgule)") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = departmentsText,
                                            onValueChange = { departmentsText = it },
                                            label = { Text("Départements") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = programsText,
                                            onValueChange = { programsText = it },
                                            label = { Text("Filières / Spécialités") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = passingAvgText,
                                    onValueChange = { passingAvgText = it },
                                    label = { Text("Seuil admission / 20") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                FilterChip(
                                    selected = termMode == "TRIMESTRE",
                                    onClick = { termMode = "TRIMESTRE" },
                                    label = { Text("Trimestres") }
                                )
                                FilterChip(
                                    selected = termMode == "SEMESTRE",
                                    onClick = { termMode = "SEMESTRE" },
                                    label = { Text("Semestres") }
                                )
                            }

                            Text("Choisissez votre formule (14 jours d'essai gratuit) :", style = MaterialTheme.typography.titleSmall)
                            plans.forEach { plan ->
                                val code = PlanCode.fromRaw(plan.code)
                                val isSelected = selectedPlan == code
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { selectedPlan = code },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) IceBlueContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) SkyBluePrimary else SlateBorder
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(plan.name, style = MaterialTheme.typography.titleMedium, color = MidnightNavy)
                                            StatusPillBadge(
                                                "${Formatters.fcfa(plan.monthlyPriceFcfa)}/mois",
                                                if (isSelected) MidnightNavy else IceBlueContainer,
                                                if (isSelected) PureWhite else MidnightNavy
                                            )
                                        }
                                        plan.features.forEach { f ->
                                            Text("• $f", style = MaterialTheme.typography.bodySmall, color = CharcoalInk)
                                        }
                                    }
                                }
                            }

                            Text("Votre fonction dans l'établissement :", style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(UserRole.DIRECTEUR, UserRole.ADMIN_ETABLISSEMENT).forEach { r ->
                                    FilterChip(
                                        selected = creatorRole == r,
                                        onClick = { creatorRole = r },
                                        label = { Text(r.label) }
                                    )
                                }
                            }

                            if (!validationError.isNullOrBlank()) {
                                Surface(color = CoralContainer, shape = RoundedCornerShape(10.dp)) {
                                    Text(
                                        validationError!!,
                                        color = CoralError,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.fillMaxWidth().padding(10.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    when {
                                        name.trim().length < 2 ->
                                            validationError = "Le nom de l'établissement doit comporter au moins 2 caractères."
                                        city.trim().length < 2 || country.trim().length < 2 ->
                                            validationError = "Veuillez indiquer la ville et le pays."
                                        academicYear.trim().length < 4 ->
                                            validationError = "Veuillez saisir une année académique valide (ex: 2026-2027)."
                                        else -> {
                                            validationError = null
                                            val facs = if (category == SchoolCategory.UNIVERSITE_INSTITUT) {
                                                facultiesText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                            } else emptyList()
                                            val depts = if (category == SchoolCategory.UNIVERSITE_INSTITUT) {
                                                departmentsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                            } else emptyList()
                                            val progs = if (category == SchoolCategory.UNIVERSITE_INSTITUT) {
                                                programsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                            } else emptyList()
                                            val avg = passingAvgText.replace(",", ".").toDoubleOrNull()?.coerceIn(5.0, 18.0) ?: 10.0
                                            onCreateSchool(
                                                name, slogan, category, schoolType, city, country, academicYear,
                                                phone, email, selectedPlan, creatorRole, facs, depts, progs, avg, termMode
                                            )
                                        }
                                    }
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("create_school_submit_button")
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(Modifier.size(20.dp), color = PureWhite, strokeWidth = 2.dp)
                                } else {
                                    Text("Lancer l'établissement (Essai 14 jours)")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

