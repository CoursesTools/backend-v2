# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build
./gradlew build          # Full build with tests
./gradlew assemble       # Compile without tests
./gradlew bootJar        # Build executable JAR

# Run
./gradlew bootRun        # Start application

# Test
./gradlew test           # Run all tests
./gradlew test --tests "ClassName"              # Run specific test class
./gradlew test --tests "ClassName.methodName"   # Run specific test method

# Other
./gradlew clean          # Clean build directory
./gradlew bootBuildImage # Build Docker image
```

## Architecture

**Stack:** Java 17, Spring Boot 3.2.4, PostgreSQL, Redis, Gradle

**Layered Architecture:** Controller → Facade → Service → Repository

- **Controllers** (`controller/`): REST endpoints, input validation
- **Facades** (`facade/`): Orchestrate multiple services for complex operations (AuthFacade, PaymentFacade, UserFacade, OrderFacade, TransactionFacade)
- **Services** (`service/`): Business logic, single responsibility
- **Repositories** (`repository/`): Data access with Spring Data JPA

**Key Patterns:**
- **Event-driven:** Application events for async processing (UserCreateEvent, SubscriptionChangeStatusEvent, UserAlertsChangeEvent) with listeners in `listener/`
- **Specification pattern:** Complex JPA queries in `specification/`
- **Message builders:** Email composition in `messaging/` implementing MessageBuilder interface
- **MapStruct mappers:** DTO transformations in `mapper/`

## Project Structure

```
src/main/java/com/winworld/coursestools/
├── config/           # Spring config, security (JWT filter), properties
├── controller/       # REST API endpoints
├── dto/              # Request/response DTOs by domain
├── entity/           # JPA entities (User, Order, Subscription, Alert, etc.)
├── enums/            # Business enums (Plan, PaymentMethod, OrderStatus, etc.)
├── event/            # Application events
├── exception/        # GlobalExceptionHandler, custom exceptions
├── facade/           # Service orchestration layer
├── listener/         # Async event listeners
├── mapper/           # MapStruct interfaces
├── messaging/        # Email message builders
├── repository/       # Spring Data repositories
├── scheduler/        # Scheduled tasks (OrderScheduler, SubscriptionScheduler)
├── service/          # Business logic
│   ├── external/     # OAuth, GeoLocation, TradingView integrations
│   ├── payment/      # Stripe, Crypto payment processing
│   └── user/         # User-related services
├── specification/    # JPA Specifications for filtering
├── util/             # JWT utils, string generators
└── validation/       # Custom validators (OrderValidator, AuthValidator, etc.)
```

## Configuration

- Database migrations: `src/main/resources/db/migration/` (Flyway)
- External configs imported in application.yml: `configs/partnership.yml`, `configs/payment-platforms.yml`, `configs/emails.yml`, `configs/scheduler.yml`
- Profiles: `dev` (SQL logging enabled), `prod` (default)
- API base path: `/api`
- Swagger UI: `/api/swagger-ui.html`, API docs: `/api/api-docs`

## Key Business Domains

- **Authentication:** JWT-based with OAuth (Google, Discord), refresh tokens
- **Subscriptions:** CT-Pro subscription with monthly/yearly plans, 3-day trial, grace periods
- **Payments:** Stripe (primary) and Crypto gateway integration
- **Partnership:** 10-tier referral system with configurable cashback
- **Alerts:** User-configurable alerts with TradingView integration

## Conventions

- Lombok: `@RequiredArgsConstructor`, `@Builder`, `@Getter/@Setter` on entities and DTOs
- Validation groups in `validation/groups/` for context-specific validation
- Async operations use `@Async` with configured ThreadPoolTaskExecutor
- Resilience4j retry for external service calls