create table super_bot_record (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    state varchar(25) NOT NULL,
    shareable_token varchar(25) NOT NULL,
    contract_serial_number int NOT NULL,
    contract_id bigint,
    created_at DATETIME,
    accepted_at DATETIME,
    did varchar(255) NOT NULL,
    convo_id varchar(255) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table state_machine (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    state varchar(25) NOT NULL,
    owned_by varchar(25) NOT NULL,
    machine_type varchar(25) NOT NULL,
    machine_version varchar(25) NOT NULL,
    machine_state varchar(25) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;