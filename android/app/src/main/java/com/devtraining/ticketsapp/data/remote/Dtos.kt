package com.devtraining.ticketsapp.data.remote

import kotlinx.serialization.Serializable

// Mirrors the Spring Boot DTOs in docs/api.md. Money is display-only on the phone; the server computes totals.

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(val email: String, val password: String, val fullName: String)

@Serializable
data class AuthResponse(val accessToken: String, val tokenType: String, val expiresAt: String, val user: UserDto)

@Serializable
data class UserDto(val id: Long, val email: String, val fullName: String, val role: String, val createdAt: String)

@Serializable
enum class EventStatus { SCHEDULED, CANCELLED }

@Serializable
data class EventDto(
    val id: Long,
    val title: String,
    val description: String? = null,
    val venue: String,
    val startsAt: String,
    val price: Double,
    val totalSeats: Int,
    val availableSeats: Int,
    val status: EventStatus,
)

/** Create or replace an event (ADMIN). [startsAt] is an ISO-8601 instant, such as `2030-06-01T19:00:00Z`. */
@Serializable
data class EventRequest(
    val title: String,
    val description: String? = null,
    val venue: String,
    val startsAt: String,
    val price: Double,
    val totalSeats: Int,
)

@Serializable
data class BookingRequest(val eventId: Long, val quantity: Int)

@Serializable
enum class BookingStatus { CONFIRMED, CANCELLED }

@Serializable
data class BookingDto(
    val id: Long,
    val eventId: Long,
    val eventTitle: String,
    val eventStartsAt: String? = null,
    val userId: Long,
    /** Who made the booking; useful in the admin's "All bookings" list. */
    val userEmail: String? = null,
    val userFullName: String? = null,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double,
    val status: BookingStatus,
    val createdAt: String,
    val cancelledAt: String? = null,
)

/** RFC 9457 problem+json body returned by the API on errors. */
@Serializable
data class ProblemDetail(
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val errors: Map<String, String> = emptyMap(),
)
