DROP TABLE IF EXISTS offender_key_worker;

CREATE TABLE offender_key_worker (
  offender_keyworker_id      VARCHAR(100) PRIMARY KEY NOT NULL,
  offender_book_id           INTEGER NOT NULL,
  officer_id                 INTEGER NOT NULL,
  assigned_datetime          TIMESTAMP NOT NULL);


INSERT INTO offender_key_worker (offender_keyworker_id, offender_book_id, officer_id, assigned_datetime)
VALUES ('e254f8c-c442-4ebe-a82a-e2fc1d1ff78a', 1, 1, '2017-08-12 09:00:00');

INSERT INTO offender_key_worker (offender_keyworker_id, offender_book_id, officer_id, assigned_datetime)
VALUES ('442adb6e-fa58-47f3-9ca2-ed1fecdfe86c', 2, 1,'2018-01-12 10:00:00');
