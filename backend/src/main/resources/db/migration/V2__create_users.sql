CREATE TABLE users (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'MEMBER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    username VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMP,
    is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (tenant_id, email)
);

CREATE TABLE REFRESH_TOKENS (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    device_name VARCHAR(255) NOT NULL,
    ip_address INET NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expirs_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE user_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL,
    provider_user_id VARCHAR(255),
    password_hash VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_provider_identity UNIQUE (provider, provider_user_id),
    CONSTRAINT uq_user_provider UNIQUE (user_id, provider)
);

CREATE TABLE auth_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_token_hash ON auth_tokens (token_hash);

CREATE INDEX idx_users_tenant ON users (tenant_id);