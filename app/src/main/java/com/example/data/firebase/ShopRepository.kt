package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.local.LocalBridgeCache
import com.example.data.model.AppLanguage
import com.example.data.model.BusinessProfile
import com.example.data.model.GreetingStyle
import com.example.data.model.MessageIntent
import com.example.data.model.MessagingPlatform
import com.example.data.model.Product
import com.example.data.model.ReplyLog
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await

class ShopRepository(
    private val db: FirebaseFirestore,
    private val localCache: LocalBridgeCache,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        localCache = LocalBridgeCache(context.applicationContext)
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeProfile(userId: String): Flow<BusinessProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            BusinessProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .onEach { profile ->
                    if (profile != null) {
                        localCache.saveProfile(profile)
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeProducts(userId: String): Flow<List<Product>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid/products"
        emitAll(
            db.collection("users").document(uid).collection("products")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Product::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .onEach { products ->
                    localCache.saveProducts(products)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeReplyLogs(userId: String): Flow<List<ReplyLog>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid/replyLogs"
        emitAll(
            db.collection("users").document(uid).collection("replyLogs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ReplyLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureInitialProfile(userId: String, ownerName: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val safeOwner = ownerName.trim().take(100)
                val defaultProfile = BusinessProfile(
                    userId = uid,
                    ownerName = safeOwner
                )
                val payload = mapOf(
                    "userId" to uid,
                    "businessName" to defaultProfile.businessName,
                    "ownerName" to defaultProfile.ownerName,
                    "greetingStyle" to defaultProfile.greetingStyle,
                    "supportPolicy" to defaultProfile.supportPolicy,
                    "casualChatEnabled" to defaultProfile.casualChatEnabled,
                    "autoReplyEnabled" to defaultProfile.autoReplyEnabled,
                    "replyToGroups" to defaultProfile.replyToGroups,
                    "enabledApps" to defaultProfile.enabledApps,
                    "languagePreference" to defaultProfile.languagePreference,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                localCache.saveProfile(defaultProfile)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(userId: String, profile: BusinessProfile): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val cleanBusinessName = profile.businessName.trim().ifBlank { "My Online Shop" }.take(100)
            val cleanOwnerName = profile.ownerName.trim().take(100)
            val cleanStyle = GreetingStyle.fromRaw(profile.greetingStyle).raw
            val cleanPolicy = profile.supportPolicy.trim().take(1500)
            val cleanApps = profile.enabledApps
                .map { it.trim().take(120) }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(10)
            val cleanLang = AppLanguage.fromRaw(profile.languagePreference).raw

            val docRef = db.collection("users").document(uid)
            val updates = mapOf(
                "businessName" to cleanBusinessName,
                "ownerName" to cleanOwnerName,
                "greetingStyle" to cleanStyle,
                "supportPolicy" to cleanPolicy,
                "casualChatEnabled" to profile.casualChatEnabled,
                "autoReplyEnabled" to profile.autoReplyEnabled,
                "replyToGroups" to profile.replyToGroups,
                "enabledApps" to cleanApps,
                "languagePreference" to cleanLang,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            localCache.saveProfile(
                profile.copy(
                    userId = uid,
                    businessName = cleanBusinessName,
                    ownerName = cleanOwnerName,
                    greetingStyle = cleanStyle,
                    supportPolicy = cleanPolicy,
                    enabledApps = cleanApps,
                    languagePreference = cleanLang
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addProduct(
        userId: String,
        name: String,
        price: Double,
        currency: String,
        category: String,
        description: String,
        inStock: Boolean
    ): Result<Unit> {
        var path = "users/$userId/products"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val cleanName = name.trim().take(120)
            require(cleanName.isNotEmpty()) { "Product name cannot be empty." }
            val safePrice = price.coerceIn(0.0, 100_000_000.0)
            val cleanCurrency = currency.trim().ifBlank { "৳" }.take(10)
            val cleanCategory = category.trim().take(60)
            val cleanDescription = description.trim().take(1000)

            val docRef = db.collection("users").document(uid).collection("products").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "price" to safePrice,
                "currency" to cleanCurrency,
                "category" to cleanCategory,
                "description" to cleanDescription,
                "inStock" to inStock,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProduct(
        userId: String,
        productId: String,
        name: String,
        price: Double,
        currency: String,
        category: String,
        description: String,
        inStock: Boolean
    ): Result<Unit> {
        var path = "users/$userId/products/$productId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            require(productId.isNotBlank()) { "Product ID must not be blank." }
            val cleanName = name.trim().take(120)
            require(cleanName.isNotEmpty()) { "Product name cannot be empty." }
            val safePrice = price.coerceIn(0.0, 100_000_000.0)
            val cleanCurrency = currency.trim().ifBlank { "৳" }.take(10)
            val cleanCategory = category.trim().take(60)
            val cleanDescription = description.trim().take(1000)

            val docRef = db.collection("users").document(uid).collection("products").document(productId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "name" to cleanName,
                    "price" to safePrice,
                    "currency" to cleanCurrency,
                    "category" to cleanCategory,
                    "description" to cleanDescription,
                    "inStock" to inStock,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteProduct(userId: String, productId: String): Result<Unit> {
        val path = "users/$userId/products/$productId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            require(productId.isNotBlank()) { "Product ID must not be blank." }
            db.collection("users").document(uid).collection("products").document(productId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun addReplyLog(
        userId: String,
        senderName: String,
        platform: MessagingPlatform,
        incomingMessage: String,
        aiReply: String,
        intent: MessageIntent,
        repliedAutomatically: Boolean
    ): Result<Unit> {
        var path = "users/$userId/replyLogs"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val cleanSender = senderName.trim().ifBlank { "Customer" }.take(120)
            val cleanIncoming = incomingMessage.trim().ifBlank { "-" }.take(2000)
            val cleanReply = aiReply.trim().ifBlank { "-" }.take(3000)

            val docRef = db.collection("users").document(uid).collection("replyLogs").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "senderName" to cleanSender,
                "platform" to platform.raw,
                "incomingMessage" to cleanIncoming,
                "aiReply" to cleanReply,
                "intent" to intent.raw,
                "repliedAutomatically" to repliedAutomatically,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteReplyLog(userId: String, logId: String): Result<Unit> {
        val path = "users/$userId/replyLogs/$logId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            require(logId.isNotBlank()) { "Log ID must not be blank." }
            db.collection("users").document(uid).collection("replyLogs").document(logId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
