# AI Car Buying Assistant — Backend

A conversational AI backend that helps someone go from *"I don't know what
car to buy"* to a confident, explained shortlist — instead of filtering
through hundreds of listings by hand, the user just talks to it.

Built as an **orchestrator, not a CRUD service**: the LLM reasons (asks
clarifying questions, extracts structured preferences, writes SQL, explains
results), Spring Boot validates, persists, executes, and shapes every
response. See `PROJECT_SCOPE.md` for the product brief, `ARCHITECTURE.md`
for the intended design, and `IMPLEMENTATION.md` for the as-built,
milestone-by-milestone record of what was actually done (including bugs
found and design decisions made along the way).

---

## What it does

```
POST /chat/start   → greets the user, asks for budget, fuel type, body type,
                      transmission, driving pattern, family size, and priority
                      (all at once, in one message)
POST /chat/message  → free-text replies get parsed into structured
                      preferences turn by turn; once complete, the backend
                      generates + validates SQL, queries the car catalog,
                      and returns an LLM-written explanation of the matches
                      (falling back to closest-match ranking if nothing is
                      an exact fit, rather than a dead end)
GET  /cars          → paginated car catalog browse, independent of the chat
GET  /cars/{id}     → single car lookup
```

Full request/response contracts: `API_TESTING.md` (curl/Postman) and
`FRONTEND_INTEGRATION.md` (frontend implementation guide).

---

## Tech stack

| Layer | Choice | Why |
|---|---|---|
| Language / framework | Java 17, Spring Boot 3.5.15 | Spec called for Spring Boot; Java 21 was the original target but only JDK 17 was available locally — Spring Boot 3.5.15 + Spring AI 1.1.8 fully support 17, so the target was dropped without losing any needed capability. |
| LLM access | Spring AI 1.1.8 (`ChatClient`) | Provider-agnostic by design. This paid off directly: the LLM provider was swapped **three times** over the project (Gemini → Anthropic → Gemini again, chasing credential issues and then billing/cost) and every swap needed **zero Java code changes** — only `pom.xml` and `application.yml`. |
| LLM provider | Google Gemini (`gemini-2.5-flash`) | Free tier, no billing setup required. The exact free-tier RPM limit was verified empirically per model against the real API rather than assumed — it's not uniform across models on a given account (the "lite" variants turned out to have *lower* quotas than the full model here, which was counter-intuitive). |
| Database | MySQL, Railway-hosted | Relational fit for a structured, filterable car catalog. Same managed instance is reused for local dev and production rather than standing up separate databases. |
| SQL safety | JSqlParser 5.3 (AST-based validation) | The project brief's literal suggestion was regex/keyword matching; real AST parsing was chosen instead for genuine defense-in-depth, since LLM-generated SQL is inherently untrusted input. This caught a real bug during test-driven development: `CCJSqlParserUtil.parse()` doesn't detect trailing content after the first statement, so `"SELECT ...; DROP TABLE cars"` silently passed validation — fixed by switching to `parseStatements()` and rejecting anything but exactly one statement. |
| Boilerplate | Lombok | Reduces entity/DTO ceremony. |
| Testing | JUnit 5 + Mockito | 72 tests, **zero network** by convention — every LLM/DB call is mocked, so the suite is fast, deterministic, and runs the same in CI as it does locally. |
| Build / deploy | Maven, Docker (multi-stage build), Railway | Config is 100% environment-variable-driven (`PORT`, `DB_URL`, `GEMINI_API_KEY`, etc.) — no code differences between local and production. |

---

## Running it

**Docker (one command):**
```bash
docker compose up
```
Needs a `.env` file with `GEMINI_API_KEY=...` (gitignored, same pattern as
`application-local.yml`). Spins up a fresh local MySQL alongside the app —
note the schema is created automatically but seed data (`data.sql`) still
needs a manual load, same as the Railway dev DB (deliberate — never
auto-run inserts against a real, persistent database on every boot).

