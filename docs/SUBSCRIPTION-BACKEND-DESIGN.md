# Subscription & Entitlement — Backend Design

**Status:** Design proposal. Nothing implemented.
**Companion docs:** `incomatic/docs/PAYWALL-STRATEGY.md` (product decisions),
`incomatic/docs/PAYWALL-IOS-DESIGN.md` (client).

---

## 1. Why the server has to care

Client-side gating alone is not viable here, for one concrete reason: two
endpoints cost real money per call.

| Endpoint | Cost | Enforcement |
| --- | --- | --- |
| `POST /v1/calculate` | CPU only | **None, ever.** Stays anonymous-capable |
| `POST /v1/budget/plan` | Vertex AI tokens | **Pro required** |
| `GET /v1/stocks/*` | Finnhub quota | **Pro required** |
| `GET /v1/calculations` | Firestore reads | Free tier capped at 3 |
| `PUT /v1/grants` | Firestore writes | Free tier capped at 1 grant |

A `curl` against `/v1/budget/plan` currently gets a free Vertex AI call. That's
the bill this design exists to prevent — and it's why the server ships first,
before any client work.

---

## 2. Architecture

Follows the existing service conventions exactly: plain constructor injection,
controllers registered in `Main.java`, an interface per store with Firestore and
in-memory implementations, `ENABLE_GCP=false` giving a fully working local boot.

```
auth/
├── AuthMiddleware.java             (exists — attaches userId, no change)
├── SessionTokenService.java        (exists — no change, see §3)
└── EntitlementMiddleware.java      NEW  attaches entitlement to Context

store/
├── EntitlementStore.java           NEW  interface
├── FirestoreEntitlementStore.java  NEW
├── InMemoryEntitlementStore.java   NEW
└── UserDirectory.java              (exists — gains a usage-counter method)

client/
├── AppStoreServerClient.java       NEW  App Store Server API (renewal state, history)
└── AppStoreJwsVerifier.java        NEW  offline JWS signature-chain verification

controller/
├── SubscriptionController.java     NEW  /v1/subscription/*
└── AppStoreWebhookController.java  NEW  /v1/webhooks/appstore

service/
└── EntitlementService.java         NEW  verify → resolve → persist → cache
```

---

## 3. Entitlement is **not** in the session JWT

`SessionTokenService` mints 30-day HS256 tokens. Putting a `pro: true` claim in
them would be free to check — and wrong:

- A user who cancels or refunds keeps Pro for up to 30 days.
- A user who subscribes stays locked out until their token rotates.
- Refund abuse becomes trivially profitable and undetectable.

Entitlement is resolved **per request**, from the store, with a short-lived
in-process cache. `SessionTokenService` and the `IssuedSession` record stay
exactly as they are.

**Caching:** Caffeine, keyed by `userId`, **60-second TTL**, max 10k entries.
Explicitly invalidated on webhook receipt and on `/v1/subscription/verify`. That
bounds worst-case staleness at 60s for the polling path and ~0 for the event
path, at roughly one Firestore read per user per minute of active use.

---

## 4. Data model

**Firestore `entitlements/{userId}`**

```json
{
  "userId":                "001234.abcd...",
  "status":                "active",
  "productId":             "app.incomatic.pro.annual",
  "originalTransactionId": "2000000123456789",
  "expiresAt":             "2027-03-14T09:12:00Z",
  "autoRenew":             true,
  "isTrial":               false,
  "environment":           "Production",
  "lastNotificationType":  "DID_RENEW",
  "updatedAt":             "2026-03-14T09:12:03Z"
}
```

`status ∈ { none, active, grace, expired, revoked }`. The Java model is a record
in `modules/common/.../dto/Entitlement.java` so the same shape serializes to the
iOS client.

**Firestore `transactionIndex/{originalTransactionId}` → `{ userId }`**

App Store Server Notifications arrive keyed by transaction, not by your user id.
Without this index, every webhook becomes a collection scan. Written on first
successful verify; never deleted (see §8).

**`users/{userId}` gains counters** — extends the existing `FirestoreUserDirectory`
(collection `users`) rather than adding a store:

```json
{ "budgetPlansGenerated": 1 }
```

