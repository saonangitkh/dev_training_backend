package com.devtraining.ticketsapp.data.repository

import com.devtraining.ticketsapp.data.remote.EventDto
import com.devtraining.ticketsapp.data.remote.EventRequest
import com.devtraining.ticketsapp.data.remote.TicketsApi
import com.devtraining.ticketsapp.data.remote.apiCall
import kotlinx.serialization.json.Json

class EventRepository(private val api: TicketsApi, private val json: Json) {

    suspend fun events(): Result<List<EventDto>> = apiCall(json) { api.events() }

    suspend fun event(id: Long): Result<EventDto> = apiCall(json) { api.event(id) }

    // Admin only: the server answers 403 for anyone else.

    suspend fun create(request: EventRequest): Result<EventDto> = apiCall(json) { api.createEvent(request) }

    suspend fun update(id: Long, request: EventRequest): Result<EventDto> = apiCall(json) { api.updateEvent(id, request) }

    suspend fun cancel(id: Long): Result<EventDto> = apiCall(json) { api.cancelEvent(id) }
}
