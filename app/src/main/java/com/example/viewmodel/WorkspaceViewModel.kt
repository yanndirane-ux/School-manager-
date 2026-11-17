package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SchoolRepository
import com.example.data.extractFirestoreErrorMessage
import com.example.model.*
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceViewModel(
    private val schoolRepo: SchoolRepository,
    private val currentUserId: String,
    private val userEmail: String,
    private val userDisplayName: String
) : ViewModel() {

    val isSuperAdmin: Boolean = schoolRepo.isSuperAdminUser()

    private val _selectedSchoolId = MutableStateFlow<String?>(null)
    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    val userProfile: StateFlow<UserProfile?> = schoolRepo.observeUserProfile(currentUserId)
        .catch { err -> handleStreamError(err) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val saasPlans: StateFlow<List<SaasPlan>> = schoolRepo.observeSaasPlans()
        .map { it.ifEmpty { defaultDisplayPlans() } }
        .catch { err -> handleStreamError(err) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), defaultDisplayPlans())

    val schoolsState: StateFlow<UiState<List<School>>> = schoolRepo.observeMySchools(currentUserId)
        .map<List<School>, UiState<List<School>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(UiState.Error(extractFirestoreErrorMessage(error, "Impossible de charger les établissements.")))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _isEmailVerified = MutableStateFlow(schoolRepo.isCurrentUserEmailVerified())

    val pendingInvitations: StateFlow<List<Invitation>> = _isEmailVerified.flatMapLatest { verified ->
        if (!verified || userEmail.isBlank()) {
            flowOf(emptyList())
        } else {
            schoolRepo.observePendingInvitationsForUser(userEmail)
                .catch { emit(emptyList()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeSchool: StateFlow<School?> = combine(
        schoolsState,
        _selectedSchoolId,
        userProfile
    ) { state, selectedId, profile ->
        val list = (state as? UiState.Success)?.data ?: emptyList()
        val targetId = selectedId ?: profile?.activeSchoolId
        list.firstOrNull { it.id == targetId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val activeMember: StateFlow<SchoolMember?> = activeSchool.flatMapLatest { school ->
        if (school == null) flowOf(null)
        else schoolRepo.observeSchoolMember(school.id, currentUserId).catch { emit(null) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val effectiveRole: StateFlow<UserRole> = combine(activeMember, activeSchool) { member, school ->
        when {
            isSuperAdmin -> UserRole.SUPER_ADMIN
            member != null -> UserRole.fromRaw(member.role)
            school?.ownerUid == currentUserId -> UserRole.DIRECTEUR
            else -> UserRole.PARENT
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), if (isSuperAdmin) UserRole.SUPER_ADMIN else UserRole.DIRECTEUR)

    val schoolMembers: StateFlow<List<SchoolMember>> = activeSchool.flatMapLatest { school ->
        if (school == null) flowOf(emptyList())
        else schoolRepo.observeSchoolMembers(school.id, currentUserId).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val schoolInvitations: StateFlow<List<Invitation>> = activeSchool.flatMapLatest { school ->
        if (school == null) flowOf(emptyList())
        else schoolRepo.observeSchoolInvitations(school.id, currentUserId).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val subscriptionInvoices: StateFlow<List<SubscriptionInvoice>> = activeSchool.flatMapLatest { school ->
        if (school == null) flowOf(emptyList())
        else schoolRepo.observeSubscriptionInvoices(school.id, currentUserId).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val auditLogs: StateFlow<List<AuditLog>> = activeSchool.flatMapLatest { school ->
        if (school == null) flowOf(emptyList())
        else schoolRepo.observeAuditLogs(school.id, currentUserId).catch { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        viewModelScope.launch {
            schoolRepo.upsertUserProfile(currentUserId, userEmail, userDisplayName)
            if (isSuperAdmin) {
                schoolRepo.seedDefaultPlansIfEmpty()
            }
        }
    }

    fun refreshEmailVerificationStatus(verified: Boolean) {
        _isEmailVerified.value = verified
    }

    private fun handleStreamError(error: Throwable) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        _feedbackMessage.value = extractFirestoreErrorMessage(error, "Erreur de synchronisation.")
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun selectSchool(schoolId: String) {
        _selectedSchoolId.value = schoolId
        viewModelScope.launch {
            schoolRepo.setActiveSchoolId(currentUserId, schoolId)
        }
    }

    fun createSchool(
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
        creatorRole: UserRole = UserRole.DIRECTEUR,
        faculties: List<String> = emptyList(),
        departments: List<String> = emptyList(),
        programs: List<String> = emptyList(),
        passingAverage: Double = 10.0,
        termMode: String = category.defaultTermMode,
        onSuccess: (String) -> Unit = {}
    ) {
        if (name.trim().length < 2) {
            _feedbackMessage.value = "Veuillez saisir le nom de l'établissement (au moins 2 caractères)."
            return
        }
        if (city.trim().length < 2 || country.trim().length < 2) {
            _feedbackMessage.value = "Veuillez renseigner la ville et le pays de l'établissement."
            return
        }
        if (academicYear.trim().length < 4) {
            _feedbackMessage.value = "Veuillez saisir une année académique valide (ex: 2026-2027)."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val plan = saasPlans.value.firstOrNull { it.code == planCode.name }
            val result = schoolRepo.createSchool(
                name = name,
                slogan = slogan,
                category = category,
                schoolType = schoolType,
                city = city,
                country = country,
                academicYear = academicYear,
                phone = phone,
                email = email,
                plan = plan,
                planCode = planCode,
                creatorRole = creatorRole,
                faculties = faculties,
                departments = departments,
                programs = programs,
                passingAverage = passingAverage,
                termMode = termMode
            )
            _isBusy.value = false
            result.onSuccess { id ->
                _selectedSchoolId.value = id
                _feedbackMessage.value = "Établissement « $name » créé avec essai gratuit de 14 jours."
                onSuccess(id)
            }.onFailure { err ->
                _feedbackMessage.value = extractFirestoreErrorMessage(err, "Échec de création de l'établissement.")
            }
        }
    }

    fun acceptInvitation(invitation: Invitation, onSuccess: (String) -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = schoolRepo.acceptInvitation(invitation)
            _isBusy.value = false
            res.onSuccess { schoolId ->
                _selectedSchoolId.value = schoolId
                _feedbackMessage.value = "Vous avez rejoint ${invitation.schoolName} (${UserRole.fromRaw(invitation.role).label})."
                onSuccess(schoolId)
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Impossible d'accepter l'invitation."
            }
        }
    }

    fun inviteMember(email: String, role: UserRole, linkedStudentIds: List<String> = emptyList()) {
        val school = activeSchool.value ?: return
        if (!email.contains("@")) {
            _feedbackMessage.value = "Adresse e-mail invalide."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val res = schoolRepo.inviteMember(school, email, role, linkedStudentIds)
            _isBusy.value = false
            res.onSuccess {
                schoolRepo.recordAuditLog(
                    school, userDisplayName, effectiveRole.value,
                    "INVITATION_MEMBRE", "Invitation", email,
                    "Invitation envoyée à $email pour le rôle ${role.label}"
                )
                _feedbackMessage.value = "Invitation envoyée à $email (${role.label})."
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Erreur lors de l'envoi."
            }
        }
    }

    fun updateMemberRole(member: SchoolMember, newRole: UserRole, linkedIds: List<String>, active: Boolean) {
        val school = activeSchool.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = schoolRepo.updateMemberRoleAndLinks(school.id, member, newRole, linkedIds, active)
            if (res.isSuccess) {
                val updatedMembers = schoolMembers.value.map {
                    if (it.uid == member.uid) it.copy(role = newRole.name, linkedStudentIds = linkedIds, active = active) else it
                }
                schoolRepo.syncMemberReaderAccess(school, updatedMembers)
                schoolRepo.recordAuditLog(
                    school, userDisplayName, effectiveRole.value,
                    "MODIFICATION_ROLE", "SchoolMember", member.uid,
                    "Rôle de ${member.displayName} mis à jour : ${newRole.label} (actif=$active)"
                )
                _feedbackMessage.value = "Profil de ${member.displayName} mis à jour et droits synchronisés."
            } else {
                _feedbackMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Mise à jour refusée."
            }
            _isBusy.value = false
        }
    }

    fun syncMemberReaderAccess() {
        val school = activeSchool.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = schoolRepo.syncMemberReaderAccess(school, schoolMembers.value)
            _isBusy.value = false
            res.onSuccess { count ->
                _feedbackMessage.value = "$count document(s) synchronisé(s) avec les droits actuels de l'équipe."
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Erreur lors de la synchronisation des droits."
            }
        }
    }

    fun updateSchoolProfileAndIntegrations(updates: Map<String, Any>, successMsg: String) {
        val school = activeSchool.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = schoolRepo.updateSchoolIntegrationsAndProfile(school.id, updates)
            _isBusy.value = false
            res.onSuccess {
                schoolRepo.recordAuditLog(
                    school, userDisplayName, effectiveRole.value,
                    "MISE_A_JOUR_PARAMETRES", "School", school.id,
                    successMsg
                )
                _feedbackMessage.value = successMsg
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Erreur de mise à jour."
            }
        }
    }

    fun superAdminUpdatePlan(
        planCode: PlanCode,
        name: String,
        priceFcfa: Long,
        trialDays: Long,
        graceDays: Long,
        maxStudents: Long,
        features: List<String>
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = schoolRepo.superAdminUpdatePlan(planCode, name, priceFcfa, trialDays, graceDays, maxStudents, features)
            _isBusy.value = false
            res.onSuccess {
                _feedbackMessage.value = "Formule ${planCode.label} mise à jour (${priceFcfa} FCFA/mois)."
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Action réservée au Super Administrateur."
            }
        }
    }

    fun superAdminManageSubscription(
        school: School,
        newStatus: SubscriptionStatus,
        newPlanCode: PlanCode,
        priceFcfa: Long,
        graceDays: Long,
        generateInvoice: Boolean
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = schoolRepo.superAdminManageSchoolSubscription(
                school, newStatus, newPlanCode, priceFcfa, graceDays, generateInvoice
            )
            _isBusy.value = false
            res.onSuccess {
                _feedbackMessage.value = "Abonnement de ${school.name} : ${newStatus.label}."
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Erreur d'abonnement."
            }
        }
    }

    private fun defaultDisplayPlans(): List<SaasPlan> = listOf(
        SaasPlan(
            id = "STARTER",
            code = "STARTER",
            name = "Starter",
            monthlyPriceFcfa = 10_000L,
            trialDays = 14L,
            gracePeriodDays = 7L,
            maxStudents = 300L,
            features = listOf("Gestion des étudiants", "Gestion des classes", "Gestion des paiements", "Tableau de bord")
        ),
        SaasPlan(
            id = "PROFESSIONAL",
            code = "PROFESSIONAL",
            name = "Professional",
            monthlyPriceFcfa = 20_000L,
            trialDays = 14L,
            gracePeriodDays = 7L,
            maxStudents = 1500L,
            features = listOf("Toutes les fonctionnalités Starter", "Notes & Bulletins PDF", "Enseignants", "Présences & Absences", "Rapports avancés")
        ),
        SaasPlan(
            id = "ENTERPRISE",
            code = "ENTERPRISE",
            name = "Enterprise",
            monthlyPriceFcfa = 40_000L,
            trialDays = 14L,
            gracePeriodDays = 10L,
            maxStudents = 10_000L,
            features = listOf("Toutes les fonctionnalités Professional", "Gestion documentaire avancée", "Notifications WhatsApp/SMS", "Multi-campus", "Assistance prioritaire")
        )
    )
}
