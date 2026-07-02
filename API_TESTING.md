# API_TESTING.md

Manual/Postman testing guide for the AI Car Buying Assistant backend. Every
request below is a plain `curl` command — copy-paste into a terminal, or
import into Postman (each `curl` can be pasted directly into Postman's
"Import → Raw text" and it'll build the request for you).

For the deeper request/response contract (used when *building* a client),
see `FRONTEND_INTEGRATION.md`. This doc is about *exercising* the API and
understanding call order.

---

## 1. Base URL

| Environment | Base URL |
|---|---|
| Local (default profile) | `http://localhost:8080` |
| Local (this machine's `local` profile via VS Code launch config) | `http://localhost:8081` |
| Railway (once deployed, M11) | whatever Railway assigns — `PORT` env var controls what the app binds to |

No auth, no API key, no context path — every endpoint below is relative to
the base URL directly (e.g. `/chat/start`, not `/api/chat/start`).

Set a Postman environment variable `baseUrl` to whichever of the above
applies and use `{{baseUrl}}` in the requests so you can swap environments
without editing every request.

---

## 2. Endpoint reference

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/chat/start` | Begin a new conversation, get a `conversationId` + greeting |
| `POST` | `/chat/message` | Send a user message, advance the conversation (question or final recommendations) |
| `GET` | `/cars` | Paginated car catalog browse |
| `GET` | `/cars/{id}` | Single car by ID |
| `GET` | `/actuator/health` | Health check (Railway-compatible) |

---

## 3. The conversational flow — call order

The chat flow is stateful and sequential. You cannot call `/chat/message`
without first calling `/chat/start` (the `conversationId` it returns is
required on every subsequent call). The conversation state lives **in
memory only** (a `ConcurrentHashMap`, not the DB) — restarting the backend
wipes all in-progress conversations.

```
1. POST /chat/start
      → { conversationId, assistantMessage: <greeting> }

2. POST /chat/message  (conversationId + your answer)
      → LLM extracts preferences from the whole transcript so far (1 LLM call)
      → if incomplete: { assistantMessage: <deterministic "still need: X, Y" text>, completed: false }
        (this follow-up is plain Java-built text, not LLM-generated — zero extra LLM calls)
      → repeat step 2 with the same conversationId until...

3. POST /chat/message  (the turn that completes all 7 required preferences)
      → LLM generates + validates SQL → queries cars table → LLM explains results
      → { assistantMessage: <markdown recommendation>, recommendations: [...], completed: true }
```

The 7 fields that must all be present before the backend moves to step 3:
`budget`, `fuelType`, `bodyType`, `transmission`, `drivingPattern`,
`familySize`, `priority`. These are extracted from free-text conversation —
you don't send them as structured fields, you just answer naturally and the
LLM parses them out. (3 more optional fields — `brandPreference`,
`groundClearance`, `bootSpace` — are captured if mentioned but never block
completion.)

**Important caveat**: every LLM call in this flow (preference extraction, SQL
generation, recommendation explanation) goes through Google Gemini
(`gemini-2.5-flash`, free tier). The free tier is rate-limited to **20
requests/minute** on this account — if you hammer `/chat/message` in quick
succession (e.g. automated Postman Collection Runner replays), you can hit
a `429` from Gemini, which surfaces as a **503 "LLM Failure"**. Space calls
out by a few seconds if you see this; it's not a bug in your test.

As of the latest backend update, the "preferences still incomplete" path
costs exactly **1** LLM call per turn, not 2 — the follow-up question for
missing fields is deterministic text (see step 2 above), not a second LLM
call. `POST /chat/start`'s greeting also now asks for **all** required
fields up front in one message, so a single thorough reply can complete
extraction in one call instead of several back-and-forth turns — useful to
know if you're trying to conserve the 20/min budget while testing.

---

## 4. Step-by-step curl walkthrough

### Step 1 — Start a conversation

```bash
curl -X POST http://localhost:8080/chat/start
```

Response (`200`):
```json
{
  "conversationId": "3f7a1c2e-9b4d-4e11-8a2f-1d6c9e0b7a55",
  "assistantMessage": "Hi! I'm your AI Car Buying Assistant. To recommend the perfect car, please tell me: your budget, preferred fuel type, body type, transmission, typical driving pattern (city, highway, or off-road), family size, and what matters most to you (e.g. safety, mileage, budget, or space)."
}
```

Note the greeting now asks for **all** required fields up front in one
message (a static string — no LLM call) rather than drip-feeding one
question per turn. This is deliberate: it lets a thorough single reply
complete the whole preference-gathering phase in one round trip instead of
five-plus, which matters because the backend's free-tier LLM quota is
rate-limited (see section 3's caveat below).

Save `conversationId` — every subsequent call needs it. In Postman, add a
"Tests" script on this request to auto-capture it:
```js
pm.environment.set("conversationId", pm.response.json().conversationId);
```

### Step 2 — Answer questions until `completed: true`

```bash
curl -X POST http://localhost:8080/chat/message \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "3f7a1c2e-9b4d-4e11-8a2f-1d6c9e0b7a55",
    "message": "My budget is around 15 lakh rupees, and I need a petrol SUV."
  }'
```

Response while preferences are still incomplete (`200`):
```json
{
  "assistantMessage": "Thanks! I still need a few more details: transmission, driving pattern, family size, top priority. Could you share those?",
  "completed": false
}
```

Note: `recommendations`/`comparison` are omitted entirely (not `null` or
`[]`) while incomplete — `application.yml` sets
`spring.jackson.default-property-inclusion: non_null`, so absent fields
just don't appear in the JSON. Also note the follow-up message itself is
**deterministic, plain Java text listing exactly the still-missing fields**
— not an LLM-phrased question — so it costs zero LLM calls; only the
preference *extraction* behind the scenes (parsing your free-text reply
into structured fields) is an LLM call.

Keep POSTing to `/chat/message` with the **same `conversationId`**,
answering whatever's still listed as missing (transmission, driving pattern
— e.g. "mostly city driving" — family size, top priority — e.g. "safety").
You can answer one field per message or several at once — the backend
re-extracts the whole running conversation from scratch each time, so
answering multiple fields in one reply (as the example above already does
for budget + fuel type + body type) skips straight past those questions.

### Step 3 — Final turn (all 7 required fields captured)

```bash
curl -X POST http://localhost:8080/chat/message \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "3f7a1c2e-9b4d-4e11-8a2f-1d6c9e0b7a55",
    "message": "Safety is my top priority."
  }'
