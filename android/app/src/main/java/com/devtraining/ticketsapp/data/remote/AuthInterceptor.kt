package com.devtraining.ticketsapp.data.remote

import com.devtraining.ticketsapp.data.local.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the saved token to every request. A 401 on a request that carried a token means the session
 * is no longer valid, so the token is cleared and the app returns to the login screen.
 */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenStore.token()
        val request = if (token == null) {
            chain.request()
        } else {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        }
        val response = chain.proceed(request)
        if (response.code == 401 && token != null) {
            tokenStore.clear()
        }
        return response
    }
}
