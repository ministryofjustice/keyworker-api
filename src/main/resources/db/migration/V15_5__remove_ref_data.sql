with po_sp as (select rd.id from reference_data rd where rd.domain = 'STAFF_POSITION' and rd.code <> 'PRO')
delete
from reference_data_policy rdp
where rdp.policy_code = 'PERSONAL_OFFICER'
  and rdp.id in (select id from po_sp);