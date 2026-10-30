package com.devtraining.ticketsapp.ui.eventform

import com.devtraining.ticketsapp.data.repository.EventRepository
import com.devtraining.ticketsapp.testing.FakeTicketsApi
import com.devtraining.ticketsapp.testing.MainDispatcherRule
import com.devtraining.ticketsapp.testing.problem
import com.devtraining.ticketsapp.testing.sampleEvent
import com.devtraining.ticketsapp.testing.testJson
import com.devtraining.ticketsapp.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class EventFormViewModelTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val api = FakeTicketsApi()
    private val repository = EventRepository(api, testJson)

    private fun newEventForm() = EventFormViewModel(null, repository).apply {
        onTitleChange(" Rock Night ")
        onVenueChange("City Arena")
        onStartsAtChange(Instant.parse("2030-06-01T19:00:00Z"))
        onPriceChange("25.50")
        onTotalSeatsChange("100")
    }

    @Test
    fun `a valid form creates the event with trimmed fields`() {
        val viewModel = newEventForm()
        api.saveEvent = { sampleEvent() }

        viewModel.onSaveClick()

        val sent = api.createdEvents.single()
        assertEquals("Rock Night", sent.title)
        assertNull(sent.description)
        assertEquals("2030-06-01T19:00:00Z", sent.startsAt)
        assertEquals(25.5, sent.price, 0.0)
        assertEquals(100, sent.totalSeats)
        assertTrue(viewModel.state.value.save is UiState.Success)
    }

    // Invalid input is caught in the ViewModel, so it never reaches the API.

    @Test
    fun `missing date is not sent`() {
        val viewModel = EventFormViewModel(null, repository).apply {
            onTitleChange("Rock Night")
            onVenueChange("City Arena")
            onPriceChange("25")
            onTotalSeatsChange("100")
        }

        viewModel.onSaveClick()

        assertEquals(UiState.Error("Pick the date and time the event starts."), viewModel.state.value.save)
        assertTrue(api.createdEvents.isEmpty())
    }

    @Test
    fun `start time in the past is not sent`() {
        val viewModel = newEventForm()
        viewModel.onStartsAtChange(Instant.parse("2020-01-01T19:00:00Z"))

        viewModel.onSaveClick()

        assertEquals(UiState.Error("Pick a start time in the future."), viewModel.state.value.save)
        assertTrue(api.createdEvents.isEmpty())
    }

    @Test
    fun `price with 3 decimals is not sent`() {
        val viewModel = newEventForm()
        viewModel.onPriceChange("25.505")

        viewModel.onSaveClick()

        assertEquals(UiState.Error("Price can have at most 2 decimals."), viewModel.state.value.save)
        assertTrue(api.createdEvents.isEmpty())
    }

    @Test
    fun `zero seats is not sent`() {
        val viewModel = newEventForm()
        viewModel.onTotalSeatsChange("0")

        viewModel.onSaveClick()

        assertEquals(UiState.Error("Enter at least 1 seat."), viewModel.state.value.save)
        assertTrue(api.createdEvents.isEmpty())
    }

    @Test
    fun `editing loads the event into the form and saves it with PUT`() {
        api.event = { sampleEvent().copy(description = "Live bands") }
        val viewModel = EventFormViewModel(1, repository)

        assertEquals("Rock Night", viewModel.state.value.title)
        assertEquals("25.00", viewModel.state.value.price)
        assertEquals("100", viewModel.state.value.totalSeats)
        assertEquals("Live bands", viewModel.state.value.description)

        api.saveEvent = { sampleEvent() }
        viewModel.onTotalSeatsChange("120")
        viewModel.onSaveClick()

        val (id, sent) = api.updatedEvents.single()
        assertEquals(1L, id)
        assertEquals(120, sent.totalSeats)
    }

    @Test
    fun `409 on edit shows the server's message`() {
        val viewModel = EventFormViewModel(1, repository)
        api.saveEvent = { throw problem(409, "totalSeats cannot be lower than the 30 seats already booked") }

        viewModel.onSaveClick()

        assertEquals(
            UiState.Error("totalSeats cannot be lower than the 30 seats already booked"),
            viewModel.state.value.save,
        )
    }
}
