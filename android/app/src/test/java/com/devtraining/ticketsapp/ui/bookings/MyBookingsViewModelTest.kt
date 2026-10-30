package com.devtraining.ticketsapp.ui.bookings

import com.devtraining.ticketsapp.data.remote.BookingStatus
import com.devtraining.ticketsapp.data.repository.BookingRepository
import com.devtraining.ticketsapp.testing.FakeTicketsApi
import com.devtraining.ticketsapp.testing.MainDispatcherRule
import com.devtraining.ticketsapp.testing.sampleBooking
import com.devtraining.ticketsapp.testing.testJson
import com.devtraining.ticketsapp.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Rule 04: My Bookings has loading, empty and error states; Cancel asks first, then reloads. */
class MyBookingsViewModelTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val api = FakeTicketsApi()
    private val viewModel = MyBookingsViewModel(BookingRepository(api, testJson))

    @Test
    fun `starts loading`() {
        assertEquals(UiState.Loading, viewModel.state.value.bookings)
    }

    @Test
    fun `no bookings is an empty success`() {
        viewModel.refresh()
        assertEquals(UiState.Success(emptyList<Any>()), viewModel.state.value.bookings)
    }

    @Test
    fun `network failure is an error with a friendly message`() {
        api.bookings = { throw IOException("timeout") }
        viewModel.refresh()
        val error = viewModel.state.value.bookings as UiState.Error
        assertTrue(error.message.startsWith("Can't reach the server"))
    }

    @Test
    fun `cancel asks to confirm and dismiss sends nothing`() {
        val booking = sampleBooking()
        viewModel.onCancelClick(booking)
        assertEquals(booking, viewModel.state.value.confirmCancel)

        viewModel.onDismissCancel()

        assertNull(viewModel.state.value.confirmCancel)
        assertTrue(api.cancelledIds.isEmpty())
    }

    @Test
    fun `confirm cancels and reloads the list`() {
        val booking = sampleBooking(id = 7)
        api.bookings = { listOf(booking) }
        viewModel.refresh()
        api.cancel = { booking.copy(status = BookingStatus.CANCELLED) }
        api.bookings = { listOf(booking.copy(status = BookingStatus.CANCELLED)) }

        viewModel.onCancelClick(booking)
        viewModel.onConfirmCancel()

        assertEquals(listOf(7L), api.cancelledIds)
        assertTrue(viewModel.state.value.cancel is UiState.Success)
        assertEquals(2, api.bookingsCalls)
        val list = (viewModel.state.value.bookings as UiState.Success).data
        assertEquals(BookingStatus.CANCELLED, list.single().status)
    }
}
