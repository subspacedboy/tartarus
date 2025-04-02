create table lock_session(
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    public_key varchar(255) NOT NULL,
    session_token varchar(10) NOT NULL,
    share_token varchar(10) NOT NULL,
    total_control_token varchar(10) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id),
    KEY `session_index` (session_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table author_session(
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    public_key varchar(255) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table contract(
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    shareable_token varchar(10) NOT NULL,
    state varchar(25) NOT NULL,
    next_counter SMALLINT UNSIGNED NOT NULL,
    serial_number SMALLINT UNSIGNED NOT NULL,
    author_id bigint NOT NULL,
    body BLOB NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table command(
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    author_id bigint NOT NULL,
    contract_id bigint NOT NULL,
    command_queue_id bigint NOT NULL,
    state varchar(25) NOT NULL,
    type varchar(25) NOT NULL,
    message varchar(255) DEFAULT NULL,
    serial_number SMALLINT UNSIGNED NOT NULL,
    counter SMALLINT UNSIGNED NOT NULL,
    body BLOB NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table command_queue(
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    lock_session_id bigint NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table safety_key (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    state varchar(25) NOT NULL,
    private_key BLOB NOT NULL,
    public_key BLOB NOT NULL,
    created_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table known_token (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    state varchar(25) NOT NULL,
    author_id bigint NOT NULL,
    notes varchar(255) NOT NULL,
    shareable_token varchar(10) NOT NULL,
    created_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;