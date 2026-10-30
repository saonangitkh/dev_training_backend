package com.devtraining.ticketsapp.ui.bookings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devtraining.ticketsapp.data.remote.BookingDto
import com.devtraining.ticketsapp.ui.UiState
import com.devtraining.ticketsapp.ui.components.AppTopBar
import com.devtraining.ticketsapp.ui.components.BookingCard
import com.devtraining.ticketsapp.ui.components.ConfirmDialog
import com.devtraining.ticketsapp.ui.components.ErrorView
import com.devtraining.ticketsapp.ui.components.LoadingView
import com.devtraining.ticketsapp.ui.components.MessageView
import com.devtraining.ticketsapp.ui.components.ResultMessage
import com.devtraining.ticketsapp.ui.hasStarted
import java.time.Instant

@Composable
fun MyBookingsScreen(
    isAdmin: Boolean,
    onBack: () -> Unit,
    viewModel: MyBookingsViewModel = viewModel(factory = MyBookingsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            // The API returns every user's bookings to an admin, and only their own to a customer.
            AppTopBar(if (isAdmin) "All bookings" else "My bookings", onBack = onBack)
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val bookings = state.bookings) {
            UiState.Idle, UiState.Loading -> LoadingView(modifier)
            is UiState.Error -> ErrorView(bookings.message, onRetry = viewModel::refresh, modifier = modifier)
            is UiState.Success ->
                if (bookings.data.isEmpty()) {
                    MessageView(if (isAdmin) "No bookings yet." else "You have no bookings yet.", modifier)
                } else {
                    val now = Instant.now()
                    LazyColumn(
                        modifier = modifier,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        cancelMessage(state.cancel)?.let { (message, isError) ->
                            item { ResultMessage(message, isError = isError) }
                        }
                        items(bookings.data, key = { it.id }) { booking ->
                            BookingCard(
                                booking = booking,
                                onCancelClick = { viewModel.onCancelClick(booking) },
                                cancelEnabled = state.cancel !is UiState.Loading,
                                // Once the event has started a booking can't be cancelled (the server answers 409).
                                canCancel = !hasStarted(booking.eventStartsAt, now),
                                showCustomer = isAdmin,
                            )
                        }
                    }
                }
        }
    }

    state.confirmCancel?.let { booking ->
        ConfirmDialog(
            title = "Cancel booking?",
            text = "Cancel ${booking.quantity} ticket(s) for ${booking.eventTitle}? The seats go back on sale.",
            confirmText = "Cancel booking",
            dismissText = "Keep",
            onConfirm = viewModel::onConfirmCancel,
            onDismiss = viewModel::onDismissCancel,
        )
    }
}

/** The result of the last cancel, shown above the list: the server's error, or which booking was cancelled. */
private fun cancelMessage(cancel: UiState<BookingDto>): Pair<String, Boolean>? = when (cancel) {
    is UiState.Success ->
        "Booking cancelled. ${cancel.data.quantity} ticket(s) for ${cancel.data.eventTitle} are back on sale." to false
    is UiState.Error -> cancel.message to true
    UiState.Idle, UiState.Loading -> null
}
