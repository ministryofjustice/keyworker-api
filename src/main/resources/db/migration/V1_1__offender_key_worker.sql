DROP TABLE IF EXISTS offender_key_worker;

CREATE TABLE offender_key_worker (
  offender_keyworker_id      BIGINT PRIMARY KEY NOT NULL IDENTITY,
  offender_book_id           BIGINT NOT NULL,
  staff_username             VARCHAR(35) NOT NULL,
  assigned_datetime          TIMESTAMP NOT NULL);


INSERT INTO offender_key_worker (offender_book_id, staff_username, assigned_datetime)
VALUES (-1, 'ITAG_USER', '2017-08-12 09:00:00');

INSERT INTO offender_key_worker (offender_book_id, staff_username, assigned_datetime)
VALUES (-2, 'ITAG_USER','2018-01-12 10:00:00');
