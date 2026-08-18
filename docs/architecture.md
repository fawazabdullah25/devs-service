# KStack Devs

Devs is KStack's bilingual, mobile-first learning service for free Courses and multi-video Series. This workspace contains a working TanStack Start frontend, a Spring Boot API, validated static HLS delivery, the retained R2-to-Mux path, Docker Compose for local integration, and a hardened Kubernetes base.

The detailed product and architecture rationale remains in [project-plan.md](./project-plan.md). The standalone visual prototype is [prototype.html](./prototype.html).

## What is implemented

- English and Arabic routes with correct LTR/RTL direction and Alexandria typography.
- Exact KStack green/neutral palette, light/dark themes, official Devs mark, and responsive ShadCN/Base UI components.
- Public landing page, featured carousel hidden below four items, filterable catalog, Course pages, Series pages, and lesson playback routes.
- Admin metadata editor, validated static-HLS registration, optional direct-to-R2/Mux upload, lesson attachment, publication validation, preview, and archive.
- Spring Boot/PostgreSQL domain with Flyway migrations, validation, optimistic locking, health probes, provider adapters, and RFC 9457 problem responses.
- Public-by-default content with `PUBLIC`, `AUTHENTICATED`, and `STUDENT_ONLY` policy values so account restrictions can be enabled without changing the content/media model.
- ES256 verification of the existing KStacks JWT, role-claim support, and a safe UUID subject allowlist bridge for the current role-less token.
- Provider-neutral media records with immutable HLS paths, normalized VTT tracks, and a resumable Telegram migration path for retained source masters.
- Unit tests, production Dockerfiles, local Compose, and Kubernetes Deployments/Services/probes/resources/security contexts/PDBs.

## Repository map

| Path                     | Purpose                                                                                   |
| ------------------------ | ----------------------------------------------------------------------------------------- |
| sibling `devs-frontend`  | React 19, TanStack Start/Router, Tailwind 4, ShadCN/Base UI, Vidstack/HLS and Mux adapters |
| repository root          | Java 25, Spring Boot 4, PostgreSQL, Flyway, Spring Security, static HLS, R2, and Mux      |
| `tools/telegram-import/` | Authorized one-time Telethon migration CLI                                                |
| `deploy/kubernetes/`     | Kustomize-ready production base; intentionally excludes team-specific Ingress and secrets |
| `compose.yml`            | Local PostgreSQL + API + frontend integration                                             |

## Runtime architecture

```text
Browser
  ├── devs-frontend (TanStack Start SSR + client navigation)
  │     └── Vidstack + local hls.js: adaptive HLS playback and VTT captions
  ├── KStacks gateway (/devs/api/**)
  │     └── devs-service (Spring Boot)
  │           ├── PostgreSQL: catalog and publication state
  │           ├── validates immutable HLS/VTT objects through the configured media origin
  │           └── optional legacy adapters: private R2 sources and Mux ingest/webhooks
  └── Cloudflare custom domain + R2: public immutable HLS renditions

Authorized encoder on Oracle VPS ──> R2 ──> Cloudflare cache ──> browser
```

Video bytes never pass through Spring. For static HLS, an operator encodes and uploads a new immutable version, publishes `master.m3u8` last, then registers relative paths in Devs. Spring performs bounded server-side validation before marking the asset `READY`; the browser receives resolved URLs. The old direct-upload/Mux route remains available behind its provider flags.

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

The integrated database starts empty by design. Create drafts at `/en/admin`. Local Compose explicitly enables insecure admin access; that setting is forcibly disabled by the Spring `production` profile and must never be used in a shared environment. Static HLS registration requires a reachable media origin and an already uploaded package. The legacy upload route additionally requires private-R2 and Mux credentials.

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

### Static HLS delivery (default)

Use a dedicated R2 bucket connected to a Cloudflare custom domain. Upload versioned packages such as `lessons/<lesson>/<encoding-version>/`, with segments and initialization objects first, then captions and rendition playlists, and `master.m3u8` last. Never overwrite a published version; create a new versioned directory instead.

Configure the service:

```text
STATIC_HLS_ENABLED=true
STATIC_HLS_BASE_URL=https://video.example.com/
STATIC_HLS_ALLOWED_PATH_PREFIX=lessons
STATIC_HLS_VALIDATION_TIMEOUT=10s
```

The allowlisted prefix and strict relative-path parser prevent an admin request from turning validation into an arbitrary server-side fetch. Redirects are not followed. Registration verifies the master is a multivariant HLS playlist, fetches each rendition playlist, and verifies every submitted caption starts with `WEBVTT`. Only then is the media row saved as `READY`.

The frontend uses Vidstack and dynamically imports the locally installed `hls.js`; native HLS remains available where Vidstack selects it. External caption URLs are rendered as selectable tracks. The public CDN remains downloadable by design while content is public.

The exact encoding/upload procedure and measurements are recorded in
[`r2-hls-pilot-guide.md`](r2-hls-pilot-guide.md) and
[`r2-hls-pilot-results.md`](r2-hls-pilot-results.md).

### Private Cloudflare R2 sources (legacy Mux path)

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

### Mux (legacy path)

Create a Mux environment, API access token, and webhook signing secret. Configure the webhook URL as:

```text
https://<gateway-host>/devs/api/v1/webhooks/mux
```

Subscribe to `video.asset.ready` and `video.asset.errored`. Set `MUX_TOKEN_ID`, `MUX_TOKEN_SECRET`, and `MUX_WEBHOOK_SECRET`.

If KStacks later restricts videos to accounts or students, public static HLS URLs are insufficient by themselves. Put authorization at the media edge—for example, signed URLs or cookies enforced by a Worker—before changing content visibility; hiding playlist URLs in the UI is not access control. Signed Mux playback remains an alternative and the player contract retains its playback-token seam.

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
- Supply managed PostgreSQL, central JWT public key/issuer, and initial administrator UUIDs through the cluster secret manager; add private R2/Mux secrets only if that legacy path remains enabled.
- Apply the gateway route and anonymous matcher changes above.
- Verify the public HLS origin's CORS, cache policy, and content types, then register one real immutable package through Devs.
- Repeat seeking, quality, captions, playback-rate, mobile rotation, and constrained-network tests inside the actual Devs player.
- If Mux remains enabled, verify ready/error webhook delivery and one playable asset.
- Review Arabic/English marketing copy, legal/privacy links, content ownership, and instructor attribution.
- Treat signed edge delivery as mandatory before restricting static-HLS content to accounts.
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
