package com.devtraining.ticketsapp.data.repository

import com.devtraining.ticketsapp.data.local.TokenStore
import com.devtraining.ticketsapp.data.remote.AuthResponse
import com.devtraining.ticketsapp.data.remote.LoginRequest
import com.devtraining.ticketsapp.data.remote.RegisterRequest
import com.devtraining.ticketsapp.data.remote.TicketsApi
import com.devtraining.ticketsapp.data.remote.UserDto
import com.devtraining.ticketsapp.data.remote.apiCall
import kotlinx.serialization.json.Json

class AuthRepository(
    private val api: TicketsApi,
    private val tokenStore: TokenStore,
    private val json: Json,
) {

    suspend fun login(email: String, password: String): Result<UserDto> =
        apiCall(json) { api.login(LoginRequest(email.trim(), password)) }.map(::saveSession)

    /** Creates a CUSTOMER account and logs it in, like [login]. */
    suspend fun register(fullName: String, email: String, password: String): Result<UserDto> =
        apiCall(json) { api.register(RegisterRequest(email.trim(), password, fullName.trim())) }.map(::saveSession)

    /** Reloads the current user, so the saved role is right even for a session saved before roles were stored. */
    suspend fun refreshUser(): Result<UserDto> =
        apiCall(json) { api.me() }.onSuccess { tokenStore.saveRole(it.role) }

    fun logout() = tokenStore.clear()

    private fun saveSession(response: AuthResponse): UserDto {
        tokenStore.save(response.accessToken, response.user.role)
        return response.user
    }
}
