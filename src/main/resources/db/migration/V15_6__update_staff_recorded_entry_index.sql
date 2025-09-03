create index if not exists idx_recorded_event_staff_id_occurred_at_policy_prison_code on recorded_event (staff_id, policy_code, occurred_at, prison_code);
create index if not exists idx_allocation_with_and_at on allocation(staff_id, person_identifier, allocated_at, policy_code, prison_code, deallocated_at);
drop index if exists idx_recorded_event_staff_id_occurred_at_policy;
drop index if exists idx_allocation_people_at;