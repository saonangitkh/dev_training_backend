package com.devtraining.ticketsapp.ui.gallery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.devtraining.ticketsapp.data.remote.BookingStatus
import com.devtraining.ticketsapp.ui.components.AddFab
import com.devtraining.ticketsapp.ui.components.AppTextField
import com.devtraining.ticketsapp.ui.components.AppTopBar
import com.devtraining.ticketsapp.ui.components.BadgeTone
import com.devtraining.ticketsapp.ui.components.BookingCard
import com.devtraining.ticketsapp.ui.components.BookingStatusBadge
import com.devtraining.ticketsapp.ui.components.ConfirmDialog
import com.devtraining.ticketsapp.ui.components.DangerTextButton
import com.devtraining.ticketsapp.ui.components.DateTimeField
import com.devtraining.ticketsapp.ui.components.ErrorView
import com.devtraining.ticketsapp.ui.components.EventCard
import com.devtraining.ticketsapp.ui.components.EventDetailCard
import com.devtraining.ticketsapp.ui.components.EventStatusBadge
import com.devtraining.ticketsapp.ui.components.LinkButton
import com.devtraining.ticketsapp.ui.components.LoadingButton
import com.devtraining.ticketsapp.ui.components.LoadingView
import com.devtraining.ticketsapp.ui.components.MessageView
import com.devtraining.ticketsapp.ui.components.PasswordField
import com.devtraining.ticketsapp.ui.components.QuantityStepper
import com.devtraining.ticketsapp.ui.components.ResultMessage
import com.devtraining.ticketsapp.ui.components.StatusBadge
import com.devtraining.ticketsapp.ui.theme.TicketsTheme
import java.time.Instant

