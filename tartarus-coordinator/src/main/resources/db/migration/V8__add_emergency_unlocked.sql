ALTER TABLE lock_session ADD COLUMN emergency_unlocked tinyint DEFAULT 0 AFTER is_locked;
