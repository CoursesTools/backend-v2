# Security Baseline

What the project does to protect data + access.

## Threat model

This is the public-facing payment backend of a live SaaS (API on VPS
5.129.216.95 behind Caddy). Three attacker profiles matter: (1)
fraudsters after free CT-Pro / TradingView indicator access — via auth
bypass, forged payment webhooks, or abuse of the partner-balance
withdrawal flow; (2) credential thieves after the live secret set in
`backend-v2/.env` (live Stripe keys, DB/Redis passwords, JWT secret,
root SSH passwords for both VPSes) — a leak there is a company-level
incident, not a bug; (3) opportunistic scanners hitting the public
endpoints (auth, webhooks, Swagger — which is publicly reachable).
Blast radius is amplified by the deploy pipeline: any push to `master`
auto-deploys prod with no staging gate
(`.github/workflows/docker-build.yml:3-5`), so a compromised commit
path is a compromised production.

## Auth / access

- **Stateless JWT bearer auth.** `config/security/JwtRequestFilter`
  parses `Authorization: Bearer`, extracts userId + `role` claim
  (HMAC-signed via jjwt, key from `JWT_SECRET` —
  `util/jwt/AbstractJwtTokenUtil.java:19`), and sets a `UserPrincipal`
  with `ROLE_<role>`. Sessions are `STATELESS`, CSRF disabled
  (`config/security/SecurityConfig.java`).
- **Deny-by-default.** `SecurityConfig` requires authentication on
  `anyRequest()`; the only public routes are the allowlist in
  `config/security/PublicUrlsHolder.java` (authorization endpoints,
  Stripe/Crypto webhook receivers, actuator GET, Swagger/api-docs,
  partnership levels GET, Telegram bind PATCH, subscription lookup GET).
- **Admin surface.** `@EnableMethodSecurity` +
  `@PreAuthorize("hasRole('ADMIN')")` on every `/v1/admin` mutation
  (`controller/AdminController.java`; one stats endpoint also allows
  `PARTNER`); News/Code mutations are ADMIN-only too.
- **Passwords** are BCrypt-hashed (`SecurityConfig.passwordEncoder()`).
  OAuth signin via Google and Discord
  (`service/external/OAuthGoogleService.java` / `OAuthDiscordService.java`).
- **Refresh tokens** are the same stateless JWT format (30d), delivered
  only as an `HttpOnly` cookie. There is NO server-side refresh-token
  store or revocation — see `../architecture/auth-and-security.md`.
- **Webhooks are authenticated:** Stripe events pass
  `Webhook.constructEvent` signature verification against
  `STRIPE_WEBHOOK_SECRET` (`service/payment/impl/StripePaymentService.java:286-293`);
  CryptoCloud postbacks carry a JWT verified against `CRYPTO_SECRET`
  with an invoice-id claim cross-check
  (`service/payment/impl/CryptoPaymentService.java:126-146`).
- **CORS** locked to `https://*.<web domain>` + `localhost`
  (`SecurityConfig`, domain from `cors.domains.web`).

## Secrets management

Single plaintext `.env` at the repo root (gitignored via `*.env`),
mirrored on the prod box — live Stripe keys, `STRIPE_WEBHOOK_SECRET`,
`JWT_SECRET`, `POSTGRES_PASSWORD`, `REDIS_PASSWORD`, `CRYPTO_API_KEY`/
`CRYPTO_SECRET`, `EMAIL_PASSWORD`, and root SSH passwords for both
VPSes. No vault, no encryption at rest, no scheduled rotation —
rotation is manual and event-driven (on any suspected leak, tell the
operator immediately; deleting a commit is not enough). CI deploy
credentials live in GitHub Actions secrets. Rule for agents: reference
secrets by env-var **name** only, never by value — full rationale in
`../conventions/mindset.md` (hard invariant 3).

## Audit log

**Not implemented.** There is no dedicated audit trail; admin actions
(grants, cashback overrides, TV retry management) leave no systematic
security record. The only forensic source is the application JSON logs
shipped to Loki/Grafana on the ct-logs VPS (`../operations/log-playbook.md`)
— INFO-level, no request IDs (no MDC), correlate by userId/orderId. If
an admin-action audit trail becomes a requirement, it is new work, not
something to "find".

## Data handling

Stored PII is minimal and lives in PostgreSQL in plaintext (no
column-level encryption): user email (`entity/user/User.java:39`),
TradingView handle (`entity/user/UserSocial.java:27`), optional
Telegram/Discord ids, and signup country/region (GeoLocation lookup).
Passwords are stored ONLY as BCrypt hashes. No card or crypto-wallet
data ever touches this backend — checkout is hosted by Stripe /
CryptoCloud. Redis holds only short-lived one-time tokens
(password-reset, email-verification, Telegram-binding —
`service/TokenService.java`). Never store or log: raw passwords, JWT
values, API keys, or webhook secrets.

## Known accepted risks

Operator-accepted trade-offs — do NOT "fix" these unilaterally; a
change here needs an operator decision (and a DEC record):

1. **No refresh-token revocation.** A stolen refresh JWT stays valid
   until expiry (~30d); signout only clears the cookie
   (`../architecture/auth-and-security.md`).
2. **Push-to-master auto-deploys prod, no staging.** Compensated by
   process, not tooling: the never-auto-push + build-gate invariants
   in `../conventions/mindset.md`.
3. **Swagger UI + api-docs are publicly reachable**
   (`PublicUrlsHolder`) — the API surface is enumerable by anyone.
4. **Password-based root SSH** to both VPSes, passwords in `.env`.
5. **No application-level brute-force throttling** on signin (only the
   password-reset resend has a Redis cooldown, `TokenService.java:42`).
6. **TV access bot is called over plain HTTP on a raw IP** — payloads
   carry only TradingView handle + expiry, no secrets, but there is no
   transport security and no revoke channel.
