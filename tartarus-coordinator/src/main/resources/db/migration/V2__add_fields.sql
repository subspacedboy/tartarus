alter table contract add column notes varchar(255) after body;

alter table lock_session add column is_locked tinyint NOT NULL after total_control_token;
alter table lock_session add column available_for_contract tinyint NOT NULL after is_locked;

create table bot(
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    state varchar(25) NOT NULL,
    description varchar(255) NOT NULL,
    public_key varchar(255) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table message(
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    type varchar(25) NOT NULL,
    bot_id bigint NOT NULL,
    contract_id bigint NOT NULL,
    body varchar(255) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;