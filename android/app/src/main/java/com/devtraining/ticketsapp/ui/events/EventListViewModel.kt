package com.devtraining.ticketsapp.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.devtraining.ticketsapp.TicketsApp
import com.devtraining.ticketsapp.data.remote.EventDto
import com.devtraining.ticketsapp.data.repository.AuthRepository
import com.devtraining.ticketsapp.data.repository.EventRepository
import com.devtraining.ticketsapp.ui.UiState
import com.devtraining.ticketsapp.ui.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EventListViewModel(
    private val eventRepository: EventRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<EventDto>>>(UiState.Loading)
    val state: StateFlow<UiState<List<EventDto>>> = _state.asStateFlow()

    init {
        // Keeps the saved role (and so the admin controls) in step with the server. A failure changes nothing.
        viewModelScope.launch { authRepository.refreshUser() }
    }

    /** Called every time the screen is shown, so seat counts are always fresh from the server. */
    fun refresh() {
        if (_state.value !is UiState.Success) _state.value = UiState.Loading
        viewModelScope.launch {
            eventRepository.events()
                .onSuccess { _state.value = UiState.Success(it) }
                .onFailure { _state.value = UiState.Error(it.userMessage()) }
        }
    }

    fun onLogoutClick() = authRepository.logout()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TicketsApp).container
                EventListViewModel(container.eventRepository, container.authRepository)
            }
        }
    }
}
