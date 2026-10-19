package de.bedrockchat.ui.chat

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.bedrockchat.data.BedrockRepository
import de.bedrockchat.data.UnauthorizedException
import de.bedrockchat.data.local.ChatDatabase
import de.bedrockchat.data.local.ChatLocalRepository
import de.bedrockchat.data.model.BedrockConfig
import de.bedrockchat.data.model.Message
import de.bedrockchat.data.model.MessageRole
import de.bedrockchat.security.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Primärer Produktivkonstruktor – wird von [by viewModels()] via Android-Reflection aufgerufen.
 *
 * [tokenStore], [repository] und [localRepository] sind `by lazy` initialisiert,
 * damit der interne Test-Konstruktor Overrides setzen kann, bevor diese initialisiert werden.
 */
class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val tokenStore: TokenStore by lazy { TokenStore(app) }
    private val repository: BedrockRepository by lazy { BedrockRepository(tokenStore) }
    private val localRepository: ChatLocalRepository by lazy {
        ChatLocalRepository(ChatDatabase.getInstance(app))
    }

    private var tokenStoreOverride: TokenStore? = null
    private var repositoryOverride: BedrockRepository? = null
    private var localRepositoryOverride: ChatLocalRepository? = null

    private val effectiveTokenStore get() = tokenStoreOverride ?: tokenStore
    private val effectiveRepository get() = repositoryOverride ?: repository
    private val effectiveLocalRepository get() = localRepositoryOverride ?: localRepository

    /** Interner Konstruktor für Unit-Tests mit injizierten Abhängigkeiten. */
    @VisibleForTesting
    internal constructor(
        app: Application,
        tokenStore: TokenStore,
        repository: BedrockRepository
    ) : this(app) {
        this.tokenStoreOverride = tokenStore
        this.repositoryOverride = repository
    }

    /** Interner Konstruktor für Unit-Tests inkl. lokalem Repository. */
    @VisibleForTesting
    internal constructor(
        app: Application,
        tokenStore: TokenStore,
        repository: BedrockRepository,
        localRepository: ChatLocalRepository
    ) : this(app) {
        this.tokenStoreOverride = tokenStore
        this.repositoryOverride = repository
        this.localRepositoryOverride = localRepository
    }

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _navigateToToken = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val navigateToToken: SharedFlow<Unit> = _navigateToToken.asSharedFlow()

    private val _pendingImage = MutableStateFlow<PendingImage?>(null)
    val pendingImage: StateFlow<PendingImage?> = _pendingImage.asStateFlow()

    /** ID der aktuell geladenen / gespeicherten Konversation. null = neue Konversation. */
    private var currentConversationId: Long? = null

    private var sendJob: Job? = null

    sealed class UiState {
        data object Idle : UiState()
        data object Loading : UiState()
        data class Error(val message: String) : UiState()
    }

    data class PendingImage(
        val uri: Uri,
        val base64: String,
        val mimeType: String
    )

    fun selectImage(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { processImage(uri) }
            if (result != null) {
                _pendingImage.value = PendingImage(uri, result.first, result.second)
            }
        }
    }

    fun clearPendingImage() {
        _pendingImage.value = null
    }

    /** Nur für Tests: setzt ein PendingImage ohne den Android-ContentResolver zu benötigen. */
    @VisibleForTesting
    internal fun setPendingImage(image: PendingImage?) {
        _pendingImage.value = image
    }

    /**
     * Lädt eine gespeicherte Konversation aus der Datenbank.
     * Wird vom [ChatFragment] aufgerufen, wenn der Nutzer einen Verlaufs-Eintrag auswählt.
     */
    fun loadConversation(conversationId: Long) {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                effectiveLocalRepository.loadMessages(conversationId)
            }
            currentConversationId = conversationId
            _messages.value = loaded
            _uiState.value = UiState.Idle
        }
    }

    fun send(text: String) {
        val pending = _pendingImage.value
        val trimmedText = text.trim()
        if (trimmedText.isBlank() && pending == null) return
        if (_uiState.value is UiState.Loading) return

        val userMsg = Message(
            role = MessageRole.USER,
            text = trimmedText,
            imageBase64 = pending?.base64,
            imageMimeType = pending?.mimeType
        )
        val updatedMessages = _messages.value + userMsg
        _messages.value = updatedMessages
        _uiState.value = UiState.Loading
        _pendingImage.value = null

        val config = BedrockConfig(
            region  = effectiveTokenStore.region,
            modelId = effectiveTokenStore.modelId
        )

        sendJob = viewModelScope.launch {
            // Konversation sofort beim ersten Senden anlegen.
            // Kein withContext(IO) nötig – Room-suspend-Funktionen dispatchen intern selbst.
            if (currentConversationId == null) {
                currentConversationId = try {
                    effectiveLocalRepository.createConversation(trimmedText)
                } catch (_: Exception) { null }
            }

            effectiveRepository.converse(updatedMessages, config)
                .onSuccess { reply ->
                    val finalMessages = updatedMessages + Message(MessageRole.ASSISTANT, reply)
                    _messages.value = finalMessages
                    _uiState.value = UiState.Idle
                    saveCurrentMessages(finalMessages)
                }
                .onFailure { error ->
                    _messages.value = _messages.value.dropLast(1)
                    when (error) {
                        is UnauthorizedException -> _navigateToToken.tryEmit(Unit)
                        else -> _uiState.value = UiState.Error(error.message ?: "Unbekannter Fehler")
                    }
                }
        }
    }

    /**
     * Speichert die aktuellen Nachrichten in der bestehenden Konversation.
     * Falls [currentConversationId] noch null ist (erster Versuch fehlgeschlagen),
     * wird die Konversation hier als Fallback erneut angelegt.
     */
    private suspend fun saveCurrentMessages(messages: List<Message>) {
        try {
            val id = currentConversationId
                ?: effectiveLocalRepository.createConversation(
                    messages.firstOrNull { it.role == MessageRole.USER }?.text ?: ""
                ).also { currentConversationId = it }
            effectiveLocalRepository.saveMessages(id, messages)
        } catch (_: Exception) {
            // Persistenzfehler sollen den Chat-Flow nicht unterbrechen
        }
    }

    fun clearError() {
        _uiState.value = UiState.Idle
    }

    /**
     * Startet ein neues leeres Gespräch (ohne die gespeicherte Konversation zu löschen).
     */
    fun clearConversation() {
        sendJob?.cancel()
        sendJob = null
        _messages.value = emptyList()
        _uiState.value = UiState.Idle
        _pendingImage.value = null
        currentConversationId = null
    }

    private fun processImage(uri: Uri): Pair<String, String>? {
        return try {
            val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                ?: return null
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            original ?: return null

            val maxDim = 1024
            val scale = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
            val bitmap = if (scale < 1f) {
                val w = (original.width * scale).toInt()
                val h = (original.height * scale).toInt()
                val scaled = Bitmap.createScaledBitmap(original, w, h, true)
                original.recycle()
                scaled
            } else {
                original
            }

            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            bitmap.recycle()

            val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            Pair(base64, "jpeg")
        } catch (e: Exception) {
            null
        }
    }
}
