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
    is_public tinyint NOT NULL,
    did varchar(255) NOT NULL,
    time_amount int NOT NULL,
    time_unit varchar(25) NOT NULL,
    contract_serial_number int NOT NULL,
    contract_id bigint,
    created_at DATETIME,
    accepted_at DATETIME,
    ends_at DATETIME,
    completed tinyint,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE timer_bot_record ADD COLUMN convo_id varchar(64) AFTER did;
ALTER TABLE timer_bot_record ADD COLUMN state varchar(25) NOT NULL AFTER name;

ALTER TABLE timer_bot_record ADD COLUMN min_duration int AFTER time_unit;
ALTER TABLE timer_bot_record ADD COLUMN min_duration_unit varchar(25) AFTER min_duration;
ALTER TABLE timer_bot_record ADD COLUMN max_duration int AFTER min_duration_unit;
ALTER TABLE timer_bot_record ADD COLUMN max_duration_unit varchar(25) AFTER max_duration;

ALTER TABLE timer_bot_record ADD COLUMN shareable_token varchar(25) AFTER is_public;
ALTER TABLE timer_bot_record ADD COLUMN is_random tinyint AFTER is_public;

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
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_did ON bsky_like(did);
CREATE INDEX idx_record ON bsky_like(record_id, record_type);

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
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_did ON bsky_repost(did);
CREATE INDEX idx_record ON bsky_repost(record_id, record_type);

create table super_bot_record (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    state varchar(25) NOT NULL,
    shareable_token varchar(25) NOT NULL,
    contract_serial_number int NOT NULL,
    contract_id bigint,
    created_at DATETIME,
    accepted_at DATETIME,
    bsky_user_id bigint,
    did varchar(255) NOT NULL,
    convo_id varchar(255) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table state_machine (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    state varchar(25) NOT NULL,
    owned_by varchar(25) NOT NULL,
    machine_type varchar(255) NOT NULL,
    machine_version varchar(25) NOT NULL,
    machine_state varchar(25) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    info_resolve_method varchar(25) NOT NULL,
    bsky_user_id bigint,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table bsky_likes_sm_context (
    id bigint NOT NULL AUTO_INCREMENT,
    state_machine_id bigint NOT NULL,
    name varchar(25) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    goal int NOT NULL,
    likes_so_far int NOT NULL,
    did varchar(255) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table bsky_crowd_time_sm_context (
        id bigint NOT NULL AUTO_INCREMENT,
        state_machine_id bigint NOT NULL,
        name varchar(25) NOT NULL,
        created_at DATETIME,
        accepted_at DATETIME,
        open_ends_at DATETIME,
        ends_at DATETIME,
        reposted_notice_at DATETIME,
        per_like_add int NOT NULL,
        per_like_add_unit varchar(25) NOT NULL,
        per_repost_add int NOT NULL,
        per_repost_add_unit varchar(25) NOT NULL,
        open_period_amount int NOT NULL,
        open_period_unit varchar(25) NOT NULL,
        notice_uri varchar(255),
        has_reposted_notice tinyint NOT NULL default 0,
        PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table bsky_user (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    did varchar(255) NOT NULL,
    handle varchar(255),
    convo_id varchar(25),
    shareable_token varchar(25),
    do_not_contact tinyint default 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table info_request (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    state varchar(25) NOT NULL,
    state_machine_id bigint,
    created_at DATETIME,
    updated_at DATETIME,
    form_type varchar(255) NOT NULL,
    bsky_user_id bigint,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
