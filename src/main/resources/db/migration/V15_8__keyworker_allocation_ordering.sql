ALTER TABLE prison_configuration ADD COLUMN IF NOT EXISTS allocation_order VARCHAR (15);
UPDATE prison_configuration SET allocation_order = 'BY_ALLOCATIONS' WHERE allocation_order IS NULL;
ALTER TABLE prison_configuration ALTER COLUMN allocation_order SET NOT NULL;

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

ALTER TABLE prison_configuration_audit ADD COLUMN IF NOT EXISTS allocation_order VARCHAR(15);
UPDATE prison_configuration_audit SET allocation_order = 'BY_ALLOCATIONS' WHERE allocation_order IS NULL;
ALTER TABLE prison_configuration_audit ALTER COLUMN allocation_order SET NOT NULL;

ALTER TABLE prison_configuration_audit ADD COLUMN IF NOT EXISTS allocation_order_modified BOOLEAN;
UPDATE prison_configuration_audit SET allocation_order_modified = false WHERE rev_type = 0;
UPDATE prison_configuration_audit SET allocation_order_modified = true WHERE rev_type != 0;
ALTER TABLE prison_configuration_audit ALTER COLUMN allocation_order_modified SET NOT NULL;