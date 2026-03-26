CREATE TABLE executions (
    id UUID PRIMARY KEY,
    script TEXT NOT NULL,
    cpu_count NUMERIC(5, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_executions_status ON executions(status);
