CREATE TABLE recommendation_impressions (
    id UUID PRIMARY KEY,
    request_id VARCHAR(255) NOT NULL,
    tenant_id UUID NOT NULL,
    user_id VARCHAR(255),
    item_id VARCHAR(255) NOT NULL,
    position INT NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    model_version VARCHAR(100),
    strategy_scores JSONB,
    CONSTRAINT fk_rec_impressions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE INDEX idx_rec_impressions_tenant_user ON recommendation_impressions (tenant_id, user_id);
CREATE INDEX idx_rec_impressions_request_id ON recommendation_impressions (request_id);
