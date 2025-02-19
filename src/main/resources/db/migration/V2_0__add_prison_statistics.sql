create table prison_statistic
(
    id                                   uuid       not null primary key,
    prison_code                          varchar(6) not null,
    date                                 date       not null,
    total_prisoners                      int        not null,
    eligible_prisoners                   int        not null,
    assigned_keyworker                   int        not null,
    active_keyworkers                    int        not null,
    keyworker_sessions                   int        not null,
    keyworker_entries                    int        not null,
    average_reception_to_allocation_days int,
    average_reception_to_session_days    int
);

create unique index idx_prison_code_date on prison_statistic (prison_code, date);