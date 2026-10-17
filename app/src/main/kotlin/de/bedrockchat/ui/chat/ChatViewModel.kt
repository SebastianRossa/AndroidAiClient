package de.bedrockchat.ui.chat

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.bedrockchat.data.BedrockRepository
import de.bedrockchat.data.UnauthorizedException
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

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val tokenStore = TokenStore(app)
    private val repository = BedrockRepository(tokenStore)

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _navigateToToken = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val navigateToToken: SharedFlow<Unit> = _navigateToToken.asSharedFlow()

    private val _pendingImage = MutableStateFlow<PendingImage?>(null)
    val pendingImage: StateFlow<PendingImage?> = _pendingImage.asStateFlow()

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
            region  = tokenStore.region,
            modelId = tokenStore.modelId
        )

        sendJob = viewModelScope.launch {
            repository.converse(updatedMessages, config)
                .onSuccess { reply ->
                    _messages.value = updatedMessages + Message(MessageRole.ASSISTANT, reply)
                    _uiState.value = UiState.Idle
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

    fun clearError() {
        _uiState.value = UiState.Idle
    }

    fun clearConversation() {
        sendJob?.cancel()
        sendJob = null
        _messages.value = emptyList()
        _uiState.value = UiState.Idle
        _pendingImage.value = null
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
