-- Cancelling an event now cancels its bookings too. Apply that to events cancelled before this rule.
UPDATE bookings b
SET status = 'CANCELLED', cancelled_at = now()
FROM events e
WHERE b.event_id = e.id
  AND e.status = 'CANCELLED'
  AND b.status = 'CONFIRMED';

UPDATE events
SET available_seats = total_seats
WHERE status = 'CANCELLED';
