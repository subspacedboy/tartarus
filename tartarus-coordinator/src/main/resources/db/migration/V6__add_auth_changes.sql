ALTER TABLE lock_session ADD COLUMN mqtt_password varchar(255) DEFAULT NULL AFTER session_token;
ALTER TABLE lock_session ADD COLUMN mqtt_salt varchar(255) DEFAULT NULL AFTER mqtt_password;
ALTER TABLE lock_user_session ADD COLUMN nonce_used varchar(255) DEFAULT NULL AFTER lock_session_id;

create table configuration_setting(
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    lock_session varchar(10) NOT NULL,
    key_name varchar(255) NOT NULL,
    key_value varchar(255) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;