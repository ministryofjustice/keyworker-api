DROP TABLE IF EXISTS PRISON_SUPPORTED;

CREATE TABLE PRISON_SUPPORTED
(
  PRISON_ID            VARCHAR(6)   NOT NULL,
  AUTO_ALLOCATE        BOOLEAN      NOT NULL DEFAULT false,
  MIGRATED             BOOLEAN      NOT NULL DEFAULT false,
  MIGRATED_DATE_TIME   TIMESTAMP    NULL,
  CONSTRAINT PRISON_PK PRIMARY KEY (PRISON_ID)
);

COMMENT ON TABLE PRISON_SUPPORTED IS 'Records the prisons have can be supported by this service and which have been migrated';

COMMENT ON COLUMN PRISON_SUPPORTED.PRISON_ID          IS 'Prison ID (NOMIS column is Agency ID)';
COMMENT ON COLUMN PRISON_SUPPORTED.AUTO_ALLOCATE      IS 'Indicates that the prisons supports auto allocation';
COMMENT ON COLUMN PRISON_SUPPORTED.MIGRATED           IS 'Indicates if the prison keyworker data has been migrated';
COMMENT ON COLUMN PRISON_SUPPORTED.MIGRATED_DATE_TIME IS 'Migrated Date and Time';