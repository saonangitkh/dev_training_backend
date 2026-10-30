package com.devtraining.ticketsapp.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.devtraining.ticketsapp.data.remote.BookingStatus
import com.devtraining.ticketsapp.data.remote.EventDto
import com.devtraining.ticketsapp.data.remote.EventStatus

enum class BadgeTone { Positive, Neutral, Warning, Negative, Muted }

@Composable
fun StatusBadge(text: String, tone: BadgeTone, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container: Color, content: Color) = when (tone) {
        BadgeTone.Positive -> colors.secondaryContainer to colors.onSecondaryContainer
        BadgeTone.Neutral -> colors.primaryContainer to colors.onPrimaryContainer
        BadgeTone.Warning -> colors.tertiaryContainer to colors.onTertiaryContainer
        BadgeTone.Negative -> colors.errorContainer to colors.onErrorContainer
        BadgeTone.Muted -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    Surface(modifier, shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/**
 * SCHEDULED, SOLD OUT (scheduled with 0 seats left), STARTED (its start time has passed) or CANCELLED.
 * The screen works out [started], so the badge itself doesn't depend on the clock.
 */
@Composable
fun EventStatusBadge(event: EventDto, modifier: Modifier = Modifier, started: Boolean = false) {
    when {
        event.status == EventStatus.CANCELLED -> StatusBadge("CANCELLED", BadgeTone.Negative, modifier)
        started -> StatusBadge("STARTED", BadgeTone.Muted, modifier)
        event.availableSeats == 0 -> StatusBadge("SOLD OUT", BadgeTone.Warning, modifier)
        else -> StatusBadge("SCHEDULED", BadgeTone.Neutral, modifier)
    }
}

@Composable
fun BookingStatusBadge(status: BookingStatus, modifier: Modifier = Modifier) {
    when (status) {
        BookingStatus.CONFIRMED -> StatusBadge("CONFIRMED", BadgeTone.Positive, modifier)
        BookingStatus.CANCELLED -> StatusBadge("CANCELLED", BadgeTone.Negative, modifier)
    }
}