```

Response (`200`, `completed: true`):
```json
{
  "assistantMessage": "## Summary\nBased on your ₹15 lakh budget and focus on safety, here are the best petrol SUV matches...\n\n### 1. Tata Nexon Fearless\n...",
  "recommendations": [
    {
      "id": 2,
      "brand": "Tata",
      "model": "Nexon",
      "variant": "Fearless",
      "bodyType": "SUV",
      "fuelType": "PETROL",
      "transmission": "AUTOMATIC",
      "price": 1450000,
      "engine": "1199cc Turbo",
      "power": "118 bhp",
      "torque": "170 Nm",
      "mileage": 17.4,
      "safetyRating": 5,
      "bootSpace": 382,
      "groundClearance": 209,
      "seatCapacity": 5,
      "reviewScore": 4.5,
      "pros": ["5-star Global NCAP safety", "Strong build quality"],
      "cons": ["Firm ride quality"]
    }
  ],
  "comparison": [ /* same array as recommendations */ ],
  "completed": true
}
```

Or, if no car satisfies every stated preference, the backend automatically
falls back to a **closest-match query** (ranked by proximity — budget
distance, matching body type/fuel/transmission as tiebreakers) instead of
a dead end. Still `200`, `completed: true`, but `assistantMessage` says up
front that these are near-matches and calls out per-car which preference
each one misses:
```json
{
  "assistantMessage": "## Summary\nNo car matched every criterion exactly, but here are the closest options to your ₹15 lakh petrol SUV request...\n\n### 1. Hyundai Creta\n- **Doesn't match**: diesel instead of petrol\n- ...",
  "recommendations": [ /* closest-match cars, same CarResponse shape */ ],
  "comparison": [ /* same array as recommendations */ ],
  "completed": true
}
```

Only if **even the fallback** returns zero rows (i.e. the catalog itself
has nothing usable, not just an overly strict filter) does the backend fall
through to the deterministic message below — a true dead end, not an
error:
```json
{
  "assistantMessage": "I couldn't find any cars matching all of your criteria. Try relaxing your budget or one of your other preferences and I'll take another look.",
  "recommendations": [],
  "comparison": [],
  "completed": true
}
```

Starting a **new** conversation after this just means calling
`POST /chat/start` again for a fresh `conversationId` — there's no
"reset" endpoint on the existing one.

---

## 5. Car catalog endpoints (independent of the chat flow)

These don't require a `conversationId` — they're a plain paginated read API
over the `cars` table, useful for browsing/testing independent of the LLM
flow (and unaffected by Gemini's free-tier rate limit, since they don't
call the LLM at all).

### Browse (paginated)

```bash
curl "http://localhost:8080/cars?page=0&size=5&sort=price,asc"
```

Query params (all optional, standard Spring Data `Pageable`):
- `page` — 0-indexed, default `0`
- `size` — default `20`
- `sort` — `<field>,<asc|desc>`, e.g. `price,desc` or `safetyRating,desc`

Response (`200`) — Spring's standard `Page` envelope:
```json
{
  "content": [
    { "id": 1, "brand": "Maruti Suzuki", "model": "Baleno", "...": "..." }
  ],
  "totalElements": 51,
  "totalPages": 11,
  "number": 0,
  "size": 5,
  "first": true,
  "last": false
}
```

### Single car by ID

```bash
curl http://localhost:8080/cars/2
```

Response (`200`): the same `CarResponse` shape as one `content[]` entry
above.

Not found (`404`):
```bash
curl http://localhost:8080/cars/9999
```
```json
{
  "timestamp": "2026-07-01T22:10:04.512",
  "status": 404,
  "error": "Not Found",
  "message": "Car not found with id: 9999",
  "path": "/cars/9999"
}
```

---

## 6. Health check

```bash
curl http://localhost:8080/actuator/health
```
```json
{ "status": "UP" }
```

---

## 7. Error responses — quick reference for testing negative cases

All errors share the `ErrorResponse` shape: `{timestamp, status, error,
message, path}`.

| Trigger | Status | `error` |
|---|---|---|
| `GET /cars/{id}` with a nonexistent ID | 404 | `Not Found` |
| `POST /chat/message` with an unknown `conversationId` | 404 | `Not Found` |
| `POST /chat/message` with blank/missing `conversationId` or `message` | 400 | `Validation Failed` |
| SQL generation fails validation 3x in a row (SqlAgent exhausted retries) | 500 | `Invalid SQL` |
| Gemini API call fails (free-tier rate limit — 20 req/min, network) | 503 | `LLM Failure` |
| Preference-extraction JSON from the LLM is malformed | 503 | `LLM Failure` |
| DB query fails (bad SQL execution, connection issue) | 503 | `Database Failure` |
| Anything else unhandled | 500 | `Internal Server Error` (generic message — original exception never leaked) |

Try the validation case directly:
```bash
curl -X POST http://localhost:8080/chat/message \
  -H "Content-Type: application/json" \
  -d '{"conversationId": "", "message": ""}'
