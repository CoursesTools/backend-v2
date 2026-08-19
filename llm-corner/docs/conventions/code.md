# Code Style

Beyond what the language formatter enforces. Style rules that
specifically matter for THIS project — keep this list short. Generic
"good code" advice belongs in language docs, not here.

## Deliverables: drive features end-to-end (HARD — non-negotiable)

A feature request is owned from **planning → implementation → WIRING →
deployment → end-to-end verification of the REAL user flow**. No
exceptions, no half-way handoff, no "it compiles so it's done".

- An endpoint, scheduler, or listener with **no caller/trigger wired to
  it** is a **FAILURE, not a deliverable**. Code that compiles while the
  user's literal flow (signup → pay → TV access granted) never actually
  happens is a bug.
- Before calling a feature done, write out the user's literal steps and
  **prove each one** on the real deployment (or a faithful local run):
  hit the endpoint, watch the row change state, confirm the **side
  effect actually occurs** (the TV bot call truly fires, the email truly
  sends, the subscription truly flips status).
- Delegating to subagents does not delegate away this responsibility —
  require them to TRACE + PROVE the flow (not just gate the build),
  and re-verify their claims yourself.

## Java / Spring rules (this repo)

Stack: Java 17, Spring Boot 3.2.4, Gradle Kotlin DSL (`build.gradle.kts`).

- **Layering is strict:** Controller → Facade → Service → Repository.
  Facades orchestrate multi-service operations (`facade/`: AuthFacade,
  OrderFacade, PaymentFacade, TransactionFacade, UserFacade); services
  stay single-responsibility; controllers only validate input and
  delegate.
- **Constructor injection via Lombok** `@RequiredArgsConstructor` with
  `private final` fields — used across ~67 classes; never field
  `@Autowired` (e.g. `service/external/ActivatingSubscriptionService.java:18`).
- **Entities:** `@Getter @Setter @SuperBuilder @NoArgsConstructor
  @AllArgsConstructor`, extend `BaseEntity`
  (`entity/user/User.java:30-37`). No `@Data` on entities (equals/
  hashCode on JPA entities is a trap).
- **DTOs:** `@Data` (+ `@Builder` or `@AllArgsConstructor`/
  `@NoArgsConstructor` as needed), e.g.
  `dto/external/ActivateTradingViewAccessDto.java:15-18`. Prefer static
  factory methods on DTOs when construction has business rules (the TV
  bot DTOs expose separate exact/payment factories so source policy is not
  inferred).
- **DTO-per-domain:** one package per domain under `dto/` (admin, alert,
  auth, code, external, news, order, partnership, payment, recovery,
  subscription, transaction, user). Entities never cross the API
  boundary — always map to a DTO.
- **MapStruct for all entity↔DTO mapping:** one mapper interface per
  domain in `mapper/`, declared
  `@Mapper(componentModel = "spring", unmappedTargetPolicy = WARN,
  uses = {JsonNullableMapper.class})` (`mapper/UserMapper.java:18`).
  Both Lombok and MapStruct run as annotation processors
  (`build.gradle.kts:77-81`) — Lombok must stay listed BEFORE the
  MapStruct processor so generated getters are visible.
- **Validation:** bean validation on DTO fields, triggered by
  `@RequestBody @Valid` in controllers
  (`controller/AuthController.java:53`). Validation groups live in
  `validation/groups/` (AuthValidationGroup, OAuthValidationGroup) and
  are attached with `groups = ...` on shared DTOs
  (`dto/auth/AuthSignUpDto.java:28-51`). Note: the live auth endpoints
  use split `BasicAuth*`/`GoogleAuth*` DTOs with plain `@Valid`; the
  grouped DTOs are the legacy shared shape. Cross-field / stateful
  business validation goes in `validation/validator/` (AuthValidator,
  OrderValidator, UserValidator, …), called from facades/services.
- **Async + events:** `@EnableAsync` with one shared
  `ThreadPoolTaskExecutor` — core 4, max 10, queue 25, prefix
  `AppAsync-`, `CallerRunsPolicy` on saturation
  (`config/AsyncConfig.java:16-24`). Side effects of state changes are
  published as application events (`event/`) and consumed by
  `@TransactionalEventListener` + `@Async` listeners in `listener/`
  (`listener/SubscriptionChangeStatusListener.java:50-51`) — they fire
  AFTER the publishing transaction commits, on the async pool.
  Consequence: an exception in a listener cannot roll back the commit;
  failures must be handled durably (see next rule).
