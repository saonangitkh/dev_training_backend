package com.devtraining.ticketsapp.ui

/** The state of one request: every request on every screen is described by one of these. */
sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

fun Throwable.userMessage(): String = message ?: "Something went wrong. Please try again."
