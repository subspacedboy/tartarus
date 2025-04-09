create table bot_map(
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    internal_name varchar(25) NOT NULL,
    external_name varchar(25) NOT NULL,
    coordinator varchar(255) NOT NULL,
    password varchar(25) NOT NULL,
    private_key BLOB NOT NULL,
    public_key BLOB NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table contract(
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    external_contract_name varchar(25),
    bot_name varchar(25),
    lock_session_token varchar(25),
    shareable_token varchar(10),
    serial_number SMALLINT UNSIGNED,
    state varchar(25) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table timer_bot_record (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    contract_id bigint NOT NULL,
    created_at DATETIME,
    ends_at DATETIME,
    completed tinyint,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;