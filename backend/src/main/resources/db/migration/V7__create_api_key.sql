CREATE TABLE api_key (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    key VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_api_key_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT uq_api_key_key UNIQUE (key)
);