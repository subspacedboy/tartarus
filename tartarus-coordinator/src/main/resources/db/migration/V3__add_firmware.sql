create table firmware (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    state varchar(25) NOT NULL,
    image MEDIUMBLOB NOT NULL,
    public_key BLOB NOT NULL,
    signature varchar(255) NOT NULL,
    major int NOT NULL,
    minor int NOT NULL,
    build int NOT NULL,
    created_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;