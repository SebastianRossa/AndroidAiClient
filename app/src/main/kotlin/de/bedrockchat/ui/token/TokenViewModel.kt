package de.bedrockchat.ui.token

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import de.bedrockchat.security.TokenStore

class TokenViewModel(app: Application) : AndroidViewModel(app) {

    private val tokenStore = TokenStore(app)

    val region:  String get() = tokenStore.region
    val modelId: String get() = tokenStore.modelId

    fun save(token: String, region: String, modelId: String) {
        tokenStore.token   = token.trim()
        tokenStore.region  = region.trim()
        tokenStore.modelId = modelId.trim()
    }
}
