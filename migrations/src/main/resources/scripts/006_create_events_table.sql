-- //
-- Create events table for calendar custom events
-- Stores user-defined events with dates, text, emojis, and colors
-- Each event must be associated with a calendar and fall within the calendar's year
-- //

CREATE TABLE events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    calendar_id UUID NOT NULL,
    event_date DATE NOT NULL,
    event_text VARCHAR(500),
    emoji VARCHAR(100),
    color VARCHAR(20),
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_events_calendar FOREIGN KEY (calendar_id) REFERENCES user_calendars(id) ON DELETE CASCADE
);

-- Indexes for efficient event queries
CREATE INDEX idx_events_calendar ON events(calendar_id, event_date);
CREATE INDEX idx_events_calendar_date_range ON events(calendar_id, event_date DESC);
CREATE INDEX idx_events_date ON events(event_date);

-- Comments for documentation
COMMENT ON TABLE events IS 'Custom events for calendars with dates, text, emojis, and colors';
COMMENT ON COLUMN events.calendar_id IS 'Reference to the calendar this event belongs to';
COMMENT ON COLUMN events.event_date IS 'Date of the event (must be within calendar year)';
COMMENT ON COLUMN events.event_text IS 'Event description or title (max 500 characters)';
COMMENT ON COLUMN events.emoji IS 'Unicode emoji for the event (max 100 characters)';
COMMENT ON COLUMN events.color IS 'Hex color code for event display (e.g., #FF5733)';

-- //@UNDO

DROP TABLE IF EXISTS events;
