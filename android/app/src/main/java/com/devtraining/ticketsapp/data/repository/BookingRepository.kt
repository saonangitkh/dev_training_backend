package com.devtraining.ticketsapp.data.repository

import com.devtraining.ticketsapp.data.remote.BookingDto
import com.devtraining.ticketsapp.data.remote.BookingRequest
import com.devtraining.ticketsapp.data.remote.TicketsApi
import com.devtraining.ticketsapp.data.remote.apiCall
import kotlinx.serialization.json.Json

class BookingRepository(private val api: TicketsApi, private val json: Json) {

    /** Only eventId and quantity are sent: the server decides the price and the seats. */
    suspend fun book(eventId: Long, quantity: Int): Result<BookingDto> =
        apiCall(json) { api.book(BookingRequest(eventId, quantity)) }

    suspend fun myBookings(): Result<List<BookingDto>> = apiCall(json) { api.bookings() }

    suspend fun cancel(bookingId: Long): Result<BookingDto> = apiCall(json) { api.cancelBooking(bookingId) }
}
