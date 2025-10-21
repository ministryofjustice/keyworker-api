alter table allocation
    add column version int not null default 0;

alter table prison_configuration
    add column version int not null default 0;

alter table staff_configuration
    add column version int not null default 0;

alter table staff_role
    add column version int not null default 0;
