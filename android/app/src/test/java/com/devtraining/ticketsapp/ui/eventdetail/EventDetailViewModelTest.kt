package com.devtraining.ticketsapp.ui.eventdetail

import com.devtraining.ticketsapp.data.remote.EventStatus
import com.devtraining.ticketsapp.data.repository.BookingRepository
import com.devtraining.ticketsapp.data.repository.EventRepository
import com.devtraining.ticketsapp.testing.FakeTicketsApi
import com.devtraining.ticketsapp.testing.MainDispatcherRule
import com.devtraining.ticketsapp.testing.problem
import com.devtraining.ticketsapp.testing.sampleBooking
import com.devtraining.ticketsapp.testing.sampleEvent
import com.devtraining.ticketsapp.testing.testJson
import com.devtraining.ticketsapp.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class EventDetailViewModelTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val api = FakeTicketsApi()
    private val viewModel = EventDetailViewModel(1, EventRepository(api, testJson), BookingRepository(api, testJson))

    // Rule 03: the stepper never leaves 1–4, so 0 or 5+ never reaches the API.

    @Test
    fun `quantity stops at 4`() {
        viewModel.refresh()
        viewModel.onQuantityChange(5)
        assertEquals(4, viewModel.state.value.quantity)
        assertFalse(viewModel.state.value.canIncrease)
    }

    @Test
    fun `quantity stops at 1`() {
        viewModel.refresh()
        viewModel.onQuantityChange(0)
        assertEquals(1, viewModel.state.value.quantity)
        assertFalse(viewModel.state.value.canDecrease)
    }

    @Test
    fun `book is disabled until the event is loaded`() {
        viewModel.onBookClick()
        assertFalse(viewModel.state.value.canBook)
        assertTrue(api.bookRequests.isEmpty())
    }

    // Rules 01 + 02: the total comes from the response, and seats are reloaded from the server.

    @Test
    fun `booking shows the server's total and reloads the seats`() {
        viewModel.refresh()
        viewModel.onQuantityChange(2)
        api.book = { sampleBooking(quantity = it.quantity).copy(totalPrice = 50.0) }
        api.event = { sampleEvent(availableSeats = 96) }

        viewModel.onBookClick()

        assertEquals(listOf(2), api.bookRequests.map { it.quantity })
        val booking = viewModel.state.value.booking as UiState.Success
        assertEquals(50.0, booking.data.totalPrice, 0.0)
        val event = viewModel.state.value.event as UiState.Success
        assertEquals(96, event.data.availableSeats)
        assertEquals(2, api.eventCalls)
    }

    // Rule 05: on 409 show the server's message, reload the event, stay on the screen; Book is off for CANCELLED.

    @Test
    fun `409 shows the server's message and reloads the event`() {
        viewModel.refresh()
        api.book = { throw problem(409, "Event 1 is cancelled") }
        api.event = { sampleEvent(status = EventStatus.CANCELLED) }

        viewModel.onBookClick()

        assertEquals(UiState.Error("Event 1 is cancelled"), viewModel.state.value.booking)
        assertEquals(EventStatus.CANCELLED, (viewModel.state.value.event as UiState.Success).data.status)
        assertFalse(viewModel.state.value.canBook)
    }

    @Test
    fun `a cancelled event can't be booked`() {
        api.event = { sampleEvent(status = EventStatus.CANCELLED) }
        viewModel.refresh()

        viewModel.onBookClick()

        assertFalse(viewModel.state.value.canBook)
        assertTrue(api.bookRequests.isEmpty())
    }

    // Once the event has started (sample event: 2030-06-01 19:00 UTC) booking, editing and cancelling are closed.

    @Test
    fun `a started event can't be booked or managed`() {
        val afterStart = EventDetailViewModel(
            1,
            EventRepository(api, testJson),
            BookingRepository(api, testJson),
            now = { Instant.parse("2030-06-01T19:00:00Z") },
        )
        afterStart.refresh()

        afterStart.onBookClick()

        assertTrue(afterStart.state.value.eventStarted)
        assertFalse(afterStart.state.value.canBook)
        assertFalse(afterStart.state.value.canManageEvent)
        assertTrue(api.bookRequests.isEmpty())
    }

    @Test
    fun `only a scheduled event that hasn't started can be edited or cancelled`() {
        viewModel.refresh()
        assertTrue(viewModel.state.value.canManageEvent)

        api.event = { sampleEvent(status = EventStatus.CANCELLED) }
        viewModel.refresh()
        assertFalse(viewModel.state.value.canManageEvent)
    }

    // Admin: cancelling an event asks first, then reloads the event from the server.

    @Test
    fun `cancel event asks first, then reloads the event`() {
        viewModel.refresh()
        api.cancelEvent = { sampleEvent(status = EventStatus.CANCELLED) }
        api.event = { sampleEvent(status = EventStatus.CANCELLED) }

        viewModel.onCancelEventClick()
        assertTrue(viewModel.state.value.confirmCancelEvent)
        assertTrue(api.cancelledEventIds.isEmpty())

        viewModel.onConfirmCancelEvent()

        assertEquals(listOf(1L), api.cancelledEventIds)
        assertFalse(viewModel.state.value.confirmCancelEvent)
        val event = viewModel.state.value.event as UiState.Success
        assertEquals(EventStatus.CANCELLED, event.data.status)
        assertFalse(viewModel.state.value.canBook)
    }

    @Test
    fun `cancel event 409 shows the server's message`() {
        viewModel.refresh()
        api.cancelEvent = { throw problem(409, "Event 1 is already cancelled") }

        viewModel.onCancelEventClick()
        viewModel.onConfirmCancelEvent()

        assertEquals(UiState.Error("Event 1 is already cancelled"), viewModel.state.value.cancelEvent)
    }
}
