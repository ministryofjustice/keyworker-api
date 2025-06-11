insert into reference_data(domain, code, description, sequence_number)
values ('DEALLOCATION_REASON', 'UNKNOWN', 'Unknown', 9)
on conflict do nothing;

create table if not exists allocation
(
    id                     uuid        not null,
    prison_code            varchar(6)  not null,
    staff_id               bigint      not null,
    person_identifier      varchar(10) not null,
    allocated_at           timestamp   not null,
    allocation_type        char(1)     not null,
    allocated_by           varchar(64) not null,
    allocation_reason_id   bigint      not null references reference_data (id),
    deallocated_at         timestamp,
    deallocation_reason_id bigint references reference_data (id),
    deallocated_by         varchar(64),
    is_active              boolean     not null,
    policy_code            varchar(16) not null references policy (code),
    legacy_id              bigint,

    constraint pk_allocation primary key (id),
    constraint allocation_allocation_type check (allocation_type in ('A', 'M', 'P')),
    constraint ch_allocation_is_active check (not (is_active = true and
                                                   (deallocated_at is not null or deallocated_by is not null or
                                                    deallocation_reason_id is not null))),
    constraint ch_allocation_is_not_active check (not (is_active = false and
                                                       (deallocated_at is null or deallocated_by is null or
                                                        deallocation_reason_id is null)))
);

create unique index if not exists uq_allocation on allocation (person_identifier, policy_code) where is_active = true;
create index if not exists idx_allocation_people_at on allocation (staff_id, person_identifier, allocated_at, policy_code);
create unique index if not exists uq_legacy_allocation on allocation (legacy_id);

create table if not exists allocation_audit
(
    rev_id                       bigint      not null references audit_revision (id),
    rev_type                     smallint    not null,
    id                           uuid        not null,
    prison_code                  varchar(6)  not null,
    staff_id                     bigint      not null,
    person_identifier            varchar(10) not null,
    allocated_at                 timestamp   not null,
    allocation_type              char(1)     not null,
    allocated_by                 varchar(64) not null,
    allocation_reason_id         bigint      not null,
    deallocated_at               timestamp,
    deallocation_reason_id       bigint,
    deallocated_by               varchar(64),
    is_active                    boolean     not null,
    policy_code                  varchar(16) not null,

    person_identifier_modified   boolean     not null,
    deallocated_at_modified      boolean     not null,
    deallocation_reason_modified boolean     not null,
    deallocated_by_modified      boolean     not null,
    is_active_modified           boolean     not null,
    primary key (rev_id, id)
);

create or replace function initialise_allocation_records(keyworker_allocation_id bigint) returns void as
$$
declare
    _rev_id                         bigint;
    _allocated_at                   timestamp;
    _allocated_by                   varchar;
    _deallocation_at                timestamp;
    _deallocated_by                 varchar;
    _deallocated                    boolean;
    _new_id                         uuid;
    _unknown_deallocation_reason_id bigint;
begin

    if exists(select 1 from allocation where legacy_id = keyworker_allocation_id) then
        return;
    end if;

    select assigned_date_time,
           user_id,
           expiry_date_time,
           modify_user_id,
           not active_flag = 'Y'
    into _allocated_at, _allocated_by, _deallocation_at, _deallocated_by, _deallocated
    from offender_key_worker
    where offender_keyworker_id = keyworker_allocation_id;

    select id
    into _unknown_deallocation_reason_id
    from reference_data
    where domain = 'DEALLOCATION_REASON'
      and code = 'UNKNOWN';

    _new_id := gen_random_uuid();
    insert into allocation(id, prison_code, staff_id, person_identifier, allocated_at, allocation_type, allocated_by,
                           allocation_reason_id, deallocated_at, deallocation_reason_id, deallocated_by, is_active,
                           policy_code, legacy_id)
    select _new_id,
           prison_id,
           staff_id,
           offender_no,
           assigned_date_time,
           alloc_type,
           user_id,
           allocation_reason_id,
           expiry_date_time,
           case when _deallocated = true then coalesce(deallocation_reason_id, _unknown_deallocation_reason_id) end,
           case when _deallocated = true then coalesce(_deallocated_by, 'SYS') end,
           active_flag = 'Y',
           'KEY_WORKER',
           keyworker_allocation_id
    from offender_key_worker
    where offender_keyworker_id = keyworker_allocation_id;

    _rev_id = nextval('audit_revision_id_seq');
    insert into audit_revision(id, timestamp, username, caseload_id, affected_entities)
    values (_rev_id, _allocated_at, _allocated_by, null, '{LegacyKeyworkerAllocation}');

    insert into allocation_audit(rev_id, rev_type, id, prison_code, staff_id, policy_code, person_identifier,
                                 allocated_at, allocation_type, allocated_by, allocation_reason_id, deallocated_at,
                                 deallocation_reason_id, deallocated_by, is_active, person_identifier_modified,
                                 deallocated_at_modified, deallocated_by_modified, deallocation_reason_modified,
                                 is_active_modified)
    select _rev_id,
           0,
           id,
           prison_code,
           staff_id,
           policy_code,
           person_identifier,
           allocated_at,
           allocation_type,
           allocated_by,
           allocation_reason_id,
           null,
           null,
           null,
           true,
           true,
           true,
           true,
           true,
           true
    from allocation
    where id = _new_id;

    if _deallocated
    then
        _rev_id = nextval('audit_revision_id_seq');
        insert into audit_revision(id, timestamp, username, caseload_id, affected_entities)
        values (_rev_id, _deallocation_at, coalesce(_deallocated_by, 'SYS'), null, '{LegacyKeyworkerAllocation}');

        insert into allocation_audit(rev_id, rev_type, id, prison_code, staff_id, policy_code, person_identifier,
                                     allocated_at, allocation_type, allocated_by, allocation_reason_id, deallocated_at,
                                     deallocation_reason_id, deallocated_by, is_active, person_identifier_modified,
                                     deallocated_at_modified, deallocated_by_modified, deallocation_reason_modified,
                                     is_active_modified)
        select _rev_id,
               1,
               id,
               prison_code,
               staff_id,
               policy_code,
               person_identifier,
               allocated_at,
               allocation_type,
               allocated_by,
               allocation_reason_id,
               deallocated_at,
               deallocation_reason_id,
               deallocated_by,
               is_active,
               true,
               true,
               true,
               true,
               true
        from allocation
        where id = _new_id;
    end if;
end;
$$ language plpgsql;