This backs the one-free-budget mechanic from the strategy doc. It lives
server-side deliberately: an `@AppStorage` counter resets on reinstall and gives
away unlimited free Vertex calls.

---

## 5. Endpoints

### `POST /v1/subscription/verify` — auth required

```json
{ "signedTransaction": "<Transaction.jwsRepresentation from StoreKit 2>" }
```

1. Resolve `userId` via `AuthMiddleware.currentUserId(ctx)` → 401 if absent.
2. Verify the JWS **offline**: parse the `x5c` header chain, validate it up to
   the Apple Root CA G3 (bundled in resources), check `notBefore`/`notAfter`,
   verify the ES256 signature. Reject on any failure. This is the security
   boundary — a forged payload that never touches signature verification is the
   whole attack.
3. Assert `bundleId` matches `APPLE_AUDIENCE` (already used for Apple sign-in
   verification) — otherwise another app's transaction grants your Pro.
4. Assert the environment matches the deployment (`Sandbox` vs `Production`),
   unless `ALLOW_SANDBOX_ENTITLEMENTS=true`. Without this, anyone with a sandbox
   tester account subscribes to production for free.
5. Call `AppStoreServerClient.subscriptionStatus(originalTransactionId)` for
   authoritative renewal state — the JWS proves a purchase happened, not that it
   is still valid today.
6. Persist entitlement + transaction index, invalidate cache.
7. Return the resolved `Entitlement`.

**Idempotent.** Same transaction posted twice is one upsert. The client retries
this call, by design.

### `GET /v1/subscription/status` — auth required

Returns the cached/stored `Entitlement`. Cheap, called on launch and on
sign-in. Returns `{ "status": "none" }` for free users — never 404.

### `POST /v1/webhooks/appstore` — **no session auth**

App Store Server Notifications V2. Authenticated by JWS signature, not by
bearer token, so it must be exempt from any auth requirement — but it must
**still verify the signature chain** before doing anything. An unauthenticated,
unverified endpoint that mutates entitlements is a free-Pro dispenser.

| Notification | Action |
| --- | --- |
| `SUBSCRIBED` (initial / resubscribe) | activate |
| `DID_RENEW` | extend `expiresAt` |
| `DID_CHANGE_RENEWAL_STATUS` | update `autoRenew`; access unchanged |
| `DID_CHANGE_RENEWAL_PREF` | update `productId` (monthly ↔ annual) |
| `DID_FAIL_TO_RENEW` (`GRACE_PERIOD`) | → `grace`, **keep access** |
| `DID_FAIL_TO_RENEW` (no grace) | → `expired` |
| `GRACE_PERIOD_EXPIRED` | → `expired` |
| `EXPIRED` | → `expired` |
| `REFUND` / `REVOKE` | → `revoked`, **revoke immediately** |
| `CONSUMPTION_REQUEST` | log; respond if you later contest refunds |

Always **200** once the signature verifies, even on an unmapped type — Apple
retries non-2xx for up to 3 days and a retry storm on an unknown enum is noise
you don't need. Log unknowns and move on.

`REFUND` and `REVOKE` are the ones that pay for this endpoint's existence.
Without webhooks, a refunded user keeps Pro until their next `status` poll —
and with a client-cached entitlement, potentially much longer.

### `GET /v1/subscription/products` — optional, no auth

Server-declared product IDs so a pricing change doesn't require an app release.
StoreKit still supplies the localized prices; this only says *which* SKUs to
show. Nice-to-have, not launch-blocking.

---

## 6. Enforcement

`EntitlementMiddleware` runs after `AuthMiddleware` in the existing
`config.routes.before(...)` chain in `Main.java`, attaching the resolved
entitlement to the `Context`:

```java
config.routes.before(authMiddleware::handle);
config.routes.before(entitlementMiddleware::handle);   // NEW — no-op when unauthenticated
```

Controllers then guard themselves, matching how they already handle auth
(`AuthMiddleware.currentUserId(ctx)` → `unauthorized(ctx)`):

```java
if (!EntitlementMiddleware.isPro(ctx)) {
    paymentRequired(ctx, "budget_plan");
    return;
}
```

