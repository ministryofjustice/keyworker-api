-- Hibernate 7 Envers now expects rev_type column to be nullable in the model
-- This migration aligns the database schema with Hibernate 7's expectations

do $$
begin
  if exists (select 1 from information_schema.tables where table_name = 'allocation_audit') then
    alter table allocation_audit alter column rev_type drop not null;
  end if;
  if exists (select 1 from information_schema.tables where table_name = 'allocation_case_note_audit') then
    alter table allocation_case_note_audit alter column rev_type drop not null;
  end if;
  if exists (select 1 from information_schema.tables where table_name = 'keyworker_configuration_audit') then
    alter table keyworker_configuration_audit alter column rev_type drop not null;
  end if;
  if exists (select 1 from information_schema.tables where table_name = 'prison_configuration_audit') then
    alter table prison_configuration_audit alter column rev_type drop not null;
  end if;
  if exists (select 1 from information_schema.tables where table_name = 'staff_configuration_audit') then
    alter table staff_configuration_audit alter column rev_type drop not null;
  end if;
  if exists (select 1 from information_schema.tables where table_name = 'staff_role_audit') then
    alter table staff_role_audit alter column rev_type drop not null;
  end if;
end $$;
