package com.devtraining.ticketsapp.testing

import com.devtraining.ticketsapp.data.remote.AuthResponse
import com.devtraining.ticketsapp.data.remote.BookingDto
import com.devtraining.ticketsapp.data.remote.BookingRequest
import com.devtraining.ticketsapp.data.remote.BookingStatus
import com.devtraining.ticketsapp.data.remote.EventDto
import com.devtraining.ticketsapp.data.remote.EventRequest
import com.devtraining.ticketsapp.data.remote.EventStatus
import com.devtraining.ticketsapp.data.remote.LoginRequest
import com.devtraining.ticketsapp.data.remote.RegisterRequest
import com.devtraining.ticketsapp.data.remote.TicketsApi
import com.devtraining.ticketsapp.data.remote.UserDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

val testJson = Json { ignoreUnknownKeys = true }

/** In-memory [TicketsApi]. Each test sets what the "server" answers and checks what the app sent. */
class FakeTicketsApi : TicketsApi {

    var event: () -> EventDto = { sampleEvent() }
    var book: (BookingRequest) -> BookingDto = { error("book not expected") }
    var bookings: () -> List<BookingDto> = { emptyList() }
    var cancel: (Long) -> BookingDto = { error("cancel not expected") }
    var saveEvent: (EventRequest) -> EventDto = { error("save event not expected") }
    var cancelEvent: (Long) -> EventDto = { error("cancel event not expected") }

    val bookRequests = mutableListOf<BookingRequest>()
    var eventCalls = 0
    var bookingsCalls = 0
    val cancelledIds = mutableListOf<Long>()
    val createdEvents = mutableListOf<EventRequest>()
    val updatedEvents = mutableListOf<Pair<Long, EventRequest>>()
    val cancelledEventIds = mutableListOf<Long>()

    override suspend fun event(id: Long): EventDto {
        eventCalls++
        return event()
    }

    override suspend fun book(body: BookingRequest): BookingDto {
        bookRequests += body
        return book.invoke(body)
    }

    override suspend fun bookings(): List<BookingDto> {
        bookingsCalls++
        return bookings.invoke()
    }

    override suspend fun cancelBooking(id: Long): BookingDto {
        cancelledIds += id
        return cancel.invoke(id)
    }

    override suspend fun createEvent(body: EventRequest): EventDto {
        createdEvents += body
        return saveEvent(body)
    }

    override suspend fun updateEvent(id: Long, body: EventRequest): EventDto {
        updatedEvents += id to body
        return saveEvent(body)
    }

    override suspend fun cancelEvent(id: Long): EventDto {
        cancelledEventIds += id
        return cancelEvent.invoke(id)
    }

    override suspend fun login(body: LoginRequest): AuthResponse = error("not used")
    override suspend fun register(body: RegisterRequest): AuthResponse = error("not used")
    override suspend fun me(): UserDto = error("not used")
    override suspend fun events(): List<EventDto> = error("not used")
}

fun sampleEvent(availableSeats: Int = 98, status: EventStatus = EventStatus.SCHEDULED) = EventDto(
    id = 1,
    title = "Rock Night",
    venue = "City Arena",
    startsAt = "2030-06-01T19:00:00Z",
    price = 25.0,
    totalSeats = 100,
    availableSeats = availableSeats,
    status = status,
)

fun sampleBooking(id: Long = 7, quantity: Int = 2, status: BookingStatus = BookingStatus.CONFIRMED) = BookingDto(
    id = id,
    eventId = 1,
    eventTitle = "Rock Night",
    eventStartsAt = "2030-06-01T19:00:00Z",
    userId = 2,
    userEmail = "alice@example.com",
    userFullName = "Alice Customer",
    quantity = quantity,
    unitPrice = 25.0,
    totalPrice = 25.0 * quantity,
    status = status,
    createdAt = "2026-10-05T14:05:00Z",
)

/** What Retrofit throws when the API answers with a problem+json error. */
fun problem(status: Int, detail: String) = HttpException(
    Response.error<Any>(
        status,
        """{"title":"Conflict","status":$status,"detail":"$detail"}"""
            .toResponseBody("application/problem+json".toMediaType()),
    ),
)
