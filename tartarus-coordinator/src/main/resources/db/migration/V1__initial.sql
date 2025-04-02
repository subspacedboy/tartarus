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
    public_key varchar(255) NOT NULL,
    body TEXT NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;