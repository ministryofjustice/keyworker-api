insert into reference_data(domain, code, description, sequence_number)
values ('DEALLOCATION_REASON', 'MIGRATION', 'Deallocated during DPS rollout', 13)
on conflict do nothing;

insert into reference_data_policy(id, policy_code)
select rd.id, 'PERSONAL_OFFICER'
from reference_data rd
where rd.domain = 'DEALLOCATION_REASON'
  and rd.code in ('MIGRATION')
on conflict do nothing;