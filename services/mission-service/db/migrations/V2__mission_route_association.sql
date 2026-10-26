-- Reviewed additive migration for associating existing saved route rows with mission-area rows.
-- Existing rows remain unassociated because the new nullable FK column has no default.
BEGIN;

ALTER TABLE missions ADD COLUMN IF NOT EXISTS associated_mission_id UUID;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_missions_associated_mission' AND conrelid = 'missions'::regclass) THEN
        ALTER TABLE missions ADD CONSTRAINT fk_missions_associated_mission
            FOREIGN KEY (associated_mission_id) REFERENCES missions(id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_missions_route_association' AND conrelid = 'missions'::regclass) THEN
        ALTER TABLE missions ADD CONSTRAINT ck_missions_route_association
            CHECK (associated_mission_id IS NULL OR (route IS NOT NULL AND geometry IS NULL AND associated_mission_id <> id));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_missions_associated_mission
    ON missions (associated_mission_id) WHERE associated_mission_id IS NOT NULL;

COMMIT;