- **External calls: resilience4j `@Retry(name = "default",
  fallbackMethod = ...)`** on every outbound HTTP method
  (`service/external/ActivatingSubscriptionService.java:29`). The
  `default` config: 3 attempts, exponential backoff 1s ×2 capped at 5s,
  ignore-lists `HttpClientErrorException` and `DataValidationException`
  (`application.yml:59-70`) — 4xx client errors are treated as
  permanent and NOT retried. Do not invent new named retry configs
  without a matching `resilience4j.retry.*` binding in application.yml:
  an unbound name silently falls back to defaults (this bit us once —
  see `../reference/gotchas.md`).
- **TV bot calls get a durable fallback:** when in-process retries are
  exhausted, the fallback does NOT throw — it enqueues a
  `trading_view_retry_jobs` row drained by TradingViewRetryScheduler
  (`ActivatingSubscriptionService.java:72-87`), so the caller's
  transaction still commits. A more-specific fallback overload rethrows
  `TradingViewUserNotFoundException` (bot 404 = permanent input error)
  instead of mis-enqueueing it (`ActivatingSubscriptionService.java:89-107`).
- **Logging:** `@Slf4j`, parameterized messages, always with
  correlation ids (userId, orderId, TV name). On an external-service
  failure, log the real HTTP status AND response body before
  rethrowing (`ActivatingSubscriptionService.java:43-46`) — a
  discarded bot response body is an undiagnosable prod incident.
- **Config:** external YAMLs imported by application.yml live in
  `configs/` (partnership.yml, payment-platforms.yml, emails.yml,
  scheduler.yml); secrets and URLs come from `.env`. Never hardcode an
  external URL — inject via `@Value("${urls.…}")`
  (`ActivatingSubscriptionService.java:23-27`).

Hard rule regardless of stack: **never swallow errors silently.**
Every important error is logged with correlation IDs (user id, order
id, retry-job id) AND — when a user action caused it — surfaced as a
real HTTP error via GlobalExceptionHandler with the reason. Errors
always wrap/carry the operation context.

## Frontend

This repository is backend-only; the web frontend and admin web run as
separate containers from their own repos. What still applies here:

- **Humanize all end-user-facing copy.** Every string a user can see
  (validation messages, error responses the frontend renders verbatim)
  must read as plain business language — no variable names, raw
  exception chains, or dev vocabulary. Raw technical detail belongs in
  logs, never in the response `message`.
- **Error responses must carry WHY.** The frontend can only show what
  the API returns — a bare 500 forces a silent-failure UI. Map known
  failures to 4xx with a human reason (e.g. bot-rejected TV nicknames
  surface as friendly 400s for admin/user flows).

## SQL / migrations

Flyway migrations in `src/main/resources/db/migration/`, named
`V<N>__snake_case_description.sql` (currently V1–V14). Spring Boot
defaults: `validate-on-migrate` on, `out-of-order` off (no flyway
overrides exist in application.yml).

- **Never amend or renumber an applied migration** — write a new
  migration to fix a previous one (observed: `V8__fix_smctb_alerts.sql`
  repairs V7). Flyway validates checksums; editing an applied file
  breaks startup.
- **Migration numbering across parallel workstreams:** with
  out-of-order disabled, a LOWER-numbered migration merged AFTER a
  higher one has been applied **fails Flyway validation at startup** —
  and since merge = deploy here, that is a crash-looping prod backend
  container. When parallel branches each need migrations, allocate the
  next numbers up front, and re-check `ls db/migration` for collisions
  right before merging.
- **Schema changes and the code that uses them ship in separate
  commits** so a bad deploy can be reasoned about (migration applied?
  code live?) from the git log alone.
- After a deploy that includes a migration, **verify the actual
  columns/tables exist on prod**, not just that the app started.

## External HTTP (no proxy in this deployment)

All outbound calls (TV access bot, alert bot, Stripe, CryptoCloud,
OAuth, geo-IP) go direct via the shared `RestTemplate` bean
(`config/ApplicationConfiguration.java:24`). Any NEW outbound
integration follows the same pattern: `@Retry` + fallback, status+body
logging on failure, URL from `.env` via `@Value`. For calls whose side
effect MUST eventually happen (access grants), add a durable retry
queue like `trading_view_retry_jobs` — in-process retries alone die
with the pod.