/** Catalog of every reusable component with sample data. Works without the backend. */
@Composable
fun ComponentGalleryScreen(onBack: () -> Unit) {
    Scaffold(topBar = { AppTopBar("UI Components", onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            TopBarSection()
            TextFieldsSection()
            ButtonsSection()
            StepperSection()
            BadgesSection()
            EventCardsSection()
            EventDetailCardSection()
            BookingCardsSection()
            MessagesSection()
            StateViewsSection()
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider()
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun TopBarSection() = Section("Top bar") {
    Label("AppTopBar with actions")
    AppTopBar("Events", windowInsets = WindowInsets(0)) {
        LinkButton("My bookings", onClick = {})
        LinkButton("Log out", onClick = {})
    }
    Label("AppTopBar with back arrow")
    AppTopBar("My bookings", onBack = {}, windowInsets = WindowInsets(0))
}

@Composable
private fun TextFieldsSection() = Section("Text fields") {
    var email by remember { mutableStateOf("customer@tickets.local") }
    var password by remember { mutableStateOf("") }
    Label("AppTextField")
    AppTextField(email, onValueChange = { email = it }, label = "Email", keyboardType = KeyboardType.Email)
    Label("PasswordField with a hint (supportingText)")
    PasswordField(password, onValueChange = { password = it }, supportingText = "At least 8 characters")
    Label("Multi-line (minLines = 3)")
    var description by remember { mutableStateOf(SampleData.event.description.orEmpty()) }
    AppTextField(description, onValueChange = { description = it }, label = "Description (optional)", minLines = 3)
    Label("DateTimeField: tap to pick a date, then a time")
    var startsAt by remember { mutableStateOf<Instant?>(Instant.parse(SampleData.event.startsAt)) }
    DateTimeField(startsAt, onValueChange = { startsAt = it }, label = "Starts at")
    Label("DateTimeField: empty")
    DateTimeField(null, onValueChange = {}, label = "Starts at")
    Label("Disabled (while a request runs)")
    AppTextField(email, onValueChange = {}, label = "Email", enabled = false)
}

@Composable
private fun ButtonsSection() = Section("Buttons") {
    var loading by remember { mutableStateOf(false) }
    Label("LoadingButton: tap to toggle loading")
    LoadingButton("Book", onClick = { loading = !loading }, loading = loading)
    Label("LoadingButton: disabled")
    LoadingButton("Book", onClick = {}, enabled = false)
    Label("LinkButton")
    Row {
        LinkButton("View my bookings", onClick = {})
        LinkButton("Disabled", onClick = {}, enabled = false)
    }
    Label("DangerTextButton")
    Row {
        DangerTextButton("Cancel", onClick = {})
        DangerTextButton("Cancel (disabled)", onClick = {}, enabled = false)
    }
    Label("AddFab (admin: New event)")
    AddFab("New event", onClick = {})
}

@Composable
private fun StepperSection() = Section("Quantity stepper") {
    var quantity by remember { mutableIntStateOf(2) }
    Label("Stops at 1 and 4 (rule 03)")
    QuantityStepper(
        quantity = quantity,
        max = 4,
        onQuantityChange = { quantity = it.coerceIn(1, 4) },
        canDecrease = quantity > 1,
        canIncrease = quantity < 4,
    )
}

@Composable
private fun BadgesSection() = Section("Status badges") {
    Label("Event")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EventStatusBadge(SampleData.event)
        EventStatusBadge(SampleData.soldOutEvent)
        EventStatusBadge(SampleData.startedEvent, started = true)
        EventStatusBadge(SampleData.cancelledEvent)
    }
    Label("Booking")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BookingStatusBadge(BookingStatus.CONFIRMED)
        BookingStatusBadge(BookingStatus.CANCELLED)
    }
    Label("All tones")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BadgeTone.entries.forEach { StatusBadge(it.name.uppercase(), it) }
    }
}

@Composable
private fun EventCardsSection() = Section("Event card") {
    EventCard(SampleData.event, onClick = {})
    EventCard(SampleData.soldOutEvent, onClick = {})
    EventCard(SampleData.startedEvent, onClick = {}, started = true)
    EventCard(SampleData.cancelledEvent, onClick = {})
}

@Composable
private fun EventDetailCardSection() = Section("Event detail card") {
    EventDetailCard(SampleData.event)
    EventDetailCard(SampleData.startedEvent, started = true)
    EventDetailCard(SampleData.cancelledEvent)
}

@Composable
private fun BookingCardsSection() = Section("Booking card") {
    BookingCard(SampleData.booking, onCancelClick = {})
    BookingCard(SampleData.booking, onCancelClick = {}, cancelEnabled = false)
    BookingCard(SampleData.cancelledBooking, onCancelClick = {})
    Label("Event already started: no Cancel")
    BookingCard(SampleData.booking, onCancelClick = {}, canCancel = false)
    Label("Admin's list: shows the customer")
    BookingCard(SampleData.booking, onCancelClick = {}, showCustomer = true)
}

@Composable
private fun MessagesSection() = Section("Messages and dialog") {
    ResultMessage("Booked 2 ticket(s). Total $50.00", isError = false)
    ResultMessage("Booking cancelled. 2 ticket(s) for Rock Night are back on sale.", isError = false)
    ResultMessage("Only 1 seat(s) left for event 1, requested 3", isError = true)

    var showDialog by remember { mutableStateOf(false) }
    LinkButton("Show ConfirmDialog", onClick = { showDialog = true })
    if (showDialog) {
        ConfirmDialog(
            title = "Cancel booking?",
            text = "Cancel 2 ticket(s) for Rock Night? The seats go back on sale.",
            confirmText = "Cancel booking",
            dismissText = "Keep",
            onConfirm = { showDialog = false },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun StateViewsSection() = Section("Screen states") {
    Label("Loading")
    StateFrame { LoadingView() }
    Label("Empty")
    StateFrame { MessageView("You have no bookings yet.") }
    Label("Error")
    StateFrame { ErrorView("Can't reach the server. Check your connection and try again.", onRetry = {}) }
}

/** Gives a full-screen state view a fixed-height frame so it can sit inside the scrolling gallery. */
@Composable
private fun StateFrame(content: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().height(180.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box { content() }
    }
}

@Preview(showBackground = true, heightDp = 4700)
@Composable
private fun ComponentGalleryPreview() {
    TicketsTheme { ComponentGalleryScreen(onBack = {}) }
}
