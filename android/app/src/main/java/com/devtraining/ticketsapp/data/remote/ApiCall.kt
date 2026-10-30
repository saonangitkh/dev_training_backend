package com.devtraining.ticketsapp.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/** An API failure with a message that is safe to show to the user. */
class ApiError(val status: Int?, override val message: String) : Exception(message)

/** Runs an API call and turns HTTP and network failures into [ApiError] with a friendly message. */
suspend fun <T> apiCall(json: Json, block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        Result.failure(e.toApiError(json))
    } catch (e: IOException) {
        Result.failure(ApiError(null, "Can't reach the server. Check your connection and try again."))
    }

private fun HttpException.toApiError(json: Json): ApiError {
    val problem = response()?.errorBody()?.string()?.let { body ->
        runCatching { json.decodeFromString(ProblemDetail.serializer(), body) }.getOrNull()
    }
    val message = when (code()) {
        400 -> problem?.errors?.entries?.firstOrNull()?.let { "${it.key} ${it.value}" }
            ?: problem?.detail
            ?: "Please check what you entered."
        401 -> problem?.detail ?: "Your session has expired. Please log in again."
        403 -> problem?.detail ?: "You don't have access to this."
        404 -> "Not found. It may have been removed."
        // 409 = sold out / event cancelled / event started / already cancelled: the server's message explains which.
        409 -> problem?.detail ?: "This can't be done right now."
        else -> "Something went wrong. Please try again."
    }
    return ApiError(code(), message)
}
