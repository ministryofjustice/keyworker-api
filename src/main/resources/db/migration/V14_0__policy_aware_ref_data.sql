create table reference_data_policy
(
    id          bigint      not null references reference_data (id),
    policy_code varchar(16) not null references policy (code)
);

insert into reference_data_policy(id, policy_code)
select distinct rd.id, p.code
from reference_data rd
         cross join policy p;