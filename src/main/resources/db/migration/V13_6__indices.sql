create index if not exists idx_allocation_audit_id on allocation_audit (id);
create index if not exists idx_case_note_prison_code_created_at on allocation_case_note (prison_code, created_at, type, sub_type);

