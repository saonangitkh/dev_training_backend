package com.devtraining.ticketsapp.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val moneyFormat = NumberFormat.getCurrencyInstance(Locale.US)

private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault())

fun formatMoney(amount: Double): String = moneyFormat.format(amount)

fun formatDate(isoInstant: String): String =
    runCatching { dateFormat.format(Instant.parse(isoInstant)) }.getOrDefault(isoInstant)

/**
 * True once the event's start time has passed. Only used to hide buttons the server would refuse (409);
 * false when the time is missing or can't be read, so the server still decides.
 */
fun hasStarted(startsAt: String?, now: Instant): Boolean =
    startsAt != null && runCatching { !Instant.parse(startsAt).isAfter(now) }.getOrDefault(false)
