#!/bin/bash
set -e

# This script sets up GitHub secrets for Village Calendar deployment
# It copies shared infrastructure secrets and sets up database credentials

REPO="teacurran/village-calendar"
PASSWORDS_FILE="../villagecompute/infra/postgres-db/.passwords"

echo "=========================================="
echo "Village Calendar GitHub Secrets Setup"
echo "=========================================="
echo ""

# Check if gh is authenticated
if ! gh auth status > /dev/null 2>&1; then
    echo "❌ Not authenticated with GitHub CLI"
    echo "Please run: gh auth login"
    exit 1
fi

echo "✅ Authenticated with GitHub"
echo ""

# Check passwords file exists
if [ ! -f "$PASSWORDS_FILE" ]; then
    echo "❌ Passwords file not found at: $PASSWORDS_FILE"
    echo "Please ensure the postgres-db setup is complete"
    exit 1
fi

echo "📖 Reading passwords from: $PASSWORDS_FILE"
echo ""

# Extract passwords
PROD_PASSWORD=$(grep "village_calendar_new:" "$PASSWORDS_FILE" | cut -d: -f2- | xargs)
BETA_PASSWORD=$(grep "village_calendar_beta_new:" "$PASSWORDS_FILE" | cut -d: -f2- | xargs)

if [ -z "$PROD_PASSWORD" ] || [ -z "$BETA_PASSWORD" ]; then
    echo "❌ Failed to extract passwords from $PASSWORDS_FILE"
    exit 1
fi

echo "✅ Passwords extracted successfully"
echo ""

# ========================================
# Repository-level secrets (shared across all environments)
# ========================================
echo "Setting up repository-level secrets..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# K3s/SSH secrets
echo "  Setting K3S_HOST..."
echo -n "10.50.0.20" | gh secret set K3S_HOST --repo "$REPO"

echo "  Setting K3S_USER..."
echo -n "root" | gh secret set K3S_USER --repo "$REPO"

echo "  Setting K3S_SSH_PRIVATE_KEY..."
# Read from the same location village-content uses
SSH_KEY_FILE="$HOME/.ssh/id_ed25519_villagecompute"
if [ -f "$SSH_KEY_FILE" ]; then
    gh secret set K3S_SSH_PRIVATE_KEY --repo "$REPO" < "$SSH_KEY_FILE"
else
    echo "    ⚠️  SSH key not found at $SSH_KEY_FILE"
    echo "    Please manually set K3S_SSH_PRIVATE_KEY"
fi

# WireGuard secrets
echo "  Setting USE_WIREGUARD..."
echo -n "true" | gh secret set USE_WIREGUARD --repo "$REPO"

# Read WireGuard config from saved file or prompt
WG_CONFIG="/tmp/github-actions-wireguard-config.txt"
if [ -f "$WG_CONFIG" ]; then
    echo "  Reading WireGuard config from $WG_CONFIG..."

    WG_ADDRESS=$(grep "WIREGUARD_ADDRESS=" "$WG_CONFIG" | cut -d= -f2-)
    WG_PRIVATE_KEY=$(grep "WIREGUARD_PRIVATE_KEY=" "$WG_CONFIG" | cut -d= -f2-)
    WG_PEER_PUBLIC_KEY=$(grep "WIREGUARD_PEER_PUBLIC_KEY=" "$WG_CONFIG" | cut -d= -f2-)
    WG_ENDPOINT=$(grep "WIREGUARD_ENDPOINT=" "$WG_CONFIG" | cut -d= -f2-)
    WG_ALLOWED_IPS=$(grep "WIREGUARD_ALLOWED_IPS=" "$WG_CONFIG" | cut -d= -f2-)

    echo "  Setting WIREGUARD_ADDRESS..."
    echo -n "$WG_ADDRESS" | gh secret set WIREGUARD_ADDRESS --repo "$REPO"

    echo "  Setting WIREGUARD_PRIVATE_KEY..."
    echo -n "$WG_PRIVATE_KEY" | gh secret set WIREGUARD_PRIVATE_KEY --repo "$REPO"

    echo "  Setting WIREGUARD_PEER_PUBLIC_KEY..."
    echo -n "$WG_PEER_PUBLIC_KEY" | gh secret set WIREGUARD_PEER_PUBLIC_KEY --repo "$REPO"

    echo "  Setting WIREGUARD_ENDPOINT..."
    echo -n "$WG_ENDPOINT" | gh secret set WIREGUARD_ENDPOINT --repo "$REPO"

    echo "  Setting WIREGUARD_ALLOWED_IPS..."
    echo -n "$WG_ALLOWED_IPS" | gh secret set WIREGUARD_ALLOWED_IPS --repo "$REPO"
