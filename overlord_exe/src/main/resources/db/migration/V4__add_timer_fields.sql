ALTER TABLE timer_bot_record ADD COLUMN convo_id varchar(64) AFTER did;
ALTER TABLE timer_bot_record ADD COLUMN state varchar(25) NOT NULL AFTER name;

ALTER TABLE timer_bot_record ADD COLUMN min_duration int AFTER time_unit;
ALTER TABLE timer_bot_record ADD COLUMN min_duration_unit varchar(25) AFTER min_duration;
ALTER TABLE timer_bot_record ADD COLUMN max_duration int AFTER min_duration_unit;
ALTER TABLE timer_bot_record ADD COLUMN max_duration_unit varchar(25) AFTER max_duration;

ALTER TABLE timer_bot_record ADD COLUMN shareable_token varchar(25) AFTER is_public;
ALTER TABLE timer_bot_record ADD COLUMN is_random tinyint AFTER is_public;