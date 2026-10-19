package de.bedrockchat.ui.chat

import android.app.Application
import de.bedrockchat.data.BedrockRepository
import de.bedrockchat.data.local.ChatLocalRepository
import de.bedrockchat.data.model.Message
import de.bedrockchat.data.model.MessageRole
import de.bedrockchat.security.TokenStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tests für das korrekte Persistenz-Verhalten von [ChatViewModel]:
 *
 * - Bug 2: saveMessages darf pro Nachricht nur EINMAL aufgerufen werden
 *   (vorher: einmal mit [userMsg] in send() + einmal mit [userMsg, assistantMsg]
 *    in saveCurrentMessages → doppelter Schreibzugriff).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelPersistenceTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApp = mockk<Application>(relaxed = true)
    private val mockTokenStore = mockk<TokenStore>(relaxed = true)
    private val mockRepo = mockk<BedrockRepository>(relaxed = true)
    private val mockLocalRepo = mockk<ChatLocalRepository>(relaxed = true)

    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockTokenStore.region } returns "eu-central-1"
        every { mockTokenStore.modelId } returns "test-model"
        coEvery { mockLocalRepo.createConversation(any()) } returns 1L
        viewModel = ChatViewModel(mockApp, mockTokenStore, mockRepo, mockLocalRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Bug 2: saveMessages wird nicht doppelt aufgerufen ─────────────────────

    @Test
    fun `send calls saveMessages exactly once after successful response`() =
        runTest(testDispatcher) {
            // Vor dem Fix wurde saveMessages zweimal aufgerufen:
            // 1x in send() mit [userMsg], 1x in saveCurrentMessages mit [userMsg, assistantMsg]
            coEvery { mockRepo.converse(any(), any()) } returns Result.success("Antwort")

            viewModel.send("Hallo")
            advanceUntilIdle()

            coVerify(exactly = 1) { mockLocalRepo.saveMessages(any(), any()) }
        }

    @Test
    fun `send does not call saveMessages when API call fails`() = runTest(testDispatcher) {
        coEvery { mockRepo.converse(any(), any()) } returns
            Result.failure(RuntimeException("Serverfehler"))

        viewModel.send("Hallo")
        advanceUntilIdle()

        coVerify(exactly = 0) { mockLocalRepo.saveMessages(any(), any()) }
    }

    @Test
    fun `saveMessages receives both user and assistant messages`() = runTest(testDispatcher) {
        coEvery { mockRepo.converse(any(), any()) } returns Result.success("Hallo zurück")
        val capturedMessages = slot<List<Message>>()
        coEvery { mockLocalRepo.saveMessages(any(), capture(capturedMessages)) } just Runs

        viewModel.send("Hallo")
        advanceUntilIdle()

        // Stellt sicher, dass die vollständige Konversation gespeichert wird
        assertEquals(2, capturedMessages.captured.size)
        assertEquals(MessageRole.USER, capturedMessages.captured[0].role)
        assertEquals("Hallo", capturedMessages.captured[0].text)
        assertEquals(MessageRole.ASSISTANT, capturedMessages.captured[1].role)
        assertEquals("Hallo zurück", capturedMessages.captured[1].text)
    }

    @Test
    fun `saveMessages is called with the correct conversationId`() = runTest(testDispatcher) {
        coEvery { mockLocalRepo.createConversation(any()) } returns 42L
        coEvery { mockRepo.converse(any(), any()) } returns Result.success("Antwort")

        viewModel.send("Test")
        advanceUntilIdle()

        coVerify { mockLocalRepo.saveMessages(42L, any()) }
    }

    // ── createConversation-Verhalten über mehrere Sends ───────────────────────

    @Test
    fun `send calls createConversation exactly once on first message`() =
        runTest(testDispatcher) {
            coEvery { mockRepo.converse(any(), any()) } returns Result.success("Antwort")

            viewModel.send("Erste Nachricht")
            advanceUntilIdle()

            coVerify(exactly = 1) { mockLocalRepo.createConversation("Erste Nachricht") }
        }

    @Test
    fun `second send in same conversation does not call createConversation again`() =
        runTest(testDispatcher) {
            coEvery { mockRepo.converse(any(), any()) } returns Result.success("Antwort")

            viewModel.send("Erste Nachricht")
            advanceUntilIdle()
            viewModel.send("Zweite Nachricht")
            advanceUntilIdle()

            // Konversation wird nur beim ersten Senden angelegt
            coVerify(exactly = 1) { mockLocalRepo.createConversation(any()) }
            // saveMessages einmal pro API-Antwort
            coVerify(exactly = 2) { mockLocalRepo.saveMessages(any(), any()) }
        }

    @Test
    fun `clearConversation allows new createConversation on next send`() =
        runTest(testDispatcher) {
            coEvery { mockRepo.converse(any(), any()) } returns Result.success("Antwort")
            coEvery { mockLocalRepo.createConversation(any()) } returnsMany listOf(1L, 2L)

            viewModel.send("Chat A")
            advanceUntilIdle()

            viewModel.clearConversation()

            viewModel.send("Chat B")
            advanceUntilIdle()

            // createConversation für jeden neuen Chat aufgerufen
            coVerify(exactly = 2) { mockLocalRepo.createConversation(any()) }
        }
}