else
    echo "  ⚠️  WireGuard config not found at $WG_CONFIG"
    echo "  Copying from village-content repo..."

    # Copy WireGuard secrets from village-content (they're the same for all repos)
    # Note: We can't read secrets via API, so we'll set them manually
    echo "  Please run the WireGuard setup script first:"
    echo "  cd ../villagecompute/infra/ansible && ./setup-github-actions-wireguard.sh"
fi

# Session encryption key (generate new one for this app)
echo "  Setting SESSION_ENCRYPTION_KEY..."
SESSION_KEY=$(openssl rand -base64 32)
echo -n "$SESSION_KEY" | gh secret set SESSION_ENCRYPTION_KEY --repo "$REPO"

echo "  Setting SESSION_ENCRYPTION_KEY_BETA..."
SESSION_KEY_BETA=$(openssl rand -base64 32)
echo -n "$SESSION_KEY_BETA" | gh secret set SESSION_ENCRYPTION_KEY_BETA --repo "$REPO"

echo ""
echo "✅ Repository-level secrets configured"
echo ""

# ========================================
# Database secrets (repository level with _BETA suffix)
# ========================================
echo "Setting up database secrets..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Production database
echo "  Setting DB_HOST..."
echo -n "10.50.0.10" | gh secret set DB_HOST --repo "$REPO"

echo "  Setting DB_PORT..."
echo -n "5432" | gh secret set DB_PORT --repo "$REPO"

echo "  Setting DB_NAME..."
echo -n "village_calendar" | gh secret set DB_NAME --repo "$REPO"

echo "  Setting DB_USER..."
echo -n "village_calendar" | gh secret set DB_USER --repo "$REPO"

echo "  Setting DB_PASSWORD..."
echo -n "$PROD_PASSWORD" | gh secret set DB_PASSWORD --repo "$REPO"

# Beta database
echo "  Setting DB_HOST_BETA..."
echo -n "10.50.0.10" | gh secret set DB_HOST_BETA --repo "$REPO"

echo "  Setting DB_PORT_BETA..."
echo -n "5432" | gh secret set DB_PORT_BETA --repo "$REPO"

echo "  Setting DB_NAME_BETA..."
echo -n "village_calendar_beta" | gh secret set DB_NAME_BETA --repo "$REPO"

echo "  Setting DB_USER_BETA..."
echo -n "village_calendar_beta" | gh secret set DB_USER_BETA --repo "$REPO"

echo "  Setting DB_PASSWORD_BETA..."
echo -n "$BETA_PASSWORD" | gh secret set DB_PASSWORD_BETA --repo "$REPO"

echo ""
echo "✅ Database secrets configured"
echo ""

echo "=========================================="
echo "✅ All secrets configured!"
echo "=========================================="
echo ""
echo "Configured secrets:"
echo "  • K3S_HOST, K3S_USER, K3S_SSH_PRIVATE_KEY"
echo "  • USE_WIREGUARD, WIREGUARD_*"
echo "  • SESSION_ENCRYPTION_KEY, SESSION_ENCRYPTION_KEY_BETA"
echo "  • DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD"
echo "  • DB_HOST_BETA, DB_PORT_BETA, DB_NAME_BETA, DB_USER_BETA, DB_PASSWORD_BETA"
echo ""
echo "Note: You may also need to create the beta database:"
echo "  cd ../villagecompute/infra/postgres-db"
echo "  ansible-playbook -i ../ansible/inventory/hosts.yml setup-database.yml"
echo ""
