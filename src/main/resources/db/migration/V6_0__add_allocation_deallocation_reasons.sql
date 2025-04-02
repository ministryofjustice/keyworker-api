with ref_data as (select *
                  from (values ('ALLOCATION_REASON', 'MANUAL', 'Manual', 1),
                               ('ALLOCATION_REASON', 'AUTO', 'Automatic', 2),
                               ('DEALLOCATION_REASON', 'MANUAL', 'Manual', 1),
                               ('DEALLOCATION_REASON', 'OVERRIDE', 'Override', 2),
                               ('DEALLOCATION_REASON', 'RELEASED', 'Released', 3),
                               ('DEALLOCATION_REASON', 'KEYWORKER_STATUS_CHANGE', 'Keyworker Status Changed', 4),
                               ('DEALLOCATION_REASON', 'TRANSFER', 'Transfer', 5),
                               ('DEALLOCATION_REASON', 'MERGED', 'Merged', 6),
                               ('DEALLOCATION_REASON', 'MISSING', 'Missing', 7),
                               ('DEALLOCATION_REASON', 'DUPLICATE', 'Duplicate',
                                8)) as t(domain, code, description, seq))
insert
into reference_data(id, domain, code, description, sequence_number)
select nextval('reference_data_id_seq'), nrd.domain, nrd.code, nrd.description, nrd.seq
from ref_data nrd
where not exists(select 1 from reference_data rd where rd.domain = nrd.domain and rd.code = nrd.code);

alter table offender_key_worker
    add column if not exists allocation_reason_id   bigint references reference_data (id),
    add column if not exists deallocation_reason_id bigint references reference_data (id);


create or replace function update_allocation_reason_to_reference_data() returns void as
$$
begin
    if exists (select 1
               from information_schema.columns
               where table_name = 'offender_key_worker'
                 and column_name = 'alloc_reason') then
        update offender_key_worker okw
        set allocation_reason_id   = (select id
                                      from reference_data rd
                                      where rd.domain = 'ALLOCATION_REASON'
                                        and rd.code = okw.alloc_reason),
            deallocation_reason_id = (select id
                                      from reference_data rd
                                      where rd.domain = 'DEALLOCATION_REASON'
                                        and rd.code = okw.dealloc_reason)
        where allocation_reason_id is null;
    end if;
end ;
$$ language plpgsql;

select update_allocation_reason_to_reference_data();

drop function update_allocation_reason_to_reference_data;

alter table offender_key_worker
    alter column allocation_reason_id set not null,
    drop column if exists alloc_reason,
    drop column if exists dealloc_reason;