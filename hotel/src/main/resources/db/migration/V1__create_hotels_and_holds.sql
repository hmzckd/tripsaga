CREATE TABLE hotels (
    id                    TEXT PRIMARY KEY,
    name                  TEXT   NOT NULL,
    city                  TEXT   NOT NULL,
    rooms_total           INT    NOT NULL CHECK (rooms_total >= 0),
    rooms_free            INT    NOT NULL CHECK (rooms_free >= 0 AND rooms_free <= rooms_total),
    price_per_night_kurus BIGINT NOT NULL CHECK (price_per_night_kurus > 0)
);

CREATE TABLE holds (
    id         UUID PRIMARY KEY,
    hotel_id   TEXT        NOT NULL REFERENCES hotels (id),
    guest      TEXT        NOT NULL,
    nights     INT         NOT NULL CHECK (nights > 0),
    status     TEXT        NOT NULL CHECK (status IN ('HELD', 'CONFIRMED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
