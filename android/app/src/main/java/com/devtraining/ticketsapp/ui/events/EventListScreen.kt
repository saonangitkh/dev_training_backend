package com.devtraining.ticketsapp.ui.events

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
import com.devtraining.ticketsapp.ui.UiState
import com.devtraining.ticketsapp.ui.components.AddFab
import com.devtraining.ticketsapp.ui.components.AppTopBar
import com.devtraining.ticketsapp.ui.components.ErrorView
import com.devtraining.ticketsapp.ui.components.EventCard
import com.devtraining.ticketsapp.ui.components.LinkButton
import com.devtraining.ticketsapp.ui.components.LoadingView
import com.devtraining.ticketsapp.ui.components.MessageView
import com.devtraining.ticketsapp.ui.hasStarted
import java.time.Instant

@Composable
fun EventListScreen(
    isAdmin: Boolean,
    onEventClick: (Long) -> Unit,
    onMyBookingsClick: () -> Unit,
    onNewEventClick: () -> Unit,
    viewModel: EventListViewModel = viewModel(factory = EventListViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            AppTopBar("Events") {
                LinkButton(if (isAdmin) "All bookings" else "My bookings", onClick = onMyBookingsClick)
                LinkButton("Log out", onClick = viewModel::onLogoutClick)
            }
        },
        floatingActionButton = {
            if (isAdmin) AddFab("New event", onClick = onNewEventClick)
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val s = state) {
            UiState.Idle, UiState.Loading -> LoadingView(modifier)
            is UiState.Error -> ErrorView(s.message, onRetry = viewModel::refresh, modifier = modifier)
            is UiState.Success ->
                if (s.data.isEmpty()) {
                    MessageView(if (isAdmin) "No events yet. Tap New event to add one." else "No events yet.", modifier)
                } else {
                    // Which events have started is checked against the phone clock; the server still refuses them (409).
                    val now = Instant.now()
                    LazyColumn(
                        modifier = modifier,
                        // Leave room under the last card so the New event button doesn't cover it.
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = if (isAdmin) 88.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(s.data, key = { it.id }) { event ->
                            EventCard(event, onClick = { onEventClick(event.id) }, started = hasStarted(event.startsAt, now))
                        }
                    }
                }
        }
    }
}