### Response shape

**402 Payment Required** — semantically correct and, critically, unambiguous
against the 401 the client already handles for sign-out. Reusing 403 would
collide with ordinary authorization failures and make the iOS error mapping
guess.

```json
{
  "error":   "Incomatic Pro required",
  "code":    "subscription_required",
  "feature": "budget_plan"
}
```

The iOS `SalaryCalculatorService` maps 402 → `.subscriptionRequired(feature:)`
→ paywall, with `feature` selecting the copy.

### Per-controller changes

**`BudgetPlanController`** (`POST /v1/budget/plan`) — the important one.
Currently auth is *optional* and anonymous callers get a plan. New logic:

```
userId absent                        → 401
isPro                                → generate
!isPro && budgetPlansGenerated == 0  → generate, increment counter   (one free budget)
!isPro && budgetPlansGenerated >= 1  → 402
```

Increment **after** a successful generation, never before — a Vertex failure
already returns 503 and the client falls back to the deterministic on-device
`BudgetEngine`. Burning the free generation on a 503 means the user pays for an
outage.

**`StocksController`** (`GET /v1/stocks/*`) — Pro only, 402 otherwise. Free
users enter grant prices manually, which the client already supports as the
`FINNHUB_API_KEY`-absent fallback path.

**`CalculationHistoryController`**
- `GET /v1/calculations` → free users get the 3 most recent; response gains
  `lockedCount` (an `int` on `CalculationListResponse`) so the client can render
  locked rows without ever receiving the hidden payloads.
- `GET /v1/calculations/{id}` → 402 if the id falls outside the free user's
  visible slice. Otherwise the client is one direct request away from
  everything.
- `DELETE` → unchanged. Never gate deletion; it's a data-rights operation.

**`GrantsController`** — free users capped at 1 grant on write; 402 on the 2nd.
Reads return everything they already have (see grandfathering, §9).

**`CalculateController`** — **no change, ever.** Anonymous, unlimited, free.
This is load-bearing for the whole funnel.

**`AccountController`** — deletion also deletes `entitlements/{userId}` but
**keeps `transactionIndex/{originalTransactionId}`** (§8).

---

## 7. Configuration

New environment variables, following the `Env` pattern in `Main.java`:

| Variable | Default | Purpose |
| --- | --- | --- |
| `APPSTORE_ISSUER_ID` | empty | App Store Connect API issuer UUID |
| `APPSTORE_KEY_ID` | empty | In-App Purchase key id |
| `APPSTORE_PRIVATE_KEY` | empty | ES256 `.p8` contents (base64) for signing Server API JWTs |
| `APPSTORE_BUNDLE_ID` | falls back to `APPLE_AUDIENCE` | Expected `bundleId` in transactions |
| `APPSTORE_ENVIRONMENT` | `Production` | `Sandbox` or `Production` |
| `ALLOW_SANDBOX_ENTITLEMENTS` | `false` | Accept sandbox transactions (dev/TestFlight only) |
| `SUBSCRIPTION_ENFORCEMENT` | `false` | **Master kill switch** |
| `FREE_HISTORY_LIMIT` | `3` | |
| `FREE_GRANT_LIMIT` | `1` | |
| `FREE_BUDGET_PLANS` | `1` | One free AI budget |

**`SUBSCRIPTION_ENFORCEMENT=false` is the most important line here.** It ships
the entire entitlement system to production — verification, storage, webhooks,
analytics — with every gate open. That gives you real subscription data flowing
before a single user is walled, and a one-variable rollback if enforcement
misfires. It's what makes the phased rollout in the strategy doc safe.

When App Store credentials are absent, `/v1/subscription/*` answers 503 and
`EntitlementService` resolves everyone to `none` — the same degradation pattern
`BudgetPlanController` already uses when Vertex is unconfigured, and the same
reason `ENABLE_GCP=false` still boots a working service.

---

## 8. Edge cases

**Account deletion → re-signup.** Sign in with Apple returns a stable `sub` per
Apple ID per app team, so a returning user gets the same `userId`. Keeping
`transactionIndex` after deletion means their still-valid subscription
re-attaches on the next `/v1/subscription/verify`. Delete the index and they've
paid for a subscription the server can no longer recognise — a guaranteed
support ticket with no self-serve fix.

