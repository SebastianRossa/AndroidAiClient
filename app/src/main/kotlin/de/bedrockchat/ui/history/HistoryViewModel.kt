package de.bedrockchat.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.bedrockchat.data.local.ChatDatabase
import de.bedrockchat.data.local.ChatLocalRepository
import de.bedrockchat.data.local.ConversationEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    app: Application,
    private val localRepository: ChatLocalRepository
) : AndroidViewModel(app) {

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HistoryViewModel(
                        app,
                        ChatLocalRepository(ChatDatabase.getInstance(app))
                    ) as T
            }
    }

    /**
     * Reaktiver Stream aller Konversationen, sortiert nach updatedAt DESC (neueste zuerst).
     */
    val conversations: StateFlow<List<ConversationEntity>> =
        localRepository.getAllConversations()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            localRepository.deleteConversation(id)
        }
    }
}
