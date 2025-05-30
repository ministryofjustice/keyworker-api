update reference_data
set code        = 'STAFF_STATUS_CHANGE',
    description = 'Staff member status change'
where domain = 'DEALLOCATION_REASON'
  and code = 'KEYWORKER_STATUS_CHANGE';

update reference_data
set domain = 'STAFF_STATUS'
where domain = 'KEYWORKER_STATUS';

delete
from audit_revision
where id in (select rev_id from keyworker_configuration_audit);
drop table keyworker_configuration_audit;

alter table keyworker_configuration
    drop constraint key_worker_pk;

alter table keyworker_configuration
    rename to staff_configuration;

alter table staff_configuration
    add column policy_code varchar(16) references policy (code);

update staff_configuration
set policy_code = 'KEY_WORKER'
where policy_code is null;

alter table staff_configuration
    alter column policy_code set not null,
    add column id uuid not null primary key default gen_random_uuid();

create unique index staff_configuration_staff_id_policy_code on staff_configuration (staff_id, policy_code);

create table staff_configuration_audit
(
    rev_id                         bigint      not null references audit_revision (id),
    rev_type                       smallint    not null,
    id                             uuid        not null,
    staff_id                       bigint      not null,
    capacity                       int         not null,
    reactivate_on                  date,
    status_id                      bigint      not null,
    allow_auto_allocation          boolean     not null,
    policy_code                    varchar(16) not null,

    capacity_modified              boolean     not null,
    reactivate_on_modified         boolean     not null,
    status_modified                boolean     not null,
    allow_auto_allocation_modified boolean     not null,
    primary key (rev_id, id)
);

create or replace function initialise_staff_config_audit(config_id uuid) returns void as
$$
declare
    rev_id bigint;
begin
    rev_id = nextval('audit_revision_id_seq');
    insert into audit_revision(id, timestamp, username, caseload_id, affected_entities)
    values (rev_id, current_date, 'SYS', null, '{LegacyKeyworkerConfiguration}');

    insert into staff_configuration_audit(rev_id, rev_type, id, staff_id, capacity, reactivate_on, status_id,
                                          allow_auto_allocation, policy_code, capacity_modified, reactivate_on_modified,
                                          status_modified, allow_auto_allocation_modified)
    select rev_id,
           0,
           id,
           staff_id,
           capacity,
           reactivate_on,
           status_id,
           allow_auto_allocation,
           policy_code,
           true,
           true,
           true,
           true
    from staff_configuration
    where id = config_id;
end;
$$ language plpgsql;

select initialise_staff_config_audit(id)
from staff_configuration;

drop function initialise_staff_config_audit(config_id uuid);

create table staff_role
(
    id               uuid        not null primary key,
    policy_code      varchar(16) not null,
    prison_code      varchar(6)  not null,
    staff_id         bigint      not null,
    position_id      bigint      not null,
    schedule_type_id bigint      not null,
    hours_per_week   int         not null,
    from_date        date        not null,
    to_date          date
);

create unique index staff_role_prison_code_staff_id_policy_code on staff_role (prison_code, staff_id, policy_code);

create table staff_role_audit
(
    rev_id                  bigint      not null references audit_revision (id),
    rev_type                smallint    not null,
    id                      uuid        not null,
    policy_code             varchar(16) not null,
    prison_code             varchar(6)  not null,
    staff_id                bigint      not null,
    position_id             bigint      not null,
    schedule_type_id        bigint      not null,
    hours_per_week          int         not null,
    from_date               date        not null,
    to_date                 date,

    position_modified       boolean     not null,
    schedule_type_modified  boolean     not null,
    hours_per_week_modified boolean     not null,
    from_date_modified      boolean     not null,
    to_date_modified        boolean     not null,
    primary key (rev_id, id)
);