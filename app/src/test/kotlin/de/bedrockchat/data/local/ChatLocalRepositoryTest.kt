package de.bedrockchat.data.local

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import de.bedrockchat.data.model.Message
import de.bedrockchat.data.model.MessageRole
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.Runs
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatLocalRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockDb = mockk<ChatDatabase>()
    private val mockConvDao = mockk<ConversationDao>()
    private val mockMsgDao = mockk<MessageDao>()
    private lateinit var repository: ChatLocalRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockDb.conversationDao() } returns mockConvDao
        every { mockDb.messageDao() } returns mockMsgDao
        coJustRun { mockConvDao.updateStats(any(), any(), any()) }
        coJustRun { mockMsgDao.deleteByConversationId(any()) }
        coJustRun { mockMsgDao.insertAll(any()) }

        // args[0] = Receiver (ChatDatabase), args[1] = Block – so bei mockkStatic Extension-Functions
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { mockDb.withTransaction(any<suspend () -> Unit>()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            (args[1] as suspend () -> Unit)()
        }

        repository = ChatLocalRepository(mockDb)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ── Bug 1: updateStats statt getById + update ─────────────────────────────

    @Test
    fun `saveMessages calls updateStats and never calls getById or update`() =
        runTest(testDispatcher) {
            repository.saveMessages(1L, listOf(Message(MessageRole.USER, "Hallo")))

            // Kein getById-Round-Trip mehr: beweist den Fix für Bug 1
            coVerify(exactly = 0) { mockConvDao.getById(any()) }
            coVerify(exactly = 0) { mockConvDao.update(any()) }
            coVerify(exactly = 1) { mockConvDao.updateStats(eq(1L), any(), any()) }
        }

    @Test
    fun `saveMessages passes correct messageCount to updateStats`() = runTest(testDispatcher) {
        val messages = listOf(
            Message(MessageRole.USER, "Frage"),
            Message(MessageRole.ASSISTANT, "Antwort"),
        )

        repository.saveMessages(7L, messages)

        coVerify { mockConvDao.updateStats(7L, any(), 2) }
    }

    @Test
    fun `saveMessages single message sets messageCount to 1`() = runTest(testDispatcher) {
        repository.saveMessages(3L, listOf(Message(MessageRole.USER, "Einzel")))

        coVerify { mockConvDao.updateStats(3L, any(), 1) }
    }

    @Test
    fun `saveMessages sets updatedAt to current timestamp`() = runTest(testDispatcher) {
        val before = System.currentTimeMillis()

        repository.saveMessages(1L, listOf(Message(MessageRole.USER, "Test")))

        val after = System.currentTimeMillis()
        // match{} vermeidet slot<Long>()-Casting-Probleme in MockK 1.14 innerhalb von coAnswers
        coVerify { mockConvDao.updateStats(eq(1L), match { it in before..after }, eq(1)) }
    }

    // ── Bug 3: Reihenfolge innerhalb der Transaktion ──────────────────────────

    @Test
    fun `saveMessages deletes then inserts then calls updateStats in that order`() =
        runTest(testDispatcher) {
            val messages = listOf(
                Message(MessageRole.USER, "Hallo"),
                Message(MessageRole.ASSISTANT, "Welt"),
            )

            repository.saveMessages(3L, messages)

            // Beweist atomare Reihenfolge: delete → insert → updateStats
            coVerifyOrder {
                mockMsgDao.deleteByConversationId(3L)
                mockMsgDao.insertAll(any())
                mockConvDao.updateStats(3L, any(), 2)
            }
        }

    @Test
    fun `saveMessages maps messages to entities with correct conversationId and sortIndex`() =
        runTest(testDispatcher) {
            val messages = listOf(
                Message(MessageRole.USER, "Erste"),
                Message(MessageRole.ASSISTANT, "Zweite"),
                Message(MessageRole.USER, "Dritte"),
            )
            val capturedEntities = slot<List<MessageEntity>>()
            coEvery { mockMsgDao.insertAll(capture(capturedEntities)) } just Runs

            repository.saveMessages(99L, messages)

            val entities = capturedEntities.captured
            assertEquals(3, entities.size)

            entities.forEachIndexed { index, entity ->
                assertEquals("sortIndex[$index] falsch", index, entity.sortIndex)
                assertEquals("conversationId[$index] falsch", 99L, entity.conversationId)
            }
            assertEquals("user", entities[0].role)
            assertEquals("assistant", entities[1].role)
            assertEquals("user", entities[2].role)
        }

    @Test
    fun `saveMessages passes message text correctly to entity`() = runTest(testDispatcher) {
        val msg = Message(MessageRole.USER, "Hallo Welt", imageBase64 = "base64data", imageMimeType = "jpeg")

        repository.saveMessages(1L, listOf(msg))

        // match{} vermeidet slot<List<T>>()-Casting-Probleme in MockK 1.14 innerhalb von coAnswers
        coVerify {
            mockMsgDao.insertAll(match { entities ->
                entities.size == 1 &&
                entities[0].text == "Hallo Welt" &&
                entities[0].imageBase64 == "base64data" &&
                entities[0].imageMimeType == "jpeg" &&
                entities[0].id == msg.id
            })
        }
    }
}
