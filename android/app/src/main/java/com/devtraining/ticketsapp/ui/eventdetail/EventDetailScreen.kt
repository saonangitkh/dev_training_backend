package com.devtraining.ticketsapp.ui.eventdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devtraining.ticketsapp.data.remote.EventDto
import com.devtraining.ticketsapp.data.remote.EventStatus
import com.devtraining.ticketsapp.ui.UiState
import com.devtraining.ticketsapp.ui.components.AppTopBar
import com.devtraining.ticketsapp.ui.components.ConfirmDialog
import com.devtraining.ticketsapp.ui.components.DangerTextButton
import com.devtraining.ticketsapp.ui.components.ErrorView
import com.devtraining.ticketsapp.ui.components.EventDetailCard
import com.devtraining.ticketsapp.ui.components.LinkButton
import com.devtraining.ticketsapp.ui.components.LoadingButton
import com.devtraining.ticketsapp.ui.components.LoadingView
import com.devtraining.ticketsapp.ui.components.QuantityStepper
import com.devtraining.ticketsapp.ui.components.ResultMessage
import com.devtraining.ticketsapp.ui.formatMoney

@Composable
fun EventDetailScreen(
    isAdmin: Boolean,
    onBack: () -> Unit,
    onMyBookingsClick: () -> Unit,
    onEditClick: () -> Unit,
    viewModel: EventDetailViewModel = viewModel(factory = EventDetailViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            AppTopBar("Event", onBack = onBack) {
                // A cancelled or started event can't be edited (the server answers 409).
                if (isAdmin && state.canManageEvent) {
                    LinkButton("Edit", onClick = onEditClick)
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val event = state.event) {
            UiState.Idle, UiState.Loading -> LoadingView(modifier)
            is UiState.Error -> ErrorView(event.message, onRetry = viewModel::refresh, modifier = modifier)
            is UiState.Success -> EventDetailContent(
                event = event.data,
                state = state,
                isAdmin = isAdmin,
                onQuantityChange = viewModel::onQuantityChange,
                onBookClick = viewModel::onBookClick,
                onMyBookingsClick = onMyBookingsClick,
                onCancelEventClick = viewModel::onCancelEventClick,
                modifier = modifier,
            )
        }
    }

    val event = (state.event as? UiState.Success)?.data
    if (state.confirmCancelEvent && event != null) {
        ConfirmDialog(
            title = "Cancel event?",
            text = "Cancel ${event.title}? New bookings will be refused, and every existing booking is cancelled.",
            confirmText = "Cancel event",
            dismissText = "Keep",
            onConfirm = viewModel::onConfirmCancelEvent,
            onDismiss = viewModel::onDismissCancelEvent,
        )
    }
}

@Composable
private fun EventDetailContent(
    event: EventDto,
    state: EventDetailUiState,
    isAdmin: Boolean,
    onQuantityChange: (Int) -> Unit,
    onBookClick: () -> Unit,
    onMyBookingsClick: () -> Unit,
    onCancelEventClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        EventDetailCard(event, started = state.eventStarted)

        // Rule 05: Book is disabled for a cancelled, started or sold-out event; say why.
        when {
            event.status == EventStatus.CANCELLED ->
                ResultMessage("This event has been cancelled. Booking is closed.", isError = true)
            state.eventStarted ->
                ResultMessage("This event has already started. Booking is closed.", isError = true)
            event.availableSeats == 0 && !isAdmin ->
                ResultMessage("Sold out. Seats may come back if someone cancels.", isError = true)
        }

        // Admins run events and can't book (the server answers 403), so only customers get the booking controls.
        if (!isAdmin) {
            BookingControls(state, onQuantityChange, onBookClick, onMyBookingsClick)
        } else {
            (state.cancelEvent as? UiState.Error)?.let { ResultMessage(it.message, isError = true) }
            if (state.canManageEvent) {
                DangerTextButton(
                    "Cancel event",
                    onClick = onCancelEventClick,
                    enabled = state.cancelEvent !is UiState.Loading,
                )
            }
        }
    }
}

@Composable
private fun BookingControls(
    state: EventDetailUiState,
    onQuantityChange: (Int) -> Unit,
    onBookClick: () -> Unit,
    onMyBookingsClick: () -> Unit,
) {
    QuantityStepper(
        quantity = state.quantity,
        max = EventDetailViewModel.MAX_TICKETS,
        onQuantityChange = onQuantityChange,
        canDecrease = state.canDecrease,
        canIncrease = state.canIncrease,
    )

    LoadingButton(
        "Book",
        onClick = onBookClick,
        enabled = state.canBook,
        loading = state.booking is UiState.Loading,
    )

    when (val booking = state.booking) {
        is UiState.Success -> {
            // The total comes from the server's response, not from price × quantity on the phone.
            ResultMessage(
                "Booked ${booking.data.quantity} ticket(s). Total ${formatMoney(booking.data.totalPrice)}",
                isError = false,
            )
            LinkButton("View my bookings", onClick = onMyBookingsClick)
        }
        is UiState.Error -> ResultMessage(booking.message, isError = true)
        UiState.Idle, UiState.Loading -> Unit
    }
}
