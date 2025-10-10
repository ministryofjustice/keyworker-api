delete
from allocation
where allocation_type = 'P';

alter table allocation
    drop constraint allocation_allocation_type;

alter table allocation
    add constraint allocation_allocation_type check (allocation_type in ('A', 'M'));