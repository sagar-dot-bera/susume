CREATE TABLE items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4 (),
    tenant_id UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    external_item_id VARCHAR(255) NOT NULL,
    metadata JSONB NOT NULL,
    embedding VECTOR (384),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, external_item_id)
);

CREATE INDEX idx_items_tenant_status ON items (tenant_id, status);

CREATE INDEX idx_items_created_at ON items (created_at);