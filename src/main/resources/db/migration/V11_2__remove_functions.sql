drop function if exists initialise_allocation_records(keyworker_allocation_id bigint);
drop function if exists initialise_allocation_records(keyworker_allocation_id bigint, deallocated_only boolean);

alter table allocation
    drop column legacy_id;

