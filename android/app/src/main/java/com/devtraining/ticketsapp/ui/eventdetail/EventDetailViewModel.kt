package com.devtraining.ticketsapp.ui.eventdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.devtraining.ticketsapp.TicketsApp
import com.devtraining.ticketsapp.data.remote.BookingDto
import com.devtraining.ticketsapp.data.remote.EventDto
import com.devtraining.ticketsapp.data.remote.EventStatus
import com.devtraining.ticketsapp.data.repository.BookingRepository
import com.devtraining.ticketsapp.data.repository.EventRepository
import com.devtraining.ticketsapp.ui.UiState
import com.devtraining.ticketsapp.ui.hasStarted
import com.devtraining.ticketsapp.ui.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

data class EventDetailUiState(
    val event: UiState<EventDto> = UiState.Loading,
    val quantity: Int = EventDetailViewModel.MIN_TICKETS,
    val booking: UiState<BookingDto> = UiState.Idle,
    /** The event's start time has passed: booking, editing and cancelling are closed. */
    val eventStarted: Boolean = false,
    /** Admin only: true while the "Cancel event?" dialog is shown. */
    val confirmCancelEvent: Boolean = false,
    val cancelEvent: UiState<EventDto> = UiState.Idle,
) {
    /** The event is loaded, scheduled and hasn't started yet. */
    private val open: Boolean
        get() = event is UiState.Success && event.data.status == EventStatus.SCHEDULED && !eventStarted

    /** Open, with seats left (as last reported by the server). */
    private val bookable: Boolean
        get() = open && (event as UiState.Success).data.availableSeats > 0

    /** Admin only: Edit and Cancel event are offered while the event is open. */
    val canManageEvent: Boolean
        get() = open

    val canDecrease: Boolean
        get() = bookable && quantity > EventDetailViewModel.MIN_TICKETS && booking !is UiState.Loading
    val canIncrease: Boolean
        get() = bookable && quantity < EventDetailViewModel.MAX_TICKETS && booking !is UiState.Loading

    /** Book is disabled while a booking is in flight, for a cancelled or started event, and when no seats are left. */
    val canBook: Boolean
        get() = bookable &&
            quantity in EventDetailViewModel.MIN_TICKETS..EventDetailViewModel.MAX_TICKETS &&
            booking !is UiState.Loading
}

class EventDetailViewModel(
    private val eventId: Long,
    private val eventRepository: EventRepository,
    private val bookingRepository: BookingRepository,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    private val _state = MutableStateFlow(EventDetailUiState())
    val state: StateFlow<EventDetailUiState> = _state.asStateFlow()

    /** Called every time the screen is shown. Seats left always come from the server. */
    fun refresh() {
        viewModelScope.launch { loadEvent() }
    }

    fun onQuantityChange(quantity: Int) {
        _state.update { it.copy(quantity = quantity.coerceIn(MIN_TICKETS, MAX_TICKETS)) }
    }

    fun onBookClick() {
        val current = _state.value
        // Rule 03: block 0 or 5+ here, before calling the API. Don't wait for the server's 400.
        if (current.quantity !in MIN_TICKETS..MAX_TICKETS) {
            _state.update { it.copy(booking = UiState.Error("You can book $MIN_TICKETS to $MAX_TICKETS tickets.")) }
            return
        }
        if (!current.canBook) return

        _state.update { it.copy(booking = UiState.Loading) }
        viewModelScope.launch {
            bookingRepository.book(eventId, current.quantity)
                .onSuccess { booking ->
                    _state.update { it.copy(booking = UiState.Success(booking), quantity = MIN_TICKETS) }
                }
                .onFailure { e ->
                    // A 409 (sold out / event cancelled) shows the server's message and stays on this screen.
                    _state.update { it.copy(booking = UiState.Error(e.userMessage())) }
                }
            // Reload after every attempt; never subtract seats on the phone.
            loadEvent()
        }
    }

    // Admin only; the server answers 403 for anyone else.

    fun onCancelEventClick() = _state.update { it.copy(confirmCancelEvent = true) }

    fun onDismissCancelEvent() = _state.update { it.copy(confirmCancelEvent = false) }

    fun onConfirmCancelEvent() {
        _state.update { it.copy(confirmCancelEvent = false, cancelEvent = UiState.Loading) }
        viewModelScope.launch {
            eventRepository.cancel(eventId)
                .onSuccess { event -> _state.update { it.copy(cancelEvent = UiState.Success(event)) } }
                // 409 = already cancelled or already started: the server's message says which.
                .onFailure { e -> _state.update { it.copy(cancelEvent = UiState.Error(e.userMessage())) } }
            loadEvent()
        }
    }

    private suspend fun loadEvent() {
        if (_state.value.event !is UiState.Success) _state.update { it.copy(event = UiState.Loading) }
        eventRepository.event(eventId)
            .onSuccess { event ->
                _state.update {
                    it.copy(event = UiState.Success(event), eventStarted = hasStarted(event.startsAt, now()))
                }
            }
            .onFailure { e -> _state.update { it.copy(event = UiState.Error(e.userMessage())) } }
    }

    companion object {
        const val MIN_TICKETS = 1
        const val MAX_TICKETS = 4
        const val ARG_EVENT_ID = "eventId"

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TicketsApp).container
                val savedStateHandle: SavedStateHandle = createSavedStateHandle()
                EventDetailViewModel(
                    eventId = checkNotNull(savedStateHandle[ARG_EVENT_ID]),
                    eventRepository = container.eventRepository,
                    bookingRepository = container.bookingRepository,
                )
            }
        }
    }
}
