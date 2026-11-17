package com.example.data

import android.content.Context
import com.example.R
import com.example.model.*
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ExternalDispatchResult(
    val status: String,
    val reference: String,
    val message: String
)

class FinanceAndCommsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Utilisateur non authentifié.")

    private fun isSuperAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        return user.isEmailVerified && user.email.equals(SchoolRepository.SUPER_ADMIN_EMAIL, ignoreCase = true)
    }

    private fun <T> observeTenantCollection(
        schoolId: String,
        collectionName: String,
        currentUserId: String,
        clazz: Class<T>
    ): Flow<List<T>> {
        val base = db.collection("schools").document(schoolId).collection(collectionName)
        val query = if (isSuperAdmin()) base else base.whereArrayContains("readerUids", currentUserId)
        return query.snapshots().map { snap ->
            snap.toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
    }

    fun observePayments(schoolId: String, uid: String): Flow<List<Payment>> =
        observeTenantCollection(schoolId, "payments", uid, Payment::class.java)

    fun observeAnnouncements(schoolId: String, uid: String): Flow<List<Announcement>> =
        observeTenantCollection(schoolId, "announcements", uid, Announcement::class.java)

    fun observeDocuments(schoolId: String, uid: String): Flow<List<SchoolDocument>> =
        observeTenantCollection(schoolId, "documents", uid, SchoolDocument::class.java)

    private fun readerList(school: School): List<String> {
        val uid = requireUserId()
        return (school.memberUids + uid).distinct().take(60)
    }

    /**
     * Effectue un véritable appel HTTP vers l'API Mobile Money configurée (MTN MoMo ou Orange Money).
     * Ne simule jamais un paiement : si le prestataire n'est pas configuré, retourne EN_ATTENTE
     * avec un statut explicite indiquant que la passerelle attend sa configuration API.
     */
    suspend fun initiateMobileMoneyRequest(
        school: School,
        method: PaymentMethod,
        payerPhone: String,
        amountFcfa: Long,
        externalRef: String
    ): ExternalDispatchResult = withContext(Dispatchers.IO) {
        val isMtn = method == PaymentMethod.MTN_MOMO
        val configured = if (isMtn) school.momoConfigured else school.orangeConfigured
        val endpoint = if (isMtn) school.momoApiBaseUrl else school.orangeApiBaseUrl
        val merchantCode = if (isMtn) school.momoMerchantCode else school.orangeMerchantCode

        if (!configured || endpoint.isNullOrBlank()) {
            return@withContext ExternalDispatchResult(
                status = ProviderStatus.EN_ATTENTE.name,
                reference = "ATTENTE-CONFIG-$externalRef",
                message = "Passerelle ${method.label} en attente de configuration API dans les paramètres de l'établissement."
            )
        }

        try {
            val bodyJson = JSONObject().apply {
                put("amount", amountFcfa.toString())
                put("currency", "XAF")
                put("externalId", externalRef)
                put("payerPhone", payerPhone.trim())
                put("merchantCode", merchantCode ?: "")
                put("payerMessage", "Scolarité ${school.name}")
            }.toString()

            val request = Request.Builder()
                .url(endpoint.trim())
                .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    ExternalDispatchResult(
                        status = ProviderStatus.CONFIRME.name,
                        reference = externalRef,
                        message = "Requête ${method.label} transmise avec succès (HTTP ${response.code})."
                    )
                } else {
                    ExternalDispatchResult(
                        status = ProviderStatus.ECHOUE.name,
                        reference = externalRef,
                        message = "Erreur prestataire ${method.label} (HTTP ${response.code})."
                    )
                }
            }
        } catch (e: Exception) {
            ExternalDispatchResult(
                status = ProviderStatus.ECHOUE.name,
                reference = externalRef,
                message = "Impossible de joindre l'API ${method.label} : ${e.localizedMessage ?: "Erreur réseau"}"
            )
        }
    }

    suspend fun recordPayment(school: School, payment: Payment): Result<String> = try {
        val uid = requireUserId()
        val id = payment.id.ifBlank { "pay_" + UUID.randomUUID().toString().replace("-", "").take(10) }
        val payload = mapOf(
            "schoolId" to school.id,
            "receiptNumber" to payment.receiptNumber.trim().ifBlank { "REC-001" }.take(50),
            "studentId" to payment.studentId,
            "studentName" to payment.studentName.trim().ifBlank { "Élève" }.take(160),
            "studentMatricule" to payment.studentMatricule.trim().ifBlank { "SM-00" }.take(40),
            "classId" to payment.classId.take(128),
            "className" to payment.className.trim().ifBlank { "Classe" }.take(60),
            "tranche" to payment.tranche,
            "amountFcfa" to payment.amountFcfa.coerceIn(1L, 25_000_000L),
            "method" to payment.method,
            "providerStatus" to payment.providerStatus,
            "transactionRef" to payment.transactionRef.trim().ifBlank { payment.receiptNumber.ifBlank { "REF" } }.take(100),
            "recordedByUid" to uid,
            "recordedByName" to payment.recordedByName.trim().ifBlank { "Comptable" }.take(100),
            "notes" to payment.notes?.trim()?.ifBlank { null }?.take(300),
            "readerUids" to readerList(school),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
        db.collection("schools").document(school.id).collection("payments").document(id).set(payload).await()
        Result.success(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "schools/${school.id}/payments")
        Result.failure(e)
    }

    suspend fun sendExternalMessageIfConfigured(
        school: School,
        channel: ChannelType,
        title: String,
        message: String
    ): String = withContext(Dispatchers.IO) {
        if (channel == ChannelType.INTERNE) return@withContext "PUBLIE"
        val configured = if (channel == ChannelType.WHATSAPP) school.whatsappConfigured else school.smsConfigured
        val endpoint = if (channel == ChannelType.WHATSAPP) school.whatsappApiUrl else school.smsApiUrl
        if (!configured || endpoint.isNullOrBlank()) {
            return@withContext "EN_ATTENTE_CONFIG_API"
        }
        try {
            val body = JSONObject().apply {
                put("schoolId", school.id)
                put("schoolName", school.name)
                put("senderId", school.smsSenderId ?: school.name.take(11))
                put("title", title)
                put("message", message)
            }.toString()
            val req = Request.Builder()
                .url(endpoint.trim())
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) "ENVOYE_API" else "ECHEC_API"
            }
        } catch (_: Exception) {
            "ECHEC_API"
        }
    }

    suspend fun publishAnnouncement(
        school: School,
        title: String,
        message: String,
        category: String,
        channel: ChannelType,
        targetAudience: String,
        authorName: String
    ): Result<String> = try {
        val uid = requireUserId()
        val deliveryStatus = sendExternalMessageIfConfigured(school, channel, title, message)
        val id = "ann_" + UUID.randomUUID().toString().replace("-", "").take(10)
        val cleanAudience = when (targetAudience.trim().uppercase()) {
            "PARENTS" -> "PARENTS"
            "ENSEIGNANTS" -> "ENSEIGNANTS"
            else -> "TOUS"
        }
        val payload = mapOf(
            "schoolId" to school.id,
            "title" to title.trim().ifBlank { "Annonce" }.let { if (it.length < 2) "$it." else it }.take(120),
            "message" to message.trim().ifBlank { "Information" }.let { if (it.length < 2) "$it." else it }.take(2000),
            "category" to category,
            "channel" to channel.name,
            "deliveryStatus" to deliveryStatus,
            "targetAudience" to cleanAudience,
            "authorUid" to uid,
            "authorName" to authorName.trim().ifBlank { "Direction" }.take(100),
            "readerUids" to readerList(school),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("schools").document(school.id).collection("announcements").document(id).set(payload).await()
        Result.success(deliveryStatus)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "schools/${school.id}/announcements")
        Result.failure(e)
    }

    suspend fun archiveDocument(school: School, document: SchoolDocument): Result<String> = try {
        requireUserId()
        val id = document.id.ifBlank { "doc_" + UUID.randomUUID().toString().replace("-", "").take(10) }
        val payload = mapOf(
            "schoolId" to school.id,
            "docType" to document.docType,
            "referenceNumber" to document.referenceNumber.trim().ifBlank { "DOC-001" }.take(60),
            "title" to document.title.trim().ifBlank { "Document officiel" }.let { if (it.length < 2) "$it." else it }.take(160),
            "studentId" to document.studentId?.ifBlank { null }?.take(128),
            "studentName" to document.studentName?.trim()?.ifBlank { null }?.take(160),
            "generatedByName" to document.generatedByName.trim().ifBlank { "Direction" }.take(100),
            "summaryText" to document.summaryText.trim().ifBlank { "Document officiel" }.let { if (it.length < 2) "$it." else it }.take(1000),
            "readerUids" to readerList(school),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
        db.collection("schools").document(school.id).collection("documents").document(id).set(payload).await()
        Result.success(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "schools/${school.id}/documents")
        Result.failure(e)
    }
}
