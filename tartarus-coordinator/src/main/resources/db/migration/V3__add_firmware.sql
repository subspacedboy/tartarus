create table firmware (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    state varchar(25) NOT NULL,
    image MEDIUMBLOB NOT NULL,
    public_key BLOB NOT NULL,
    signature varchar(255) NOT NULL,
    digest varchar(255) NOT NULL,
    version varchar(50) NOT NULL,
    created_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

alter table lock_session add column validated_firmware tinyint NOT NULL default 0 after available_for_contract;
alter table lock_session add column last_validated DATETIME default null after validated_firmware;

create table admin_session(
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    public_key varchar(255) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;