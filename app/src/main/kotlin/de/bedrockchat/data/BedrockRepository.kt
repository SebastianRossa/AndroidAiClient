package de.bedrockchat.data

import de.bedrockchat.data.model.BedrockConfig
import de.bedrockchat.data.model.Message
import de.bedrockchat.security.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
            val payload = buildJsonObject {
                putJsonArray("messages") {
                    messages.forEach { msg ->
                        addJsonObject {
                            put("role", msg.role)
                            putJsonArray("content") {
                                addJsonObject { put("text", msg.text) }
                            }
                        }
                    }
                }
                putJsonObject("inferenceConfig") {
                    put("maxTokens", config.maxTokens)
                    put("temperature", config.temperature)
                }
            }

            val url = "https://bedrock-runtime.${config.region}.amazonaws.com"
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("model")
                .addPathSegment(config.modelId)
                .addPathSegment("converse")
                .build()

            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                when {
                    response.code == 401 -> {
                        tokenStore.clearToken()
                        throw UnauthorizedException()
                    }
                    response.code == 429 -> error("Rate Limit erreicht (429) — kurz warten")
                    response.code == 403 -> error("Zugriff verweigert (403) — Region/Model prüfen")
                    !response.isSuccessful -> {
                        val body = response.body.string()
                        error("HTTP ${response.code}: $body")
                    }
                    else -> {
                        val body = response.body.string()
                        val root = json.parseToJsonElement(body).jsonObject
                        root["output"]
                            ?.jsonObject?.get("message")
                            ?.jsonObject?.get("content")
                            ?.jsonArray?.firstOrNull()
                            ?.jsonObject?.get("text")
                            ?.jsonPrimitive?.content
                            ?: error("Unerwartetes Antwortformat")
                    }
                }
            }
        }
    }
}
