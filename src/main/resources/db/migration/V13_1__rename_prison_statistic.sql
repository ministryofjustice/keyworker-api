alter table keyworker_stats
    rename to prison_statistic;
alter table prison_statistic
    rename column keyworker_stats_id to id;
alter table prison_statistic
    alter column id type bigint,
    alter column id set not null;
alter table prison_statistic
    rename column prison_id to prison_code;
alter table prison_statistic
    rename column snapshot_date to statistic_date;
alter table prison_statistic
    rename column total_num_prisoners to prisoner_count;
alter table prison_statistic
    rename column high_complexity_of_need_prisoners to high_complexity_of_need_prisoner_count;
alter table prison_statistic
    rename column num_prisoners_assigned_kw to prisoners_assigned_count;
alter table prison_statistic
    rename column total_num_eligible_prisoners to eligible_prisoner_count;
alter table prison_statistic
    rename column num_active_keyworkers to eligible_staff_count;
alter table prison_statistic
    rename column num_kw_sessions to recorded_session_count;
alter table prison_statistic
    rename column num_kw_entries to recorded_entry_count;
alter table prison_statistic
    rename column recpt_to_alloc_days to reception_to_allocation_days;
alter table prison_statistic
    rename column recpt_to_kw_session_days to reception_to_recorded_event_days;

alter table prison_statistic
    add column policy_code varchar(16) references policy (code);

update prison_statistic
    set policy_code = 'KEY_WORKER'
where policy_code is null;

alter table prison_statistic
    alter column policy_code set not null;

drop index if exists keyworker_stats_idx1;
drop index if exists keyworker_stats_idx2;
alter index if exists keyworker_stats_pk rename to pk_prison_statistic;

create index if not exists idx_prison_statistic_date_prison on prison_statistic (statistic_date, prison_code);