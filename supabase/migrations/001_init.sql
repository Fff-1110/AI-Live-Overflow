-- AI-Live-Overflow Supabase Database Migration
-- Run this in Supabase SQL Editor: https://supabase.com/dashboard/project/ebmzkftreptofjmdsiam/sql/new

-- Gesture Log Table
CREATE TABLE IF NOT EXISTS gesture_log (
    id bigserial PRIMARY KEY,
    gesture_type text NOT NULL,  -- tap, double_tap, long_press, fling
    x integer,
    y integer,
    created_at timestamptz DEFAULT now()
);

-- App Usage Table
CREATE TABLE IF NOT EXISTS app_usage (
    id bigserial PRIMARY KEY,
    package_name text NOT NULL,
    started_at timestamptz DEFAULT now()
);

-- Pet State Table (AI interacts with this)
CREATE TABLE IF NOT EXISTS pet_state (
    id bigserial PRIMARY KEY,
    state_key text NOT NULL,
    state_value text,
    updated_at timestamptz DEFAULT now()
);

-- Row Level Security - allow anon access for personal project
ALTER TABLE gesture_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_usage ENABLE ROW LEVEL SECURITY;
ALTER TABLE pet_state ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow all access to anon"
ON gesture_log FOR ANON
USING (anon_key() = current_setting('anon.key'))
WITH CHECK (true);

CREATE POLICY "Allow all access to anon"
ON app_usage FOR ANON
USING (anon_key() = current_setting('anon.key'))
WITH CHECK (true);

CREATE POLICY "Allow all access to anon"
ON pet_state FOR ANON
USING (anon_key() = current_setting('anon.key'))
WITH CHECK (true);
