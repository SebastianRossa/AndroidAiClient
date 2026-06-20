package de.bedrockchat.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.bedrockchat.data.BedrockRepository
import de.bedrockchat.data.UnauthorizedException
import de.bedrockchat.data.model.BedrockConfig
import de.bedrockchat.data.model.Message
import de.bedrockchat.data.model.MessageRole
import de.bedrockchat.security.TokenStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val tokenStore = TokenStore(app)
    private val repository = BedrockRepository(tokenStore)

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _navigateToToken = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val navigateToToken: SharedFlow<Unit> = _navigateToToken.asSharedFlow()

    private var sendJob: Job? = null

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Error(val message: String) : UiState()
    }

    fun send(text: String) {
        if (text.isBlank() || _uiState.value is UiState.Loading) return

        val userMsg = Message(MessageRole.USER, text.trim())
        val updatedMessages = _messages.value + userMsg
        _messages.value = updatedMessages
        _uiState.value = UiState.Loading

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
    }
}
