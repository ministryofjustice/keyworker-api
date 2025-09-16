ALTER TABLE prison_configuration ADD COLUMN IF NOT EXISTS allocation_order VARCHAR (15) NOT NULL DEFAULT 'BY_ALLOCATIONS';

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'prison_config_allocation_order_valid'
    ) THEN
ALTER TABLE prison_configuration ADD CONSTRAINT prison_config_allocation_order_valid CHECK (allocation_order IN ('BY_ALLOCATIONS', 'BY_NAME'));
END IF;
END;
$$;

ALTER TABLE prison_configuration_audit ADD COLUMN IF NOT EXISTS allocation_order VARCHAR(15) NOT NULL DEFAULT 'BY_ALLOCATIONS';

ALTER TABLE prison_configuration_audit ADD COLUMN IF NOT EXISTS allocation_order_modified BOOLEAN NOT NULL DEFAULT false;
UPDATE prison_configuration_audit SET allocation_order_modified = true WHERE rev_type != 0;