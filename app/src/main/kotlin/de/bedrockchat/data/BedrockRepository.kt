package de.bedrockchat.data

import de.bedrockchat.data.model.BedrockConfig
import de.bedrockchat.data.model.Message
import de.bedrockchat.security.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

class BedrockRepository(private val tokenStore: TokenStore) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun converse(
        messages: List<Message>,
        config: BedrockConfig = BedrockConfig()
    ): Result<String> = withContext(Dispatchers.IO) {
        val token = tokenStore.token
            ?: return@withContext Result.failure(IllegalStateException("Kein Token konfiguriert"))

        runCatching {
            val request = buildRequest(messages, config, token)
            client.newCall(request).execute().use { response -> handleResponse(response) }
        }
    }

    private fun buildRequest(messages: List<Message>, config: BedrockConfig, token: String): Request =
        Request.Builder()
            .url(buildUrl(config))
            .post(buildPayload(messages, config).toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .build()

    private fun buildPayload(messages: List<Message>, config: BedrockConfig): JsonObject =
        buildJsonObject {
            putJsonArray("messages") {
                messages.forEach { msg ->
                    addJsonObject {
                        put("role", msg.role.value)
                        putJsonArray("content") {
                            if (msg.imageBase64 != null) {
                                addJsonObject {
                                    putJsonObject("image") {
                                        put("format", msg.imageMimeType ?: "jpeg")
                                        putJsonObject("source") {
                                            put("bytes", msg.imageBase64)
                                        }
                                    }
                                }
                            }
                            if (msg.text.isNotBlank()) {
                                addJsonObject { put("text", msg.text) }
                            }
                        }
                    }
                }
            }
            putJsonObject("inferenceConfig") {
                put("maxTokens", config.maxTokens)
                put("temperature", config.temperature)
            }
        }

    private fun buildUrl(config: BedrockConfig): HttpUrl =
        "https://bedrock-runtime.${config.region}.amazonaws.com"
            .toHttpUrl()
            .newBuilder()
            .addPathSegment("model")
            .addPathSegment(config.modelId)
            .addPathSegment("converse")
            .build()

    private fun handleResponse(response: Response): String = when {
        response.code == HTTP_UNAUTHORIZED -> {
            tokenStore.clearToken()
            throw UnauthorizedException()
        }
        response.code == HTTP_RATE_LIMITED -> error("Rate Limit erreicht (429) — kurz warten")
        response.code == HTTP_FORBIDDEN    -> error("Zugriff verweigert (403) — Region/Model prüfen")
        !response.isSuccessful             -> error("HTTP ${response.code}: ${response.body.string()}")
        else                               -> extractTextFromResponse(response.body.string())
    }

    private fun extractTextFromResponse(body: String): String {
        val root = json.parseToJsonElement(body).jsonObject
        return root["output"]
            ?.jsonObject?.get("message")
            ?.jsonObject?.get("content")
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")
            ?.jsonPrimitive?.content
            ?: error("Unerwartetes Antwortformat")
    }

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN    = 403
        private const val HTTP_RATE_LIMITED = 429
    }
}
