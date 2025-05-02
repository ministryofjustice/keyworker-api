alter table keyworker_stats
    add if not exists high_complexity_of_need_prisoners int;

update keyworker_stats
set high_complexity_of_need_prisoners = total_num_prisoners - total_num_eligible_prisoners
where high_complexity_of_need_prisoners is null;

alter table keyworker_stats
    alter high_complexity_of_need_prisoners set not null;

