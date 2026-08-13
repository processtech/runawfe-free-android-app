package ru.runa.wfe.rest

import okhttp3.Interceptor
import okhttp3.Response

class ApiInterceptor : Interceptor {
    // TODO: automatic token request when there's no token / token is expired
    override fun intercept(chain: Interceptor.Chain): Response {
        val modifiedRequest = chain.request().newBuilder()
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer ${TokenManager.token}")
            .build()
        return chain.proceed(modifiedRequest)
    }
}