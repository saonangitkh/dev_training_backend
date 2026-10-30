package com.devtraining.ticketsapp.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devtraining.ticketsapp.data.remote.EventDto
import com.devtraining.ticketsapp.data.remote.EventStatus
import com.devtraining.ticketsapp.ui.formatDate
import com.devtraining.ticketsapp.ui.formatMoney

/** The full event on the detail screen: everything [EventCard] shows, plus the description and total seats. */
@Composable
fun EventDetailCard(event: EventDto, modifier: Modifier = Modifier, started: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val onSale = event.status == EventStatus.SCHEDULED && !started
    TicketCard(
        stripColor = eventStripColor(event, started),
        modifier = modifier,
        footer = {
            Text(
                "${formatMoney(event.price)} / ticket",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
            )
            Spacer(Modifier.weight(1f))
            // Seats left is exactly what the API returned (availableSeats); the phone never subtracts.
            Text(
                if (onSale) "${event.availableSeats} of ${event.totalSeats} seats left" else "Not on sale",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(event.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            EventStatusBadge(event, started = started)
        }
        Text(event.venue, style = MaterialTheme.typography.bodyLarge)
        Text(formatDate(event.startsAt), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        event.description?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
