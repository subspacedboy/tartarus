create table bot_map (
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
    PRIMARY KEY (id),
    UNIQUE KEY uk_bot_map_internal_name (internal_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table contract (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    external_contract_name varchar(64),
    bot_name varchar(25) NOT NULL,
    lock_session_token varchar(64),
    shareable_token varchar(64),
    serial_number SMALLINT UNSIGNED NOT NULL,
    next_counter int NOT NULL DEFAULT 1,
    state varchar(25) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id),
    INDEX idx_contract_bot_serial (bot_name, serial_number),
    INDEX idx_contract_bot_state (bot_name, state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table bot_engagement (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    bot_id varchar(25) NOT NULL,
    phase varchar(25) NOT NULL,
    contract_id bigint,
    contract_serial_number int NOT NULL DEFAULT 0,
    shareable_token varchar(64),
    subject_did varchar(255) NOT NULL,
    convo_id varchar(64) NOT NULL,
    keyholder_did varchar(255),
    info_token varchar(25),
    status_token varchar(25),
    data JSON NOT NULL,
    created_at DATETIME,
    accepted_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_engagement_name (name),
    INDEX idx_engagement_bot_serial (bot_id, contract_serial_number),
    INDEX idx_engagement_contract_id (contract_id),
    INDEX idx_engagement_info_token (info_token),
    INDEX idx_engagement_status_token (status_token),
    INDEX idx_engagement_proxy_pair (bot_id, keyholder_did, subject_did, phase)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table bsky_user (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    did varchar(255) NOT NULL,
    handle varchar(255),
    convo_id varchar(64),
    shareable_token varchar(64),
    do_not_contact tinyint default 0,
    PRIMARY KEY (id),
    INDEX idx_bsky_user_did (did),
    INDEX idx_bsky_user_handle (handle),
    INDEX idx_bsky_user_token (shareable_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table bsky_like (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    record_id int,
    record_type varchar(25),
    at_uri varchar(255) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    did varchar(255) NOT NULL,
    handle varchar(255) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_like_did (did),
    INDEX idx_like_record (record_id, record_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table bsky_repost (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    record_id int,
    record_type varchar(25),
    at_uri varchar(255) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    did varchar(255) NOT NULL,
    handle varchar(255) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_repost_did (did),
    INDEX idx_repost_record (record_id, record_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
