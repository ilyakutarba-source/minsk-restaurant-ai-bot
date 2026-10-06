CREATE TABLE selection_items (
    chat_id BIGINT NOT NULL,
    position INTEGER NOT NULL CHECK (position BETWEEN 1 AND 3),
    selection_version BIGINT NOT NULL CHECK (selection_version > 0),
    restaurant_id BIGINT NOT NULL,
    PRIMARY KEY (chat_id, position),
    CONSTRAINT fk_selection_chat FOREIGN KEY (chat_id) REFERENCES conversation_state(chat_id),
    CONSTRAINT fk_selection_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id)
);

CREATE INDEX idx_selection_restaurant ON selection_items(restaurant_id);
