CREATE TABLE qrtz_blob_triggers (
  trigger_name  VARCHAR(80)  NOT NULL,
  trigger_group VARCHAR(80)  NOT NULL,
  blob_data     TEXT,
  sched_name    VARCHAR(120) NOT NULL
);

CREATE TABLE qrtz_calendars (
  calendar_name VARCHAR(80)  NOT NULL,
  calendar      TEXT         NOT NULL,
  sched_name    VARCHAR(120) NOT NULL
);

CREATE TABLE qrtz_cron_triggers (
  trigger_name    VARCHAR(80)  NOT NULL,
  trigger_group   VARCHAR(80)  NOT NULL,
  cron_expression VARCHAR(80)  NOT NULL,
  time_zone_id    VARCHAR(80),
  sched_name      VARCHAR(120) NOT NULL
);

CREATE TABLE qrtz_fired_triggers (
  entry_id          VARCHAR(95)  NOT NULL,
  trigger_name      VARCHAR(80)  NOT NULL,
  trigger_group     VARCHAR(80)  NOT NULL,
  instance_name     VARCHAR(80)  NOT NULL,
  fired_time        BIGINT       NOT NULL,
  priority          INTEGER      NOT NULL,
  state             VARCHAR(16)  NOT NULL,
  job_name          VARCHAR(80),
  job_group         VARCHAR(80),
  is_nonconcurrent  BOOLEAN,
  is_update_data    BOOLEAN,
  sched_name        VARCHAR(120) NOT NULL,
  sched_time        BIGINT       NOT NULL,
  requests_recovery BOOLEAN
);

CREATE TABLE qrtz_job_details (
  job_name          VARCHAR(128) NOT NULL,
  job_group         VARCHAR(80)  NOT NULL,
  description       VARCHAR(120),
  job_class_name    VARCHAR(200) NOT NULL,
  is_durable        BOOLEAN,
  is_nonconcurrent  BOOLEAN,
  is_update_data    BOOLEAN,
  sched_name        VARCHAR(120) NOT NULL,
  requests_recovery BOOLEAN,
  job_data          BYTEA
);

CREATE TABLE qrtz_locks (
  lock_name  VARCHAR(40)  NOT NULL,
  sched_name VARCHAR(120) NOT NULL
);


CREATE TABLE qrtz_paused_trigger_grps (
  trigger_group VARCHAR(80)  NOT NULL,
  sched_name    VARCHAR(120) NOT NULL
);

CREATE TABLE qrtz_scheduler_state (
  instance_name     VARCHAR(200) NOT NULL,
  last_checkin_time BIGINT,
  checkin_interval  BIGINT,
  sched_name        VARCHAR(120) NOT NULL
);

CREATE TABLE qrtz_simple_triggers (
  trigger_name    VARCHAR(80)  NOT NULL,
  trigger_group   VARCHAR(80)  NOT NULL,
  repeat_count    BIGINT       NOT NULL,
  repeat_interval BIGINT       NOT NULL,
  times_triggered BIGINT       NOT NULL,
  sched_name      VARCHAR(120) NOT NULL
);

CREATE TABLE qrtz_simprop_triggers (
  sched_name    VARCHAR(120) NOT NULL,
  trigger_name  VARCHAR(200) NOT NULL,
  trigger_group VARCHAR(200) NOT NULL,
  str_prop_1    VARCHAR(512),
  str_prop_2    VARCHAR(512),
  str_prop_3    VARCHAR(512),
  int_prop_1    INTEGER,
  int_prop_2    INTEGER,
  long_prop_1   BIGINT,
  long_prop_2   BIGINT,
  dec_prop_1    NUMERIC(13, 4),
  dec_prop_2    NUMERIC(13, 4),
  bool_prop_1   BOOLEAN,
  bool_prop_2   BOOLEAN
);

CREATE TABLE qrtz_triggers (
  trigger_name   VARCHAR(80)  NOT NULL,
  trigger_group  VARCHAR(80)  NOT NULL,
  job_name       VARCHAR(80)  NOT NULL,
  job_group      VARCHAR(80)  NOT NULL,
  description    VARCHAR(120),
  next_fire_time BIGINT,
  prev_fire_time BIGINT,
  priority       INTEGER,
  trigger_state  VARCHAR(16)  NOT NULL,
  trigger_type   VARCHAR(8)   NOT NULL,
  start_time     BIGINT       NOT NULL,
  end_time       BIGINT,
  calendar_name  VARCHAR(80),
  misfire_instr  SMALLINT,
  job_data       BYTEA,
  sched_name     VARCHAR(120) NOT NULL
);


ALTER TABLE ONLY qrtz_blob_triggers
  ADD CONSTRAINT qrtz_blob_triggers_pkey PRIMARY KEY (sched_name, trigger_name, trigger_group);

ALTER TABLE ONLY qrtz_calendars
  ADD CONSTRAINT qrtz_calendars_pkey PRIMARY KEY (sched_name, calendar_name);

ALTER TABLE ONLY qrtz_cron_triggers
  ADD CONSTRAINT qrtz_cron_triggers_pkey PRIMARY KEY (sched_name, trigger_name, trigger_group);

ALTER TABLE ONLY qrtz_fired_triggers
  ADD CONSTRAINT qrtz_fired_triggers_pkey PRIMARY KEY (sched_name, entry_id);

