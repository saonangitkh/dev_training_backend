package com.devtraining.ticketsapp.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.devtraining.ticketsapp.data.remote.BookingDto
import com.devtraining.ticketsapp.data.remote.BookingStatus
import com.devtraining.ticketsapp.ui.formatDate
import com.devtraining.ticketsapp.ui.formatMoney

/**
 * One booking. The Cancel button only appears for CONFIRMED bookings whose event hasn't started ([canCancel]).
 * [showCustomer] adds who made the booking, for the admin's list of everyone's bookings.
 */
@Composable
fun BookingCard(
    booking: BookingDto,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
    cancelEnabled: Boolean = true,
    canCancel: Boolean = true,
    showCustomer: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val confirmed = booking.status == BookingStatus.CONFIRMED
    TicketCard(
        stripColor = if (confirmed) colors.primary else colors.error,
        modifier = modifier,
        footer = {
            Text(
                "${booking.quantity} ticket(s) · ${formatMoney(booking.totalPrice)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
            )
            Spacer(Modifier.weight(1f))
            if (confirmed && canCancel) {
                DangerTextButton("Cancel", onClick = onCancelClick, enabled = cancelEnabled)
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(booking.eventTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            BookingStatusBadge(booking.status)
        }
        booking.eventStartsAt?.let {
            Text("Event ${formatDate(it)}", style = MaterialTheme.typography.bodyMedium)
        }
        if (showCustomer) {
            val customer = listOfNotNull(booking.userFullName, booking.userEmail).joinToString(" · ")
            Text(
                customer.ifEmpty { "Customer #${booking.userId}" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            "Booked ${formatDate(booking.createdAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}
