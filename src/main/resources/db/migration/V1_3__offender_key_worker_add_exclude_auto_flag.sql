
alter TABLE KEY_WORKER ADD COLUMN AUTO_ALLOCATION_FLAG char(1) NOT NULL DEFAULT 'Y';

COMMENT ON COLUMN KEY_WORKER.AUTO_ALLOCATION_FLAG        IS 'Y - include in auto allocation pool, N - exclude from auto allocation pool';
