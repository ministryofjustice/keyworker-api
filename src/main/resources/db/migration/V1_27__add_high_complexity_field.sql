alter table prison_supported
    add column if not exists has_prisoners_with_high_complexity_needs boolean not null default false;

with prison_ids as (select * from unnest('{AGI,BZI,DHI,DWI,ESI,EWI,FHI,LNI,NHI,PFI,SDI,STI}'::text[]) as prison_code)
insert
into prison_supported(prison_id, migrated, capacity_tier_1, capacity_tier_2, kw_session_freq_weeks,
                      has_prisoners_with_high_complexity_needs)
select pi.prison_code, false, 1, 9, 1, false
from prison_ids pi
where not exists(select 1 from prison_supported where prison_id = pi.prison_code);


update prison_supported
set has_prisoners_with_high_complexity_needs = true
where prison_id in ('AGI', 'BZI', 'DHI', 'DWI', 'ESI', 'EWI', 'FHI', 'LNI', 'NHI', 'PFI', 'SDI', 'STI');

