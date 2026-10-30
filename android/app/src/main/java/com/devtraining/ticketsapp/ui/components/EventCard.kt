package com.devtraining.ticketsapp.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devtraining.ticketsapp.data.remote.EventDto
import com.devtraining.ticketsapp.data.remote.EventStatus
import com.devtraining.ticketsapp.ui.formatDate
import com.devtraining.ticketsapp.ui.formatMoney

/**
 * Strip colour for an event: red when cancelled, grey once started, amber when sold out, navy otherwise.
 * Matches [EventStatusBadge].
 */
@Composable
internal fun eventStripColor(event: EventDto, started: Boolean): Color {
    val colors = MaterialTheme.colorScheme
    return when {
        event.status == EventStatus.CANCELLED -> colors.error
        started -> colors.outline
        event.availableSeats == 0 -> colors.tertiary
        else -> colors.primary
    }
}

/** One event in the list. [started] (worked out by the screen) marks an event whose start time has passed. */
@Composable
fun EventCard(event: EventDto, onClick: () -> Unit, modifier: Modifier = Modifier, started: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val onSale = event.status == EventStatus.SCHEDULED && !started
    TicketCard(
        stripColor = eventStripColor(event, started),
        onClick = onClick,
        modifier = modifier,
        footer = {
            Text(
                formatMoney(event.price),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
            )
            Spacer(Modifier.weight(1f))
            // A cancelled or started event is off sale, so its seat count means nothing.
            Text(
                if (onSale) "${event.availableSeats} seats left" else "Not on sale",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                // The footer leaves 8dp on the right for a button's own padding; text needs 16dp.
                modifier = Modifier.padding(end = 8.dp),
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(event.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            EventStatusBadge(event, started = started)
        }
        Text(event.venue, style = MaterialTheme.typography.bodyMedium)
        Text(formatDate(event.startsAt), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}
