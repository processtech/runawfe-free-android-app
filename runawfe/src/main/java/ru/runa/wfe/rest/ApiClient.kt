package ru.runa.wfe.rest

import android.net.Uri
import android.util.Log
import android.webkit.URLUtil
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import ru.runa.wfe.restapi.client.AuthControllerApi
import ru.runa.wfe.restapi.client.ChatControllerApi
import ru.runa.wfe.restapi.client.TaskControllerApi
import ru.runa.wfe.restapi.infrastructure.ApiClient
import ru.runa.wfe.restapi.model.MessageAddedBroadcast
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

object ApiClient {
    const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080/"
    var baseUrl: String = DEFAULT_SERVER_URL
        private set

    private lateinit var basicApiClient: ApiClient

    private var _chatService: ChatControllerApi? = null
    val chatService: ChatControllerApi
        get() = _chatService ?: basicApiClient.createService(ChatControllerApi::class.java)
            .also { _chatService = it }

    private var _taskService: TaskControllerApi? = null
    val taskService: TaskControllerApi
        get() = _taskService ?: basicApiClient.createService(TaskControllerApi::class.java)
            .also { _taskService = it }

    val authService: AuthControllerApi
        get() = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClientBuilder.build())
            // Jackson doesn't work well with text/plain responses
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthControllerApi::class.java)

    private val okHttpClientBuilder = OkHttpClient.Builder()
        .callTimeout(1, TimeUnit.MINUTES)

    private val mapper = ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .registerModules(JavaTimeModule())
        .registerKotlinModule()
        .findAndRegisterModules()
        .addMixIn(MessageAddedBroadcast::class.java, MessageAddedBroadcastMixin::class.java)

    fun isApiClientInitialized(): Boolean = this::basicApiClient.isInitialized

    fun setServerUrl(checkedUrl: ServerCheckResult) {
        if (checkedUrl is ServerCheckResult.Valid) {
            baseUrl = checkedUrl.baseUrl
            initBasicApiClient()
            _chatService = null
            _taskService = null
        } else {
            Log.e(this::class.simpleName, "Given URL is not a RunaWFE server")
        }
    }

    private fun initBasicApiClient() {
        basicApiClient = ApiClient(
            baseUrl,
            okHttpClientBuilder,
            mapper,
            null,
            listOf(),
            listOf(JacksonConverterFactory.create(mapper))
        )
        basicApiClient.addAuthorization("token", ApiInterceptor())
    }

    fun toOrigin(url: String): String {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) return ""
        if (URLUtil.isNetworkUrl(trimmedUrl)) { // https or http
            try {
                val originUrl = Uri.parse(trimmedUrl)
                val scheme = originUrl.scheme ?: "http"
                val port = if (originUrl.port != -1) originUrl.port else ""
                return if (originUrl.host != null) {
                    "$scheme://${originUrl.host}:$port"
                } else ""
            } catch (e: Exception) {
                Log.e(this::class.simpleName, "Invalid URL: $url")
            }
        }
        return ""
    }

    suspend fun checkServer(url: String): ServerCheckResult = withContext(Dispatchers.IO) {
        val clearBaseUrl = toOrigin(url)
        if (clearBaseUrl.isEmpty()) {
            return@withContext ServerCheckResult.Invalid
        }
        try {
            val versionRequest = Request.Builder()
                .url("${clearBaseUrl}/wfe/version")
                .get()
                .build()
            val response = OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .build()
                .newCall(versionRequest)
                .execute()
            return@withContext if (!response.isSuccessful) {
                Log.e(this::class.simpleName, "Failed server check: ${response.code}")
                ServerCheckResult.Invalid
            } else if (response.body?.string().isNullOrBlank()) {
                Log.e(this::class.simpleName, "Failed server check: empty response")
                ServerCheckResult.Invalid
            } else {
                ServerCheckResult.Valid(clearBaseUrl)
            }
        } catch (ex: Exception) {
            when (ex) {
                is SocketTimeoutException, is IOException -> {
                    Log.e(this::class.simpleName, "Network exception: ${ex.message.toString()}")
                    return@withContext ServerCheckResult.NetworkError
                }

                else -> {
                    Log.e(this::class.simpleName, "Failed server check: ${ex.message.toString()}")
                    return@withContext ServerCheckResult.Invalid
                }
            }
        }
    }
}

sealed class ServerCheckResult {
    data class Valid(val baseUrl: String) : ServerCheckResult()
    data object Invalid : ServerCheckResult()
    data object NetworkError : ServerCheckResult()
}