with to_update as (select ar.id, pmdh.migration_date
                   from prison_configuration_audit pca
                            join audit_revision ar on ar.id = pca.rev_id
                            join prison_migration_date_history pmdh on pmdh.prison_code = pca.prison_code
                   where pca.rev_type = 0
                     and username = 'SYS')
update audit_revision ar
set timestamp = migration_date
from to_update
where ar.id = to_update.id;

drop table prison_migration_date_history;

