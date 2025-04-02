CREATE INDEX idx_author_name ON author_session (name);
CREATE INDEX idx_author_pub_key ON author_session (public_key);

CREATE INDEX idx_contract_state ON contract (state, id);
CREATE INDEX idx_contract_name ON contract (name);
CREATE INDEX idx_contract_serial_number ON contract (serial_number);
CREATE INDEX idx_contract_shareable ON contract (shareable_token);
CREATE INDEX idx_contract_lock_session_id ON contract (lock_session_id);

CREATE INDEX idx_lock_session_shareable ON lock_session (share_token);
CREATE INDEX idx_lock_session_tc_shareable ON lock_session (total_control_token);
CREATE INDEX idx_lock_session_nested_shareable ON lock_session (share_token, total_control_token);
CREATE INDEX idx_lock_session_public_key ON lock_session (public_key);

CREATE INDEX idx_lock_user_lock_session_id ON lock_user_session(lock_session_id);
CREATE INDEX idx_lock_user_public_key ON lock_user_session(public_key);
CREATE INDEX idx_lock_user_name ON lock_user_session(name);

CREATE INDEX idx_known_token_shareable ON known_token (shareable_token);
CREATE INDEX idx_known_token_author_id ON known_token (author_id);

CREATE INDEX idx_message_bot_id ON message (bot_id);
CREATE INDEX idx_message_contract_id ON message (contract_id);

CREATE INDEX idx_bot_name ON bot (name);

CREATE INDEX idx_command_contract_id ON command (contract_id);
CREATE INDEX idx_command_state ON command (state);
CREATE INDEX idx_command_serial_number ON command (serial_number);