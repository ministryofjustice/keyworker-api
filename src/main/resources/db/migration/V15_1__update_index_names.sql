alter table recorded_event
    rename constraint allocation_case_note_pkey to recorded_event_pkey;
alter index if exists idx_case_note_person_identifier rename to idx_recorded_event_person_identifier;
alter table recorded_event_audit
    rename constraint allocation_case_note_audit_rev_id_fkey to recorded_event_audit_rev_id_fkey;
