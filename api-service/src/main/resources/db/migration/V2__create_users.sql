CREATE TABLE dashboard_users (
  id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  tenant_id      UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  email          VARCHAR(255) NOT NULL,
  password_hash  VARCHAR(255) NOT NULL,
  role           VARCHAR(20)  NOT NULL DEFAULT 'ADMIN',
  created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
  last_login_at  TIMESTAMP,
  UNIQUE (tenant_id, email)
);

CREATE INDEX idx_dashboard_users_tenant ON dashboard_users(tenant_id);
