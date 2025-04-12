create table bsky_like_bot_record (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    contract_serial_number int NOT NULL,
    contract_id bigint,
    created_at DATETIME,
    accepted_at DATETIME,
    goal int NOT NULL,
    likes_so_far int NOT NULL,
    did varchar(255) NOT NULL,
    completed tinyint,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table announcer_record (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    token varchar(25) NOT NULL,
    did varchar(255) NOT NULL,
    created_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;