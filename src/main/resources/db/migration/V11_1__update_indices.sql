create index if not exists idx_allocation_prison_code on allocation (prison_code, policy_code);
create index if not exists idx_allocation_person_identifier on allocation (person_identifier, policy_code);
create index if not exists idx_active_staff_allocations on allocation (staff_id, prison_code, policy_code) where is_active = true;

create or replace function initialise_allocation_records(keyworker_allocation_id bigint, deallocated_only boolean) returns void as
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

    if deallocated_only = true and _deallocated = false then
        return;
    end if;

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