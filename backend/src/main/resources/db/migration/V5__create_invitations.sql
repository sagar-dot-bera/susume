CREATE TABLE tenant_invitations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);