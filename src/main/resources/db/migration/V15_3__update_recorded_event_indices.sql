create index if not exists idx_recorded_event_person_identifier_policy on recorded_event (person_identifier, policy_code);
create index if not exists idx_recorded_event_staff_id_policy on recorded_event (staff_id, policy_code);
create index if not exists idx_recorded_event_type_id_policy on recorded_event (type_id, policy_code);
create index if not exists idx_recorded_event_prison_code_occurred_at_policy on recorded_event (prison_code, occurred_at, policy_code);
create index if not exists idx_recorded_event_prison_code_created_at_policy on recorded_event (prison_code, created_at, policy_code);

drop index if exists idx_recorded_event_person_identifier;
drop index if exists idx_recorded_event_type_id;

create index if not exists idx_allocation_staff_id_allocated_at_deallocated_at on allocation(staff_id, allocated_at, deallocated_at, policy_code);
create index if not exists idx_allocation_person_identifier_prison_code_policy on allocation(person_identifier, prison_code, policy_code);
