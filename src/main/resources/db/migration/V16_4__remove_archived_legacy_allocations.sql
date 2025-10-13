drop table if exists zz_archived_legacy_keyworker_allocation;
alter table prison_statistic
    drop column if exists recorded_session_count,
    drop column if exists recorded_entry_count;