ALTER TABLE ONLY qrtz_job_details
  ADD CONSTRAINT qrtz_job_details_pkey PRIMARY KEY (sched_name, job_name, job_group);

ALTER TABLE ONLY qrtz_locks
  ADD CONSTRAINT qrtz_locks_pkey PRIMARY KEY (sched_name, lock_name);

ALTER TABLE ONLY qrtz_paused_trigger_grps
  ADD CONSTRAINT qrtz_paused_trigger_grps_pkey PRIMARY KEY (sched_name, trigger_group);

ALTER TABLE ONLY qrtz_scheduler_state
  ADD CONSTRAINT qrtz_scheduler_state_pkey PRIMARY KEY (sched_name, instance_name);

ALTER TABLE ONLY qrtz_simple_triggers
  ADD CONSTRAINT qrtz_simple_triggers_pkey PRIMARY KEY (sched_name, trigger_name, trigger_group);

ALTER TABLE ONLY qrtz_simprop_triggers
  ADD CONSTRAINT qrtz_simprop_triggers_pkey PRIMARY KEY (sched_name, trigger_name, trigger_group);

ALTER TABLE ONLY qrtz_triggers
  ADD CONSTRAINT qrtz_triggers_pkey PRIMARY KEY (sched_name, trigger_name, trigger_group);

CREATE INDEX fki_qrtz_simple_triggers_job_details_name_group
  ON qrtz_triggers USING BTREE (job_name, job_group);

CREATE INDEX fki_qrtz_simple_triggers_qrtz_triggers
  ON qrtz_simple_triggers USING BTREE (trigger_name, trigger_group);

CREATE INDEX idx_qrtz_ft_j_g
  ON qrtz_fired_triggers USING BTREE (sched_name, job_name, job_group);

CREATE INDEX idx_qrtz_ft_jg
  ON qrtz_fired_triggers USING BTREE (sched_name, job_group);

CREATE INDEX idx_qrtz_ft_t_g
  ON qrtz_fired_triggers USING BTREE (sched_name, trigger_name, trigger_group);

CREATE INDEX idx_qrtz_ft_tg
  ON qrtz_fired_triggers USING BTREE (sched_name, trigger_group);

CREATE INDEX idx_qrtz_ft_trig_inst_name
  ON qrtz_fired_triggers USING BTREE (sched_name, instance_name);

CREATE INDEX idx_qrtz_j_grp
  ON qrtz_job_details USING BTREE (sched_name, job_group);

CREATE INDEX idx_qrtz_t_c
  ON qrtz_triggers USING BTREE (sched_name, calendar_name);

CREATE INDEX idx_qrtz_t_g
  ON qrtz_triggers USING BTREE (sched_name, trigger_group);

CREATE INDEX idx_qrtz_t_j
  ON qrtz_triggers USING BTREE (sched_name, job_name, job_group);

CREATE INDEX idx_qrtz_t_jg
  ON qrtz_triggers USING BTREE (sched_name, job_group);

CREATE INDEX idx_qrtz_t_n_g_state
  ON qrtz_triggers USING BTREE (sched_name, trigger_group, trigger_state);

CREATE INDEX idx_qrtz_t_n_state
  ON qrtz_triggers USING BTREE (sched_name, trigger_name, trigger_group, trigger_state);

CREATE INDEX idx_qrtz_t_next_fire_time
  ON qrtz_triggers USING BTREE (sched_name, next_fire_time);

CREATE INDEX idx_qrtz_t_nft_misfire
  ON qrtz_triggers USING BTREE (sched_name, misfire_instr, next_fire_time);

CREATE INDEX idx_qrtz_t_nft_st
  ON qrtz_triggers USING BTREE (sched_name, trigger_state, next_fire_time);

CREATE INDEX idx_qrtz_t_nft_st_misfire
  ON qrtz_triggers USING BTREE (sched_name, misfire_instr, next_fire_time, trigger_state);

CREATE INDEX idx_qrtz_t_nft_st_misfire_grp
  ON qrtz_triggers USING BTREE (sched_name, misfire_instr, next_fire_time, trigger_group, trigger_state);

CREATE INDEX idx_qrtz_t_state
  ON qrtz_triggers USING BTREE (sched_name, trigger_state);

ALTER TABLE ONLY qrtz_blob_triggers
  ADD CONSTRAINT qrtz_blob_triggers_sched_name_fkey FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES qrtz_triggers (sched_name, trigger_name, trigger_group);

ALTER TABLE ONLY qrtz_cron_triggers
  ADD CONSTRAINT qrtz_cron_triggers_sched_name_fkey FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES qrtz_triggers (sched_name, trigger_name, trigger_group);

ALTER TABLE ONLY qrtz_simple_triggers
  ADD CONSTRAINT qrtz_simple_triggers_sched_name_fkey FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES qrtz_triggers (sched_name, trigger_name, trigger_group);

ALTER TABLE ONLY qrtz_simprop_triggers
  ADD CONSTRAINT qrtz_simprop_triggers_sched_name_fkey FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES qrtz_triggers (sched_name, trigger_name, trigger_group);

ALTER TABLE ONLY qrtz_triggers
  ADD CONSTRAINT qrtz_triggers_sched_name_fkey FOREIGN KEY (sched_name, job_name, job_group) REFERENCES qrtz_job_details (sched_name, job_name, job_group);
