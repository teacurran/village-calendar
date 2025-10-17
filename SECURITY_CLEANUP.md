# 🚨 SECURITY INCIDENT - Credential Exposure Cleanup

## What Happened

The file `set-beta-credentials.sh.template` was committed to git with **real credentials** and pushed to GitHub:
- Repository: `git@github.com:teacurran/village-calendar.git`
- Commit: `bfce605` (Add beta deployment infrastructure and credential management)

## Exposed Credentials

1. **Database Password**: `BetaCalendar2024!SecurePass`
2. **AWS IAM Access Key**: `AKIA4QT55I4HGYS2PGXM`
3. **AWS SMTP Password**: `BHVZj6rCnwj+sb2haYYgpjBGo8An5LlwFAoYIbLOqWtw`

## Immediate Actions Required

### 1. ✅ DONE: Sanitize Template File
- [x] Replaced real credentials with placeholders
- [x] Committed sanitized version

### 2. 🔴 CRITICAL: Rotate ALL Exposed Credentials (DO THIS FIRST!)

**Before removing from git history**, rotate these credentials immediately:

#### A. AWS SMTP Credentials (HIGHEST PRIORITY)
```bash
# The exposed AWS access key can be used to send emails as you
# Delete the compromised IAM user or rotate credentials:

# 1. Go to AWS IAM Console
aws iam delete-access-key --access-key-id AKIA4QT55I4HGYS2PGXM --user-name villagecms-ses-smtp

# 2. Create new SMTP credentials
aws iam create-access-key --user-name villagecms-ses-smtp
```

Or via AWS Console:
1. https://console.aws.amazon.com/iam/home#/users/villagecms-ses-smtp?section=security_credentials
2. Delete the access key `AKIA4QT55I4HGYS2PGXM`
3. Create new access key

#### B. Database Password
```bash
# Connect to your beta database and change the password:
psql -h 10.50.0.10 -U postgres -d village_calendar_beta

# In psql:
ALTER USER calendar_user_beta WITH PASSWORD 'NEW_STRONG_PASSWORD_HERE';
```

#### C. Update Your Local Credentials File
```bash
# Copy template and add NEW credentials:
cp set-beta-credentials.sh.template set-beta-credentials.sh
# Edit set-beta-credentials.sh with the NEW rotated credentials
```

### 3. Remove Credentials from Git History

**⚠️ WARNING**: This rewrites git history. Coordinate with team members!

#### Option A: Using BFG Repo-Cleaner (Recommended - Faster)
```bash
# Install BFG (Mac)
brew install bfg

# Download a fresh clone
cd /tmp
git clone --mirror git@github.com:teacurran/village-calendar.git

cd village-calendar.git

# Create a file with the secrets to remove
cat > ../secrets.txt << 'EOF'
BetaCalendar2024!SecurePass
AKIA4QT55I4HGYS2PGXM
BHVZj6rCnwj+sb2haYYgpjBGo8An5LlwFAoYIbLOqWtw
EOF

# Remove the secrets
bfg --replace-text ../secrets.txt

# Clean up
git reflog expire --expire=now --all
git gc --prune=now --aggressive

# Force push (⚠️ WARNING: This overwrites GitHub history!)
git push --force

# Return to your working directory
cd /Users/tea/dev/VillageCompute/code/village-calendar
git pull --force
```

#### Option B: Using git filter-branch (Slower but built-in)
```bash
# Backup your repo first!
cd /Users/tea/dev/VillageCompute/code/village-calendar
git clone . ../village-calendar-backup

# Remove the commit entirely (if it only contains this change)
git filter-branch --force --index-filter \
  "git rm --cached --ignore-unmatch set-beta-credentials.sh.template" \
  --prune-empty --tag-name-filter cat -- --all

# Force push to overwrite GitHub history
git push origin --force --all
git push origin --force --tags

# Clean up
rm -rf .git/refs/original/
git reflog expire --expire=now --all
git gc --prune=now --aggressive
```

### 4. Verify Cleanup

```bash
# Search git history for exposed secrets
git log --all -S "AKIA4QT55I4HGYS2PGXM"
# Should return: (nothing)

git log --all -S "BetaCalendar2024!SecurePass"
# Should return: (nothing)
```

### 5. Post-Cleanup Actions

- [ ] Verify credentials have been rotated
- [ ] Verify git history is clean
- [ ] Force push completed successfully
- [ ] Notify team members to re-clone the repository
- [ ] Review GitHub security scanning alerts (if any)
- [ ] Consider enabling GitHub secret scanning: Settings → Security → Secret scanning

## Prevention for the Future

1. **Always use `.template` files with placeholders only**
2. **Never commit actual credentials** - use environment variables or secret managers
3. **Enable pre-commit hooks** to scan for secrets
4. **Use `git-secrets`** or similar tools:
   ```bash
   brew install git-secrets
   cd /Users/tea/dev/VillageCompute/code/village-calendar
   git secrets --install
   git secrets --register-aws
   ```

## Questions?

If you have any questions or need help with the cleanup, please reach out before proceeding.

---

**Timeline:**
- Exposure: Commit `bfce605`
- Detection: 2025-10-16
- Sanitization: 2025-10-16 (Commit `1e0819c`)
- Credential Rotation: **PENDING - DO THIS NOW**
- History Cleanup: **PENDING**
