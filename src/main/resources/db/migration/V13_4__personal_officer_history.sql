create or replace function initialise_po_record(allocation_id uuid) returns void as
$$
declare
    _rev_id                         bigint;
    _allocated_at                   timestamp;
    _allocated_by                   varchar;
    _deallocation_at                timestamp;
    _deallocated_by                 varchar;
    _deallocated                    boolean;
begin

    select allocated_at,
           allocated_by,
           deallocated_at,
           deallocated_by,
           not is_active
    into _allocated_at, _allocated_by, _deallocation_at, _deallocated_by, _deallocated
    from allocation
    where id = allocation_id;

    _rev_id = nextval('audit_revision_id_seq');
    insert into audit_revision(id, timestamp, username, caseload_id, affected_entities)
    values (_rev_id, _allocated_at, _allocated_by, null, '{Allocation}');

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
    where id = allocation_id;

    if _deallocated
    then
        _rev_id = nextval('audit_revision_id_seq');
        insert into audit_revision(id, timestamp, username, caseload_id, affected_entities)
        values (_rev_id, _deallocation_at, coalesce(_deallocated_by, 'SYS'), null, '{Allocation}');

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
        where id = allocation_id;
    end if;
end;
$$ language plpgsql;