package com.devtraining.ticketsapp.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.devtraining.ticketsapp.TicketsApp
import com.devtraining.ticketsapp.data.repository.AuthRepository
import com.devtraining.ticketsapp.ui.UiState
import com.devtraining.ticketsapp.ui.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val register: UiState<Unit> = UiState.Idle,
) {
    val canSubmit: Boolean
        get() = fullName.isNotBlank() && email.isNotBlank() && password.isNotEmpty() && register !is UiState.Loading
}

/** The same limits as the API's RegisterRequest, checked before sending. Returns null when the form is valid. */
fun registrationError(fullName: String, email: String, password: String): String? = when {
    fullName.isBlank() -> "Enter your full name."
    fullName.trim().length > RegisterViewModel.MAX_NAME -> "Full name can be at most ${RegisterViewModel.MAX_NAME} characters."
    !EMAIL.matches(email.trim()) -> "Enter a valid email address."
    password.length < RegisterViewModel.MIN_PASSWORD -> "Password must be at least ${RegisterViewModel.MIN_PASSWORD} characters."
    password.length > RegisterViewModel.MAX_PASSWORD -> "Password can be at most ${RegisterViewModel.MAX_PASSWORD} characters."
    else -> null
}

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

class RegisterViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onFullNameChange(fullName: String) = _state.update { it.copy(fullName = fullName) }

    fun onEmailChange(email: String) = _state.update { it.copy(email = email) }

    fun onPasswordChange(password: String) = _state.update { it.copy(password = password) }

    /** On success the token is saved and MainActivity switches to the app, like after logging in. */
    fun onRegisterClick() {
        val current = _state.value
        if (!current.canSubmit) return
        registrationError(current.fullName, current.email, current.password)?.let { message ->
            _state.update { it.copy(register = UiState.Error(message)) }
            return
        }
        _state.update { it.copy(register = UiState.Loading) }
        viewModelScope.launch {
            authRepository.register(current.fullName, current.email, current.password)
                .onSuccess { _state.update { it.copy(password = "", register = UiState.Success(Unit)) } }
                // 409 = the email is already registered: the server's message says so.
                .onFailure { e -> _state.update { it.copy(register = UiState.Error(e.userMessage())) } }
        }
    }

    companion object {
        const val MAX_NAME = 100
        const val MIN_PASSWORD = 8
        const val MAX_PASSWORD = 72

        val Factory = viewModelFactory {
            initializer {
                RegisterViewModel((this[APPLICATION_KEY] as TicketsApp).container.authRepository)
            }
        }
    }
}
