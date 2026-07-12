# Auth & Security

How a request gets authenticated, how users sign up / sign in (basic +
Google OAuth), and how roles gate endpoints. All paths below are under
`src/main/java/com/winworld/coursestools/` unless noted; all HTTP routes
sit under the global `/api` base path.

## Security filter chain

`config/security/SecurityConfig.java` — `@EnableWebSecurity` +
`@EnableMethodSecurity` (the latter enables `@PreAuthorize`,
SecurityConfig.java:25).

- Stateless: CSRF disabled, `SessionCreationPolicy.STATELESS`
  (SecurityConfig.java:48,66).
- Everything requires authentication except `PUBLIC_URL_PATTERNS`
  (SecurityConfig.java:61-64).
- `JwtRequestFilter` runs before `UsernamePasswordAuthenticationFilter`
  (SecurityConfig.java:65).
- CORS: allowed origins `https://*.{webDomain}`, `https://{webDomain}`,
  `http://localhost:*`, credentials allowed; `webDomain` comes from
  `cors.domains.web` = `${CORS_WEB_DOMAINS}` (SecurityConfig.java:49-60,
  application.yml:87-89).
- Passwords hashed with `BCryptPasswordEncoder` (SecurityConfig.java:32-34).

### Public (unauthenticated) URLs

`config/security/PublicUrlsHolder.java:14-25`:

| Pattern | Why public |
|---|---|
| `/v1/authorization/**` | signup/signin/refresh/recovery |
| GET `/v1/partnerships/levels` | public partnership tier table |
| GET `/actuator/**` | health/metrics |
| PATCH `/v1/users/socials/telegram/bind` | Telegram bot binds via one-time Redis token |
| `/v1/payments/crypto`, `/v1/payments/stripe` | payment-provider webhooks (verified by their own signatures) |
| `/swagger-ui/**`, `/api-docs`, `/api-docs.yaml` | API docs |
| GET `/v1/subscriptions/*` (case-insensitive) | public product/pricing info (SubscriptionController.java:33-41) |

## JWT tokens

- `util/jwt/AbstractJwtTokenUtil.java` — HMAC-signed JWTs via jjwt;
  `AuthJwtTokenUtil` uses **HS512** with secret `${JWT_SECRET}`
  (util/jwt/impl/AuthJwtTokenUtil.java). Subject = user id (integer as
  string), single custom claim `role`.
- Access and refresh tokens are the same format, only lifetimes differ:
  access `${JWT_ACCESS_LIFETIME:30m}`, refresh `${JWT_REFRESH_LIFETIME:30d}`
  (application.yml:73-76). Generated together in
  `AuthService.generateAuthTokens`.
- **Delivery split** (AuthController.createAuthResponse): access token in
  the JSON body; refresh token only as an `HttpOnly` cookie
  `refresh_token` (path `/`, `Secure` per `${COOKIE_SECURE:true}`,
  `SameSite` per `${COOKIE_SAME_SITE:None}`, domain `.{webDomain}` unless
  webDomain is `localhost`; application.yml:91-93).
- Refresh: `POST /v1/authorization/refresh` reads the cookie, loads the
  user, and issues a fresh pair (role re-read from DB —
  `AuthService.refresh`). There is **no server-side refresh-token store,
  rotation, or revocation list**; `DELETE /signout` merely clears the
  cookie (maxAge 0). A stolen refresh JWT stays valid until expiry.
- Separate util `CryptoJwtTokenUtil` (own secret) exists only for
  CryptoCloud payment postbacks (`service/payment/impl/CryptoPaymentService.java:38`)
  — not user auth.

## Request authentication — JwtRequestFilter

`config/security/JwtRequestFilter.java`:

1. Skips public URLs entirely (`shouldNotFilter`, lines 66-69).
2. Parses `Authorization: Bearer <jwt>`; subject → `userId`, claim
   `role` → authority `ROLE_<role>` (lines 40-59).
3. Principal is `UserPrincipal(int userId)` (config/security/UserPrincipal.java:3);
   controllers receive it via `@AuthenticationPrincipal UserPrincipal`.
4. Expired token → 401 `"Token expired"`; bad signature → 401
   `"Invalid signature"` — JSON `ErrorResponse` written directly
   (lines 71-95).
5. No/malformed header → filter passes through unauthenticated; the
   authorization rules then reject with 403 (Spring default), not 401.

## Roles & method security

`enums/UserRole.java:4` — `USER`, `ADMIN`, `PARTNER`.

- Every signup gets `USER` (AuthService.java:106). No code path promotes
  to ADMIN/PARTNER — done manually in the DB.
- Admin endpoints use `@PreAuthorize("hasRole('ADMIN')")`
  (AdminController, NewsController, CodeController,
  SubscriptionController.java:44). One endpoint allows
  `ADMIN or PARTNER` (AdminController.java:49).

