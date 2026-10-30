package com.devtraining.ticketsapp.ui.eventform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devtraining.ticketsapp.data.remote.EventDto
import com.devtraining.ticketsapp.ui.UiState
import com.devtraining.ticketsapp.ui.components.AppTextField
import com.devtraining.ticketsapp.ui.components.AppTopBar
import com.devtraining.ticketsapp.ui.components.DateTimeField
import com.devtraining.ticketsapp.ui.components.ErrorView
import com.devtraining.ticketsapp.ui.components.LoadingButton
import com.devtraining.ticketsapp.ui.components.LoadingView
import com.devtraining.ticketsapp.ui.components.ResultMessage

/** New event, or Edit event when the route has an event id. [onSaved] gets the event as the server saved it. */
@Composable
fun EventFormScreen(
    onBack: () -> Unit,
    onSaved: (EventDto) -> Unit,
    viewModel: EventFormViewModel = viewModel(factory = EventFormViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saving = state.save is UiState.Loading

    LaunchedEffect(state.save) {
        (state.save as? UiState.Success)?.let { onSaved(it.data) }
    }

    Scaffold(topBar = { AppTopBar(if (viewModel.isEdit) "Edit event" else "New event", onBack = onBack) }) { padding ->
        val modifier = Modifier.padding(padding)
        when (val load = state.load) {
            UiState.Loading -> LoadingView(modifier)
            is UiState.Error -> ErrorView(load.message, onRetry = viewModel::load, modifier = modifier)
            UiState.Idle, is UiState.Success -> Column(
                modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppTextField(state.title, onValueChange = viewModel::onTitleChange, label = "Title", enabled = !saving)
                AppTextField(state.venue, onValueChange = viewModel::onVenueChange, label = "Venue", enabled = !saving)
                DateTimeField(
                    value = state.startsAt,
                    onValueChange = viewModel::onStartsAtChange,
                    label = "Starts at",
                    enabled = !saving,
                )
                AppTextField(
                    state.price,
                    onValueChange = viewModel::onPriceChange,
                    label = "Price per ticket",
                    enabled = !saving,
                    keyboardType = KeyboardType.Decimal,
                    supportingText = "For example 25.00",
                )
                AppTextField(
                    state.totalSeats,
                    onValueChange = viewModel::onTotalSeatsChange,
                    label = "Total seats",
                    enabled = !saving,
                    keyboardType = KeyboardType.Number,
                    supportingText = if (viewModel.isEdit) "Can't be fewer than the seats already booked" else null,
                )
                AppTextField(
                    state.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = "Description (optional)",
                    enabled = !saving,
                    imeAction = ImeAction.Default,
                    minLines = 3,
                )
                (state.save as? UiState.Error)?.let {
                    ResultMessage(it.message, isError = true)
                }
                LoadingButton(
                    if (viewModel.isEdit) "Save changes" else "Create event",
                    onClick = viewModel::onSaveClick,
                    enabled = state.canSave,
                    loading = saving,
                )
            }
        }
    }
}
