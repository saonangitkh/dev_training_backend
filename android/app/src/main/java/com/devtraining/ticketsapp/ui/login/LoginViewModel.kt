package com.devtraining.ticketsapp.ui.login

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

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val login: UiState<Unit> = UiState.Idle,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotBlank() && login !is UiState.Loading
}

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(email: String) = _state.update { it.copy(email = email) }

    fun onPasswordChange(password: String) = _state.update { it.copy(password = password) }

    /**
     * On success the token is saved and MainActivity switches to the app; nothing to navigate here.
     * The password is cleared so it isn't still filled in after logging out.
     */
    fun onLoginClick() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(login = UiState.Loading) }
        viewModelScope.launch {
            authRepository.login(current.email, current.password)
                .onSuccess { _state.update { it.copy(password = "", login = UiState.Success(Unit)) } }
                .onFailure { e -> _state.update { it.copy(login = UiState.Error(e.userMessage())) } }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                LoginViewModel((this[APPLICATION_KEY] as TicketsApp).container.authRepository)
            }
        }
    }
}
