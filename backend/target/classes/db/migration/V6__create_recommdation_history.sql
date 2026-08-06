CREATE TABLE recommendation_sessions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    user_id UUID NOT NULL REFERENCES users (id),
    algorithm VARCHAR(100) NOT NULL,
    generated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    latency_ms INT NOT NULL
);

CREATE TABLE recommendation_results (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES recommendation_sessions (id) ON DELETE CASCADE,
    item_id UUID NOT NULL REFERENCES items (id),
    score DOUBLE PRECISION NOT NULL,
    rank INT NOT NULL,
    UNIQUE (session_id, rank),
    UNIQUE (session_id, item_id)
);