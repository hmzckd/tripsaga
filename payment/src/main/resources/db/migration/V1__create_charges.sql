CREATE TABLE charges (
    id           UUID PRIMARY KEY,
    customer     TEXT        NOT NULL,
    amount_kurus BIGINT      NOT NULL CHECK (amount_kurus > 0),
    card_token   TEXT        NOT NULL,
    status       TEXT        NOT NULL CHECK (status IN ('CAPTURED', 'DECLINED', 'REFUNDED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
