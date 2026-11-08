package ru.runa.wfe.rest

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.runa.wfe.R
import ru.runa.wfe.data.PreferencesManager
import ru.runa.wfe.restapi.model.WfeCredentials
import ru.runa.wfe.ui.login.LoginResult
import java.time.Instant

object TokenManager {
    var token: String = ""
        private set
    var sub: String = "" // Username
        private set

    fun clearToken() {
        token = ""
    }

    fun clearSub() {
        sub = ""
    }

    private fun getTokenPayloadString(token: String): String {
        val tokenParts = token.split(".")
        if (tokenParts.size >= 2)
            return String(java.util.Base64.getUrlDecoder().decode(tokenParts[1]), Charsets.UTF_8)
        return ""
    }

    private fun checkExpirationAndSetSub(payload: String): Boolean {
        val jsonPayload = JSONObject(payload)
        val expiration = jsonPayload.optString("exp")
        if (expiration.isNotBlank()) {
            val currentTime = Instant.now().epochSecond
            val expirationTime = expiration.toString().toLongOrNull()
            if (expirationTime != null && expirationTime > currentTime) {
                val subValue = jsonPayload.optString("sub")
                if (subValue.isNotBlank()) {
                    sub = subValue
                }
                return true
            }
        }
        return false
    }

    private fun checkToken(token: String = this.token): Boolean {
        if (token.isNotEmpty()) {
            return checkExpirationAndSetSub(getTokenPayloadString(token))
        }
        return false
    }

    suspend fun loadToken(preferencesManager: PreferencesManager): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val loadedToken = preferencesManager
                    .getSecureValue(PreferencesManager.TOKEN, String::class.java)
                loadedToken?.let {
                    if (checkToken(it)) {
                        token = loadedToken
                        return@withContext true
                    } else {
                        clearSub()
                        clearToken()
                    }
                    return@withContext false
                } ?: false
            } catch (ex: Exception) {
                Log.e(this.javaClass.simpleName, ex.message.toString())
            }
            return@withContext false
        }

    suspend fun requestToken(credentials: WfeCredentials): LoginResult {
        return try {
            if (!ApiClient.isApiClientInitialized()) {
                return LoginResult(false, R.string.server_url_error)
            }
            val response = ApiClient.authService.basicUsingPOST(credentials)
            if (response.isSuccessful) {
                val token = response.body().toString()
                if (checkToken(token)) {
                    this.token = token
                    return LoginResult(true)
                } else {
                    return LoginResult(false, R.string.token_error)
                }
            } else {
                when (response.code()) {
                    401 -> LoginResult(false, R.string.auth_error)
                    404 -> LoginResult(false, R.string.not_found_login)
                    else -> {
                        Log.e(this.javaClass.simpleName, "Undefined response error")
                        LoginResult(false, R.string.undefined_error)
                    }
                }
            }
        } catch (exception: Exception) {
            Log.e("TokenManager", "Unknown error: ${exception.localizedMessage}")
            LoginResult(false, R.string.undefined_error)
        }
    }
}