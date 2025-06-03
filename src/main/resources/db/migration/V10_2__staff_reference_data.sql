update reference_data
set domain = 'STAFF_POSITION'
where domain = 'STAFF_POS';

update reference_data
set domain = 'STAFF_SCHEDULE_TYPE'
where domain = 'SCHEDULE_TYPE';

alter table staff_role
    alter column hours_per_week type decimal;
alter table staff_role
    alter column hours_per_week set not null;

