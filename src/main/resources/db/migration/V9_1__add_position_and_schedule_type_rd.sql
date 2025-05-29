with ref_data as (select *
                  from (values ('SCHEDULE_TYPE', 'FT', 'Full Time', 10),
                               ('SCHEDULE_TYPE', 'PT', 'Part Time', 20),
                               ('SCHEDULE_TYPE', 'SESS', 'Sessional', 30),
                               ('SCHEDULE_TYPE', 'VOL', 'Volunteer', 40),
                               ('STAFF_POS', 'AO', 'Admin Officer', 10),
                               ('STAFF_POS', 'AA', 'Administrative Assistant', 20),
                               ('STAFF_POS', 'CHAP', 'Chaplain', 30),
                               ('STAFF_POS', 'CON', 'Contractor', 40),
                               ('STAFF_POS', 'EO', 'Executive Officer', 50),
                               ('STAFF_POS', 'FP', 'Fee Paid', 60),
                               ('STAFF_POS', 'IG4', 'Industrial Grade 4', 70),
                               ('STAFF_POS', 'IG5', 'Industrial Grade 5', 80),
                               ('STAFF_POS', 'MGRD', 'Manager D', 90),
                               ('STAFF_POS', 'MGRE', 'Manager E', 100),
                               ('STAFF_POS', 'MGRF', 'Manager F', 110),
                               ('STAFF_POS', 'MGRG', 'Manager G', 120),
                               ('STAFF_POS', 'OSG', 'Operational Support Grade', 130),
                               ('STAFF_POS', 'PS', 'Personal Secretary', 140),
                               ('STAFF_POS', 'PPO', 'Principal Prison Officer', 150),
                               ('STAFF_POS', 'PRO', 'Prison Officer', 160),
                               ('STAFF_POS', 'PO', 'Probation Officer', 170),
                               ('STAFF_POS', 'PTO', 'Professional and Technology Officer', 180),
                               ('STAFF_POS', 'PSYA', 'Psychological Assistant', 190),
                               ('STAFF_POS', 'PSY', 'Psychologist', 200),
                               ('STAFF_POS', 'PYST', 'Psychologist Trainee', 210),
                               ('STAFF_POS', 'SECMGR', 'Security Manager', 220),
                               ('STAFF_POS', 'SCS1', 'Senior Civil Servant Pay Band 1', 230),
                               ('STAFF_POS', 'SCS2', 'Senior Civil Servant Pay Band 2', 240),
                               ('STAFF_POS', 'SCS3', 'Senior Civil Servant Pay Band 3', 250),
                               ('STAFF_POS', 'SMGRA', 'Senior Manager A', 260),
                               ('STAFF_POS', 'SMGRB', 'Senior Manager B', 270),
                               ('STAFF_POS', 'SMGRC', 'Senior Manager C', 280),
                               ('STAFF_POS', 'SMGRD', 'Senior Manager D', 290),
                               ('STAFF_POS', 'SPSEC', 'Senior Personal Secretary', 300),
                               ('STAFF_POS', 'SUP1', 'Support Grade Band 1', 310),
                               ('STAFF_POS', 'SUP2', 'Support Grade Band 2', 320),
                               ('STAFF_POS', 'TECH1', 'Technical Grade 1', 330),
                               ('STAFF_POS', 'TYP', 'Typist', 340)) as t(domain, code, description, seq))
insert
into reference_data(id, domain, code, description, sequence_number)
select nextval('reference_data_id_seq'), nrd.domain, nrd.code, nrd.description, nrd.seq
from ref_data nrd
where not exists(select 1 from reference_data rd where rd.domain = nrd.domain and rd.code = nrd.code);



































