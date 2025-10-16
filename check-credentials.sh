#!/bin/bash

################################################################################
# Village Calendar Beta - Credential Verification Script
# Run this to verify all required environment variables are set
################################################################################

REQUIRED_VARS=(
    "REGISTRY_USER"
    "REGISTRY_PASSWORD"
    "DB_PASSWORD"
    "DB_HOST"
    "DB_NAME"
    "DB_USER"
    "GOOGLE_CLIENT_ID"
    "GOOGLE_CLIENT_SECRET"
    "JWT_PUBLIC_KEY"
    "JWT_PRIVATE_KEY"
    "MAIL_HOST"
    "MAIL_USERNAME"
    "MAIL_PASSWORD"
    "R2_ENDPOINT"
    "R2_ACCESS_KEY"
    "R2_SECRET_KEY"
    "R2_PUBLIC_URL"
    "STRIPE_SECRET_KEY"
    "STRIPE_PUBLISHABLE_KEY"
    "STRIPE_WEBHOOK_SECRET"
)

echo "=================================="
echo "Credential Verification"
echo "=================================="
echo ""

MISSING=0
SET=0

for var in "${REQUIRED_VARS[@]}"; do
    if [ -z "${!var:-}" ]; then
        echo "❌ Missing: $var"
        MISSING=$((MISSING + 1))
    else
        # Check if it contains placeholder text
        if [[ "${!var}" == *"YOUR_"* ]] || [[ "${!var}" == *"HERE"* ]]; then
            echo "⚠️  Placeholder: $var (needs to be updated)"
            MISSING=$((MISSING + 1))
        else
            # Show first 10 chars only for security
            VALUE="${!var}"
            PREVIEW="${VALUE:0:10}"
            if [ ${#VALUE} -gt 10 ]; then
                PREVIEW="${PREVIEW}..."
            fi
            echo "✅ Set: $var = ${PREVIEW}"
            SET=$((SET + 1))
        fi
    fi
done

echo ""
echo "=================================="
echo "Summary: $SET set, $MISSING missing/placeholder"
echo "=================================="

if [ $MISSING -eq 0 ]; then
    echo ""
    echo "🎉 All required credentials are set!"
    echo "Ready to deploy: ./deploy-to-beta.sh"
    echo ""
    exit 0
else
    echo ""
    echo "❌ $MISSING credentials still need to be set"
    echo ""
    echo "Update set-beta-credentials.sh with your actual credentials, then:"
    echo "  source ./set-beta-credentials.sh"
    echo "  ./check-credentials.sh"
    echo ""
    exit 1
fi