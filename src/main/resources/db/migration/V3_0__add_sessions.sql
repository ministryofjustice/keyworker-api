create table keyworker_session
(
    id                uuid        not null primary key,
    occurred_at       timestamp   not null,
    person_identifier varchar(7),
    staff_id          bigint      not null,
    staff_username    varchar(64) not null,
    prison_code       varchar(6)  not null,
    created_at        timestamp   not null,
    version           int         not null
);

create index idx_keyworker_session_person_identifier on keyworker_session (person_identifier, occurred_at, prison_code);
create index idx_keyworker_session_staff_id on keyworker_session (staff_id, occurred_at, prison_code);

create table keyworker_entry
(
    id                uuid        not null primary key,
    occurred_at       timestamp   not null,
    person_identifier varchar(7),
    staff_id          bigint      not null,
    staff_username    varchar(64) not null,
    prison_code       varchar(6)  not null,
    created_at        timestamp   not null,
    version           int         not null
);

create index idx_keyworker_entry_person_identifier on keyworker_entry (person_identifier, occurred_at, prison_code);
create index idx_keyworker_entry_staff_id on keyworker_entry (staff_id, occurred_at, prison_code);