package com.devtraining.ticketsapp.ui.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.devtraining.ticketsapp.TicketsApp
import com.devtraining.ticketsapp.data.remote.BookingDto
import com.devtraining.ticketsapp.data.repository.BookingRepository
import com.devtraining.ticketsapp.ui.UiState
import com.devtraining.ticketsapp.ui.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyBookingsUiState(
    val bookings: UiState<List<BookingDto>> = UiState.Loading,
    /** The booking waiting for the user to confirm cancellation, or null when no dialog is shown. */
    val confirmCancel: BookingDto? = null,
    val cancel: UiState<BookingDto> = UiState.Idle,
)

class MyBookingsViewModel(private val bookingRepository: BookingRepository) : ViewModel() {

    private val _state = MutableStateFlow(MyBookingsUiState())
    val state: StateFlow<MyBookingsUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch { loadBookings() }
    }

    fun onCancelClick(booking: BookingDto) = _state.update { it.copy(confirmCancel = booking) }

    fun onDismissCancel() = _state.update { it.copy(confirmCancel = null) }

    fun onConfirmCancel() {
        val booking = _state.value.confirmCancel ?: return
        _state.update { it.copy(confirmCancel = null, cancel = UiState.Loading) }
        viewModelScope.launch {
            bookingRepository.cancel(booking.id)
                .onSuccess { cancelled -> _state.update { it.copy(cancel = UiState.Success(cancelled)) } }
                .onFailure { e -> _state.update { it.copy(cancel = UiState.Error(e.userMessage())) } }
            // Reload the list from the server. The event screens reload their seats when shown again.
            loadBookings()
        }
    }

    private suspend fun loadBookings() {
        if (_state.value.bookings !is UiState.Success) _state.update { it.copy(bookings = UiState.Loading) }
        bookingRepository.myBookings()
            .onSuccess { list -> _state.update { it.copy(bookings = UiState.Success(list)) } }
            .onFailure { e -> _state.update { it.copy(bookings = UiState.Error(e.userMessage())) } }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                MyBookingsViewModel((this[APPLICATION_KEY] as TicketsApp).container.bookingRepository)
            }
        }
    }
}
