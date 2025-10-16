# Village Calendar - Terraform Infrastructure

This directory contains Terraform configuration for Village Calendar infrastructure, specifically Cloudflare R2 storage for generated PDF files.

## Prerequisites

1. **Terraform** (>= 1.7)
   ```bash
   brew install terraform
   ```

2. **Cloudflare API Token**
   - Go to https://dash.cloudflare.com/profile/api-tokens
   - Create token with permissions:
     - **Account** → R2: Edit
     - **Zone** → DNS: Edit (optional, for custom domain)

## Setup

### 1. Create Configuration File

```bash
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars` with your values:
```hcl
cloudflare_account_id = "5cd6a013116fd24457ed3964e1ccab36"
cloudflare_api_token  = "YOUR_API_TOKEN_HERE"

# Optional: Enable custom domain
create_dns_records = true
zone_id = "e35d3b616f6107243316b5a3d42fc9e7"
```

### 2. Initialize Terraform

```bash
terraform init
```

### 3. Review Plan

```bash
terraform plan
```

This will show:
- R2 bucket creation: `village-calendar-beta`
- DNS record (if enabled): `calendars-beta.villagecompute.com`

### 4. Apply Configuration

```bash
terraform apply
```

Type `yes` to confirm.

### 5. Get Setup Instructions

After applying, Terraform will output instructions for creating R2 API credentials:

```bash
terraform output r2_setup_instructions
```

## Resources Created

- **R2 Bucket**: `village-calendar-beta`
  - Location: Western North America (WNAM)
  - Purpose: Store generated calendar PDF files
  - Access: S3-compatible API

- **DNS Record** (optional): `calendars-beta.villagecompute.com`
  - Type: CNAME
  - Points to: R2 bucket via Cloudflare CDN
  - Purpose: Public URL for accessing PDFs

## Creating R2 API Credentials

After the bucket is created, you need API credentials for the application:

1. Go to https://dash.cloudflare.com/5cd6a013116fd24457ed3964e1ccab36/r2/overview/api-tokens

2. Click "Create API Token"

3. Configure:
   - Name: `village-calendar-beta`
   - Permissions: **Object Read & Write**
   - Apply to bucket: `village-calendar-beta`

4. Save the credentials:
   ```bash
   Access Key ID: [copy this]
   Secret Access Key: [copy this]
   ```

5. Update `set-beta-credentials.sh`:
   ```bash
   export R2_ENDPOINT="https://5cd6a013116fd24457ed3964e1ccab36.r2.cloudflarestorage.com"
   export R2_BUCKET="village-calendar-beta"
   export R2_ACCESS_KEY="[paste Access Key ID]"
   export R2_SECRET_KEY="[paste Secret Access Key]"
   export R2_PUBLIC_URL="https://calendars-beta.villagecompute.com"
   ```

## Custom Domain vs R2.dev Subdomain

### Option 1: Custom Domain (Recommended)
- Set `create_dns_records = true` in terraform.tfvars
- Provides branded URL: `https://calendars-beta.villagecompute.com`
- Uses Cloudflare CDN (no egress fees)
- Requires DNS management

### Option 2: R2.dev Subdomain (Quick Setup)
- Automatically provided by Cloudflare
- URL format: `https://pub-[random-hash].r2.dev`
- Enable in R2 bucket settings: "Public access" → "Allowed"
- No DNS configuration needed

## Outputs

View all outputs:
```bash
terraform output
```

Specific outputs:
```bash
terraform output r2_endpoint          # S3-compatible endpoint
terraform output r2_bucket_name       # Bucket name
terraform output r2_cdn_url          # Public CDN URL
```

## DNS Management Note

The main VillageCompute infrastructure already manages DNS via Terraform at:
`../villagecompute/infra/terraform/cloudflare-dns.tf`

If the DNS record `calendars-beta.villagecompute.com` is already created there, set:
```hcl
create_dns_records = false
```

## Cleanup

To destroy all resources:
```bash
terraform destroy
```

**Warning**: This will delete the R2 bucket and all PDFs stored in it!

## Integration with Deployment

The R2 credentials are used by:
- `deploy-to-beta.sh` - Deployment script
- `set-beta-credentials.sh` - Credential management
- Ansible playbook - K8s secret creation

After applying Terraform and creating R2 API credentials, update the deployment credentials and run the deployment script.
