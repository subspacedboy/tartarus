ALTER TABLE lock_session ADD COLUMN current_firmware varchar(50) DEFAULT NULL AFTER validated_firmware;
