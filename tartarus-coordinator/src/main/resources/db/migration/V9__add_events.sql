create table event(
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(25) NOT NULL,
    session_token varchar(10) NOT NULL,
    contract_serial_number int NOT NULL,
    counter int NOT NULL,
    event_type varchar(50) NOT NULL,
    correlation_id varchar(255) DEFAULT NULL,
    body BLOB DEFAULT NULL,
    lock_session_id bigint DEFAULT NULL,
    contract_id bigint DEFAULT NULL,
    created_at DATETIME,
    PRIMARY KEY (id),
    KEY idx_event_contract_counter (contract_id, counter),
    KEY idx_event_session (session_token),
    UNIQUE KEY uq_contract_event_counter (contract_id, counter)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

create table event_delivery(
    id bigint NOT NULL AUTO_INCREMENT,
    event_id bigint NOT NULL,
    bot_id bigint NOT NULL,
    contract_id bigint NOT NULL,
    delivered tinyint NOT NULL DEFAULT 0,
    delivered_at DATETIME DEFAULT NULL,
    created_at DATETIME,
    PRIMARY KEY (id),
    KEY idx_delivery_bot_contract (bot_id, contract_id),
    KEY idx_delivery_status (bot_id, delivered),
    UNIQUE KEY uq_event_bot_delivery (event_id, bot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
