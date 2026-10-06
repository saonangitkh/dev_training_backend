-- Cinema ticket booking schema: movies are shown in halls with numbered seats, at showtimes.

CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER', 'ADMIN')),
    -- optional; needed to book PG13 and R18 movies
    date_of_birth DATE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_users_email ON users (lower(email));

-- Needed for the "no overlapping showtimes in a hall" exclusion constraint (trusted extension).
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE movies (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title            VARCHAR(200) NOT NULL,
    description      TEXT,
    duration_minutes INT          NOT NULL CHECK (duration_minutes BETWEEN 1 AND 600),
    age_rating       VARCHAR(10)  NOT NULL CHECK (age_rating IN ('G', 'PG13', 'R18')),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE halls (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_halls_name ON halls (lower(name));

CREATE TABLE seats (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    hall_id     BIGINT      NOT NULL REFERENCES halls (id),
    row_label   VARCHAR(3)  NOT NULL,
    seat_number INT         NOT NULL CHECK (seat_number > 0),
    seat_type   VARCHAR(20) NOT NULL CHECK (seat_type IN ('STANDARD', 'VIP', 'COUPLE')),
    CONSTRAINT ux_seats_position UNIQUE (hall_id, row_label, seat_number)
);

CREATE TABLE showtimes (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    movie_id   BIGINT         NOT NULL REFERENCES movies (id),
    hall_id    BIGINT         NOT NULL REFERENCES halls (id),
    starts_at  TIMESTAMPTZ    NOT NULL,
    -- starts_at + movie duration + cleaning time
    ends_at    TIMESTAMPTZ    NOT NULL,
    base_price NUMERIC(10, 2) NOT NULL CHECK (base_price >= 0),
    status     VARCHAR(20)    NOT NULL CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_showtimes_ends_after_start CHECK (ends_at > starts_at),
    -- Database-level guard: two scheduled showtimes can't use the same hall at the same time.
    CONSTRAINT ex_showtimes_hall_overlap EXCLUDE USING gist (hall_id WITH =, tstzrange(starts_at, ends_at) WITH &&)
        WHERE (status = 'SCHEDULED')
);

CREATE INDEX ix_showtimes_movie_id ON showtimes (movie_id);
CREATE INDEX ix_showtimes_starts_at ON showtimes (starts_at);

CREATE TABLE bookings (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT         NOT NULL REFERENCES users (id),
    showtime_id     BIGINT         NOT NULL REFERENCES showtimes (id),
    quantity        INT            NOT NULL CHECK (quantity BETWEEN 1 AND 4),
    total_price     NUMERIC(12, 2) NOT NULL,
    status          VARCHAR(20)    NOT NULL CHECK (status IN ('HELD', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    hold_expires_at TIMESTAMPTZ    NOT NULL,
    idempotency_key VARCHAR(100),
    ticket_code     VARCHAR(20),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    confirmed_at    TIMESTAMPTZ,
    cancelled_at    TIMESTAMPTZ,
    checked_in_at   TIMESTAMPTZ,
    CONSTRAINT ux_bookings_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT ux_bookings_ticket_code UNIQUE (ticket_code)
);

CREATE INDEX ix_bookings_user_id ON bookings (user_id);
CREATE INDEX ix_bookings_showtime_status ON bookings (showtime_id, status);

CREATE TABLE booking_seats (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id  BIGINT         NOT NULL REFERENCES bookings (id),
    showtime_id BIGINT         NOT NULL REFERENCES showtimes (id),
    seat_id     BIGINT         NOT NULL REFERENCES seats (id),
    price       NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    -- false once the booking is cancelled or its hold expires, which frees the seat
    active      BOOLEAN        NOT NULL DEFAULT true
);

CREATE INDEX ix_booking_seats_booking_id ON booking_seats (booking_id);

-- Database-level guard for "no overbooking": a seat can be held or sold only once per showtime.
CREATE UNIQUE INDEX ux_booking_seats_active ON booking_seats (showtime_id, seat_id) WHERE active;
