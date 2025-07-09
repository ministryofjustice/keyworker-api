insert into reference_data(domain, code, description, sequence_number)
values ('DEALLOCATION_REASON', 'CHANGE_IN_COMPLEXITY_OF_NEED', 'Change in complexity of need', 10)
on conflict do nothing;