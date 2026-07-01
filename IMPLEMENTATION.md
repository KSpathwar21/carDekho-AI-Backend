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
│   ├── orchestrator/             ConversationOrchestrator, ConversationNotFoundException
│   ├── agent/                    ConversationAgent
│   ├── model/                    Conversation (+preferences, toTranscript()), Message, MessageRole, ConversationStatus
│   ├── store/                    ConversationStore (in-memory, ConcurrentHashMap)
│   ├── service/                  ConversationService (M4's thin store-interaction layer)
│   └── dto/                      ConversationResponse, ChatRequest, ChatResponse
│
├── preference/                   # structured preference extraction feature
│   ├── agent/                    PreferenceAgent, PreferenceExtractionException
│   └── dto/                      UserPreference
│
├── sql/                          # text-to-SQL feature
│   ├── agent/                    SqlAgent, SqlGenerationException
│   ├── validator/                SqlValidator (JSqlParser AST-based), SqlValidationResult
│   └── dto/                      (empty — SqlResponse deferred to M8)
│
├── car/                          # car catalog feature
│   ├── controller/                CarController          (/cars, /cars/{id})
│   ├── entity/                    Car, BodyType, FuelType, Transmission
│   ├── repository/                CarRepository
│   ├── service/                   CarService, CarNotFoundException
│   ├── tool/                      DatabaseTool, DatabaseQueryException
│   └── dto/                       CarResponse, CarMapper
│
├── recommendation/               # explanation generation feature
│   ├── agent/                     RecommendationAgent
│   └── dto/                       (empty — RecommendationResponse deferred, unused)
│
├── llm/                          # shared LLM access (used by all agents above)
│   ├── config/                    LlmConfig (ChatClient bean)
│   └── client/                    LlmClient (prompt logging), LlmException
│
└── common/                       # cross-cutting only
    ├── config/                    CorsConfig
    ├── exception/                 GlobalExceptionHandler, ErrorResponse
    └── util/                      (empty — no shared helper has needed one yet)
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
| `spring-ai-starter-model-anthropic` (BOM `spring-ai-bom:1.1.8`) | Anthropic Claude chat client for all agents |
| `jsqlparser:5.3` | AST-based SQL parsing for `SqlValidator` (security boundary — real parsing, not regex) |
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
  Spring AI to the **1.1.8** line, which targets Boot 3.5.15.
- LLM provider is **Anthropic Claude** (`spring-ai-starter-model-anthropic`,
  API-key auth). Config: `spring.ai.anthropic.api-key` and
  `spring.ai.anthropic.chat.options.model` (nested under `options`, verified
  against `AnthropicChatProperties`/`AnthropicConnectionProperties` source at
  v1.1.8 — same nested-options pattern the Gemini integration used), sourced
  from env vars `ANTHROPIC_API_KEY` and `ANTHROPIC_MODEL` (default
  `claude-opus-4-8`). Switched from Gemini after repeated credential problems
  (leaked-key revocation, then a value that turned out to be a short-lived
  OAuth token rather than a real API key) — Anthropic's `sk-ant-...` keys
  don't have that ambiguity. `LlmConfig`/`LlmClient`/`LlmException` needed
  zero code changes for the swap since they only depend on Spring AI's
  provider-agnostic `ChatClient`/`ChatClient.Builder`.
- `application.yml` reads `ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL`, `DB_URL`,
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
  the app won't connect — `application.yml`'s `DB_PASSWORD`/`ANTHROPIC_API_KEY`
  placeholders have no working defaults by design (never hardcode secrets in
  the committed base config).
- Anthropic account currently has **no billing/credits** — auth succeeds
  (confirmed via a live call) but requests fail with "credit balance is too
  low". Live end-to-end LLM verification is blocked until billing is set up
  at console.anthropic.com; all code is otherwise verified via mocked tests.

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

**M4 — Conversation Core (no LLM yet)** ✅ *done*
`chat/model/{MessageRole, ConversationStatus}` enums, `Message` (record),
`Conversation` (mutable, id-only equality, mirrors `Car`'s Lombok pattern).
`chat/store/ConversationStore` (in-memory `ConcurrentHashMap`).
`chat/service/ConversationService` builds a new `Conversation` seeded with
the assistant's greeting and saves it. `chat/dto/ConversationResponse`
(`{conversationId, assistantMessage}`) + `ChatController` (`POST /chat/start`).
`ChatRequest`/`ChatResponse` and `Conversation.preferences` deliberately
**deferred to M6** — they belong to `POST /chat/message`, which isn't wired
until the orchestrator/agents exist. Covered by `ConversationServiceTest`;
verified live (`POST /chat/start` → distinct `conversationId` + exact
greeting text on repeated calls).

**M5 — LLM Integration** ✅ *done, provider switched Gemini → Anthropic*
`llm/config/LlmConfig` exposes a `ChatClient` bean from the auto-configured
`ChatClient.Builder` (provider-agnostic — currently Anthropic Claude).
`llm/client/LlmClient` — the one shared LLM client used by all future agents
— with `call(conversationId, userMessage)` and `call(conversationId,
systemPrompt, userMessage)` overloads, logging conversation ID/prompt/latency
(never the API key) via `@Slf4j`, and wrapping any failure in
`llm/client/LlmException`. Covered by a mocked `LlmClientTest` (no network).

Originally built and initially verified against Gemini (Spring AI's Google
GenAI starter), then switched to Anthropic Claude after Gemini credentials
kept failing for reasons outside this codebase: the first key was revoked by
Google as leaked, and a follow-up value turned out to be a short-lived OAuth
token rather than a real API key. The provider swap required **zero Java
code changes** — only `pom.xml` (`spring-ai-starter-model-google-genai` →
`spring-ai-starter-model-anthropic`) and `application.yml`/`application-local.yml`
config changed, since `LlmConfig`/`LlmClient` were built against Spring AI's
provider-agnostic `ChatClient` abstraction from the start.

Bugs found and fixed via live verification (same nested-`options` gotcha hit
on both providers — Spring AI 1.1.8 consistently nests chat model config
under `chat.options.model`, not a flat `chat.model`, verified against
`GoogleGenAiChatProperties`/`AnthropicChatProperties` source directly):
- `spring.ai.google.genai.chat.model` wasn't a real property — silently
  ignored, so Gemini calls used the library's undocumented default
  (`gemini-2.0-flash`) instead of the configured model. Same class of bug
  avoided on the Anthropic side by verifying `spring.ai.anthropic.chat.options.model`
  against source before wiring it.
- The original Gemini key was revoked by Google as leaked; a replacement
  value copied from AI Studio's UI turned out to be a short-lived OAuth
  token (worked once, then failed with `401: Expected OAuth 2 access token`)
  rather than a real `AIzaSy...` API key.
- Current state: Anthropic Claude (`claude-opus-4-8`), key format verified
  correct (`sk-ant-api03-...`, authenticates successfully), but the account
  has no billing/credits yet — live calls fail with "credit balance is too
  low" until billing is set up. Not a code issue.

**M6 — Agents: Conversation + Preference** ✅ *done*
`preference/dto/UserPreference` (record: `budget`/`familySize`/`groundClearance`/
`bootSpace` numeric, category fields as `String` — not enums, since these are
LLM-extracted from free text rather than our own seed data; `isComplete()`
checks the 7 required fields). `preference/agent/PreferenceAgent` re-extracts
the full `UserPreference` from the whole transcript every turn (via
`Conversation.toTranscript()`, added to the model along with the `preferences`
field), strips markdown code fences, parses with Jackson, throws
`PreferenceExtractionException` on malformed JSON.
`chat/agent/ConversationAgent` asks the next question, passing an explicit
"still need: X, Y, Z" hint (derived from missing `UserPreference` fields) so
the model doesn't have to infer what's already been asked. `chat/orchestrator/
ConversationOrchestrator` (+ `ConversationNotFoundException`) coordinates both
agents behind the new `POST /chat/message` (`ChatRequest`/`ChatResponse` —
`recommendations`/`comparison` deliberately deferred to M8, same reasoning as
M4's DTO scoping). When preferences are complete, returns a plain-language
summary of what was captured (`completed=true`) rather than fake
recommendations — M8 replaces that branch with the real SQL → DB →
RecommendationAgent chain. Covered by `PreferenceAgentTest`,
`ConversationAgentTest`, `ConversationOrchestratorTest` (13/13 tests passing,
no network). Live end-to-end verification (`/chat/start` → `/chat/message`
against the real Anthropic API) remains blocked on the same billing issue
noted in M5.

**M7 — SQL Agent + Validator** ✅ *done*
`sql/validator/SqlValidator` validates via **real AST parsing (JSqlParser 5.3)**,
not regex — the user opted for genuine defense-in-depth over the docs' literal
keyword-matching design. `Statement` → `PlainSelect` type-checking structurally
rejects every forbidden statement type (`UPDATE`/`DELETE`/`INSERT`/`DROP`/
`ALTER`/`CREATE`/`TRUNCATE`/`UNION`) in one check; `FROM`/`JOIN` inspection
enforces single-table (`cars`) scope; `WHERE`/`HAVING`/select-item expressions
are scanned (via their parsed, deparsed form — immune to comment obfuscation)
for nested `SELECT`s to reject subqueries. `sql/agent/SqlAgent` generates SQL
from `UserPreference` with a system prompt embedding the real schema and the
fact that enum columns store exact UPPERCASE values; on rejection it retries
up to 3 times, feeding the validator's specific rejection reason back into the
prompt so the LLM can fix the actual problem; `SqlGenerationException` after
exhausting attempts. `sql/dto/SqlResponse` deliberately **not created** —
same deferred-until-needed precedent as M4/M6 (M8 decides the shape it
actually needs). Covered by `SqlValidatorTest` (26 cases) and `SqlAgentTest`
(4 cases), 43/43 total passing, no network.

**A real bug was caught by the test suite during this milestone**:
`CCJSqlParserUtil.parse(String)` — verified via source, not docs — turned out
to only call the grammar's single-`Statement()` production and **does not
check for trailing content**, so `"SELECT * FROM cars; DROP TABLE cars"`
silently parsed as just the first statement, passing validation. Fixed by
switching to `CCJSqlParserUtil.parseStatements(String)` (`Statements extends
ArrayList<Statement>`) and rejecting unless `statements.size() == 1` — the
purpose-built API for exactly this case. A reminder that library source
needs verifying even when the initial read seemed to confirm the intended
behavior (an EOF check existed in the file, just in a different, unused
overload).

**M8 — Database Tool + Recommendation Flow** ✅ *done*
`car/tool/DatabaseTool` executes the validated SQL via `JdbcTemplate` to get
an **ordered** list of matching car IDs, then re-fetches full `Car` entities
via `CarRepository.findAllById` (correctly hydrating `pros`/`cons` through
JPA's `@ElementCollection`, which a raw JDBC row-mapper against `cars` alone
can't do — those live in separate `car_pros`/`car_cons` tables `SqlValidator`
forbids joining to) and **re-orders the result to match the original ID
order** (`findAllById` doesn't preserve input order, and that order is the
LLM's chosen `ORDER BY`/`LIMIT` ranking). `DatabaseQueryException` wraps any
`DataAccessException`, satisfying "never expose SQL exceptions to frontend."

`recommendation/agent/RecommendationAgent` takes `UserPreference` + `List<Car>`
directly (matching the docs literally, no pointless `CarResponse` translation)
and returns a markdown explanation (summary, pros/cons/trade-offs per car,
closing "Alternative Suggestions" section, explicit never-hallucinate
instruction). `recommendation/dto/RecommendationResponse` deliberately **not
created** — same deferred-until-needed precedent as M4/M6/M7 (`explain`
returns a plain markdown `String`, nothing needs a wrapper type).

`ChatResponse` gains `recommendations`/`comparison` (both `List<CarResponse>`,
reusing the same mapped list for both — no distinct "comparison" shape exists
anywhere in the docs, and `PROJECT_SCOPE.md`'s Comparison View fields are a
strict subset of `CarResponse`'s existing fields). `ConversationOrchestrator`
now has 6 dependencies; the "preferences complete" branch generates SQL,
executes it, and **skips `RecommendationAgent` entirely on empty results**
(deterministic "no matches, try relaxing your criteria" message instead —
avoids an LLM call explaining zero cars and any hallucination risk on empty
data). Covered by `DatabaseToolTest` (6 cases, including a reordering
regression test), `RecommendationAgentTest` (6 cases), and a rewritten
`ConversationOrchestratorTest` (4 cases, replacing the old placeholder-text
assertion with real recommendation/empty-result paths) — 56/56 tests passing,
no network. Live end-to-end verification of the full `/chat/start` →
`/chat/message` → recommendations flow remains blocked on the same Anthropic
billing issue noted since M5.

**M9 — Cross-Cutting: Validation & Exceptions** ✅ *done*
`common/exception/GlobalExceptionHandler` (`@RestControllerAdvice`) now
centralizes all 6 custom exceptions built across M3–M8, replacing the two
controller-local handlers (`CarController`/`ChatController`) that returned
plain, inconsistent `String` bodies. Status mapping honors the docs' category
split even though several exceptions originate from the same LLM-agent
layer: `{LlmException, PreferenceExtractionException, DatabaseQueryException}`
→ **503** (genuine upstream-dependency failures — the LLM call itself failing,
its output being unusable, or the DB being unreachable; consolidated into one
handler with an `instanceof` check picking "Database Failure" vs "LLM
Failure" as the label) vs. `SqlGenerationException` → **500**, its own
"Invalid SQL" category (our own system failing to produce valid SQL after
3 attempts — not a dependency being down, distinct from the generic
"Internal Server Error" catch-all). `{CarNotFoundException,
ConversationNotFoundException}` → 404 (consolidated). `MethodArgumentNotValidException`
→ 400, field errors joined into one message. Generic `Exception` catch-all →
500, logs the full exception server-side but never leaks the original message
to the client.

`common/exception/ErrorResponse` (record: `timestamp`, `status`, `error`,
`message`, `path` — `path` via `HttpServletRequest.getRequestURI()`, worth
the one extra parameter per handler for a small API surface).

`common/config/CorsConfig` — constructor-injected `@Value("${cors.allowed-origins}")`
(not field injection, matching this project's 100%-consistent convention),
splits on commas for future multi-origin support (M11), `allowedMethods`
deliberately trimmed to `GET, POST, OPTIONS` — the API's actual current
surface, not pre-added for hypothetical endpoints.

Covered by `GlobalExceptionHandlerTest` (8 cases, plain JUnit + Mockito, no
Spring context — same zero-network convention as every other test in this
project) — 64/64 total tests passing.

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
