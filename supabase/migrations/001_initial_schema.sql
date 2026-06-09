-- SecureSMS Guardian - Supabase Database Migration
-- Run this in your Supabase SQL editor

-- ─── Enable UUID extension ────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ─── 1. Temporary Messages (24-hour TTL) ─────────────────────────────────
CREATE TABLE IF NOT EXISTS temporary_messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    sender TEXT NOT NULL,
    sender_display_name TEXT,
    is_known_contact BOOLEAN DEFAULT FALSE,
    message_raw TEXT NOT NULL,
    extracted_links JSONB DEFAULT '[]'::jsonb,
    extracted_domains JSONB DEFAULT '[]'::jsonb,
    threat_level TEXT NOT NULL DEFAULT 'SAFE' CHECK (threat_level IN ('SAFE', 'SUSPICIOUS', 'RED_FLAG')),
    is_blocked BOOLEAN DEFAULT FALSE,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '24 hours')
);

-- RLS for temporary_messages
ALTER TABLE temporary_messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can only see their own messages"
    ON temporary_messages FOR ALL
    USING (auth.uid() = user_id);

-- Auto-delete expired messages via pg_cron (set up in Supabase Dashboard > Extensions)
-- SELECT cron.schedule('delete-expired-messages', '0 * * * *', $$
--   DELETE FROM temporary_messages WHERE expires_at < NOW();
-- $$);

-- Index for performance
CREATE INDEX IF NOT EXISTS idx_temp_messages_user_id ON temporary_messages(user_id);
CREATE INDEX IF NOT EXISTS idx_temp_messages_expires ON temporary_messages(expires_at);
CREATE INDEX IF NOT EXISTS idx_temp_messages_threat ON temporary_messages(threat_level);

-- ─── 2. Flagged Domains ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS flagged_domains (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reporter_user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE SET NULL,
    domain TEXT NOT NULL,
    domain_hash TEXT NOT NULL,
    threat_level TEXT NOT NULL DEFAULT 'RED_FLAG' CHECK (threat_level IN ('SAFE', 'SUSPICIOUS', 'RED_FLAG')),
    report_count INTEGER NOT NULL DEFAULT 1,
    reason TEXT,
    is_personal BOOLEAN DEFAULT TRUE,
    last_reported TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE flagged_domains ENABLE ROW LEVEL SECURITY;

-- Users can see all non-personal (community) entries and their own
CREATE POLICY "Read community flagged domains"
    ON flagged_domains FOR SELECT
    USING (is_personal = FALSE OR auth.uid() = reporter_user_id);

-- Users can insert their own reports
CREATE POLICY "Insert own flagged domains"
    ON flagged_domains FOR INSERT
    WITH CHECK (auth.uid() = reporter_user_id);

-- Users can update their own entries
CREATE POLICY "Update own flagged domains"
    ON flagged_domains FOR UPDATE
    USING (auth.uid() = reporter_user_id);

CREATE INDEX IF NOT EXISTS idx_flagged_domains_hash ON flagged_domains(domain_hash);
CREATE INDEX IF NOT EXISTS idx_flagged_domains_reporter ON flagged_domains(reporter_user_id);
CREATE INDEX IF NOT EXISTS idx_flagged_domains_report_count ON flagged_domains(report_count DESC);

-- ─── 3. Threat Reports ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS threat_reports (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reporter_user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    report_type TEXT NOT NULL CHECK (report_type IN ('MESSAGE', 'URL_DOMAIN', 'PHONE_NUMBER')),
    content TEXT NOT NULL,
    reason TEXT NOT NULL CHECK (reason IN ('PHISHING', 'FRAUD', 'SPAM', 'MALWARE', 'ILLEGAL_CONTENT', 'OTHER')),
    notes TEXT,
    is_anonymous BOOLEAN DEFAULT TRUE,
    extracted_entities JSONB DEFAULT '[]'::jsonb,
    status TEXT DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'REVIEWED', 'CONFIRMED', 'DISMISSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE threat_reports ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can insert reports"
    ON threat_reports FOR INSERT
    WITH CHECK (auth.uid() = reporter_user_id OR is_anonymous = TRUE);

CREATE POLICY "Users can view their own reports"
    ON threat_reports FOR SELECT
    USING (auth.uid() = reporter_user_id);

CREATE INDEX IF NOT EXISTS idx_threat_reports_user ON threat_reports(reporter_user_id);
CREATE INDEX IF NOT EXISTS idx_threat_reports_type ON threat_reports(report_type);
CREATE INDEX IF NOT EXISTS idx_threat_reports_created ON threat_reports(created_at DESC);

-- ─── 4. User Contacts (Permanent) ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_contacts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    phone_number TEXT NOT NULL,
    phone_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, phone_hash)
);

