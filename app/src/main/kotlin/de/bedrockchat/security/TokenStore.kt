package de.bedrockchat.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import de.bedrockchat.Defaults
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import androidx.core.content.edit

class TokenStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)?.let { decrypt(it) }
        set(value) {
            if (value.isNullOrBlank()) prefs.edit { remove(KEY_TOKEN) }
            else prefs.edit { putString(KEY_TOKEN, encrypt(value)) }
        }

    var region: String
        get() = prefs.getString(KEY_REGION, Defaults.REGION) ?: Defaults.REGION
        set(value) = prefs.edit { putString(KEY_REGION, value) }

    var modelId: String
        get() = prefs.getString(KEY_MODEL, Defaults.MODEL_ID) ?: Defaults.MODEL_ID
        set(value) = prefs.edit { putString(KEY_MODEL, value) }

    val isConfigured: Boolean get() = !token.isNullOrBlank()

    fun clearToken() = prefs.edit { remove(KEY_TOKEN) }

    fun clear() = prefs.edit { clear() }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setKeySize(KEY_SIZE_BITS)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }.generateKey()
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv         = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String? = runCatching {
        val combined   = Base64.decode(encoded, Base64.NO_WRAP)
        val iv         = combined.copyOfRange(0, GCM_IV_SIZE)
        val ciphertext = combined.copyOfRange(GCM_IV_SIZE, combined.size)
        val cipher     = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_SIZE, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    companion object {
        private const val PREFS_NAME     = "bedrock_prefs"
        private const val KEY_TOKEN      = "bearer_token_enc"
        private const val KEY_REGION     = "aws_region"
        private const val KEY_MODEL      = "model_id"
        private const val PROVIDER       = "AndroidKeyStore"
        private const val ALIAS          = "bedrock_token_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS  = 256
        private const val GCM_IV_SIZE    = 12
        private const val GCM_TAG_SIZE   = 128
    }
}
