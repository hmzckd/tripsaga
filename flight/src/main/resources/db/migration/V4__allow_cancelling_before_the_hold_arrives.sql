ALTER TABLE holds ALTER COLUMN flight_id DROP NOT NULL;
ALTER TABLE holds ALTER COLUMN passenger DROP NOT NULL;
ALTER TABLE holds ADD CONSTRAINT holds_only_cancelled_holds_may_be_empty
    CHECK ((flight_id IS NOT NULL AND passenger IS NOT NULL) OR status = 'CANCELLED');
