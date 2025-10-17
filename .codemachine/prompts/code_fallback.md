# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Set up Quarkus OIDC extension for OAuth 2.0 / OpenID Connect authentication with Google, Facebook, and Apple providers. Configure application.properties with OIDC tenant configuration for each provider (Google: use Google Cloud Console credentials, Facebook: App ID/Secret, Apple: Service ID/Key). Implement OAuthCallbackResource REST controller to handle OAuth redirects and JWT token generation. Create UserService method for user lookup/creation based on OAuth subject ID. Configure JWT token generation with custom claims (user_id, role, email). Set token expiration to 24 hours. Test OAuth flow in development environment (requires ngrok or localhost redirect URLs configured in provider consoles).

---

## Issues Detected

*   **Test Failure:** The test `CalendarGraphQLTest.testQuery_Templates_Public` is failing because the Apple OIDC configuration is incomplete. The error indicates: `UserInfo is required but the OpenID Provider UserInfo endpoint is not configured. Use 'quarkus.oidc.user-info-path' if the discovery is disabled.`
*   **Root Cause:** Apple OAuth does not support OIDC discovery (no `.well-known/openid-configuration` endpoint), so Quarkus cannot automatically discover the UserInfo endpoint. The current configuration in `application.properties` only sets `auth-server-url` but doesn't configure the specific endpoints manually.

---

## Best Approach to Fix

You MUST modify the Apple OIDC configuration in `src/main/resources/application.properties` to add the missing endpoint configurations. Apple Sign In requires manual endpoint configuration because it doesn't support standard OIDC discovery.

Add the following properties to the Apple OIDC section (lines 79-91):

```properties
# OAuth2 / OIDC Configuration (Apple)
quarkus.oidc.apple.enabled=false
quarkus.oidc.apple.auth-server-url=https://appleid.apple.com
quarkus.oidc.apple.client-id=${APPLE_CLIENT_ID:placeholder}
quarkus.oidc.apple.credentials.secret=${APPLE_CLIENT_SECRET:placeholder}
quarkus.oidc.apple.application-type=web-app
quarkus.oidc.apple.authentication.redirect-path=/auth/apple/callback
quarkus.oidc.apple.authentication.cookie-path=/
quarkus.oidc.apple.authentication.scopes=openid,email,name
quarkus.oidc.apple.token.verify-access-token-with-user-info=true
quarkus.oidc.apple.authentication.user-info-required=true
# Apple-specific configuration: requires form_post response mode
quarkus.oidc.apple.authentication.extra-params.response_mode=form_post
# Apple requires manual endpoint configuration (no OIDC discovery)
quarkus.oidc.apple.discovery-enabled=false
quarkus.oidc.apple.authorization-path=/auth/authorize
quarkus.oidc.apple.token-path=/auth/token
quarkus.oidc.apple.jwks-path=/auth/keys
quarkus.oidc.apple.user-info-path=/auth/userinfo
```

The key additions are:
- `quarkus.oidc.apple.discovery-enabled=false` - Disables OIDC discovery
- `quarkus.oidc.apple.authorization-path=/auth/authorize` - Manual authorization endpoint
- `quarkus.oidc.apple.token-path=/auth/token` - Manual token endpoint
- `quarkus.oidc.apple.jwks-path=/auth/keys` - Manual JWKS (JSON Web Key Set) endpoint
- `quarkus.oidc.apple.user-info-path=/auth/userinfo` - Manual UserInfo endpoint (this is what's missing)

These endpoints are relative to the `auth-server-url` (`https://appleid.apple.com`).

After making this change, run the tests again to verify the fix works.
