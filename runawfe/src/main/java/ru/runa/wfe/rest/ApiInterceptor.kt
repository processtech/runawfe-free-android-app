package ru.runa.wfe.rest

import okhttp3.Interceptor
import okhttp3.Response

class ApiInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        if (TokenManager.token.isNotBlank()) {
            request
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${TokenManager.token}")
        }
        return chain.proceed(request.build())
    }
}