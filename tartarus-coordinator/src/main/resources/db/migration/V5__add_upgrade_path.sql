create table firmware_upgrade_path (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    state varchar(25) NOT NULL,
    from_version varchar(25) NOT NULL,
    to_version varchar(25) NOT NULL,
    created_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_from_version ON firmware_upgrade_path (from_version);

ALTER TABLE bot ADD COLUMN password varchar(64) NOT NULL AFTER public_key;
ALTER TABLE bot ADD COLUMN salt varchar(32) NOT NULL AFTER password;