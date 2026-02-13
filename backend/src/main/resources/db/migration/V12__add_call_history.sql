-- Call history table for tracking voice/video call records
CREATE TABLE call_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    call_id VARCHAR(36) NOT NULL UNIQUE,
    caller_id UUID NOT NULL REFERENCES users(id),
    callee_id UUID NOT NULL REFERENCES users(id),
    call_type VARCHAR(10) NOT NULL,     -- VOICE or VIDEO
    status VARCHAR(20) NOT NULL,        -- INITIATED, ANSWERED, ENDED, DECLINED, MISSED
    started_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    answered_at TIMESTAMP WITH TIME ZONE,
    ended_at TIMESTAMP WITH TIME ZONE,
    duration_seconds INT
);

CREATE INDEX idx_call_history_caller ON call_history(caller_id);
CREATE INDEX idx_call_history_callee ON call_history(callee_id);
CREATE INDEX idx_call_history_started_at ON call_history(started_at DESC);
