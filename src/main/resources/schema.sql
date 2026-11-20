CREATE TABLE IF NOT EXISTS events (
    event_id text PRIMARY KEY,
    event_timestamp timestamptz NOT NULL,
    received_at timestamptz NOT NULL,
    config_id integer NOT NULL,
    client_ip text NOT NULL,
    path text NOT NULL,
    category text NOT NULL,
    action text NOT NULL,
    threat_score smallint NOT NULL CHECK (threat_score BETWEEN 0 AND 100),
    payload jsonb NOT NULL
);

CREATE INDEX IF NOT EXISTS events_by_timestamp ON events (event_timestamp DESC, event_id);
CREATE INDEX IF NOT EXISTS events_by_config ON events (config_id, event_timestamp DESC, event_id);
CREATE INDEX IF NOT EXISTS events_by_ip_window ON events (client_ip, received_at);
CREATE INDEX IF NOT EXISTS events_by_category ON events (category, event_timestamp);

CREATE TABLE IF NOT EXISTS alert_rules (
    id uuid PRIMARY KEY,
    category text NOT NULL,
    threshold integer NOT NULL CHECK (threshold >= 0),
    window_minutes integer NOT NULL CHECK (window_minutes > 0)
);
