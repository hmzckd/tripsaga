ALTER TABLE charges ALTER COLUMN customer DROP NOT NULL;
ALTER TABLE charges ALTER COLUMN amount_kurus DROP NOT NULL;
ALTER TABLE charges ALTER COLUMN card_token DROP NOT NULL;
ALTER TABLE charges DROP CONSTRAINT charges_status_check;
ALTER TABLE charges ADD CONSTRAINT charges_status_check
    CHECK (status IN ('CAPTURED', 'DECLINED', 'REFUNDED', 'CANCELLED'));
ALTER TABLE charges ADD CONSTRAINT charges_only_cancelled_charges_may_be_empty
    CHECK ((customer IS NOT NULL AND amount_kurus IS NOT NULL AND card_token IS NOT NULL) OR status = 'CANCELLED');