## Signup / signin flows

Endpoints in `controller/AuthController.java` (`/v1/authorization`):
`POST /signup` (201), `POST /signup/google` (201), `POST /signin`,
`POST /signin/google`, `POST /refresh`, `DELETE /signout` (204),
`POST /recovery`. Facade `facade/AuthFacade.java` → `service/AuthService.java`.

### Basic signup (`AuthService.signup`, AuthService.java:65)

1. `AuthFacade.signup` checks password == confirmPassword.
2. `AuthValidator.validateSignUp` (validation/validator/AuthValidator.java:19):
   referrer code non-blank if present, email unique, tradingViewName unique.
3. Password BCrypt-hashed; `setupAndSaveUser` sets role USER, partnership
   level 0, creates UserProfile/UserPartnership/UserFinance/UserSocial,
   **verifies the TradingView nickname exists** (below), stores it on
   UserSocial, registers referral if `referrerCode` given, creates a
   partner code.
4. Publishes `UserCreateEvent` (with client IP: `X-Forwarded-For` header
   or remote addr, AuthController.java:53-60) → async listeners.
5. Returns tokens immediately (auto-login).

### TradingView nickname verification

`service/external/TradingViewService.checkTradingViewName` does
`GET https://tradingview.com/u/{name}`; 404 →
`EntityNotFoundException("TradingView user not found")`, any other
failure → `ExternalServiceException`. So signups **fail if TradingView
is unreachable**. No resilience4j retry on this call (unlike the OAuth
services).

### Google OAuth signup/signin

- `service/external/OAuthGoogleService.getUserInfo` exchanges the
  frontend-supplied `authorizationCode` at `oauth2.googleapis.com/token`
  (client id/secret/redirect-uri from `GoogleOAuthProperties`) and reads
  `oauth2/v3/userinfo`. resilience4j `@Retry(name = "default")` with a
  fallback that throws `ExternalServiceException`.
- Signup (`AuthService.googleSignup`, AuthService.java:80): rejects if
  Google reports `email_verified=false`; generates a random 10-char
  password (`StringGeneratorUtil.generatePassword`), BCrypts it, and
  emails it to the user via the `UserCreateEvent` variant that carries
  the plaintext password. Rest identical to basic signup (same TV-name
  checks).
- Signin (`AuthService.googleSignIn`, AuthService.java:135): exchanges
  code, looks the user up **by email only** — no password involved.

### Discord OAuth

`service/external/OAuthDiscordService` (same shape as Google, discord.com
endpoints) is **not** a login method — it is used only by
`service/user/UserSocialService.java:109` to bind a Discord id to an
existing account.

### Password recovery (`AuthService.recovery`)

One endpoint, two modes on `dto/recovery/RecoveryDto`:
- `email` present → 8-char token (Redis `reset-token:<token>` → userId,
  TTL `tokens.password-reset.lifetime` = 2m, application.yml:95-97) with
  an equal-TTL cooldown key per user; emails link
  `${urls.web-recovery}?token=...`.
- `token`+`password` present → confirm-password match, token consumed
  (`getAndDelete`), new BCrypt password saved. `service/TokenService.java`.

## Signup DTO validation

`dto/auth/BasicAuthSignUpDto` / `GoogleAuthSignUpDto`:
- `tradingViewName`: `@NotBlank`, `@Size(3-25)`, `@Pattern` =
  `^[a-zA-Z0-9_](?:[a-zA-Z0-9_.\-]{1,23}[a-zA-Z0-9_])?$`
  (constants/RegularExpression.java).
- Basic adds `email` (`@Email`), `password`/`confirmPassword`
  (`@Size(7-64)`); Google adds `authorizationCode` (`@NotBlank`).
  `referrerCode` optional in both.

## Gotchas

- `dto/auth/AuthSignUpDto` and `AuthSignInDto` (with validation groups)
  are **dead code** — no controller/service references them; the live
  DTOs are the Basic*/Google* split.
- Emails are lowercased on signin lookup (`AuthService.signIn`) but not
  normalized on signup — uniqueness check uses the raw DTO value.
- Auth error statuses (via `exception/GlobalExceptionHandler.java`):
  wrong password → 400 (`BusinessException`, AuthValidator.java:31);
  email/TV name taken → 409 (`EntityAlreadyExistException`); TV nickname
  not found at signup → 404 (`EntityNotFoundException`); unverified
  Google email / recovery-token errors → 400 (`SecurityException`);
  TradingView/Google outage → 503 (`ExternalServiceException`). Only the
  JWT filter itself emits 401.
- The `role` claim is only refreshed on token refresh/signin; a role
  change in DB takes effect on the next token, not immediately.
