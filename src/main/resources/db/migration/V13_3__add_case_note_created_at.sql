alter table allocation_case_note
    add column if not exists created_at timestamp;

alter table allocation_case_note_audit
    add column if not exists created_at timestamp;

-- this will be manually done along with the setting of the column but here for documentation reasons
-- truncate table allocation_case_note_audit;
-- delete from audit_revision where affected_entities = '{AllocationCaseNote}';
-- truncate table allocation_case_note;

alter table allocation_case_note
    alter column created_at set not null;

alter table allocation_case_note_audit
    alter column created_at set not null;
