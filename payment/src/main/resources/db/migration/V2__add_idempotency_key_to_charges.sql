ALTER TABLE charges ADD COLUMN idempotency_key TEXT;
UPDATE charges SET idempotency_key = id::text WHERE idempotency_key IS NULL;
ALTER TABLE charges ALTER COLUMN idempotency_key SET NOT NULL;
ALTER TABLE charges ADD CONSTRAINT charges_idempotency_key_unique UNIQUE (idempotency_key);
