alter table if exists allocation_case_note
    rename to recorded_event;
alter table if exists allocation_case_note_audit
    rename to recorded_event_audit;
alter table recorded_event
    add column if not exists policy_code            varchar(16) references policy (code),
    add column if not exists recorded_event_type_id bigint references reference_data (id);
alter table recorded_event_audit
    add column if not exists policy_code            varchar(16) references policy (code),
    add column if not exists recorded_event_type_id bigint;

alter table recorded_event
    rename column type to cn_type;
alter table recorded_event_audit
    rename column type to cn_type;

alter table recorded_event
    rename column sub_type to cn_sub_type;
alter table recorded_event_audit
    rename column sub_type to cn_sub_type;

alter table reference_data_policy
    add column if not exists description_override varchar(128);

insert into reference_data(domain, code, description, sequence_number)
values ('RECORDED_EVENT_TYPE', 'SESSION', 'Session', 1),
       ('RECORDED_EVENT_TYPE', 'ENTRY', 'Entry', 2)
on conflict do nothing;

insert into reference_data_policy(id, policy_code, description_override)
select rd.id, 'KEY_WORKER', case when rd.code = 'SESSION' then 'Key Worker Session' else 'Key Worker Entry' end
from reference_data rd
where rd.domain = 'RECORDED_EVENT_TYPE'
on conflict do nothing;

insert into reference_data_policy(id, policy_code, description_override)
select rd.id, 'PERSONAL_OFFICER', 'Personal Officer Entry'
from reference_data rd
where rd.domain = 'RECORDED_EVENT_TYPE' and rd.code = 'ENTRY'
on conflict do nothing;

create table if not exists case_note_type_recorded_event_type
(
    id                     bigserial   not null primary key,
    cn_type                varchar(32) not null,
    cn_sub_type            varchar(32) not null,
    policy_code            varchar(16) not null,
    recorded_event_type_id bigint      not null
);

create unique index if not exists uq_case_note_type_subtype on case_note_type_recorded_event_type (cn_type, cn_sub_type);

insert into case_note_type_recorded_event_type(cn_type, cn_sub_type, policy_code, recorded_event_type_id)
values ('KA', 'KS', 'KEY_WORKER',
        (select id from reference_data where domain = 'RECORDED_EVENT_TYPE' and code = 'SESSION')),
       ('KA', 'KE', 'KEY_WORKER',
        (select id from reference_data where domain = 'RECORDED_EVENT_TYPE' and code = 'ENTRY')),
       ('REPORT', 'POE', 'PERSONAL_OFFICER',
        (select id from reference_data where domain = 'RECORDED_EVENT_TYPE' and code = 'ENTRY'));

update recorded_event re
set recorded_event_type_id = cntret.recorded_event_type_id
from case_note_type_recorded_event_type cntret
where re.cn_type = cntret.cn_type
  and re.cn_sub_type = cntret.cn_sub_type;

update recorded_event_audit rea
set recorded_event_type_id = cntret.recorded_event_type_id
from case_note_type_recorded_event_type cntret
where rea.cn_type = cntret.cn_type
  and rea.cn_sub_type = cntret.cn_sub_type;

alter table recorded_event
    drop column if exists cn_type,
    drop column if exists cn_sub_type;

alter table recorded_event_audit
    drop column if exists cn_type,
    drop column if exists cn_sub_type;

alter table recorded_event
    alter policy_code set not null;
alter table recorded_event
    alter recorded_event_type_id set not null;