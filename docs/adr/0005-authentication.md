# ADR-0005: OTP login built in Identity, JWT access tokens with rotating refresh tokens

- Status: Proposed
- Date: 2026-11-13
- Related: FR-1, FR-3, FR-3a, NFR-28, NFR-31, NFR-37, NFR-41, NFR-44, NFR-46, ADR-0001, ADR-0002

## Context

Users log in with a one-time code sent to their mobile number, or to a verified email as a fallback (FR-1, FR-3, NFR-31). Sessions use a 15-minute access token, a 7-day idle timeout and a 30-day absolute limit, with a fresh OTP for sensitive actions. Deleting or suspending an account must cut off access immediately (FR-3a, NFR-41).

There are two services (ADR-0001). If the worker ever serves the inbox API, it must verify who is calling without depending on the core.

The OTP rules are already specified: codes hashed and expiring after 5 minutes, at most 5 attempts, resend limits, Indian numbers only, per-IP limits (NFR-28, NFR-37). Commercial SMS to Indian numbers requires DLT registration (a registered sender ID and approved templates), and load tests can't send real SMS.

## Options considered

### Tokens

1. **JWT access token with an opaque refresh token**
   - Pro: any service verifies the access token locally from its signature, with no lookup per request.
   - Pro: refresh tokens are stored hashed and rotated on every use; reuse of an old refresh token reveals theft.
   - Con: a JWT can't be revoked before it expires, so access continues for up to 15 minutes after deletion or suspension unless checked against a deny list.
2. **Opaque session ID looked up on every request**
   - Pro: instant revocation; simple.
   - Con: a lookup on every request, and every service needs access to the session store.

### OTP handling

1. **Built in the Identity module with an SMS gateway** (MSG91, Twilio, AWS SNS)
   - Pro: full control over the specified OTP rules; demonstrates the security design.
   - Con: DLT registration needed before sending real SMS in India.
2. **Firebase Phone Auth, exchanged for our own tokens**
   - Pro: much less to build; delivery and abuse protection handled.
   - Con: most of the specified security behaviour is delegated; vendor dependency.
3. **A full identity provider** (AWS Cognito, Auth0, Keycloak)
   - Pro: everything managed.
   - Con: heavy to configure for phone OTP flows; hides the engineering the project aims to show.

## Decision

- **Access token:** a JWT signed with a private key held by Identity, valid for 15 minutes; any service verifies it with the public key.
- **Refresh token:** an opaque random token stored hashed in the Session entity, rotated on every use, with reuse detection revoking the whole session. Sessions end after 7 days idle or 30 days in total.
- **Deny list:** account IDs that are deleted or suspended are kept in Redis until their last access token would have expired, and checked on every request, so access ends immediately.
- **Step-up:** changing a mobile number or email, and deleting an account, require a fresh OTP regardless of session age.
- **OTP:** built in the Identity module, following NFR-28 and NFR-37. Codes and refresh tokens are stored hashed; mobile numbers and emails are encrypted (NFR-44).
- **Sending:** behind an OtpSender interface. Phase 1 uses a development sender that writes codes to the logs or a test inbox, for local work, demos and load tests; it is selected by the configuration property gigwork.identity.otp.sender (none by default). The development sender is refused at startup outside the local and test profiles.

## Consequences

**What gets easier**

- Services verify callers locally, so the worker can authenticate users without calling the core.
- Deletion and suspension cut off access immediately despite stateless tokens.
- Demos and load tests run without SMS cost or DLT registration.

**What gets harder**

- Key management: the signing key pair must be stored in the secrets manager (NFR-45) and rotated, with services accepting both old and new public keys during rotation.
- Refresh-token rotation and reuse detection must be implemented and tested carefully, including concurrent refreshes from the same device.
- One Redis lookup on every request for the deny list.
- The development OTP sender is a risk if ever enabled in production; it needs a test that fails if the flag is on in a production profile.

**What would make us revisit this decision**

- DLT registration or SMS delivery proving impractical → consider Firebase Phone Auth for delivery only, keeping our own tokens.
- Requirements for social login or enterprise single sign-on → consider a full identity provider.