ALTER TABLE user_contacts ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can only access their own contacts"
    ON user_contacts FOR ALL
    USING (auth.uid() = user_id);

CREATE INDEX IF NOT EXISTS idx_contacts_user_id ON user_contacts(user_id);
CREATE INDEX IF NOT EXISTS idx_contacts_phone_hash ON user_contacts(phone_hash);

-- ─── 5. Blocked Messages Log ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS blocked_messages_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    sender TEXT NOT NULL,
    message_raw TEXT NOT NULL,
    block_reason TEXT NOT NULL,
    threat_domains JSONB DEFAULT '[]'::jsonb,
    blocked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_overridden BOOLEAN DEFAULT FALSE,
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '24 hours')
);

ALTER TABLE blocked_messages_log ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can only see their own blocked messages"
    ON blocked_messages_log FOR ALL
    USING (auth.uid() = user_id);

-- ─── 6. Threat Intelligence (Global Stats) ───────────────────────────────
CREATE TABLE IF NOT EXISTS threat_intelligence (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    domain_hash TEXT NOT NULL UNIQUE,
    threat_score INTEGER DEFAULT 0 CHECK (threat_score BETWEEN 0 AND 100),
    report_count INTEGER DEFAULT 0,
    last_reported TIMESTAMPTZ DEFAULT NOW(),
    confirmed_threat BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Publicly readable threat intelligence
ALTER TABLE threat_intelligence ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Anyone can read threat intelligence"
    ON threat_intelligence FOR SELECT
    USING (TRUE);

CREATE INDEX IF NOT EXISTS idx_threat_intel_hash ON threat_intelligence(domain_hash);
CREATE INDEX IF NOT EXISTS idx_threat_intel_score ON threat_intelligence(threat_score DESC);

-- ─── 7. Helper Functions ─────────────────────────────────────────────────

-- Increment domain report count (called when a domain is reported again)
CREATE OR REPLACE FUNCTION increment_domain_report(p_hash TEXT)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    UPDATE flagged_domains
    SET report_count = report_count + 1,
        last_reported = NOW()
    WHERE domain_hash = p_hash;

    UPDATE threat_intelligence
    SET report_count = report_count + 1,
        last_reported = NOW()
    WHERE domain_hash = p_hash;
END;
$$;

-- Get aggregate threat intelligence stats
CREATE OR REPLACE FUNCTION get_threat_intel_stats()
RETURNS JSON
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    result JSON;
BEGIN
    SELECT json_build_object(
        'total_flagged_domains', (SELECT COUNT(*) FROM flagged_domains),
        'total_reports', (SELECT COUNT(*) FROM threat_reports),
        'community_domains', (SELECT COUNT(*) FROM flagged_domains WHERE is_personal = FALSE),
        'confirmed_threats', (SELECT COUNT(*) FROM threat_intelligence WHERE confirmed_threat = TRUE)
    ) INTO result;
    RETURN result;
END;
$$;

-- ─── 8. Cleanup Function (call periodically via pg_cron or Edge Function) ─
CREATE OR REPLACE FUNCTION cleanup_expired_data()
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    deleted_count INTEGER := 0;
    temp_count INTEGER;
    blocked_count INTEGER;
BEGIN
    -- Delete expired temporary messages
    DELETE FROM temporary_messages WHERE expires_at < NOW();
    GET DIAGNOSTICS temp_count = ROW_COUNT;

    -- Delete expired blocked message logs
    DELETE FROM blocked_messages_log WHERE expires_at < NOW();
    GET DIAGNOSTICS blocked_count = ROW_COUNT;

    deleted_count := temp_count + blocked_count;
    RETURN deleted_count;
END;
$$;

-- ─── Done ─────────────────────────────────────────────────────────────────
-- To enable scheduled cleanup, go to Supabase Dashboard > Database > Extensions
-- Enable pg_cron, then run:
-- SELECT cron.schedule('cleanup-expired-data', '0 0 * * *', 'SELECT cleanup_expired_data();');
