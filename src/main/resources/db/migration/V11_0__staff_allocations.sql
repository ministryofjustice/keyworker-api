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

    deallocated_at_modified      boolean     not null,
    deallocation_reason_modified boolean     not null,
    deallocated_by_modified      boolean     not null,
    is_active_modified           boolean     not null,
    primary key (rev_id, id)
);

create or replace function initialise_allocation_records(keyworker_allocation_id bigint) returns void as
$$
declare
    rev_id              bigint;
    created_at          timestamp;
    created_by          varchar;
    modified_at         timestamp;
    modified_by         varchar;
    deallocated         boolean;
    new_id              uuid;
    separate_deallocate boolean;
begin

    if exists(select 1 from allocation where legacy_id = keyworker_allocation_id) then
        return;
    end if;

    select create_datetime,
           create_user_id,
           modify_datetime,
           modify_user_id,
           not active_flag = 'Y'
    into created_at, created_by, modified_at, modified_by, deallocated
    from offender_key_worker
    where offender_keyworker_id = keyworker_allocation_id;

    new_id := gen_random_uuid();
    insert into allocation(id, prison_code, staff_id, person_identifier, allocated_at, allocation_type, allocated_by,
                           allocation_reason_id, deallocated_at, deallocation_reason_id, deallocated_by, is_active,
                           policy_code, legacy_id)
    select new_id,
           prison_id,
           staff_id,
           offender_no,
           assigned_date_time,
           alloc_type,
           user_id,
           allocation_reason_id,
           expiry_date_time,
           deallocation_reason_id,
           case when deallocated = true then coalesce(modified_by, 'SYS') end,
           active_flag = 'Y',
           'KEY_WORKER',
           keyworker_allocation_id
    from offender_key_worker
    where offender_keyworker_id = keyworker_allocation_id;

    separate_deallocate := case when deallocated = true and created_at <> modified_at then true else false end;

    if separate_deallocate
    then
        rev_id = nextval('audit_revision_id_seq');
        insert into audit_revision(id, timestamp, username, caseload_id, affected_entities)
        values (rev_id, created_at, created_by, null, '{LegacyKeyworkerAllocation}');

        insert into allocation_audit(rev_id, rev_type, id, prison_code, staff_id, policy_code, person_identifier,
                                     allocated_at, allocation_type, allocated_by, allocation_reason_id, deallocated_at,
                                     deallocation_reason_id, deallocated_by, is_active, deallocated_at_modified,
                                     deallocated_by_modified, deallocation_reason_modified, is_active_modified)
        select rev_id,
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
               true
        from allocation
        where id = new_id;

        rev_id = nextval('audit_revision_id_seq');
        insert into audit_revision(id, timestamp, username, caseload_id, affected_entities)
        values (rev_id, modified_at, coalesce(modified_by, 'SYS'), null, '{LegacyKeyworkerAllocation}');

        insert into allocation_audit(rev_id, rev_type, id, prison_code, staff_id, policy_code, person_identifier,
                                     allocated_at, allocation_type, allocated_by, allocation_reason_id, deallocated_at,
                                     deallocation_reason_id, deallocated_by, is_active, deallocated_at_modified,
                                     deallocated_by_modified, deallocation_reason_modified, is_active_modified)
        select rev_id,
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
               true
        from allocation
        where id = new_id;
    else
        rev_id = nextval('audit_revision_id_seq');
        insert into audit_revision(id, timestamp, username, caseload_id, affected_entities)
        values (rev_id, created_at, created_by, null, '{LegacyKeyworkerAllocation}');

        insert into allocation_audit(rev_id, rev_type, id, prison_code, staff_id, policy_code, person_identifier,
                                     allocated_at, allocation_type, allocated_by, allocation_reason_id, deallocated_at,
                                     deallocation_reason_id, deallocated_by, is_active, deallocated_at_modified,
                                     deallocated_by_modified, deallocation_reason_modified, is_active_modified)
        select rev_id,
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
               deallocated_at,
               deallocation_reason_id,
               deallocated_by,
               is_active,
               true,
               true,
               true,
               true
        from allocation
        where id = new_id;
    end if;
end;
$$ language plpgsql;


create or replace function delete_fake_allocations(keyworker_allocation_id bigint) returns void as
$$
declare
    rev_id bigint;
begin
    rev_id = nextval('audit_revision_id_seq');
    insert into audit_revision(id, timestamp, username, caseload_id, affected_entities)
    values (rev_id, current_timestamp, 'SYS', null, '{LegacyKeyworkerAllocation}');

    insert into allocation_audit(rev_id, rev_type, id, prison_code, staff_id, policy_code, person_identifier,
                                 allocated_at, allocation_type, allocated_by, allocation_reason_id, deallocated_at,
                                 deallocation_reason_id, deallocated_by, is_active, deallocated_at_modified,
                                 deallocated_by_modified, deallocation_reason_modified, is_active_modified)
    select rev_id,
           2,
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
           false,
           false,
           false,
           false
    from allocation
    where legacy_id = keyworker_allocation_id;

    delete from allocation where legacy_id = keyworker_allocation_id;
end;
$$ language plpgsql;