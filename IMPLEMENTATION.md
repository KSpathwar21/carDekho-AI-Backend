# IMPLEMENTATION.md

Implementation roadmap for the AI Car Buying Assistant backend, derived from
`PROJECT_SCOPE.md`, `ARCHITECTURE.md` and `CLAUDE_BACKEND.md`.

---

# 1. Project Summary

An AI-native conversational backend (Java 21 / Spring Boot 3) that acts as an
**orchestrator**, not a CRUD service. The LLM reasons (asks questions,
extracts preferences, writes SQL, explains results); Spring Boot validates,
persists, executes SQL and shapes responses. Conversation state is in-memory
only — the single persisted entity is `cars`.

Flow: `ChatController → ConversationOrchestrator → ConversationAgent →
PreferenceAgent → (enough info?) → SqlAgent → SqlValidator → DatabaseTool
(JdbcTemplate/JPA) → RecommendationAgent → response`.

---

# 2. Package Structure (feature-based)

Each feature owns its full vertical slice (controller/agent/dto/...);
only cross-cutting concerns live in `common/` and `llm/`.

```
com.carDekhoAI
├── CarDekhoAiApplication.java
│
├── chat/                        # conversation lifecycle feature
│   ├── controller/               ChatController        (/chat/start, /chat/message)
│   ├── orchestrator/             ConversationOrchestrator
│   ├── agent/                    ConversationAgent
│   ├── model/                    Conversation, Message
│   ├── store/                    ConversationStore (in-memory, ConcurrentHashMap)
│   └── dto/                      ChatRequest, ChatResponse, ConversationResponse
│
├── preference/                   # structured preference extraction feature
│   ├── agent/                    PreferenceAgent
│   └── dto/                      UserPreference
│
├── sql/                          # text-to-SQL feature
│   ├── agent/                    SqlAgent
│   ├── validator/                SqlValidator
│   └── dto/                      SqlResponse
│
├── car/                          # car catalog feature
│   ├── controller/                CarController          (/cars, /cars/{id})
│   ├── entity/                    Car, BodyType, FuelType, Transmission
│   ├── repository/                CarRepository
│   ├── service/                   CarService, CarNotFoundException
│   ├── tool/                      DatabaseTool (executes validated SQL — M8)
│   └── dto/                       CarResponse, CarMapper
│
├── recommendation/               # explanation generation feature
│   ├── agent/                     RecommendationAgent
│   └── dto/                       RecommendationResponse
│
├── llm/                          # shared LLM access (used by all agents above)
│   ├── config/                    LlmConfig (ChatClient bean)
│   └── client/                    LlmClient (thin wrapper, prompt logging)
│
└── common/                       # cross-cutting only
    ├── config/                    CorsConfig, JacksonConfig
    ├── exception/                 GlobalExceptionHandler, ErrorResponse, custom exceptions
    └── util/                      shared helpers
```

Rule of thumb: if a class is agent/domain-specific, it lives inside that
feature package. Only put something in `common/` if two or more features
need it unchanged.

---

# 3. Dependencies Configured (`pom.xml`)

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | REST controllers |
| `spring-boot-starter-data-jpa` | `Car` entity + repository |
| `mysql-connector-j` | MySQL driver (runtime) |
| `spring-boot-starter-validation` | Bean Validation on request DTOs |
| `spring-boot-starter-actuator` | `/health` (Railway-compatible) |
| `spring-ai-starter-model-google-genai` (BOM `spring-ai-bom:1.1.8`) | Gemini Developer API chat client for all agents |
| `lombok` | reduce boilerplate on entities/DTOs |
| `spring-boot-starter-test` | JUnit 5 + Mockito |

Notes:
- Java target set to **17** via `spring-boot-starter-parent:3.5.15` (the docs
  call for Java 21, but only JDK 17 and a non-LTS Corretto 20 are installed
  locally; Spring Boot 3.5.15 and Spring AI 1.1.8 fully support 17). Bump to
  21 later by installing a JDK 21 and changing `java.version` in `pom.xml`.
