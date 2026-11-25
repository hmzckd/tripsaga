CREATE TABLE flights (
    id          TEXT PRIMARY KEY,
    origin      TEXT        NOT NULL,
    destination TEXT        NOT NULL,
    departs_at  TIMESTAMPTZ NOT NULL,
    seats_total INT         NOT NULL CHECK (seats_total >= 0),
    seats_free  INT         NOT NULL CHECK (seats_free >= 0 AND seats_free <= seats_total),
    price_kurus BIGINT      NOT NULL CHECK (price_kurus > 0)
);

CREATE TABLE holds (
    id         UUID PRIMARY KEY,
    flight_id  TEXT        NOT NULL REFERENCES flights (id),
    passenger  TEXT        NOT NULL,
    status     TEXT        NOT NULL CHECK (status IN ('HELD', 'CONFIRMED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
