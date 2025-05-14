create table if not exists audit_revision
(
    id                bigserial   not null primary key,
    timestamp         timestamp   not null,
    username          varchar(32) not null,
    caseload_id       varchar(10),
    affected_entities varchar[]   not null
);

alter table if exists key_worker
    rename to keyworker_configuration;
alter table keyworker_configuration
    add column if not exists auto_allocation boolean,
    add column if not exists reactivate_on   date;

update keyworker_configuration
set auto_allocation = case when auto_allocation_flag = 'Y' then true else false end,
    reactivate_on   = active_date
where auto_allocation is null
   or (reactivate_on is null and active_date is not null);

alter table keyworker_configuration
    alter auto_allocation set not null;

create table keyworker_configuration_audit
(
    rev_id                   bigint   not null references audit_revision (id),
    rev_type                 smallint not null,
    staff_id                 bigint   not null,
    capacity                 int      not null,
    reactivate_on            date,
    status_id                bigint   not null,
    auto_allocation          boolean  not null,

    capacity_modified        boolean  not null,
    reactivate_on_modified   boolean  not null,
    status_modified          boolean  not null,
    auto_allocation_modified boolean  not null,
    primary key (rev_id, staff_id)
);

alter table keyworker_configuration
    drop column auto_allocation_flag,
    drop column active_date;