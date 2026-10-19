package de.bedrockchat.ui.history

import android.app.Application
import de.bedrockchat.data.local.ChatLocalRepository
import de.bedrockchat.data.local.ConversationEntity
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests für [HistoryViewModel].
 *
 * Gewünschtes Verhalten:
 * – conversations gibt initial emptyList() zurück.
 * – Sobald Room Daten liefert, werden diese im StateFlow sichtbar.
 * – Wenn Room einen neuen Eintrag vorne einfügt (saveMessages abgeschlossen),
 *   erscheint dieser Eintrag als erstes Element im StateFlow.
 * – deleteConversation delegiert korrekt an das Repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApp = mockk<Application>(relaxed = true)
    private val mockRepo = mockk<ChatLocalRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Hilfsfunktion ─────────────────────────────────────────────────────────

    private fun makeConv(id: Long, updatedAt: Long = id * 100) =
        ConversationEntity(id = id, title = "Chat $id", createdAt = id * 50, updatedAt = updatedAt, messageCount = 1)

    private fun viewModel(flow: MutableStateFlow<List<ConversationEntity>>): HistoryViewModel {
        every { mockRepo.getAllConversations() } returns flow
        return HistoryViewModel(mockApp, mockRepo).also {
            // conversations ist by lazy: erster Zugriff startet den stateIn-Coroutine.
            // Explizit hier triggern, damit advanceUntilIdle() die Kollektion ausführen kann.
            it.conversations
        }
    }

    // ── Initialer Zustand ──────────────────────────────────────────────────────

    @Test
    fun `initial value is empty list before Room delivers data`() = runTest(testDispatcher) {
        val flow = MutableStateFlow<List<ConversationEntity>>(emptyList())
        val vm = viewModel(flow)

        // Erstzugriff auf conversations startet den Flow (by lazy + Eagerly).
        assertEquals(emptyList<ConversationEntity>(), vm.conversations.value)
    }

    // ── Datenweitergabe ────────────────────────────────────────────────────────

    @Test
    fun `conversations emits Room data after initial query`() = runTest(testDispatcher) {
        val conv = makeConv(1)
        val flow = MutableStateFlow(listOf(conv))
        val vm = viewModel(flow)

        advanceUntilIdle()

        assertEquals(listOf(conv), vm.conversations.value)
    }

    @Test
    fun `conversations updates when Room emits new data via Flow`() = runTest(testDispatcher) {
        val old = makeConv(2, updatedAt = 150)
        val flow = MutableStateFlow(listOf(old))
        val vm = viewModel(flow)
        advanceUntilIdle()
        assertEquals(listOf(old), vm.conversations.value)

        // Simuliert abgeschlossenes saveMessages → Room emittiert aktualisierte Liste.
        val newConv = makeConv(1, updatedAt = 300)
        flow.value = listOf(newConv, old)
        advanceUntilIdle()

        assertEquals(listOf(newConv, old), vm.conversations.value)
    }

    @Test
    fun `newest conversation appears first after Room update`() = runTest(testDispatcher) {
        val a = makeConv(3, updatedAt = 100)
        val b = makeConv(2, updatedAt = 200)
        val flow = MutableStateFlow(listOf(b, a))
        val vm = viewModel(flow)
        advanceUntilIdle()

        val newest = makeConv(1, updatedAt = 400)
        flow.value = listOf(newest, b, a)
        advanceUntilIdle()

        assertEquals(newest.id, vm.conversations.value.first().id)
        assertEquals(3, vm.conversations.value.size)
    }

    @Test
    fun `all conversations are preserved after Room update`() = runTest(testDispatcher) {
        val existing = (1L..5L).map { makeConv(it, updatedAt = it * 100) }.sortedByDescending { it.updatedAt }
        val flow = MutableStateFlow(existing)
        val vm = viewModel(flow)
        advanceUntilIdle()

        val newest = makeConv(99, updatedAt = 9999)
        flow.value = listOf(newest) + existing
        advanceUntilIdle()

        assertEquals(6, vm.conversations.value.size)
        assertEquals(newest.id, vm.conversations.value.first().id)
        // Ältere Einträge bleiben erhalten
        assertTrue(vm.conversations.value.any { it.id == existing.last().id })
    }

    // ── Löschen ────────────────────────────────────────────────────────────────

    @Test
    fun `deleteConversation delegates to repository with correct id`() = runTest(testDispatcher) {
        val flow = MutableStateFlow<List<ConversationEntity>>(emptyList())
        coJustRun { mockRepo.deleteConversation(any()) }
        val vm = viewModel(flow)

        vm.deleteConversation(42L)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockRepo.deleteConversation(42L) }
    }

    @Test
    fun `deleteConversation does not affect unrelated conversations`() = runTest(testDispatcher) {
        val conv1 = makeConv(1)
        val conv2 = makeConv(2)
        val flow = MutableStateFlow(listOf(conv1, conv2))
        coJustRun { mockRepo.deleteConversation(any()) }
        val vm = viewModel(flow)
        advanceUntilIdle()

        vm.deleteConversation(conv2.id)
        // Nur Repository aufrufen; das Fragment reagiert auf den Room-Flow-Update.
        advanceUntilIdle()

        coVerify(exactly = 1) { mockRepo.deleteConversation(conv2.id) }
        coVerify(exactly = 0) { mockRepo.deleteConversation(conv1.id) }
    }
}
