# KStack Devs

Devs is KStack's bilingual, mobile-first learning service for free Courses and multi-video Series. This workspace contains a working TanStack Start frontend, a Spring Boot API, a resumable Telegram-to-R2/Mux importer, Docker Compose for local integration, and a hardened Kubernetes base.

The detailed product and architecture rationale remains in [project-plan.md](./project-plan.md). The standalone visual prototype is [prototype.html](./prototype.html).

## What is implemented

- English and Arabic routes with correct LTR/RTL direction and Alexandria typography.
- Exact KStack green/neutral palette, light/dark themes, official Devs mark, and responsive ShadCN/Base UI components.
- Public landing page, featured carousel hidden below four items, filterable catalog, Course pages, Series pages, and lesson playback routes.
- Admin metadata editor, direct-to-R2 video upload with progress, Mux ingest, lesson attachment, publication validation, preview, and archive.
- Spring Boot/PostgreSQL domain with Flyway migrations, validation, optimistic locking, health probes, provider adapters, and RFC 9457 problem responses.
- Public-by-default content with `PUBLIC`, `AUTHENTICATED`, and `STUDENT_ONLY` policy values so account restrictions can be enabled without changing the content/media model.
- ES256 verification of the existing KStacks JWT, role-claim support, and a safe UUID subject allowlist bridge for the current role-less token.
- Resumable, checksum-deduplicated Telegram channel migration that stores masters in R2 and idempotently registers them for Mux processing.
- Unit tests, production Dockerfiles, local Compose, and Kubernetes Deployments/Services/probes/resources/security contexts/PDBs.

## Repository map

| Path                     | Purpose                                                                                   |
| ------------------------ | ----------------------------------------------------------------------------------------- |
| sibling `devs-frontend`  | React 19, TanStack Start/Router, Tailwind 4, ShadCN/Base UI, Mux Player                   |
| repository root          | Java 25, Spring Boot 4, PostgreSQL, Flyway, Spring Security, R2 and Mux adapters          |
| `tools/telegram-import/` | Authorized one-time Telethon migration CLI                                                |
| `deploy/kubernetes/`     | Kustomize-ready production base; intentionally excludes team-specific Ingress and secrets |
| `compose.yml`            | Local PostgreSQL + API + frontend integration                                             |

## Runtime architecture

```text
Browser
  ├── devs-frontend (TanStack Start SSR + client navigation)
  ├── KStacks gateway (/devs/api/**)
  │     └── devs-service (Spring Boot)
  │           ├── PostgreSQL: catalog and publication state
  │           ├── private R2: permanent original videos
  │           └── Mux: encoding, adaptive playback, webhooks
  └── Mux CDN: HLS playback

Telegram importer ──> private R2 ──> Devs import API ──> Mux
```

Video bytes never pass through Spring. The browser receives a short-lived presigned R2 PUT, while Mux receives a short-lived presigned R2 GET. R2 remains the recoverable master archive; Mux handles streaming renditions.

## Fastest local preview

This mode uses realistic in-memory sample content and does not need PostgreSQL, R2, or Mux.

```bash
cd ../devs-frontend
cp .env.example .env
npm install
npm run dev
```

Open `http://localhost:3000/en`, `http://localhost:3000/ar`, or `http://localhost:3000/en/admin`.

## Integrated local stack

Copy `.env.example` to `.env`, choose a local PostgreSQL password, then run:

```bash
docker compose up --build
```

The integrated database starts empty by design. Create drafts at `/en/admin`. Local Compose explicitly enables insecure admin access; that setting is forcibly disabled by the Spring `production` profile and must never be used in a shared environment. Media upload requires real R2 and Mux credentials.

## Existing KStacks integration

The inspected gateway already reads the HTTP-only `access_token` cookie, validates it, and forwards the token as `Authorization: Bearer …`. Devs validates that signature again using the central access-token public key.

Two gateway changes are required:

1. Route `/devs/api/**` to `http://devs-service:8080` (Kubernetes service DNS), without stripping the prefix.
2. Permit anonymous access to `/devs/api/v1/public/**` and `/devs/api/v1/webhooks/mux`; keep `/devs/api/v1/admin/**` authenticated.

Equivalent Spring Cloud Gateway intent:

```properties
spring.cloud.gateway.server.webflux.routes[3].id=devs-service
spring.cloud.gateway.server.webflux.routes[3].uri=http://devs-service:8080
spring.cloud.gateway.server.webflux.routes[3].predicates[0]=Path=/devs/api/**
```

