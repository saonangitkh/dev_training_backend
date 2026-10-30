package com.devtraining.ticketsapp.ui.gallery

import com.devtraining.ticketsapp.data.remote.BookingDto
import com.devtraining.ticketsapp.data.remote.BookingStatus
import com.devtraining.ticketsapp.data.remote.EventDto
import com.devtraining.ticketsapp.data.remote.EventStatus

/** Fake data for the component gallery and previews. Never sent to the API. */
object SampleData {

    val event = EventDto(
        id = 1,
        title = "Rock Night",
        description = "Live bands all night",
        venue = "City Arena",
        startsAt = "2030-06-01T19:00:00Z",
        price = 25.00,
        totalSeats = 100,
        availableSeats = 98,
        status = EventStatus.SCHEDULED,
    )

    val soldOutEvent = event.copy(id = 2, title = "Jazz Evening", venue = "Blue Room", availableSeats = 0)

    val startedEvent = event.copy(id = 4, title = "Morning Yoga", venue = "Park Stage", startsAt = "2026-01-10T07:00:00Z")

    val cancelledEvent = event.copy(id = 3, title = "Comedy Show", venue = "Main Hall", status = EventStatus.CANCELLED)

    val booking = BookingDto(
        id = 7,
        eventId = 1,
        eventTitle = "Rock Night",
        eventStartsAt = "2030-06-01T19:00:00Z",
        userId = 2,
        userEmail = "alice@example.com",
        userFullName = "Alice Customer",
        quantity = 2,
        unitPrice = 25.00,
        totalPrice = 50.00,
        status = BookingStatus.CONFIRMED,
        createdAt = "2026-10-05T14:05:00Z",
    )

    val cancelledBooking = booking.copy(
        id = 8,
        quantity = 1,
        totalPrice = 25.00,
        status = BookingStatus.CANCELLED,
        cancelledAt = "2026-10-06T09:00:00Z",
    )
}
