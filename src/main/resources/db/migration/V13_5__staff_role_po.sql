create or replace function initialise_staff_role_record(staff_role_id uuid) returns void as
$$
declare
    _rev_id       bigint;
    _allocated_at timestamp;
    _allocated_by varchar;
begin

    select from_date, 'SYS'
    into _allocated_at, _allocated_by
    from staff_role
    where id = staff_role_id;

    _rev_id = nextval('audit_revision_id_seq');
    insert into audit_revision(id, timestamp, username, caseload_id, affected_entities)
    values (_rev_id, _allocated_at, _allocated_by, null, '{StaffRole}');

    insert into staff_role_audit(rev_id, rev_type, id, policy_code, prison_code, staff_id, position_id,
                                 schedule_type_id, hours_per_week, from_date, to_date, position_modified,
                                 schedule_type_modified, hours_per_week_modified, from_date_modified, to_date_modified)
    select _rev_id,
           0,
           id,
           policy_code,
           prison_code,
           staff_id,
           position_id,
           schedule_type_id,
           hours_per_week,
           from_date,
           to_date,
           true,
           true,
           true,
           true,
           true
    from staff_role
    where id = staff_role_id;
end;
$$ language plpgsql;