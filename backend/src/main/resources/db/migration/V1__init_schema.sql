CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER', 'ADMIN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_users_email ON users (lower(email));

CREATE TABLE events (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title           VARCHAR(200)   NOT NULL,
    description     TEXT,
    venue           VARCHAR(200)   NOT NULL,
    starts_at       TIMESTAMPTZ    NOT NULL,
    price           NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    total_seats     INT            NOT NULL CHECK (total_seats > 0),
    available_seats INT            NOT NULL,
    status          VARCHAR(20)    NOT NULL CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    -- Database-level guard for "no overbooking" and "seats stay correct".
    CONSTRAINT ck_events_available_seats CHECK (available_seats BETWEEN 0 AND total_seats)
);

CREATE TABLE bookings (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT         NOT NULL REFERENCES users (id),
    event_id     BIGINT         NOT NULL REFERENCES events (id),
    quantity     INT            NOT NULL CHECK (quantity BETWEEN 1 AND 4),
    unit_price   NUMERIC(10, 2) NOT NULL,
    total_price  NUMERIC(12, 2) NOT NULL,
    status       VARCHAR(20)    NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    cancelled_at TIMESTAMPTZ
);

CREATE INDEX ix_bookings_user_id ON bookings (user_id);
CREATE INDEX ix_bookings_event_id ON bookings (event_id);
