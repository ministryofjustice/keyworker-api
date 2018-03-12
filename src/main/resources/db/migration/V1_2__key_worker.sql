DROP TABLE IF EXISTS KEY_WORKER;

CREATE TABLE KEY_WORKER
(
  STAFF_ID                            BIGINT    NOT NULL,
  STATUS                        VARCHAR( 12)    NOT NULL,
  CAPACITY                               INT    NOT NULL,

  CONSTRAINT KEY_WORKER_PK PRIMARY KEY (STAFF_ID)
);

COMMENT ON TABLE KEY_WORKER IS 'Stores Key worker basic details, including status and capacity.';

COMMENT ON COLUMN KEY_WORKER.STAFF_ID           IS 'The unique staff identifier for Key worker in core OMS';
COMMENT ON COLUMN KEY_WORKER.STATUS             IS 'Current status of Key worker';
COMMENT ON COLUMN KEY_WORKER.CAPACITY           IS 'Standard number of offenders that can be allocated to Key worker';
