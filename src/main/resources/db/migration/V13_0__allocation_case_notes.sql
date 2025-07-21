drop table if exists keyworker_entry;
drop table if exists keyworker_session;

create table if not exists allocation_case_note
(
    id                uuid        not null primary key,
    prison_code       varchar(6)  not null,
    person_identifier varchar(7)  not null,
    staff_id          bigint      not null,
    username          varchar(64) not null,
    type              varchar(12) not null,
    sub_type          varchar(12) not null,
    occurred_at       timestamp   not null,
    version           int         not null
);

create index if not exists idx_case_note_person_identifier on allocation_case_note (person_identifier);

create table if not exists allocation_case_note_audit
(
    rev_id            bigint      not null references audit_revision (id),
    rev_type          smallint    not null,
    id                uuid        not null,
    prison_code       varchar(6)  not null,
    person_identifier varchar(7)  not null,
    staff_id          bigint      not null,
    username          varchar(64) not null,
    type              varchar(12) not null,
    sub_type          varchar(12) not null,
    occurred_at       timestamp   not null
)