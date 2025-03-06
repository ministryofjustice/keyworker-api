alter table keyworker_session
    add column text_length     int not null default 0,
    add column amendment_count int not null default 0;

alter table keyworker_entry
    add column text_length     int not null default 0,
    add column amendment_count int not null default 0;