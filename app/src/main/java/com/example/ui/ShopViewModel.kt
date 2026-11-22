package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiReplyEngine
import com.example.ai.AiReplyResult
import com.example.data.firebase.ShopRepository
import com.example.data.local.LocalBridgeCache
import com.example.data.model.AppLanguage
import com.example.data.model.BusinessProfile
import com.example.data.model.MessageIntent
import com.example.data.model.MessagingPlatform
import com.example.data.model.Product
import com.example.data.model.ReplyLog
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface SandboxUiState {
    data object Idle : SandboxUiState
    data object Loading : SandboxUiState
    data class Success(val result: AiReplyResult, val savedToLogs: Boolean) : SandboxUiState
    data class Error(val message: String) : SandboxUiState
}

class ShopViewModel(
    private val repository: ShopRepository,
    private val currentUserId: String,
    private val localCache: LocalBridgeCache,
    private val aiEngine: AiReplyEngine = AiReplyEngine()
) : ViewModel() {

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _sandboxState = MutableStateFlow<SandboxUiState>(SandboxUiState.Idle)
    val sandboxState: StateFlow<SandboxUiState> = _sandboxState.asStateFlow()

    val profileState: StateFlow<UiState<BusinessProfile>> = repository.observeProfile(currentUserId)
        .map<BusinessProfile?, UiState<BusinessProfile>> { profile ->
            UiState.Success(profile ?: BusinessProfile(userId = currentUserId))
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing profile", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load business profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val productsState: StateFlow<UiState<List<Product>>> = repository.observeProducts(currentUserId)
        .map<List<Product>, UiState<List<Product>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing products", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load products"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val replyLogsState: StateFlow<UiState<List<ReplyLog>>> = repository.observeReplyLogs(currentUserId)
        .map<List<ReplyLog>, UiState<List<ReplyLog>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing reply logs", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load reply logs"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    init {
        // Keep the background notification listener in sync with Firestore-backed UI state.
        // Without this bridge, the service sees only default profile settings and an empty catalog.
        viewModelScope.launch {
            profileState.collect { state ->
                if (state is UiState.Success) localCache.saveProfile(state.data)
            }
        }
        viewModelScope.launch {
            productsState.collect { state ->
                if (state is UiState.Success) localCache.saveProducts(state.data)
            }
        }
        viewModelScope.launch {
            val ownerName = Firebase.auth.currentUser?.displayName.orEmpty()
            repository.ensureInitialProfile(currentUserId, ownerName)
        }
    }

    fun toggleAutoReply(enabled: Boolean) {
        val current = (profileState.value as? UiState.Success)?.data ?: return
        updateProfile(current.copy(autoReplyEnabled = enabled))
    }

    fun toggleLanguage(language: AppLanguage) {
        val current = (profileState.value as? UiState.Success)?.data ?: return
        updateProfile(current.copy(languagePreference = language.raw))
    }

    fun updateProfile(updated: BusinessProfile) {
        viewModelScope.launch {
            val result = repository.updateProfile(currentUserId, updated)
            result.onFailure { e ->
                _events.tryEmit(e.localizedMessage ?: "Failed to update settings")
            }
        }
    }

    fun saveProduct(
        editingId: String?,
        name: String,
        price: Double,
        currency: String,
        category: String,
        description: String,
        inStock: Boolean,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val result = if (editingId.isNullOrBlank()) {
                repository.addProduct(currentUserId, name, price, currency, category, description, inStock)
            } else {
                repository.updateProduct(currentUserId, editingId, name, price, currency, category, description, inStock)
            }
            result.onSuccess {
                onComplete()
            }.onFailure { e ->
                _events.tryEmit(e.localizedMessage ?: "Failed to save product")
            }
        }
    }

    fun toggleProductStock(product: Product) {
        viewModelScope.launch {
            val result = repository.updateProduct(
                userId = currentUserId,
                productId = product.id,
                name = product.name,
                price = product.price,
                currency = product.currency,
                category = product.category,
                description = product.description,
                inStock = !product.inStock
            )
            result.onFailure { e ->
                _events.tryEmit(e.localizedMessage ?: "Failed to update stock status")
            }
        }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            val result = repository.deleteProduct(currentUserId, productId)
            result.onFailure { e ->
                _events.tryEmit(e.localizedMessage ?: "Failed to delete product")
            }
        }
    }

    fun addSampleProducts() {
        viewModelScope.launch {
            val samples = listOf(
                Triple("Smart Fitness Watch X8", 1450.0, "Electronics") to "AMOLED display, Bluetooth calling, 7 days battery backup, 6 months warranty.",
                Triple("Premium Cotton Panjabi", 1290.0, "Fashion") to "100% pure Egyptian cotton, Sizes: M, L, XL, XXL. Available in Navy, Olive & White.",
                Triple("Wireless Earbuds Pro", 980.0, "Accessories") to "Active noise cancellation, 24h charging case, waterproof IPX5."
            )
            for ((meta, desc) in samples) {
                repository.addProduct(
                    userId = currentUserId,
                    name = meta.first,
                    price = meta.second,
                    currency = "৳",
                    category = meta.third,
                    description = desc,
                    inStock = true
                )
            }
        }
    }

    fun deleteReplyLog(logId: String) {
        viewModelScope.launch {
            val result = repository.deleteReplyLog(currentUserId, logId)
            result.onFailure { e ->
                _events.tryEmit(e.localizedMessage ?: "Failed to delete log")
            }
        }
    }

    fun runSandboxSimulation(
        senderName: String,
        message: String,
        platform: MessagingPlatform
    ) {
        val cleanMsg = message.trim()
        if (cleanMsg.isEmpty()) return
        val cleanSender = senderName.trim().ifBlank { "Test Client" }
        val profile = (profileState.value as? UiState.Success)?.data ?: BusinessProfile(userId = currentUserId)
        val products = (productsState.value as? UiState.Success)?.data ?: emptyList()

        viewModelScope.launch {
            _sandboxState.value = SandboxUiState.Loading
            try {
                val replyResult = aiEngine.generateReply(
                    senderName = cleanSender,
                    incomingMessage = cleanMsg,
                    profile = profile,
                    products = products
                )
                val logResult = repository.addReplyLog(
                    userId = currentUserId,
                    senderName = cleanSender,
                    platform = platform,
                    incomingMessage = cleanMsg,
                    aiReply = replyResult.reply,
                    intent = replyResult.intent,
                    repliedAutomatically = false
                )
                _sandboxState.value = SandboxUiState.Success(
                    result = replyResult,
                    savedToLogs = logResult.isSuccess
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Sandbox AI timed out", e)
                _sandboxState.value = SandboxUiState.Error("AI reply timed out. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Sandbox AI failed", e)
                _sandboxState.value = SandboxUiState.Error(e.localizedMessage ?: "AI generation failed.")
            }
        }
    }

    companion object {
        private const val TAG = "ShopViewModel"
        private const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
