package com.devtraining.ticketsapp.data.local

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keeps the access token encrypted at rest (AES-256, key in the Android Keystore). */
class TokenStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        "session",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _loggedIn = MutableStateFlow(token() != null)

    /** The UI switches between the login screen and the app based on this. */
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _isAdmin = MutableStateFlow(prefs.getString(KEY_ROLE, null) == ROLE_ADMIN)

    /** Admin controls (new, edit and cancel event) are shown based on this. The server still checks every request. */
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    fun token(): String? = prefs.getString(KEY_TOKEN, null)

    fun save(token: String, role: String) {
        prefs.edit {
            putString(KEY_TOKEN, token)
            putString(KEY_ROLE, role)
        }
        _isAdmin.value = role == ROLE_ADMIN
        _loggedIn.value = true
    }

    /** Updates the role of the current session; does nothing once logged out. */
    fun saveRole(role: String) {
        if (token() == null) return
        prefs.edit { putString(KEY_ROLE, role) }
        _isAdmin.value = role == ROLE_ADMIN
    }

    fun clear() {
        prefs.edit {
            remove(KEY_TOKEN)
            remove(KEY_ROLE)
        }
        _isAdmin.value = false
        _loggedIn.value = false
    }

    private companion object {
        const val KEY_TOKEN = "access_token"
        const val KEY_ROLE = "role"
        const val ROLE_ADMIN = "ADMIN"
    }
}
