terraform {
  required_version = ">= 1.4"

  required_providers {
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 4.0"
    }
  }
}

provider "cloudflare" {
  api_token = var.cloudflare_api_token
}

# Local variables
locals {
  environment = "beta"
  tags = {
    Service     = "village-calendar"
    Environment = local.environment
    ManagedBy   = "Terraform"
  }
}