- `spring-boot-starter-parent` was bumped from 3.3.5 to **3.5.15** (still
  Spring Boot 3.x, no breaking changes) after discovering `spring-ai-bom:2.0.0`
  requires Spring Boot 4.1.0 (`org.springframework.core.retry.RetryTemplate`
  only exists in Spring Framework 6.2+). Rather than jump to Boot 4, pinned
  Spring AI to the **1.1.8** line, which targets Boot 3.5.15 and still ships
  the same `spring-ai-starter-model-google-genai` starter.
- LLM provider is **Gemini via the Developer API** (`spring-ai-starter-model-google-genai`,
  not Vertex AI — the provided key is an `AIzaSy...` Generative Language API
  key, so `spring.ai.google.genai.api-key` auth mode applies; setting
  `project-id`/`location` anywhere would flip the client into Vertex AI mode
  and break auth). Config: `spring.ai.google.genai.api-key` and
  `spring.ai.google.genai.chat.model`, sourced from env vars `GEMINI_API_KEY`
  and `GEMINI_MODEL` (default `gemini-3-flash-preview`).
- `application.yml` reads `GEMINI_API_KEY`, `GEMINI_MODEL`, `DB_URL`,
  `DB_USERNAME`, `DB_PASSWORD`, `PORT`, `FRONTEND_ORIGIN` from env — nothing
  hardcoded.
- Dev/test database is a **Railway-hosted MySQL** (public proxy host/port
  default into `DB_URL`; `DB_PASSWORD` has no default and must be supplied).
  A local MySQL 8.0 is also available on this machine if ever needed instead.
