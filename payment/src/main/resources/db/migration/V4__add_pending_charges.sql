ALTER TABLE charges DROP CONSTRAINT charges_status_check;
ALTER TABLE charges ADD CONSTRAINT charges_status_check
    CHECK (status IN ('PENDING', 'CAPTURED', 'DECLINED', 'REFUNDED', 'CANCELLED'));
