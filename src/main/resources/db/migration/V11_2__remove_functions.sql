drop function if exists initialise_allocation_records(keyworker_allocation_id bigint);
drop function if exists initialise_allocation_records(keyworker_allocation_id bigint, deallocated_only boolean);

alter table allocation
    drop column legacy_id;

alter table offender_key_worker
    rename to zz_archived_legacy_keyworker_allocation;