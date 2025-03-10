create table if not exists reference_data
(
    id              bigserial    not null primary key,
    domain          varchar(32)  not null,
    code            varchar(32)  not null,
    description     varchar(128) not null,
    sequence_number int          not null,
    short_code      varchar(3)
);

create unique index if not exists unq_reference_data_domain_code on reference_data (domain, code);
create unique index if not exists unq_reference_data_domain_sequence on reference_data (domain, sequence_number);

with status_rd as (select *
                   from (values ('ACTIVE', 'Active', 10, 'ACT'),
                                ('UNAVAILABLE_ANNUAL_LEAVE', 'Unavailable - annual leave', 20, 'UAL'),
                                ('UNAVAILABLE_LONG_TERM_ABSENCE', 'Unavailable - long term absence', 30, 'ULT'),
                                ('UNAVAILABLE_NO_PRISONER_CONTACT', 'Unavailable - no prisoner contact', 40, 'UNP'),
                                ('INACTIVE', 'Inactive', 50, 'INA')) as t(code, description, seq, short))
insert
into reference_data(id, domain, code, description, sequence_number, short_code)
select nextval('reference_data_id_seq'), 'KEYWORKER_STATUS', code, description, seq, short
from status_rd srd
where not exists(select 1 from reference_data rd where domain = 'KEYWORKER_STATUS' and rd.code = srd.code);

alter table key_worker
    add column if not exists status_id bigint references reference_data (id);

update key_worker kw
set status_id = (select id
                 from reference_data rd
                 where rd.domain = 'KEYWORKER_STATUS'
                   and rd.short_code = kw.status)
where status_id is null;

alter table key_worker
    alter column status_id set not null,
    drop column status;

alter table reference_data
    drop column short_code;