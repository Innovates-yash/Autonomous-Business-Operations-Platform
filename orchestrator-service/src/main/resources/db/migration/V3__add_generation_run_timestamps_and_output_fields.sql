-- Add startedAt and completedAt to generation_run (Requirements 6.1, 6.9).
-- These track when the first agent was dispatched and when the run finished.

ALTER TABLE generation_run ADD COLUMN started_at DATETIME(6) NULL AFTER status;
ALTER TABLE generation_run ADD COLUMN completed_at DATETIME(6) NULL AFTER started_at;

-- Add agent_type and version to agent_output (Requirements 6.2, 6.3).
-- agent_type is denormalized from the parent invocation for efficient querying.
-- version supports iterative refinement across generation runs.

ALTER TABLE agent_output ADD COLUMN agent_type VARCHAR(32) NOT NULL DEFAULT 'REQUIREMENT_ANALYST' AFTER invocation_id;
ALTER TABLE agent_output ADD COLUMN version INT NOT NULL DEFAULT 1 AFTER content;

CREATE INDEX idx_agent_output_agent_type ON agent_output (agent_type);
