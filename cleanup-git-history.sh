#!/bin/bash

################################################################################
# Git History Cleanup Script - Remove Exposed Credentials
#
# ⚠️  WARNING: This script rewrites git history!
# ⚠️  Make sure you have:
#     1. Rotated all exposed credentials FIRST
#     2. Notified team members
#     3. Created a backup of your repository
################################################################################

set -e

echo "================================"
echo "Git History Cleanup"
echo "================================"
echo ""

# Check if we're in the right directory
if [ ! -f "set-beta-credentials.sh.template" ]; then
    echo "❌ Error: set-beta-credentials.sh.template not found"
    echo "   Please run this script from the village-calendar root directory"
    exit 1
fi

echo "⚠️  WARNING: This will rewrite git history!"
echo ""
echo "Have you completed these steps?"
echo "  1. Rotated AWS SMTP credentials"
echo "  2. Changed database password"
echo "  3. Updated your local credentials file"
echo "  4. Notified team members"
echo "  5. Created a backup of your repository"
echo ""
read -p "Continue? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo "Aborted."
    exit 0
fi

echo ""
echo "Creating backup..."
cd ..
if [ -d "village-calendar-backup" ]; then
    echo "❌ Backup directory already exists: village-calendar-backup"
    echo "   Please remove or rename it first"
    exit 1
fi

cp -r village-calendar village-calendar-backup
echo "✅ Backup created at: $(pwd)/village-calendar-backup"

cd village-calendar

echo ""
echo "Removing credentials from git history..."

# Create a temp file with secrets to remove
cat > /tmp/secrets-to-remove.txt << 'EOF'
BetaCalendar2024!SecurePass===>***REDACTED***
AKIA4QT55I4HGYS2PGXM===>***REDACTED***
BHVZj6rCnwj+sb2haYYgpjBGo8An5LlwFAoYIbLOqWtw===>***REDACTED***
EOF

# Use git filter-repo if available, otherwise use filter-branch
if command -v git-filter-repo &> /dev/null; then
    echo "Using git-filter-repo (recommended)..."
    git filter-repo --replace-text /tmp/secrets-to-remove.txt --force
else
    echo "Using git filter-branch (slower)..."
    git filter-branch --force --tree-filter '
        if [ -f set-beta-credentials.sh.template ]; then
            sed -i.bak "s/BetaCalendar2024!SecurePass/YOUR_DATABASE_PASSWORD_HERE/g" set-beta-credentials.sh.template
            sed -i.bak "s/AKIA4QT55I4HGYS2PGXM/YOUR_AWS_SMTP_USERNAME_HERE/g" set-beta-credentials.sh.template
            sed -i.bak "s|BHVZj6rCnwj+sb2haYYgpjBGo8An5LlwFAoYIbLOqWtw|YOUR_AWS_SMTP_PASSWORD_HERE|g" set-beta-credentials.sh.template
            rm -f set-beta-credentials.sh.template.bak
        fi
    ' --prune-empty --tag-name-filter cat -- --all
fi

rm -f /tmp/secrets-to-remove.txt

echo ""
echo "Cleaning up..."
rm -rf .git/refs/original/
git reflog expire --expire=now --all
git gc --prune=now --aggressive

echo ""
echo "Verifying cleanup..."
if git log --all -S "AKIA4QT55I4HGYS2PGXM" --oneline | grep -q .; then
    echo "❌ ERROR: Credentials still found in git history!"
    echo "   Cleanup may have failed. Check the backup before proceeding."
    exit 1
fi

echo "✅ Credentials removed from git history"

echo ""
echo "================================"
echo "⚠️  NEXT STEP: Force Push"
echo "================================"
echo ""
echo "To complete the cleanup, you must force push to GitHub:"
echo ""
echo "  git push origin --force --all"
echo "  git push origin --force --tags"
echo ""
echo "⚠️  This will overwrite the remote repository!"
echo ""
echo "After force pushing:"
echo "  1. Verify on GitHub that credentials are removed"
echo "  2. Tell team members to re-clone or:"
echo "     git fetch origin"
echo "     git reset --hard origin/main"
echo ""

read -p "Force push now? (yes/no): " push_confirm

if [ "$push_confirm" = "yes" ]; then
    echo "Force pushing..."
    git push origin --force --all
    git push origin --force --tags
    echo "✅ Done! Credentials removed from GitHub."
else
    echo "Skipped force push. Remember to push manually later:"
    echo "  git push origin --force --all"
    echo "  git push origin --force --tags"
fi

echo ""
echo "✅ Cleanup complete!"
echo ""
