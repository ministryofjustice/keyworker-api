insert into reference_data(domain, code, description, sequence_number)
values ('DEALLOCATION_REASON', 'NO_LONGER_IN_PRISON', 'No longer in the prison', 11),
       ('DEALLOCATION_REASON', 'PRISON_USES_KEY_WORK', 'Prison now uses key worker', 12)
on conflict do nothing;

insert into reference_data_policy(id, policy_code)
select rd.id, 'KEY_WORKER'
from reference_data rd
where rd.domain = 'DEALLOCATION_REASON'
  and rd.code in ('NO_LONGER_IN_PRISON')
on conflict do nothing;

insert into reference_data_policy(id, policy_code)
select rd.id, 'PERSONAL_OFFICER'
from reference_data rd
where rd.domain = 'DEALLOCATION_REASON'
  and rd.code in ('NO_LONGER_IN_PRISON', 'PRISON_USES_KEY_WORK')
on conflict do nothing;