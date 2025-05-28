create table policy
(
    code        varchar(16)  not null primary key,
    description varchar(255) not null
);

insert into policy(code, description)
values ('KEY_WORKER', 'Key worker'),
       ('PERSONAL_OFFICER', 'Personal officer');

alter table prison_supported
    drop constraint prison_pk,
    drop column migrated_date_time;

alter table prison_supported
    rename to prison_configuration;
alter table prison_configuration
    rename prison_id to prison_code;
alter table prison_configuration
    rename auto_allocate to allow_auto_allocation;
alter table prison_configuration
    rename migrated to is_enabled;
alter table prison_configuration
    rename capacity_tier_1 to capacity;
alter table prison_configuration
    rename capacity_tier_2 to maximum_capacity;
alter table prison_configuration
    rename kw_session_freq_weeks to frequency_in_weeks;

alter table prison_configuration
    alter column maximum_capacity set not null;

alter table prison_configuration
    add column policy_code varchar(16) references policy (code);

update prison_configuration
set policy_code = 'KEY_WORKER'
where policy_code is null;

alter table prison_configuration
    alter column policy_code set not null,
    add column id uuid not null primary key default gen_random_uuid();

create unique index prison_configuration_prison_code_policy_code on prison_configuration (prison_code, policy_code);

create table prison_configuration_audit
(
    rev_id                                            bigint      not null references audit_revision (id),
    rev_type                                          smallint    not null,
    id                                                uuid        not null,
    prison_code                                       varchar(6)  not null,
    is_enabled                                        boolean     not null,
    capacity                                          int         not null,
    maximum_capacity                                  int         not null,
    allow_auto_allocation                             boolean     not null,
    frequency_in_weeks                                int         not null,
    has_prisoners_with_high_complexity_needs          boolean     not null,
    policy_code                                       varchar(16) not null,

    is_enabled_modified                               boolean     not null,
    capacity_modified                                 boolean     not null,
    maximum_capacity_modified                         boolean     not null,
    allow_auto_allocation_modified                    boolean     not null,
    frequency_in_weeks_modified                       boolean     not null,
    has_prisoners_with_high_complexity_needs_modified boolean     not null,
    primary key (rev_id, id)
);

create or replace function initialise_prison_config_audit(config_id uuid) returns void as
$$
declare
    rev_id bigint;
begin
    rev_id = nextval('audit_revision_id_seq');
    insert into audit_revision(id, timestamp, username, caseload_id, affected_entities)
    values (rev_id, current_date, 'SYS', null, '{PrisonConfiguration}');

    insert into prison_configuration_audit(rev_id, rev_type, id, prison_code, is_enabled, capacity, maximum_capacity,
                                           allow_auto_allocation, frequency_in_weeks,
                                           has_prisoners_with_high_complexity_needs, policy_code, is_enabled_modified,
                                           capacity_modified, maximum_capacity_modified, allow_auto_allocation_modified,
                                           frequency_in_weeks_modified,
                                           has_prisoners_with_high_complexity_needs_modified)
    select rev_id,
           0,
           id,
           prison_code,
           is_enabled,
           capacity,
           maximum_capacity,
           allow_auto_allocation,
           frequency_in_weeks,
           has_prisoners_with_high_complexity_needs,
           policy_code,
           true,
           true,
           true,
           true,
           true,
           true
    from prison_configuration
    where id = config_id;
end;
$$ language plpgsql;

select initialise_prison_config_audit(id)
from prison_configuration;

drop function initialise_prison_config_audit(config_id uuid);
