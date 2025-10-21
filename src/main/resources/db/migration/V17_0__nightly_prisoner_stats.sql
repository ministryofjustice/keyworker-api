alter sequence if exists keyworker_stats_keyworker_stats_id_seq rename to prison_statistic_id_seq;

create table prisoner_statistic
(
    id                          bigserial   not null,
    prison_statistic_id         bigint      not null,
    person_identifier           varchar(7)  not null,
    cell_location               varchar(64),
    allocation_eligibility_date date,
    policy_code                 varchar(16) not null,

    constraint pk_prisoner_statistic primary key (id),
    constraint fk_prisoner_statistic_id foreign key (prison_statistic_id) references prison_statistic (id),
    constraint uq_prisoner_statistic_person_identifier unique (prison_statistic_id, person_identifier),
    constraint fk_prisoner_statistic_policy_code foreign key (policy_code) references policy (code)
);