```
```json
{
  "timestamp": "2026-07-01T22:11:30.001",
  "status": 400,
  "error": "Validation Failed",
  "message": "conversationId: must not be blank; message: must not be blank",
  "path": "/chat/message"
}
```

Try the not-found case directly:
```bash
curl -X POST http://localhost:8080/chat/message \
  -H "Content-Type: application/json" \
  -d '{"conversationId": "does-not-exist", "message": "hello"}'
```
```json
{
  "timestamp": "2026-07-01T22:12:05.221",
  "status": 404,
  "error": "Not Found",
  "message": "Conversation not found with id: does-not-exist",
  "path": "/chat/message"
}
```

---

## 8. Postman collection tips

- Create one environment per target (`local`, `local-8081`, `railway`) with
  a `baseUrl` variable.
- On the `/chat/start` request, add a post-response test script to auto-set
  `conversationId` (shown in step 1 above) so you never have to
  copy-paste it by hand between requests.
- Group requests into a "Chat Flow" folder in call order (`start` →
  `message` → `message` → ...) so Postman's Collection Runner can replay
  the whole conversation in one click once you've filled in realistic
  `message` bodies for each step.
- CORS doesn't apply to curl/Postman (it's a browser-enforced mechanism) —
  if something works here but fails from a browser frontend, check
  `cors.allowed-origins` (`FRONTEND_ORIGIN` env var, defaults to
  `http://localhost:5173`) matches the frontend's actual origin.
