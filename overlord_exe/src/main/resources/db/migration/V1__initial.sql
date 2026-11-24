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
    emergency_unlocked tinyint(1) NOT NULL DEFAULT 0,
    created_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id),
    INDEX idx_contract_bot_serial (bot_name, serial_number),
    INDEX idx_contract_bot_state (bot_name, state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table user_profile (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    did varchar(255) NOT NULL,
    handle varchar(255),
    display_name varchar(255),
    convo_id varchar(64),
    do_not_contact tinyint(1) NOT NULL DEFAULT 0,
    lists_json JSON,
    preferences_json JSON,
    stats_json JSON,
    last_synced_at DATETIME,
    created_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_profile_did (did),
    INDEX idx_user_profile_handle (handle),
    INDEX idx_user_profile_convo (convo_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table user_shareable_token (
    id bigint NOT NULL AUTO_INCREMENT,
    user_did varchar(255) NOT NULL,
    token varchar(64) NOT NULL,
    token_type varchar(16) NOT NULL,
    label varchar(64),
    is_default tinyint(1) NOT NULL DEFAULT 0,
    last_used_at DATETIME,
    created_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_shareable_token (user_did, token),
    INDEX idx_shareable_token_value (token),
    INDEX idx_shareable_token_did (user_did)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table conversation_message (
    id bigint NOT NULL AUTO_INCREMENT,
    user_did varchar(255) NOT NULL,
    convo_id varchar(64) NOT NULL,
    role varchar(20) NOT NULL,
    content TEXT NOT NULL,
    tool_name varchar(64),
    input_tokens int,
    output_tokens int,
    model_id varchar(255),
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_convo_msg_convo (convo_id, created_at),
    INDEX idx_convo_msg_did (user_did, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table contract_session (
    id bigint NOT NULL AUTO_INCREMENT,
    token varchar(32) NOT NULL,
    bot_id varchar(25) NOT NULL DEFAULT 'overlord',
    phase varchar(25) NOT NULL,
    release_policy varchar(20) NOT NULL DEFAULT 'ALL',
    is_public tinyint(1) NOT NULL DEFAULT 0,
    terms varchar(255),
    contract_id bigint,
    contract_serial_number int NOT NULL DEFAULT 0,
    shareable_token varchar(64),
    subject_did varchar(255) NOT NULL,
    convo_id varchar(64) NOT NULL,
    keyholder_did varchar(255),
    created_at DATETIME,
    accepted_at DATETIME,
    completed_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_token (token),
    INDEX idx_session_bot_serial (bot_id, contract_serial_number),
    INDEX idx_session_contract (contract_id),
    INDEX idx_session_subject (subject_did, phase),
    INDEX idx_session_keyholder (keyholder_did, phase),
    INDEX idx_session_convo (convo_id, phase)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table session_machine (
    id bigint NOT NULL AUTO_INCREMENT,
    session_id bigint NOT NULL,
    machine_type varchar(40) NOT NULL,
    status varchar(25) NOT NULL,
    info_token varchar(32),
    status_token varchar(32),
    state_json JSON NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id),
    INDEX idx_machine_session (session_id),
    INDEX idx_machine_info_token (info_token),
    INDEX idx_machine_status_token (status_token)
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
