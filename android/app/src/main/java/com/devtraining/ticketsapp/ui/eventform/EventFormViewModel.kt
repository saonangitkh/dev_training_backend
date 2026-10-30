package com.devtraining.ticketsapp.ui.eventform

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.devtraining.ticketsapp.TicketsApp
import com.devtraining.ticketsapp.data.remote.EventDto
import com.devtraining.ticketsapp.data.remote.EventRequest
import com.devtraining.ticketsapp.data.repository.EventRepository
import com.devtraining.ticketsapp.ui.UiState
import com.devtraining.ticketsapp.ui.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.util.Locale

data class EventFormUiState(
    val title: String = "",
    val venue: String = "",
    val description: String = "",
    val startsAt: Instant? = null,
    val price: String = "",
    val totalSeats: String = "",
    /** The event being edited, loaded to fill in the form. Idle when creating a new event. */
    val load: UiState<EventDto> = UiState.Idle,
    val save: UiState<EventDto> = UiState.Idle,
) {
    /** The form is shown once there is nothing left to load. */
    val formReady: Boolean get() = load is UiState.Idle || load is UiState.Success

    val canSave: Boolean get() = formReady && save !is UiState.Loading
}

/**
 * Creates an event, or edits one when the route has an event id. Admin only; the server answers 403 for anyone else.
 * The form is checked against the API's limits before anything is sent.
 */
class EventFormViewModel(
    private val eventId: Long?,
    private val eventRepository: EventRepository,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    private val _state = MutableStateFlow(EventFormUiState())
    val state: StateFlow<EventFormUiState> = _state.asStateFlow()

    val isEdit: Boolean get() = eventId != null

    init {
        load()
    }

    /** Loads the event to edit. Does nothing for a new event. */
    fun load() {
        val id = eventId ?: return
        _state.update { it.copy(load = UiState.Loading) }
        viewModelScope.launch {
            eventRepository.event(id)
                .onSuccess { event -> _state.update { it.filledFrom(event).copy(load = UiState.Success(event)) } }
                .onFailure { e -> _state.update { it.copy(load = UiState.Error(e.userMessage())) } }
        }
    }

    fun onTitleChange(title: String) = _state.update { it.copy(title = title) }

    fun onVenueChange(venue: String) = _state.update { it.copy(venue = venue) }

    fun onDescriptionChange(description: String) = _state.update { it.copy(description = description) }

    fun onStartsAtChange(startsAt: Instant) = _state.update { it.copy(startsAt = startsAt) }

    fun onPriceChange(price: String) = _state.update { it.copy(price = price) }

    fun onTotalSeatsChange(totalSeats: String) = _state.update { it.copy(totalSeats = totalSeats) }

    fun onSaveClick() {
        val current = _state.value
        if (!current.canSave) return
        val request = current.toRequest(now()).getOrElse { e ->
            _state.update { it.copy(save = UiState.Error(e.userMessage())) }
            return
        }
        _state.update { it.copy(save = UiState.Loading) }
        viewModelScope.launch {
            val result = if (eventId == null) eventRepository.create(request) else eventRepository.update(eventId, request)
            result
                .onSuccess { event -> _state.update { it.copy(save = UiState.Success(event)) } }
                // 409 on edit = fewer seats than are already booked, or the event is cancelled or has started.
                .onFailure { e -> _state.update { it.copy(save = UiState.Error(e.userMessage())) } }
        }
    }

    companion object {
        const val ARG_EVENT_ID = "eventId"
        const val MAX_TITLE = 200
        const val MAX_VENUE = 200
        const val MAX_DESCRIPTION = 5000
        private const val MAX_PRICE_DIGITS = 8
        private const val MAX_PRICE_DECIMALS = 2

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TicketsApp).container
                val savedStateHandle: SavedStateHandle = createSavedStateHandle()
                EventFormViewModel(savedStateHandle.get<Long>(ARG_EVENT_ID), container.eventRepository)
            }
        }

        private fun EventFormUiState.filledFrom(event: EventDto) = copy(
            title = event.title,
            venue = event.venue,
            description = event.description.orEmpty(),
            startsAt = runCatching { Instant.parse(event.startsAt) }.getOrNull(),
            price = String.format(Locale.US, "%.2f", event.price),
            totalSeats = event.totalSeats.toString(),
        )

        /** The same limits as the API's EventRequest. Fails with a message to show when something is wrong. */
        private fun EventFormUiState.toRequest(now: Instant): Result<EventRequest> {
            val price = price.trim().toBigDecimalOrNull()
            val seats = totalSeats.trim().toIntOrNull()
            val error = when {
                title.isBlank() -> "Enter a title."
                title.trim().length > MAX_TITLE -> "Title can be at most $MAX_TITLE characters."
                venue.isBlank() -> "Enter a venue."
                venue.trim().length > MAX_VENUE -> "Venue can be at most $MAX_VENUE characters."
                description.trim().length > MAX_DESCRIPTION -> "Description can be at most $MAX_DESCRIPTION characters."
                startsAt == null -> "Pick the date and time the event starts."
                !startsAt.isAfter(now) -> "Pick a start time in the future."
                price == null || price < BigDecimal.ZERO -> "Enter a price of 0 or more, such as 25.00."
                price.stripTrailingZeros().scale() > MAX_PRICE_DECIMALS -> "Price can have at most 2 decimals."
                price.precision() - price.scale() > MAX_PRICE_DIGITS -> "Price is too large."
                seats == null || seats < 1 -> "Enter at least 1 seat."
                else -> null
            }
            if (error != null) return Result.failure(IllegalArgumentException(error))
            return Result.success(
                EventRequest(
                    title = title.trim(),
                    description = description.trim().ifEmpty { null },
                    venue = venue.trim(),
                    startsAt = checkNotNull(startsAt).toString(),
                    price = checkNotNull(price).toDouble(),
                    totalSeats = checkNotNull(seats),
                ),
            )
        }
    }
}
