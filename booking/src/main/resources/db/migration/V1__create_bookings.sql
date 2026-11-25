CREATE TABLE bookings (
    id          UUID PRIMARY KEY,
    customer    TEXT        NOT NULL,
    flight_id   TEXT        NOT NULL,
    hotel_id    TEXT        NOT NULL,
    nights      INT         NOT NULL,
    status      TEXT        NOT NULL,
    total_kurus BIGINT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
