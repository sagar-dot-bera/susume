CREATE TABLE items (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    external_item_id VARCHAR(255) NOT NULL,
    metadata JSONB NOT NULL,
    embedding FLOAT8[] NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    embedding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT unique_tenant_external_item_id UNIQUE (tenant_id, external_item_id)
);

CREATE INDEX idx_items_tenant_status ON items (tenant_id, status);

CREATE INDEX idx_items_tenant ON items (tenant_id);