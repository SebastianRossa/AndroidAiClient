package de.bedrockchat.ui.history

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.bedrockchat.data.local.ChatDatabase
import de.bedrockchat.data.local.ChatLocalRepository
import de.bedrockchat.data.local.ConversationEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    // by lazy: wird erst beim ersten Zugriff initialisiert, damit der Test-Konstruktor
    // davor ein Override setzen kann (dasselbe Muster wie in ChatViewModel).
    private val localRepository: ChatLocalRepository by lazy {
        ChatLocalRepository(ChatDatabase.getInstance(app))
    }
    private var localRepositoryOverride: ChatLocalRepository? = null
    private val effectiveLocalRepository get() = localRepositoryOverride ?: localRepository

    /** Nur für Unit-Tests: injiziert ein gemocktes Repository. */
    @VisibleForTesting
    internal constructor(
        app: Application,
        localRepository: ChatLocalRepository
    ) : this(app) {
        this.localRepositoryOverride = localRepository
    }

    /**
     * Reaktiver Stream aller Konversationen, sortiert nach updatedAt DESC (neueste zuerst).
     *
     * by lazy + SharingStarted.Eagerly: Der Flow startet sobald conversations zum ersten
     * Mal zugegriffen wird. Im Produktivbetrieb passiert das in onViewCreated des Fragments;
     * in Tests durch direkten Zugriff auf viewModel.conversations.
     */
    val conversations: StateFlow<List<ConversationEntity>> by lazy {
        effectiveLocalRepository.getAllConversations()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            effectiveLocalRepository.deleteConversation(id)
        }
    }
}
