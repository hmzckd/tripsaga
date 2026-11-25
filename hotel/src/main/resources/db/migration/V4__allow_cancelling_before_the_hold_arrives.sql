ALTER TABLE holds ALTER COLUMN hotel_id DROP NOT NULL;
ALTER TABLE holds ALTER COLUMN guest DROP NOT NULL;
ALTER TABLE holds ALTER COLUMN nights DROP NOT NULL;
ALTER TABLE holds ADD CONSTRAINT holds_only_cancelled_holds_may_be_empty
    CHECK ((hotel_id IS NOT NULL AND guest IS NOT NULL AND nights IS NOT NULL) OR status = 'CANCELLED');