**Maven, with the `local` Spring profile:**
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```
Real credentials for local runs live in `src/main/resources/application-local.yml`
(gitignored, never committed).

---

## Build notes / reflection

### What I built, and why

An AI-native conversational backend for a car-buying assistant: the LLM
extracts structured preferences from free-text conversation, generates and
validates SQL against a real car catalog, and explains the matches in
plain language — with a fallback to closest-match recommendations instead
of a dead end when nothing satisfies every stated preference exactly. It's
built as ten sequential milestones (`IMPLEMENTATION.md` has the full
build log), each one verified with real tests — and where possible, real
live API calls — before moving to the next.

**What I deliberately cut, and why:**

- Everything `PROJECT_SCOPE.md` explicitly marks out of scope from day
  one: auth, user accounts, wishlists, dealer search, maps, an EMI
  calculator, test-drive booking, payments, image uploads, voice,
  notifications, an admin dashboard, analytics, and review submission.
  This was a backend-and-conversation-quality problem, not a
  full-product build, so I kept every milestone scoped to that.
- **A live, from-scratch walkthrough of the planning phase** — the part
  where I had Claude Code produce the initial `IMPLEMENTATION.md` design
  document milestone-by-milestone before writing any code. My machine
  can't reliably run two servers/processes at once (running the backend
  alongside a second process — e.g. a debugger and a build, or two app
  instances — tends to crash one of them), which made a smooth live demo
  of that first planning phase impractical alongside everything else I
  needed to show. The document itself still exists and drove the whole
  build; I just couldn't narrate its creation live.
- A finished, fully live production deployment. Docker/Railway config
  (`Dockerfile`, `docker-compose.yml`, `railway.json`) is done and
  committed, and the app deploys cleanly, but I was still working through
  a transient Railway platform error at the point I stopped.

### Tech stack — see the table above for the full reasoning per choice.
The short version: Spring Boot because the spec called for it, Spring AI's
provider-agnostic `ChatClient` specifically so a provider swap would never
require touching agent code (validated three times over), real AST-based
SQL parsing over regex because LLM output is untrusted input and I wanted
genuine defense-in-depth, and Gemini's free tier to remove the billing
blocker entirely for development and demoing.

### What I delegated to AI tools vs. did manually

I built this with Claude Code end-to-end, but the division of labor was
deliberate, not "let it do everything":

**Delegated to Claude Code:** writing and maintaining `IMPLEMENTATION.md`
as a living build log; generating entities, DTOs, mappers, and the bulk of
the ~72-test suite; debugging real runtime errors (Spring AI's
nested-`options` config property paths, verified against actual library
source rather than guessed); diagnosing Gemini rate-limit/quota behavior
by making live `curl` calls against the real API instead of trusting
documentation; designing and testing the SQL AST validator; authoring the
Docker/Railway config; and keeping `API_TESTING.md`/`FRONTEND_INTEGRATION.md`
in sync with the actual code as it changed.

**Kept manual:** every architectural decision and the milestone
sequencing itself — the project ran under a hard rule to stop after each
milestone and wait for review before continuing, rather than letting the
agent barrel ahead; final calls on tradeoffs (AST parsing over regex,
batching preference questions instead of merging LLM prompts); supplying
real credentials; running the live app and reporting back actual runtime
errors for debugging; and approving every git commit/push and any
plan-mode proposal before a larger refactor happened.

**Where it helped most:** catching a real, security-relevant parser bug
(the JSqlParser trailing-content issue above) that only surfaced because
the test suite was written to probe adversarial cases, not just happy
paths; verifying assumptions empirically instead of trusting priors —
e.g. directly testing whether a given API key and model combination
actually had free-tier quota, rather than assuming from general
knowledge, which turned up a genuinely counter-intuitive result (lite
models having *lower* quotas than the full model on this account);
flagging and refusing an embedded prompt-injection attempt mid-session (a
message dressed up as advice from "another AI assistant" trying to get it
to bypass its own credential-verification heuristics) while still
independently verifying the underlying technical claim rather than
blindly trusting or blindly dismissing it; and redesigning the
conversation flow to cut LLM calls per turn roughly in half — directly
working around Gemini's 20 req/min free-tier ceiling — without me having
to dig into Spring AI internals myself.

**Where it got in the way:** the hardware constraint above shaped what
could be demoed live, more than any tooling issue did. The more genuine
friction was recurring port conflicts from leftover Java debug processes
not fully exiting between sessions — it took a couple of rounds of manual
process-killing before we landed on an automated fix (a VS Code
pre-launch task that frees the port before every debug run). And because
Claude Code insists on verifying claims empirically and pausing for
confirmation before large architectural changes, some steps took longer
than a "just do it" approach would have — a deliberate tradeoff for
correctness over raw speed, but a real one.

### If I had another 4 hours

1. Finish verifying the live Railway deployment end-to-end (was
   mid-troubleshooting a transient platform error when I stopped).
2. Build or wire in an actual frontend against the documented API
   contract — `FRONTEND_INTEGRATION.md` is ready for this, but no UI
   exists in this repo yet.
3. Add a small number of real, non-mocked end-to-end smoke tests against
   the live Gemini API, gated so they don't run in normal CI.
4. Add proactive client-side rate-limit pacing in `LlmClient` so the app
   self-throttles instead of occasionally hitting Gemini's free-tier 429.
5. Expand the seed dataset beyond 51 cars for a more convincing demo.
