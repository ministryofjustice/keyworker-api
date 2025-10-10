alter table staff_configuration
    drop column allow_auto_allocation;

alter table staff_configuration_audit
    drop column allow_auto_allocation,
    drop column allow_auto_allocation_modified;

alter table prison_configuration
    drop column capacity;
alter table prison_configuration
    rename column maximum_capacity to capacity;

alter table prison_configuration_audit
    drop column capacity,
    drop column capacity_modified;

alter table prison_configuration_audit
    rename column maximum_capacity to capacity;

alter table prison_configuration_audit
    rename column maximum_capacity_modified to capacity_modified;