**Two Apple IDs, one subscription.** Not possible without Family Sharing;
recommend Family Sharing stays off at launch (strategy doc §11). If it's enabled
later, the transaction's `originalTransactionId` can belong to the family
organiser rather than the signed-in user, and the index becomes many-to-one —
design for it before flipping the switch, not after.

**Sandbox vs production.** Sandbox transactions carry `environment: "Sandbox"`.
Rejecting them in production isn't optional; sandbox tester accounts are
trivially created.

**Clock skew.** Compare `expiresAt` against server time with a 5-minute grace,
never against a client-supplied timestamp.

**Webhook before verify.** A `SUBSCRIBED` notification can arrive before the
client's `verify` call. `transactionIndex` won't have the mapping yet, so
`userId` is unknown. Park the notification in `pendingNotifications/{originalTransactionId}`
and drain it when `verify` lands. Dropping it means a subscriber whose first
renewal event vanished.

**Duplicate webhooks.** Apple retries. Every handler is idempotent — writes are
upserts keyed by `originalTransactionId`, and a notification whose
`signedDate` is older than the stored `updatedAt` is discarded so a delayed
retry can't resurrect a revoked entitlement.

---

## 9. Grandfathering

At the moment enforcement flips on, existing users may already exceed the free
limits — more than 3 saved calculations, more than 1 grant. **Read paths must
never retroactively hide data a user already has.** Existing rows stay visible
and openable; the limits apply to *new* writes only.

Implementation: stamp `grandfatheredHistoryCount` / `grandfatheredGrantCount`
onto `users/{userId}` in a one-off migration at enforcement time, and treat the
effective limit as `max(configuredLimit, grandfatheredCount)`. It's a small
amount of work and it's the difference between a paywall rollout and a wave of
one-star reviews about deleted data.

---

## 10. Testing

The existing suite (`modules/api/src/test/java/app/salary/api/`) already covers
`auth/`, `service/`, `store/`, `client/`, and `controller/` — this follows the
same layout.

- **`AppStoreJwsVerifierTest`** — valid chain accepts; tampered payload rejects;
  expired leaf cert rejects; wrong bundle id rejects; sandbox-in-production
  rejects. This is the security-critical class; it deserves the most tests.
- **`EntitlementServiceTest`** — status transitions across every notification
  type; grace grants access; revoked denies immediately; out-of-order
  notification by `signedDate` is discarded.
- **`SubscriptionControllerTest`** — 401 unauthenticated; idempotent double
  verify; 503 when unconfigured.
- **`AppStoreWebhookControllerTest`** — unsigned payload rejected; unknown
  notification type still 200; pending-notification park-and-drain.
- **Per-controller 402 tests** — budget plan (including free-generation
  accounting: counter increments on success, *not* on Vertex 503), stocks,
  history depth, grant limit.
- **`CalculateControllerTest`** — regression guard asserting `/v1/calculate`
  stays anonymous and ungated. Worth an explicit test with a comment, because
  it's the one thing a future refactor must not quietly break.
- `InMemoryEntitlementStore` keeps the whole suite runnable with
  `ENABLE_GCP=false`, as the existing in-memory stores do.

---

## 11. Sequencing

1. `Entitlement` DTO, `EntitlementStore` (+ both impls), `EntitlementService`
2. `AppStoreJwsVerifier` (offline chain verification) — heavily tested
3. `AppStoreServerClient` (renewal status lookup)
4. `SubscriptionController` — `/verify`, `/status`
5. `AppStoreWebhookController` + pending-notification handling
6. `EntitlementMiddleware`, wired into `Main.java`'s `before` chain
7. Per-controller 402 guards, all behind `SUBSCRIPTION_ENFORCEMENT=false`
8. Deploy to production with enforcement **off** — verification and webhooks
   run live, nothing is walled
9. iOS client work begins against a live, honest API
10. Flip `SUBSCRIPTION_ENFORCEMENT=true` per the strategy doc's phased rollout

Steps 1–8 ship to production with zero behaviour change for any existing user.
