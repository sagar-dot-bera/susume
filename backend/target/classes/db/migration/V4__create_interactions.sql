CREATE TABLE interactions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    external_user_id VARCHAR(255) NOT NULL,
    external_item_id VARCHAR(255) NOT NULL,
    interaction_type VARCHAR(20) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_interactions_user ON interactions (tenant_id, external_user_id);

CREATE INDEX idx_interactions_item ON interactions (tenant_id, external_item_id);

CREATE INDEX idx_interactions_time ON interactions (tenant_id, timestamp);

CREATE INDEX idx_interactions_tenant ON interactions (tenant_id);

CREATE INDEX idx_interactions_tenant_user_timestamp ON interactions (
    tenant_id,
    external_user_id,
    timestamp DESC
);