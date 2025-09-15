ALTER TABLE prison_configuration ADD COLUMN IF NOT EXISTS allocation_order VARCHAR (15);
ALTER TABLE prison_configuration ALTER COLUMN allocation_order SET DEFAULT 'BY_ALLOCATIONS';

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

ALTER TABLE prison_configuration_audit ADD COLUMN IF NOT EXISTS allocation_order_modified BOOLEAN;