package de.bedrockchat.data

import de.bedrockchat.data.model.BedrockConfig
import de.bedrockchat.data.model.Message
import de.bedrockchat.data.model.MessageRole
import de.bedrockchat.security.TokenStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BedrockRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: BedrockRepository
    private val mockTokenStore = mockk<TokenStore>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    private val successResponseBody = """
        {
          "output": {
            "message": {
              "content": [{ "text": "Test reply" }]
            }
          }
        }
    """.trimIndent()

    private fun mockResponse(code: Int, body: String? = null): MockResponse {
        val builder = MockResponse.Builder().code(code)
        if (body != null) builder.body(body)
        return builder.build()
    }

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        every { mockTokenStore.token } returns "test-bearer-token"
        repository = BedrockRepository(
            tokenStore = mockTokenStore,
            baseUrl = mockWebServer.url("/").toString()
        )
    }

    @After
    fun tearDown() {
        mockWebServer.close()
    }

    // ── URL und Header ────────────────────────────────────────────────────────

    @Test
    fun `request URL path contains model ID and converse`() = runTest {
        mockWebServer.enqueue(mockResponse(200, successResponseBody))
        repository.converse(
            listOf(Message(MessageRole.USER, "Hello")),
            BedrockConfig(modelId = "my-model")
        )
        val request = mockWebServer.takeRequest()
        assertTrue(request.target.contains("my-model"))
        assertTrue(request.target.endsWith("/converse"))
    }

    @Test
    fun `request sends bearer token in Authorization header`() = runTest {
        mockWebServer.enqueue(mockResponse(200, successResponseBody))
        repository.converse(listOf(Message(MessageRole.USER, "Hi")), BedrockConfig())
        val request = mockWebServer.takeRequest()
        assertEquals("Bearer test-bearer-token", request.headers["Authorization"])
    }

    // ── JSON-Payload ──────────────────────────────────────────────────────────

    @Test
    fun `text-only message builds correct JSON payload`() = runTest {
        mockWebServer.enqueue(mockResponse(200, successResponseBody))
        repository.converse(listOf(Message(MessageRole.USER, "Hello")), BedrockConfig())

        val body = json.parseToJsonElement(mockWebServer.takeRequest().body!!.utf8()).jsonObject
        val content = body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray

        assertEquals(1, content.size)
        assertEquals("Hello", content[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertNull(content[0].jsonObject["image"])
    }

    @Test
    fun `message with image and text has image block before text block`() = runTest {
        mockWebServer.enqueue(mockResponse(200, successResponseBody))
        val msg = Message(
            role = MessageRole.USER,
            text = "Describe this",
            imageBase64 = "abc123",
            imageMimeType = "jpeg"
        )
        repository.converse(listOf(msg), BedrockConfig())

        val body = json.parseToJsonElement(mockWebServer.takeRequest().body!!.utf8()).jsonObject
        val content = body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray

        assertEquals(2, content.size)
        // Erster Block: Bild
        val imageBlock = content[0].jsonObject["image"]!!.jsonObject
        assertEquals("jpeg", imageBlock["format"]!!.jsonPrimitive.content)
        assertEquals("abc123", imageBlock["source"]!!.jsonObject["bytes"]!!.jsonPrimitive.content)
        // Zweiter Block: Text
        assertEquals("Describe this", content[1].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `message with image but blank text omits text block`() = runTest {
        mockWebServer.enqueue(mockResponse(200, successResponseBody))
        val msg = Message(MessageRole.USER, "", imageBase64 = "abc123", imageMimeType = "jpeg")
        repository.converse(listOf(msg), BedrockConfig())

        val body = json.parseToJsonElement(mockWebServer.takeRequest().body!!.utf8()).jsonObject
        val content = body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray

        assertEquals(1, content.size)
        assertNotNull(content[0].jsonObject["image"])
        assertNull(content[0].jsonObject["text"])
    }

    @Test
    fun `inferenceConfig contains maxTokens and temperature`() = runTest {
        mockWebServer.enqueue(mockResponse(200, successResponseBody))
        repository.converse(
            listOf(Message(MessageRole.USER, "Hi")),
            BedrockConfig(maxTokens = 1000, temperature = 0.7)
        )

        val body = json.parseToJsonElement(mockWebServer.takeRequest().body!!.utf8()).jsonObject
        val inferenceConfig = body["inferenceConfig"]!!.jsonObject

        assertEquals(1000, inferenceConfig["maxTokens"]!!.jsonPrimitive.int)
        assertEquals(0.7, inferenceConfig["temperature"]!!.jsonPrimitive.double, 0.001)
    }

    @Test
    fun `multi-turn conversation sends all messages`() = runTest {
        mockWebServer.enqueue(mockResponse(200, successResponseBody))
        val messages = listOf(
            Message(MessageRole.USER, "Hallo"),
            Message(MessageRole.ASSISTANT, "Hi"),
            Message(MessageRole.USER, "Wie geht's?")
        )
        repository.converse(messages, BedrockConfig())

        val body = json.parseToJsonElement(mockWebServer.takeRequest().body!!.utf8()).jsonObject
        val sentMessages = body["messages"]!!.jsonArray

        assertEquals(3, sentMessages.size)
        assertEquals("user", sentMessages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("assistant", sentMessages[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("user", sentMessages[2].jsonObject["role"]!!.jsonPrimitive.content)
    }

    // ── Response-Handling ─────────────────────────────────────────────────────

    @Test
    fun `200 response returns extracted assistant text`() = runTest {
        mockWebServer.enqueue(mockResponse(200, successResponseBody))
        val result = repository.converse(listOf(Message(MessageRole.USER, "Hi")), BedrockConfig())
        assertTrue(result.isSuccess)
        assertEquals("Test reply", result.getOrNull())
    }

    @Test
    fun `401 response throws UnauthorizedException`() = runTest {
        mockWebServer.enqueue(mockResponse(401))
        val result = repository.converse(listOf(Message(MessageRole.USER, "Hi")), BedrockConfig())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UnauthorizedException)
    }

    @Test
    fun `403 response returns failure with descriptive message`() = runTest {
        mockWebServer.enqueue(mockResponse(403))
        val result = repository.converse(listOf(Message(MessageRole.USER, "Hi")), BedrockConfig())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("403"))
    }

    @Test
    fun `429 response returns failure with rate limit message`() = runTest {
        mockWebServer.enqueue(mockResponse(429))
        val result = repository.converse(listOf(Message(MessageRole.USER, "Hi")), BedrockConfig())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("429"))
    }

    @Test
    fun `null token returns failure without making HTTP call`() = runTest {
        every { mockTokenStore.token } returns null
        val result = repository.converse(listOf(Message(MessageRole.USER, "Hi")), BedrockConfig())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Token"))
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun `default maxTokens is large enough for full responses`() {
        val config = BedrockConfig()
        assertTrue(
            "maxTokens (${'$'}{config.maxTokens}) sollte >= 4096 sein, damit Antworten nicht abgeschnitten werden",
            config.maxTokens >= 4096
        )
    }

    @Test
    fun `default config sends correct maxTokens to API`() = runTest {
        mockWebServer.enqueue(mockResponse(200, successResponseBody))
        repository.converse(listOf(Message(MessageRole.USER, "Hi")), BedrockConfig())

        val body = json.parseToJsonElement(mockWebServer.takeRequest().body!!.utf8()).jsonObject
        val inferenceConfig = body["inferenceConfig"]!!.jsonObject

        assertEquals(4096, inferenceConfig["maxTokens"]!!.jsonPrimitive.int)
    }

    @Test
    fun `long response body is fully extracted`() = runTest {
        val longText = "A".repeat(5000)
        val responseBody = """
            {
              "output": {
                "message": {
                  "content": [{ "text": "$longText" }]
                }
              }
            }
        """.trimIndent()
        mockWebServer.enqueue(mockResponse(200, responseBody))
        val result = repository.converse(listOf(Message(MessageRole.USER, "Hi")), BedrockConfig())
        assertTrue(result.isSuccess)
        assertEquals(5000, result.getOrNull()!!.length)
    }

    @Test
    fun `malformed JSON response returns failure`() = runTest {
        mockWebServer.enqueue(mockResponse(200, "not valid json"))
        val result = repository.converse(listOf(Message(MessageRole.USER, "Hi")), BedrockConfig())
        assertTrue(result.isFailure)
    }
}
