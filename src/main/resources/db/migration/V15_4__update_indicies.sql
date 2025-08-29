create index if not exists idx_recorded_event_staff_id_occurred_at_policy on recorded_event (staff_id, policy_code, occurred_at);
create index if not exists idx_recorded_event_person_identifier_occurred_at_policy on recorded_event (person_identifier, policy_code, occurred_at);
drop index if exists idx_recorded_event_staff_id_policy;
drop index if exists idx_recorded_event_person_identifier_policy;