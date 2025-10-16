# Cloudflare R2 Storage for Village Calendar
# R2 is S3-compatible object storage for generated PDF files

# Calendar Beta bucket for generated PDF files
resource "cloudflare_r2_bucket" "calendar_beta" {
  account_id = var.cloudflare_account_id
  name       = var.r2_bucket_name
  location   = var.r2_location
}

# Custom domain for calendar beta bucket (optional - requires DNS)
# This provides a CDN URL: https://calendars-beta.villagecompute.com
resource "cloudflare_record" "r2_calendar_beta_cdn" {
  count   = var.create_dns_records && var.zone_id != "" ? 1 : 0
  zone_id = var.zone_id
  name    = "calendars-beta"
  type    = "CNAME"
  content = "${cloudflare_r2_bucket.calendar_beta.name}.r2.cloudflarestorage.com"
  ttl     = 1
  proxied = true # Use Cloudflare CDN
}

# Outputs
output "r2_bucket_name" {
  value       = cloudflare_r2_bucket.calendar_beta.name
  description = "R2 bucket name for calendar PDFs"
}

output "r2_endpoint" {
  value       = "https://${var.cloudflare_account_id}.r2.cloudflarestorage.com"
  description = "R2 endpoint URL (use for S3-compatible API)"
  sensitive   = true
}

output "r2_bucket_url" {
  value       = "https://${var.cloudflare_account_id}.r2.cloudflarestorage.com/${cloudflare_r2_bucket.calendar_beta.name}"
  description = "Direct bucket URL (requires authentication)"
  sensitive   = true
}

output "r2_cdn_url" {
  value       = var.create_dns_records && var.zone_id != "" ? "https://calendars-beta.villagecompute.com" : "https://pub-<random>.r2.dev (enable custom domain or use R2.dev subdomain)"
  description = "Public CDN URL for calendar PDFs"
}

# Instructions for R2 API credentials
output "r2_setup_instructions" {
  value = <<-EOT

  ========================================
  R2 API CREDENTIALS SETUP
  ========================================

  To use this R2 bucket, you need to create API credentials:

  1. Go to Cloudflare Dashboard:
     https://dash.cloudflare.com/${var.cloudflare_account_id}/r2/overview/api-tokens

  2. Click "Create API Token"

  3. Configure the token:
     - Token name: village-calendar-beta
     - Permissions: Object Read & Write
     - Apply to bucket: ${cloudflare_r2_bucket.calendar_beta.name}
     - TTL: (optional, leave blank for no expiration)

  4. Save the credentials (you'll only see them once):
     - Access Key ID
     - Secret Access Key

  5. Use these values in your deployment:
     export R2_ENDPOINT="${var.cloudflare_account_id}.r2.cloudflarestorage.com"
     export R2_BUCKET="${cloudflare_r2_bucket.calendar_beta.name}"
     export R2_ACCESS_KEY="<your-access-key-id>"
     export R2_SECRET_KEY="<your-secret-access-key>"
     export R2_PUBLIC_URL="https://calendars-beta.villagecompute.com"

  For custom domain (CDN):
  - Set create_dns_records = true and provide zone_id in terraform.tfvars
  - Or manually create CNAME: calendars-beta → ${cloudflare_r2_bucket.calendar_beta.name}.r2.cloudflarestorage.com
  - Or use R2.dev subdomain (free, automatically provisioned)

  ========================================

  EOT
  sensitive = true
}
