CREATE TABLE conversation_state (
    chat_id BIGINT PRIMARY KEY,
    generation BIGINT NOT NULL CHECK (generation >= 0),
    current_criteria VARCHAR(4000) NOT NULL,
    selection_version BIGINT NOT NULL CHECK (selection_version >= 0),
    updated_at TIMESTAMP NOT NULL
);
