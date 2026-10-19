package de.bedrockchat.ui.chat

import android.app.Application
import android.net.Uri
import de.bedrockchat.data.BedrockRepository
import de.bedrockchat.data.UnauthorizedException
import de.bedrockchat.data.local.ChatLocalRepository
import de.bedrockchat.data.model.MessageRole
import de.bedrockchat.security.TokenStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

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
        // 4-Argument-Konstruktor nutzen, damit effectiveLocalRepository stets gemockt ist
        // und keine echten Room-/Android-Aufrufe stattfinden.
        viewModel = ChatViewModel(mockApp, mockTokenStore, mockRepo, mockLocalRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Grundlegende Guard-Bedingungen ────────────────────────────────────────

    @Test
    fun `send with blank text and no image does nothing`() = runTest(testDispatcher) {
        viewModel.send("   ")
        advanceUntilIdle()

        assertTrue(viewModel.messages.value.isEmpty())
        assertTrue(viewModel.uiState.value is ChatViewModel.UiState.Idle)
    }

    @Test
    fun `second send while loading is ignored`() = runTest(testDispatcher) {
        coEvery { mockRepo.converse(any(), any()) } returns Result.success("Antwort")
        viewModel.send("Erste Nachricht")
        // State ist Loading, zweite Nachricht wird ignoriert
        viewModel.send("Zweite Nachricht")
        advanceUntilIdle()

        assertEquals(2, viewModel.messages.value.size) // User + Assistant, nicht 3
    }

    // ── Optimistisches Update und Loading-State ───────────────────────────────

    @Test
    fun `send appends user message synchronously and sets Loading state`() = runTest(testDispatcher) {
        coEvery { mockRepo.converse(any(), any()) } returns Result.success("Antwort")
        viewModel.send("Hallo")

        // NOCH KEIN advanceUntilIdle – coroutine ist noch nicht gelaufen
        assertEquals(1, viewModel.messages.value.size)
        assertEquals("Hallo", viewModel.messages.value[0].text)
        assertEquals(MessageRole.USER, viewModel.messages.value[0].role)
        assertTrue(viewModel.uiState.value is ChatViewModel.UiState.Loading)
    }

    // ── Erfolgsfall ───────────────────────────────────────────────────────────

    @Test
    fun `successful response appends assistant message and returns to Idle`() = runTest(testDispatcher) {
        coEvery { mockRepo.converse(any(), any()) } returns Result.success("Hallo zurück")
        viewModel.send("Hallo")
        advanceUntilIdle()

        assertEquals(2, viewModel.messages.value.size)
        assertEquals("Hallo zurück", viewModel.messages.value[1].text)
        assertEquals(MessageRole.ASSISTANT, viewModel.messages.value[1].role)
        assertTrue(viewModel.uiState.value is ChatViewModel.UiState.Idle)
    }

    // ── Fehlerfall ────────────────────────────────────────────────────────────

    @Test
    fun `failed response rolls back user message and sets Error state`() = runTest(testDispatcher) {
        coEvery { mockRepo.converse(any(), any()) } returns Result.failure(RuntimeException("Serverfehler"))
        viewModel.send("Hallo")
        advanceUntilIdle()

        assertTrue(viewModel.messages.value.isEmpty())
        val state = viewModel.uiState.value
        assertTrue(state is ChatViewModel.UiState.Error)
        assertEquals("Serverfehler", (state as ChatViewModel.UiState.Error).message)
    }

    @Test
    fun `unauthorized response emits navigateToToken event`() = runTest(testDispatcher) {
        coEvery { mockRepo.converse(any(), any()) } returns Result.failure(UnauthorizedException())

        val events = mutableListOf<Unit>()
        val collectJob = launch { viewModel.navigateToToken.collect { events.add(it) } }

        viewModel.send("Hallo")
        advanceUntilIdle()

        assertEquals(1, events.size)
        collectJob.cancel()
    }

    // ── Bild-Features ─────────────────────────────────────────────────────────

    @Test
    fun `send with pending image includes imageBase64 in user message`() = runTest(testDispatcher) {
        coEvery { mockRepo.converse(any(), any()) } returns Result.success("Beschreibung")
        val fakeUri = mockk<Uri>(relaxed = true)
        viewModel.setPendingImage(ChatViewModel.PendingImage(fakeUri, "base64data", "jpeg"))

        viewModel.send("Was siehst du?")
        advanceUntilIdle()

        val userMsg = viewModel.messages.value[0]
        assertEquals("base64data", userMsg.imageBase64)
        assertEquals("jpeg", userMsg.imageMimeType)
        assertEquals("Was siehst du?", userMsg.text)
    }

    @Test
    fun `send with image but no text is allowed`() = runTest(testDispatcher) {
        coEvery { mockRepo.converse(any(), any()) } returns Result.success("Ich sehe ein Bild")
        val fakeUri = mockk<Uri>(relaxed = true)
        viewModel.setPendingImage(ChatViewModel.PendingImage(fakeUri, "imagedata", "jpeg"))

        viewModel.send("")  // leerer Text – sollte trotzdem senden
        advanceUntilIdle()

        assertEquals(2, viewModel.messages.value.size)
        assertEquals("imagedata", viewModel.messages.value[0].imageBase64)
        assertEquals("", viewModel.messages.value[0].text)
    }

    @Test
    fun `pending image is cleared after send`() = runTest(testDispatcher) {
        coEvery { mockRepo.converse(any(), any()) } returns Result.success("ok")
        val fakeUri = mockk<Uri>(relaxed = true)
        viewModel.setPendingImage(ChatViewModel.PendingImage(fakeUri, "data", "jpeg"))

        viewModel.send("Test")
        // pendingImage wird synchron in send() geleert
        assertNull(viewModel.pendingImage.value)
        advanceUntilIdle()
    }

    // ── Verwaltungsfunktionen ─────────────────────────────────────────────────

    @Test
    fun `clearConversation resets messages, uiState and pendingImage`() = runTest(testDispatcher) {
        coEvery { mockRepo.converse(any(), any()) } returns Result.success("Antwort")
        val fakeUri = mockk<Uri>(relaxed = true)
        viewModel.setPendingImage(ChatViewModel.PendingImage(fakeUri, "data", "jpeg"))
        viewModel.send("Hallo")
        advanceUntilIdle()

        viewModel.clearConversation()

        assertTrue(viewModel.messages.value.isEmpty())
        assertTrue(viewModel.uiState.value is ChatViewModel.UiState.Idle)
        assertNull(viewModel.pendingImage.value)
    }

    @Test
    fun `clearError sets state back to Idle`() = runTest(testDispatcher) {
        coEvery { mockRepo.converse(any(), any()) } returns Result.failure(RuntimeException("Fehler"))
        viewModel.send("Hallo")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ChatViewModel.UiState.Error)
        viewModel.clearError()
        assertTrue(viewModel.uiState.value is ChatViewModel.UiState.Idle)
    }
}
