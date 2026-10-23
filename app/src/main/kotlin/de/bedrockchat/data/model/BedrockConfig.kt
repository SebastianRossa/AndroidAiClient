package de.bedrockchat.data.model

import de.bedrockchat.Defaults

data class BedrockConfig(
    val region: String = Defaults.REGION,
    val modelId: String = Defaults.MODEL_ID,
    val maxTokens: Int = Defaults.MAX_TOKENS,
    val temperature: Double = Defaults.TEMPERATURE
)
