-- //  Add SEO fields to calendar_templates for static product page generation

-- Add SEO fields for static page generation
ALTER TABLE calendar_templates
ADD COLUMN slug VARCHAR(100) UNIQUE,
ADD COLUMN og_description VARCHAR(160),
ADD COLUMN meta_keywords TEXT,
ADD COLUMN price_cents INTEGER DEFAULT 2999,
ADD COLUMN generated_thumbnail_url VARCHAR(500);

-- Create index for slug lookups (only non-null slugs)
CREATE UNIQUE INDEX idx_calendar_templates_slug ON calendar_templates(slug) WHERE slug IS NOT NULL;

-- Add comments for documentation
COMMENT ON COLUMN calendar_templates.slug IS 'URL-friendly identifier for static pages (e.g., vermont-2025)';
COMMENT ON COLUMN calendar_templates.og_description IS 'OpenGraph description for social sharing (max 160 chars)';
COMMENT ON COLUMN calendar_templates.meta_keywords IS 'SEO meta keywords (comma-separated)';
COMMENT ON COLUMN calendar_templates.price_cents IS 'Price in cents for product structured data (default $29.99)';
COMMENT ON COLUMN calendar_templates.generated_thumbnail_url IS 'Auto-generated PNG thumbnail URL from R2';

-- // @UNDO

ALTER TABLE calendar_templates
DROP COLUMN IF EXISTS slug,
DROP COLUMN IF EXISTS og_description,
DROP COLUMN IF EXISTS meta_keywords,
DROP COLUMN IF EXISTS price_cents,
DROP COLUMN IF EXISTS generated_thumbnail_url;

DROP INDEX IF EXISTS idx_calendar_templates_slug;
