# Cloudflare Configuration
variable "cloudflare_account_id" {
  description = "Cloudflare account ID"
  type        = string
  sensitive   = true
}

variable "cloudflare_api_token" {
  description = "Cloudflare API token with R2 and DNS permissions"
  type        = string
  sensitive   = true
}

# R2 Configuration
variable "r2_bucket_name" {
  description = "R2 bucket name for calendar PDFs"
  type        = string
  default     = "village-calendar-beta"
}

variable "r2_location" {
  description = "R2 bucket location hint"
  type        = string
  default     = "WNAM" # Western North America
}

# DNS Configuration (optional - DNS may already be managed in main infra)
variable "zone_id" {
  description = "Cloudflare zone ID for villagecompute.com (optional)"
  type        = string
  default     = ""
}

variable "create_dns_records" {
  description = "Whether to create DNS records (set to false if managed elsewhere)"
  type        = bool
  default     = false
}