- For day-to-day local runs (e.g. IntelliJ's Run button), real credentials
  live in `src/main/resources/application-local.yml` (gitignored — never
  committed) under the `local` Spring profile, rather than passing env vars
  by hand every time. Activate it once in IntelliJ: **Run/Debug Configurations
  → CarDekhoAiApplication → Active profiles: `local`** (or `mvnw spring-boot:run
  -Dspring-boot.run.profiles=local` from the CLI). Without this profile active,
  the app won't connect — `application.yml`'s `DB_PASSWORD`/`GEMINI_API_KEY`
  placeholders have no working defaults by design (never hardcode secrets in
  the committed base config).
- `GEMINI_API_KEY.txt` in the repo root holds the actual dev key. It is now
  gitignored (`.gitignore` → `GEMINI_API_KEY.txt`, `*.env`, `.env.*`) so it's
  never committed. Load it into your shell/IDE run config before running the
  app, e.g. `export GEMINI_API_KEY=<value from file>` — don't reference the
  raw file from application code.

The Maven Wrapper (`mvnw` / `mvnw.cmd` / `.mvn/wrapper/`) is included, so the
project builds with one command: `./mvnw.cmd compile`. Verified with
`BUILD SUCCESS` against JDK 17.

---

# 4. Milestones

Build and verify one milestone at a time; each should compile/run before
moving to the next.

**M1 — Project Setup** ✅ *done in this change*
`pom.xml` dependencies, feature-based package skeleton, `CarDekhoAiApplication`,
base `application.yml`.

**M2 — Car Entity & DTOs** ✅ *done*
`car/entity/Car.java` (all columns from `CLAUDE_BACKEND.md`), plus
`BodyType`/`FuelType`/`Transmission` enums (`@Enumerated(STRING)`),
`pros`/`cons` as `@ElementCollection List<String>`, `price` as `Long`.
`car/dto/CarResponse.java` (Java record) + `CarMapper` static utility.
Covered by `CarMapperTest`.

**M3 — Database** ✅ *done*
`car/repository/CarRepository.java` (Spring Data JPA). `car/service/CarService.java`
+ `CarNotFoundException` (a minimal, feature-scoped 404 — not the full
`GlobalExceptionHandler`, which stays deferred to M9). `car/controller/CarController.java`
exposes `GET /cars` (Spring Data `Pageable`, returns `Page<CarResponse>`) and
`GET /cars/{id}` (404 via a controller-local `@ExceptionHandler`).
`data.sql` seeds 51 realistic Indian cars (all `BodyType`/`FuelType`/`Transmission`
enum values covered) plus their `car_pros`/`car_cons` rows.

`data.sql` is **not** auto-executed on startup — Spring Boot only auto-runs
`data.sql` for embedded databases unless `spring.sql.init.mode=always` is set,
which is deliberately not set here since the datasource is a real, persistent
Railway MySQL instance (auto-running inserts with fixed IDs on every boot
would fail/duplicate on restart). Seed it manually once, after Hibernate has
created the schema:
```
mysql -h <host> -P <port> -u root -p<password> railway < src/main/resources/data.sql
```
Verified end to end against the live Railway DB: Hibernate created `cars`
(with native MySQL `ENUM` columns for `body_type`/`fuel_type`/`transmission`),
`car_pros`, `car_cons`; seeded 51/153/103 rows; `GET /cars?page=0&size=3`
returns paginated `CarResponse` JSON with `pros`/`cons` arrays; `GET /cars/2`
returns the full car; `GET /cars/9999` returns HTTP 404.

**M4 — Conversation Core (no LLM yet)**
`chat/model/Conversation`, `chat/model/Message`, `chat/store/ConversationStore`
(in-memory `ConcurrentHashMap`), `chat/dto/*`. `ChatController` with
`/chat/start` returning a stubbed greeting so the wiring is provable before
LLM calls are involved.

**M5 — LLM Integration**
`llm/config/LlmConfig` (Spring AI `ChatClient` bean from `OPENAI_API_KEY`).
`llm/client/LlmClient` thin wrapper that logs prompt/latency
(`CLAUDE_BACKEND.md` logging requirements) and centralizes error handling.

**M6 — Agents: Conversation + Preference**
`chat/agent/ConversationAgent` (ask one question at a time, never repeat).
`preference/agent/PreferenceAgent` (extract `UserPreference` JSON: budget,
fuelType, bodyType, transmission, drivingPattern, familySize, priority +
optional fields). Orchestrator decides "enough info?" and loops back to
`/chat/message` with the next question when not.

**M7 — SQL Agent + Validator**
`sql/agent/SqlAgent` generates SQL from `UserPreference`.
`sql/validator/SqlValidator` allow-lists `SELECT/WHERE/ORDER BY/LIMIT/AND/OR/LIKE`
and rejects `UPDATE/DELETE/INSERT/DROP/ALTER/CREATE/TRUNCATE/UNION/subqueries/
multiple statements`. Invalid SQL triggers regeneration, not execution.

**M8 — Database Tool + Recommendation Flow**
`car/tool/DatabaseTool` executes validated SQL via `JdbcTemplate`, returns
`List<Car>`, never leaks SQL exceptions upward.
`recommendation/agent/RecommendationAgent` turns `UserPreference` + cars into
a markdown explanation (summary, pros/cons, trade-offs, alternatives).
Wire the full chain in `ConversationOrchestrator`; `/chat/message` now
returns real recommendations + comparison + `completed` flag.

**M9 — Cross-Cutting: Validation & Exceptions**
`common/config/CorsConfig`, `common/exception/GlobalExceptionHandler`
covering LLM failure, DB failure, invalid SQL, conversation-not-found,
bean-validation errors — all mapped to a consistent `ErrorResponse`.

**M10 — Testing**
JUnit 5 + Mockito unit tests for `SqlValidator`, `ConversationOrchestrator`,
`RecommendationAgent`, `DatabaseTool` (per `CLAUDE_BACKEND.md`).

**M11 — Docker & Railway**
`Dockerfile`, `docker-compose.yml` (app + MySQL), confirm `application.yml`
env vars are the only thing that changes between local and Railway, confirm
`/health` responds for Railway health checks.

---

# 5. Explicit Non-Goals (per `PROJECT_SCOPE.md`)

No auth, user accounts, wishlists, dealer search, maps, EMI calculator,
test-drive booking, payments, image uploads, voice, notifications, admin
dashboard, analytics, or review submission. Keep every milestone scoped to
what's above.
