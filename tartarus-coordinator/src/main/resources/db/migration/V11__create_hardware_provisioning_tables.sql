CREATE TABLE provisioned_hardware_units (
    hardware_id                 VARCHAR(64) PRIMARY KEY,
    provisioning_mode           VARCHAR(16) NOT NULL,
    auth_pubkey_b64             VARCHAR(255) NOT NULL UNIQUE,
    manufacturer_sig_b64        TEXT NOT NULL,
    attestation_digest_hex      VARCHAR(64) NOT NULL,
    infineon_certs_json         TEXT NOT NULL,
    leaf_cert_subject           TEXT NOT NULL,
    leaf_cert_issuer            TEXT NOT NULL,
    leaf_cert_serial_hex        VARCHAR(128) NOT NULL,

    -- Snapshot of ESP32-S3 eFuse security posture
    blk3_provisioned            BOOLEAN NOT NULL DEFAULT FALSE,
    blk3_write_protected        BOOLEAN NOT NULL DEFAULT FALSE,
    secure_boot_enabled         BOOLEAN NOT NULL DEFAULT FALSE,
    pad_jtag_disabled           BOOLEAN NOT NULL DEFAULT FALSE,
    usb_jtag_disabled           BOOLEAN NOT NULL DEFAULT FALSE,
    soft_jtag_disabled          BOOLEAN NOT NULL DEFAULT FALSE,
    security_download_enabled   BOOLEAN NOT NULL DEFAULT FALSE,
    usb_download_disabled       BOOLEAN NOT NULL DEFAULT FALSE,
    secure_version              INTEGER NOT NULL DEFAULT 0,

    -- Mutable / rotatable operational state
    initial_session_token       VARCHAR(32) NOT NULL,
    last_known_session_token    VARCHAR(32),
    last_known_lock_pubkey_b64  TEXT,
    last_firmware_version       VARCHAR(64),
    last_attested_at            DATETIME DEFAULT NULL,

    -- Administrative metadata
    label                       VARCHAR(128),
    notes                       TEXT,
    is_revoked                  BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_reason              TEXT,
    provisioned_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_provisioned_units_session
    ON provisioned_hardware_units (last_known_session_token);

CREATE INDEX idx_provisioned_units_mode
    ON provisioned_hardware_units (provisioning_mode);

CREATE TABLE hardware_attestation_logs (
    id                          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    hardware_id                 VARCHAR(64),
    session_token               VARCHAR(32) NOT NULL,
    firmware_version            VARCHAR(64),
    verification_method         VARCHAR(32) NOT NULL,
    success                     BOOLEAN NOT NULL,
    failure_reason              TEXT,
    created_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attestation_logs_hardware_id FOREIGN KEY (hardware_id) REFERENCES provisioned_hardware_units(hardware_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_attestation_logs_hardware_id
    ON hardware_attestation_logs (hardware_id, created_at DESC);
