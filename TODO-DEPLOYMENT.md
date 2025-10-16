# Village Calendar - Beta Deployment TODO

**Status**: 85% Ready - Infrastructure Complete, Credentials In Progress
**Last Updated**: October 16, 2025

## ✅ Completed

### Infrastructure (100%)
- [x] K3s cluster configured and accessible (10.50.0.20)
- [x] PostgreSQL database provisioned (village_calendar_beta on 10.50.0.10)
  - Database: `village_calendar_beta`
  - User: `calendar_user_beta`
  - Extensions: pgcrypto, postgis
- [x] VPN/WireGuard connectivity established
- [x] DNS configured via Terraform
  - `beta.calendar.villagecompute.com` → Cloudflare Tunnel → K3s
  - `calendar.villagecompute.com` → Cloudflare Tunnel → K3s (production)
- [x] Cloudflare Tunnel ingress rules configured
- [x] Terraform applied successfully

### Deployment Automation (100%)
- [x] Deployment script: `deploy-to-beta.sh`
- [x] Ansible playbook: `villagecompute/infra/ansible/deploy-calendar.yml`
- [x] GitHub Actions workflow: `.github/workflows/deploy-k3s-wireguard.yml`
- [x] K8s manifests in `k8s/beta/`
- [x] Credential management scripts:
  - `set-beta-credentials.sh.template` (template)
  - `check-credentials.sh` (verification)

### Application Build (100%)
- [x] Docker image built: `teacurran/village-calendar:beta-latest`
- [x] Multi-stage Dockerfile optimized
- [x] Maven build configuration
- [x] Health checks configured

### Credentials Obtained (65%)
- [x] **JWT Keys** - Generated at `~/.village-secrets/`
- [x] **Docker Hub** - teacurran account with access token
- [x] **Google OAuth2** - Client ID and Secret configured
- [x] **Database** - PostgreSQL credentials set
- [x] **Email/SMTP** - AWS SES credentials available
- [x] **Stripe Test Keys** - Publishable and Secret keys obtained
- [ ] **Stripe Webhook** - Need webhook secret (create endpoint first)
- [ ] **Cloudflare R2** - Need to create bucket and get credentials

## 🔨 Remaining Tasks

### 1. Complete Stripe Setup (5 minutes)

**Current Status**: Have publishable and secret keys, missing webhook secret

**Steps**:
1. Go to https://dashboard.stripe.com/test/webhooks
2. Click "Add endpoint"
3. Endpoint URL: `https://beta.calendar.villagecompute.com/webhooks/stripe`
4. Select events:
   - `checkout.session.completed`
   - `payment_intent.succeeded`
   - `payment_intent.payment_failed`
5. Copy the signing secret (starts with `whsec_`)
6. Update in `set-beta-credentials.sh`:
   ```bash
   export STRIPE_WEBHOOK_SECRET="whsec_..."
   ```

### 2. Set Up Cloudflare R2 (10 minutes)

**Steps**:
1. Go to https://dash.cloudflare.com/?to=/:account/r2
2. Click "Create bucket"
   - Name: `village-calendar-beta`
   - Location: Auto (closest to K3s cluster)
3. Create API Token:
   - Navigate to "Manage R2 API Tokens"
   - Click "Create API token"
   - Permissions: Object Read & Write
   - Apply to bucket: `village-calendar-beta`
   - Copy Access Key ID and Secret Access Key
4. Enable Public Access:
   - In bucket settings → "Public access" → "Connect Domain"
   - Use R2.dev subdomain (free) or custom domain
   - Note the public URL
5. Update in `set-beta-credentials.sh`:
   ```bash
   export R2_ENDPOINT="https://5cd6a013116fd24457ed3964e1ccab36.r2.cloudflarestorage.com"
   export R2_ACCESS_KEY="..."
   export R2_SECRET_KEY="..."
   export R2_PUBLIC_URL="https://village-calendar-beta.[random].r2.dev"
   ```

### 3. Deploy to Beta (20 minutes)

Once all credentials are set:

```bash
cd /Users/tea/dev/VillageCompute/code/village-calendar

# 1. Load credentials
source ./set-beta-credentials.sh

# 2. Verify all credentials are set
./check-credentials.sh

# 3. Deploy!
./deploy-to-beta.sh
```

The script will:
- ✅ Validate prerequisites (Maven, Docker, kubectl, Ansible)
- ✅ Test database connection
- ✅ Build Maven project
- ✅ Build and push Docker image
- ✅ Run database migrations
- ✅ Deploy to K3s via Ansible
- ✅ Run health checks and smoke tests

**Expected deployment time**: 15-20 minutes

### 4. Post-Deployment Verification (10 minutes)

After deployment completes:

1. **Test Application URL**:
   ```bash
   curl https://beta.calendar.villagecompute.com/q/health
   ```

2. **Check Pod Status**:
   ```bash
   ssh tea@10.50.0.20 "kubectl get pods -n calendar-beta"
   ```

3. **View Logs**:
   ```bash
   ssh tea@10.50.0.20 "kubectl logs -n calendar-beta -l app=calendar-app --tail=50"
   ```

4. **Access Application**:
   - Main app: https://beta.calendar.villagecompute.com
   - GraphQL UI: https://beta.calendar.villagecompute.com/graphql-ui
   - Health: https://beta.calendar.villagecompute.com/q/health
   - Metrics: https://beta.calendar.villagecompute.com/q/metrics

5. **Update OAuth2 Redirect URIs**:
   - Google Console: Add `https://beta.calendar.villagecompute.com/auth/google/callback`

## 📊 Progress Summary

| Category | Progress | Status |
|----------|----------|--------|
| Infrastructure | 100% | ✅ Complete |
| Automation | 100% | ✅ Complete |
| Credentials | 65% | 🔨 In Progress |
| Deployment | 0% | ⏸️ Waiting on credentials |
| Testing | 0% | ⏸️ Post-deployment |

**Overall: 85% Ready**

## 🎯 Next Session Goals

1. [ ] Complete Stripe webhook setup (5 min)
2. [ ] Create Cloudflare R2 bucket and get credentials (10 min)
3. [ ] Run `./deploy-to-beta.sh` (20 min)
4. [ ] Verify deployment and run smoke tests (10 min)
5. [ ] Update OAuth2 redirect URIs (5 min)

**Total estimated time**: ~50 minutes to full beta deployment

## 📝 Notes

- **WWW Redirect Issue**: Cloudflare redirect rules temporarily disabled due to API token permissions. File restored for future use. To fix: update API token with "Account Filter Lists: Edit" permission.
- **Database Password**: Currently using `BetaCalendar2024!SecurePass` - consider rotating for production
- **R2 Alternative**: Can deploy without R2 initially using placeholder values - PDF generation will fail but rest of app will work
- **Documentation**: Full deployment report available in `DEPLOYMENT_REPORT_I4T2.md`

## 🔗 Quick Links

- Cloudflare Dashboard: https://dash.cloudflare.com/
- Google Cloud Console: https://console.cloud.google.com/apis/credentials
- Stripe Dashboard: https://dashboard.stripe.com/test
- Cloudflare R2: https://dash.cloudflare.com/?to=/:account/r2
- Deployment Report: `DEPLOYMENT_REPORT_I4T2.md`