Add these matchers before the gateway's authenticated fallback:

```java
.pathMatchers("/devs/api/v1/public/**", "/devs/api/v1/webhooks/mux").permitAll()
```

### JWT/admin bridge

The current central token has `sub`, `type`, `name`, `email`, `gender`, `iss`, and timestamps, but no role claim. Configure:

- `DEVS_JWT_PUBLIC_KEY`: the same Base64 X.509 EC public key configured for the central access token; PEM is accepted too.
- `DEVS_JWT_ISSUER`: the central `jwt.issuer` value.
- `DEVS_ADMIN_SUBJECTS`: comma-separated KStacks user UUIDs for the initial Devs administrators.

Devs also understands `roles` and `realm_access.roles`. Once auth emits `DEVS_ADMIN`/`ADMIN` and `STUDENT`, remove the subject bridge. Production fails closed if JWT verification is disabled or the public key is missing.

## Media provisioning

### Cloudflare R2

Create one private bucket and an R2 API token limited to that bucket. Configure the bucket to accept browser PUTs only from the Devs frontend origin:

```json
[
  {
    "AllowedOrigins": ["https://replace-with-devs-origin.example"],
    "AllowedMethods": ["PUT"],
    "AllowedHeaders": ["content-type"],
    "ExposeHeaders": ["etag"],
    "MaxAgeSeconds": 3600
  }
]
```

Set `R2_ENDPOINT`, `R2_BUCKET`, `R2_ACCESS_KEY_ID`, and `R2_SECRET_ACCESS_KEY`. Keep the bucket private and do not add a public custom domain for source videos.

### Mux

Create a Mux environment, API access token, and webhook signing secret. Configure the webhook URL as:

```text
https://<gateway-host>/devs/api/v1/webhooks/mux
```

Subscribe to `video.asset.ready` and `video.asset.errored`. Set `MUX_TOKEN_ID`, `MUX_TOKEN_SECRET`, and `MUX_WEBHOOK_SECRET`.

The day-one build uses Mux `public` playback IDs because the learning library is public. If KStacks later restricts videos to accounts or students, switch to signed Mux playback and issue short-lived playback JWTs from Devs; the content visibility model and player already have a playback-token seam, but that signing key configuration is a required security step before private rollout.

## Production deployment

Read [deploy/kubernetes/README.md](../deploy/kubernetes/README.md). From the service repository root, build the frontend image with the public gateway API URL because Vite embeds it at build time:

```bash
docker build \
  --build-arg VITE_API_URL=https://<gateway-host>/devs/api/v1 \
  --build-arg VITE_USE_MOCKS=false \
  -t ghcr.io/kstacks-org/devs-frontend:<sha> ../devs-frontend
docker build -t ghcr.io/kstacks-org/devs-service:<sha> .
```

Never build a production frontend without `VITE_API_URL`; the build deliberately fails instead of silently shipping mock data.

## Launch checklist requiring team input

- Confirm the final frontend/gateway hosts, TLS, cookie domain/SameSite behavior, and Ingress ownership.
- Supply managed PostgreSQL, R2, Mux, central JWT public key/issuer, and initial administrator UUIDs through the cluster secret manager.
- Apply the gateway route and anonymous matcher changes above.
- Verify R2 CORS and complete a real multi-gigabyte upload on the target browser/network.
- Verify Mux ready/error webhook delivery and one playable asset.
- Review Arabic/English marketing copy, legal/privacy links, content ownership, and instructor attribution.
- Decide whether public Mux playback remains acceptable; signed playback is mandatory before restricting content.
- Run the Telegram dry run, compare counts, migrate, and reconcile the JSONL manifest before publishing.

## Intentional next slices

The core catalog/upload/publication path is complete. The larger plan also specifies reusable instructor/taxonomy management, custom cover uploads, unit reorder/delete, admin team management, audit history, and provider-backed analytics. Those are explicit follow-up slices rather than half-secured placeholders in this build. Do not represent the current `views`/`watchedMinutes` fields as live analytics until a Mux Data synchronization job is added.

## Verification

Frontend:

```bash
cd ../devs-frontend
npm run check
VITE_USE_MOCKS=true npm run build
```

Backend (Maven 3.9+, Java 25):

```bash
mvn test
mvn -DskipTests package
```
