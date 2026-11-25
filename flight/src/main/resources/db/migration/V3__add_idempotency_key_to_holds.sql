ALTER TABLE holds ADD COLUMN idempotency_key TEXT;
UPDATE holds SET idempotency_key = id::text WHERE idempotency_key IS NULL;
ALTER TABLE holds ALTER COLUMN idempotency_key SET NOT NULL;
ALTER TABLE holds ADD CONSTRAINT holds_idempotency_key_unique UNIQUE (idempotency_key);
