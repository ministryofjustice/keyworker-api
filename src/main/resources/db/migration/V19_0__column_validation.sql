alter table prison_statistic
    alter column statistic_date type date;

alter table staff_role
    alter column hours_per_week type numeric;

alter table staff_role_audit
    alter column hours_per_week type numeric;