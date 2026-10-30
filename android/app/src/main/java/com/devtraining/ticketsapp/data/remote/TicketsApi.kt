package com.devtraining.ticketsapp.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface TicketsApi {

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @GET("api/auth/me")
    suspend fun me(): UserDto

    @GET("api/events")
    suspend fun events(): List<EventDto>

    @GET("api/events/{id}")
    suspend fun event(@Path("id") id: Long): EventDto

    @POST("api/events")
    suspend fun createEvent(@Body body: EventRequest): EventDto

    @PUT("api/events/{id}")
    suspend fun updateEvent(@Path("id") id: Long, @Body body: EventRequest): EventDto

    @POST("api/events/{id}/cancel")
    suspend fun cancelEvent(@Path("id") id: Long): EventDto

    @POST("api/bookings")
    suspend fun book(@Body body: BookingRequest): BookingDto

    @GET("api/bookings")
    suspend fun bookings(): List<BookingDto>

    @POST("api/bookings/{id}/cancel")
    suspend fun cancelBooking(@Path("id") id: Long): BookingDto
}
