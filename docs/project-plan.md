# KStack Devs — Product and Implementation Plan

**Status:** implementation-ready project brief with explicit launch gates

**Research baseline:** 9 August 2026
**Product:** a free, public learning service within the KStack ecosystem

> **Implementation checkpoint — 11 August 2026:** The production-shaped core described in this plan is now implemented in this workspace: bilingual public discovery/playback routes, ShadCN UI, Spring/PostgreSQL catalog, admin metadata and direct R2→Mux video workflow, current KStacks JWT verification, resumable Telegram migration, containers, and Kubernetes base. The exact shipped surface and remaining follow-up slices are tracked in [README.md](../README.md). Where this long-range plan describes Owner/Editor team management, audit history, taxonomy/instructor CRUD, custom cover processing, unit reorder/delete, Mux Data synchronization, or signed private playback, those remain planned follow-ups and should not be mistaken for current behavior.

## 1. Executive summary

Devs will be a standalone KStack frontend and backend that reuse the organization’s existing authentication, gateway, discovery, and design ecosystem. Anonymous students can browse and watch all published material. Privileged KStacks users receive a separate Devs role—Owner or Editor—and manage the catalog through an admin dashboard.

V1 is intentionally a focused video-learning catalog, not a full LMS:

- A **Course** contains exactly one long-form video.
- A **Series** contains two or more ordered video lessons.
- Public features cover discovery, search/filtering, detail pages, playback, instructor attribution, Arabic/English presentation, and shareable lesson URLs.
- Admin features cover media ingestion, content creation, taxonomy, reusable instructor profiles, publication, archival, team access, and basic viewing analytics.
- Student accounts, enrollment, progress, quizzes, certificates, comments, ratings, text-only lessons, captions, and AI-generated articles are deferred.

The launch video architecture is **Mux for encoding, playback, and viewing metrics plus private Cloudflare R2 for permanent original masters**. The application keeps provider-neutral media records and adapter boundaries so KStacks can migrate to Bunny Stream, Cloudflare Stream, or another provider without rebuilding the catalog.

### Success criteria

The first release is successful when:

1. An anonymous student can find and watch any published Course or Series lesson on mobile or desktop without signing in.
2. The landing page shows a manually ordered featured rail only when at least four eligible published items exist.
3. Arabic and English interfaces work correctly, including RTL layout, optional metadata translations, and documented fallback behavior.
4. An Editor can upload a master, see processing state, create or edit content, reorder series lessons, and publish without handling infrastructure credentials.
5. An Owner can manage the Devs admin team and safely delete eligible archived/draft content.
6. A one-time importer migrates videos from multiple authorized Telegram channels into a private media inbox and can resume safely after interruption.
7. Video bytes never pass through the Spring application; originals remain private in R2 and public playback is delivered by the video provider.
8. The public and admin interfaces pass agreed responsive, accessibility, security, and visual-approval checks.

## 2. Confirmed scope and product decisions

| Area              | V1 decision                                                                                         |
| ----------------- | --------------------------------------------------------------------------------------------------- |
| Service shape     | Separate `devs-frontend` and `devs-service`, integrated with existing KStacks auth and gateway      |
| Public access     | Catalog and playback are public; students do not authenticate                                       |
| Content types     | Course = exactly one video; Series = at least two ordered video lessons                             |
| Content lifecycle | Draft → Published → Archived; no scheduled publication                                              |
| Admin roles       | Owner manages team and content; Editor manages content; permanent deletion is Owner-only            |
| Admin onboarding  | Candidate signs into `/admin` with KStacks first; Owner can then grant the known identity a role    |
| Languages         | Arabic and English UI; primary content locale plus optional translation and primary-locale fallback |
| Theme             | Light and dark, initially following saved preference or the operating-system preference             |
| Discovery         | Combined catalog with search and type/topic/language/level filters                                  |
| Taxonomy          | Reusable, localized, admin-managed topics and levels                                                |
| Instructors       | Reusable profiles assignable to multiple items                                                      |
| Covers            | Custom 16:9 cover is required before publication                                                    |
| Featured content  | Manually selected and ordered mix of Courses and Series; entire rail hidden below four items        |
| Series completion | End screen prompts for the next lesson; no autoplay                                                 |
| Descriptions      | Sanitized Markdown with edit/preview experience                                                     |
| Analytics         | Published counts plus provider-sourced views and watched minutes by item                            |
| Telegram          | One-time, resumable import from multiple channels into a private media inbox                        |
| Video             | Mux delivery/analytics; R2 permanent private masters; provider-portable adapters                    |
| Captions/text     | No captions or text lessons in V1; transcription/article conversion is a later phase                |
| Initial migration | Import the authorized Telegram library into dashboard drafts; editors organize and publish it later |
| Marketing copy    | Draft Arabic and English copy during implementation; KStacks reviews before launch                  |
| Legal             | Production launch is gated on KStacks-approved privacy, terms, copyright, and analytics wording     |

### Explicit V1 exclusions

- No student profiles, progress synchronization, bookmarks, enrollment, completion state, or certificates.
- No payment, subscriptions, advertising, premium content, or private student playback.
- No quizzes, assignments, comments, ratings, community features, or notifications.
- No scheduled publishing, multi-stage editorial approval, content version restoration, or live streaming.
- No automatic Telegram synchronization after migration.
- No captions, transcript search, AI article generation, or translated articles in V1.

## 3. Experience and information architecture

### 3.1 Public frontend

Use React, TypeScript, TanStack Start/Router/Query, Tailwind, and the component conventions already present in the [KStacks portal frontend](https://github.com/KStacks-org/portal-frontend). Keep Devs separately deployable while matching the organization’s route localization and SSO behavior.

#### Landing page

1. **Header:** Devs mark, Catalog link, language selector, theme toggle, and an unobtrusive Admin link.
2. **Hero:** concise Arabic/English message that KStacks provides practical technology learning free to students; primary CTA opens the catalog.
3. **Featured rail:** manually ordered content cards, touch-swipeable and keyboard accessible; rendered only when four or more published featured items are available. It must not autoplay.
4. **Explore section:** latest published Courses and Series in a responsive grid with type tabs and a clear link to the full catalog.
5. **Student-first section:** short reviewed copy about free access and practical learning.
6. **Footer:** KStack/Devs identity, portal and social links, contact route, and launch-gated legal links.

#### Catalog

- One combined list for Courses and Series.
- Search localized titles, summaries, instructor names, and topics.
- Filters: content type, one or more topics, spoken language, and level.
- Default order: newest published first. Other supported orders: title and manually defined relevance/featured order.
- Filter state lives in the URL so results are shareable and browser navigation works.
- Use server rendering, paginated API results, useful empty states, and skeletons that do not cause layout shift.

#### Content routes

- `/{locale?}/courses/{contentSlug}` — Course detail and single player.
- `/{locale?}/series/{seriesSlug}` — Series overview, instructor/profile data, description, and ordered lesson list.
- `/{locale?}/series/{seriesSlug}/lessons/{lessonSlug}` — dedicated lesson player with a persistent playlist and next-lesson prompt.
- `/{locale?}/catalog` — combined searchable catalog.

Each published page includes a required cover, type, spoken language, level, topics, instructor cards, localized summary and Markdown description, video duration, and share metadata. Stable locale-neutral slugs are editable before first publication; subsequent changes retain redirect history.

Use canonical links, Arabic/English `hreflang`, sitemap entries, Open Graph metadata, and appropriate Course/VideoObject structured data. Archived and draft items return a normal public 404 and are removed from discovery.

### 3.2 Admin dashboard

Admin routes live in the Devs frontend under `/{locale?}/admin` and use the same responsive shell and bilingual behavior.

| Area        | Behavior                                                                                                                       |
| ----------- | ------------------------------------------------------------------------------------------------------------------------------ |
| Dashboard   | Published/draft/archived counts, failed/processing media, recent changes, views and watched minutes                            |
| Media inbox | Upload new video, inspect Telegram imports, retry ingest, preview ready media, attach media to content                         |
| Content     | Create Course/Series, translate metadata, assign instructor/topic/level, upload cover, order lessons, feature, publish/archive |
| Instructors | Localized name and bio, portrait, verified public links, active/inactive state                                                 |
| Taxonomy    | Localized topics and levels, stable slug, sort order, usage protection                                                         |
| Team        | Owner-only list of known identities, role grant/change/revoke, and audit history                                               |
| Audit       | Actor, action, entity, timestamp, and safe before/after summary for material admin mutations                                   |

Editors may create, edit, publish, and archive content and manage media, instructors, topics, and levels. Owners have the same permissions plus team management and permanent safe deletion. Neither role can delete a media asset still referenced by another item.

## 4. System architecture

The public organization repositories show a Spring/Java/PostgreSQL service pattern behind Spring Cloud Gateway and Eureka, plus React/TanStack frontends. Devs should extend that shape rather than creating a parallel identity system. Relevant references are the [auth service](https://github.com/KStacks-org/auth-service), [gateway](https://github.com/KStacks-org/gateway-service), and [portal frontend](https://github.com/KStacks-org/portal-frontend).

```text
Browser
  ├─ devs-frontend (SSR React/TanStack)
  │    ├─ public pages and Mux Player
  │    └─ authenticated admin dashboard
  └─ KStacks Gateway: /devs/**
       └─ devs-service (Spring Boot)
            ├─ PostgreSQL: catalog, roles, workflow, audit, cached metrics
            ├─ R2 adapter: masters, covers, portraits
            ├─ Mux adapter: ingest, signed playback, metrics
            ├─ Mux webhook receiver
            └─ KStacks JWT/JWKS validation and local Devs authorization

One-time Telegram importer
  ├─ Telegram user-authorized MTProto session
  ├─ local SQLite resume ledger and temporary files
  └─ short-lived Devs import session → presigned R2 upload → Mux ingest
```

### 4.1 Service boundaries

- **`devs-frontend`:** UI, SSR, localized routing, SEO, Mux Player integration, and admin forms. It never holds Mux, R2, Telegram, or signing secrets.
- **`devs-service`:** authoritative content and authorization API, publication validation, upload coordination, provider adapters, webhooks, audit, and metrics cache.
- **PostgreSQL:** one Devs-owned database/schema. No cross-service database reads.
- **Gateway:** register `/devs/**`; permit explicit public and integration routes and require valid KStacks authentication for admin routes.
- **Importer:** keep under `devs-service/tools/telegram-importer` unless the organization’s repo policy requires a separate private repo. It is an operator tool, not a continuously deployed service.

### 4.2 Authentication and authorization

The existing auth flow remains the source of identity. The gateway validates the shared `access_token` cookie and forwards authenticated identity; the Devs service validates the signed token/JWKS rather than trusting arbitrary forwarding headers.

Because central JWTs do not currently contain roles, Devs owns `Owner`/`Editor` membership:

1. Seed the first Owner email/UUID through a deployment secret and bind it on verified login.
2. Any candidate visits `/admin`, completes normal KStacks login, and calls `GET /devs/admin/session`.
3. Devs records the verified UUID/email/name as a known candidate but returns `role: null` and denies dashboard access.
4. An Owner grants that known identity Owner or Editor. There are no invitations to unverified addresses.
5. Role changes take effect immediately; all team changes are audited.

Protect cookie-authenticated mutations with CSRF tokens plus strict Origin/Host validation. Apply rate limits to login/session, playback-token, upload-session, webhook, search, and import endpoints. Admin responses use `Cache-Control: no-store`.

## 5. Domain model and invariants

Use UUID primary keys, UTC timestamps, optimistic locking versions on edited records, and database constraints for ordering and uniqueness.

| Entity                          | Important fields and rules                                                                                                |
| ------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `Content`                       | kind `COURSE                                                                                                              | SERIES`, stable slug, status, primary locale, spoken language `AR | EN  | MIXED`, cover asset, level, featured rank, publication timestamps |
| `ContentTranslation`            | content, locale `ar                                                                                                       | en`, title, summary, sanitized Markdown source; unique per locale |
| `ContentUnit`                   | parent content, stable lesson slug, position, video asset; Course has exactly one, Series has at least two when published |
| `UnitTranslation`               | localized lesson title and optional summary                                                                               |
| `MediaAsset`                    | R2 object key/checksum/size, provider type, Mux asset/playback IDs, state, duration, resolution, error, import provenance |
| `CoverAsset`                    | private source and generated public 16:9 derivatives; publication requires a valid cover                                  |
| `Instructor` + translation      | localized name/bio, portrait, approved links, active flag                                                                 |
| `Topic` / `Level` + translation | stable slug, localized label/description, sort order, active flag                                                         |
| `AdminIdentity`                 | verified KStacks UUID/email/name, optional role, first/last seen, role audit data                                         |
| `AuditEvent`                    | actor, action, entity type/id, timestamp, correlation ID, non-secret change summary                                       |
| `ProviderMetricDaily`           | provider date, asset/content, views, watched minutes, refresh timestamp                                                   |
| `ImportSource`                  | Telegram channel ID, message ID, caption/date/file metadata, checksum; unique on channel+message                          |

Publication is transactional and fails unless:

- Primary-locale title, summary, description, content language, active level, at least one topic, at least one instructor, and required custom cover are present.
- A Course has exactly one `READY` video unit.
- A Series has at least two uniquely positioned `READY` video units.
- Slugs are unique and all assigned entities are active.
- Featured rank is unique among currently featured published items.

Translations are optional. If the requested translation is absent, the API returns the primary-locale value and a `translationFallback: true` signal; the UI does not show empty translated fields.

## 6. Public and administrative interfaces

Use a versioned JSON contract under `/devs/api/v1` even when the gateway exposes it as `/devs/**`. Return a consistent error envelope with `code`, localized-safe `message`, `fieldErrors`, `correlationId`, and timestamp.

### Public API

- `GET /devs/api/v1/public/home?locale=` — hero configuration key, featured items, latest Courses/Series, catalog counts.
- `GET /devs/api/v1/public/catalog?q=&type=&topic=&level=&language=&sort=&page=&size=&locale=` — paginated filters and metadata.
- `GET /devs/api/v1/public/courses/{slug}?locale=` — Course detail and video metadata.
- `GET /devs/api/v1/public/series/{slug}?locale=` — Series overview and ordered lesson summaries.
- `GET /devs/api/v1/public/series/{seriesSlug}/lessons/{lessonSlug}?locale=` — lesson detail, playlist context, previous/next links.
- `POST /devs/api/v1/public/media/{mediaId}/playback-token` — short-lived, rate-limited signed Mux playback token for a published asset.
- `GET /devs/api/v1/public/taxonomy?locale=` — active filter options.

### Admin API

- `GET /devs/api/v1/admin/session` — verify identity, register known candidate, return role/capabilities.
- CRUD `/admin/content`, `/admin/instructors`, `/admin/topics`, and `/admin/levels` with optimistic version checks.
- `PUT /admin/content/{id}/units/reorder`, `POST /publish`, `POST /archive`, and Owner-only `DELETE`.
- `POST /admin/uploads` → presigned multipart instructions; `POST /admin/uploads/{id}/complete` → checksum verification and Mux ingest.
- `GET /admin/media` and retry/attach/detach operations.
- `GET /admin/team/candidates`, CRUD `/admin/team/members` (Owner only).
- `GET /admin/analytics?from=&to=&contentId=` — cached provider metrics and catalog counts.
- `POST /admin/import-sessions` (Owner only) — issue a hashed, single-purpose, time-limited importer credential shown once.

### Integration API

- `POST /devs/api/v1/integrations/mux/webhook` — verify Mux signature, persist the event ID, handle idempotently, and tolerate out-of-order processing/ready/error notifications.
- Import-media creation endpoints accept only the scoped import credential and cannot publish, edit the team, or read unrelated admin data.

## 7. Video, storage, and analytics plan

### 7.1 Launch architecture: Mux + R2

Mux is the best pilot default because it combines encoding, adaptive playback, signed playback, player integration, webhooks, and video analytics. Its published pricing currently includes a small free allowance and usage credit, making an under-100-video launch inexpensive; pricing must be reconfirmed before creating paid resources. See [Mux pricing](https://www.mux.com/docs/pricing/overview), [direct upload/ingest](https://www.mux.com/docs/guides/upload-files-directly), [signed playback](https://www.mux.com/docs/guides/secure-video-playback), and [Data metrics](https://www.mux.com/docs/api-reference/data/metrics).

R2 is the durable source of truth for original masters. Keep the bucket private, disable automatic expiry for masters, store a checksum, and never return master object keys or credentials to public clients. R2 currently advertises 10 GB free, then usage-based storage with no internet egress charge; verify current rates at provisioning in [R2 pricing](https://developers.cloudflare.com/r2/pricing/).

#### Upload/ingest flow

1. Admin or importer requests a multipart upload session from Devs.
2. Browser/CLI uploads directly to private R2 using short-lived presigned parts; Spring never proxies the bytes.
3. Client completes the upload; Devs validates recorded size/checksum and saves the master record.
4. Devs gives Mux a short-lived presigned R2 GET URL for remote ingest.
5. Mux webhooks move the asset through `UPLOADING → STORED → PROCESSING → READY|FAILED`.
6. Publication is blocked until the provider asset is ready.
7. The frontend requests a short-lived signed playback token and renders Mux Player with Mux Data enabled and no personally identifying student ID.

Use signed playback even though content is public to limit hotlinking and third-party embedding. This cannot prevent screen recording; do not make DRM-like promises.

### 7.2 Provider portability

Define backend `VideoProvider` operations for ingest, status, playback authorization, deletion, and metrics. Store KStacks media IDs separately from provider IDs. Define a frontend player adapter so provider-specific props do not leak into content pages. Retained R2 masters allow bulk re-ingest and a temporary dual-provider migration.

| Provider              | Strengths                                                                                                     | Trade-offs                                                                                                                                   | Best fit                                      |
| --------------------- | ------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------- |
| **Mux**               | Strong developer API, player, signed playback, webhooks, and watch-time analytics; economical pilot allowance | Per-minute delivery becomes expensive at high watch volume; only ten stored videos on the free plan; downloadable/static renditions add cost | V1 and early growth                           |
| **Bunny Stream**      | Low storage/CDN unit prices, resumable uploads, token security, webhooks, optional original retention         | Pricing depends on encoded storage and delivered GB; cheapest Volume network uses fewer PoPs, so Saudi startup/buffering must be benchmarked | Cost optimization after traffic stabilizes    |
| **Cloudflare Stream** | Simple minute-based bill, direct uploads, signed URLs, global Cloudflare integration                          | At published rates of $5/1,000 stored minutes and $1/1,000 delivered minutes, it is materially costlier for long free-form viewing           | Operational simplicity when cost is secondary |

Sources: [Bunny Stream pricing](https://docs.bunny.net/stream/pricing), [Bunny CDN pricing](https://docs.bunny.net/cdn/pricing), [Cloudflare Stream pricing](https://developers.cloudflare.com/stream/pricing/), and [Cloudflare Stream security](https://developers.cloudflare.com/stream/viewing-videos/securing-your-stream/).

Illustrative only: 50 stored hours and 2,000 watched hours in a month is roughly 3,000 stored minutes and 120,000 delivered minutes. At currently published rates, Mux is approximately $9–$29 before/after applicable allowance or account credit, while Cloudflare Stream is about $135. Bunny must be modeled from actual encoded GB/bitrate. These are not quotes; re-run the calculation with real catalog duration, rendition size, regional delivery, and account tier before launch.

Review provider spend and Saudi playback quality monthly. Consider migration only after three stable months show meaningful savings after migration labor, and only if a Saudi mobile/desktop benchmark demonstrates acceptable startup, rebuffering, and resolution on the target Bunny/alternative network.

### 7.3 Student download options

All three options remain documented for KStacks review. Until KStacks explicitly chooses otherwise, V1’s release-safe behavior is **streaming only** and no download endpoint or button is exposed.

| Option                                                    | Advantages                                                                    | Costs/risks                                                                            | Implementation consequence                                                                            |
| --------------------------------------------------------- | ----------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| **Streaming only — V1 default**                           | Lowest storage/delivery complexity; discourages casual redistribution         | No official offline access; cannot stop screen capture or technical extraction         | Signed adaptive playback only                                                                         |
| **Per-item, default off — recommended future compromise** | Editors can enable offline access where licensing and student need justify it | Adds UI/state, signed derivative generation, storage/delivery cost, and support burden | Add `downloadEnabled`; serve a controlled MP4 derivative with expiry; never expose R2 master          |
| **Downloads for every item**                              | Best offline accessibility                                                    | Highest redistribution, takedown, rendition, and bandwidth exposure                    | Generate and authorize a derivative for every published unit; not recommended as an automatic default |

## 8. Telegram migration

Use a user-authorized MTProto client—not a bot—as an operator-run Python/Telethon tool. Telegram supports paged chat history and file download through its client APIs; see [TDLib chat history](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1get_chat_history.html), [TDLib file download](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1download_file.html), and [Telethon client methods](https://docs.telethon.dev/en/stable/modules/client.html). Use KStacks’ own Telegram API ID, keep the session outside the repository, limit access to authorized channels, and comply with the [Telegram API Terms](https://core.telegram.org/api/terms).

### Import behavior

1. Owner creates a short-lived, import-only Devs session.
2. Operator authenticates Telegram locally and provides one or more channel identifiers.
3. CLI scans history oldest-to-newest, selecting video documents/messages and producing a dry-run inventory.
4. For each item, record `(channelId, messageId)`, original caption, date, filename, MIME type, duration, size, and ordering hints in local SQLite.
5. Download to an explicit temporary directory, calculate SHA-256, request a presigned R2 upload, and upload the permanent master.
6. Create an unassigned `MediaAsset`/`ImportSource` record; Devs initiates Mux ingest.
7. Delete the local temporary file only after the backend confirms the R2 object/checksum record.
8. On rerun, skip confirmed channel/message IDs and checksums; retry only incomplete or failed stages.
9. Reconcile totals—scanned, skipped, uploaded, processing, failed—and save CSV/JSON inventory reports.

Imported media appears only in the private media inbox. Editors can preview it, correct its display title, and attach it to a Course or an ordered Series. The importer never guesses groupings and never publishes. Multiple channels are supported, and the same file imported twice is flagged by checksum rather than silently duplicated.

The plan assumes KStacks owns the videos or has permission to download, retain, transform, and publicly stream them. Do not use Telegram as the production video origin.

## 9. Brand and visual implementation

### 9.1 Source resolution

The supplied [Behance visual identity](https://www.behance.net/gallery/248203443/KStack-Visual-Identity) screenshots are the newest client-provided source and therefore lead the Devs brand implementation. They confirm:

- Emerald `#15BB81`
- Light reflected green `#8ADDC0`
- Dark reflected green `#1A6F52`
- Black `#0B0B0B`
- Grey `#434242`
- White `#FFFFFF`
- Alexandria as the Arabic and Latin brand typeface
- The geometric Devs service mark already available as light/dark SVGs in the portal repository

There are two repository conflicts that must be documented rather than copied:

1. The older repository PDF labels Poppins, while the newer supplied identity screenshots specify Alexandria.
2. The portal stylesheet contains duplicated token blocks and currently rendered OKLCH greens/neutrals that differ from the exact identity palette.

Resolution: Devs gets one canonical token layer based on the supplied identity screenshots and Devs SVG. Alexandria is the default brand/UI typeface. The portal remains the reference for interaction conventions, localization, and component ergonomics—not for copying its conflicting color declarations.

### 9.2 Design approval gate

Before production styling, create and obtain KStacks approval for:

1. A one-page token sheet covering light/dark surfaces, text, borders, focus, success/warning/error states, typography scale, spacing, radius, shadows, and approved logo usage.
2. Responsive high-fidelity mockups for landing/catalog, Course/Series playback, and the admin content editor at mobile and desktop widths.
3. An Arabic RTL pass using real-length content, not mirrored English placeholders.
4. Contrast results for both themes and visible keyboard/focus behavior.

The approval gate validates application of the identity; it does not reopen the platform architecture. After approval, visual regression tests use those mockups as reference.

### 9.3 Accessibility and responsive behavior

- Target WCAG 2.2 AA: contrast, keyboard access, visible focus, labels/errors, logical headings, landmark navigation, and reduced-motion support.
- Test at 320, 375, 768, 1024, and 1440 CSS pixels.
- Featured rail uses scroll snap, touch dragging, arrow controls, and accessible item status; no timed movement.
- Player and playlist remain usable in portrait mobile layouts; avoid hover-only controls.
- Arabic sets `lang="ar"` and `dir="rtl"`; directional icons and layout reverse intentionally, while code/URLs/durations retain correct direction.

## 10. Covers, images, and Markdown safety

- Require a custom cover before publication. Accept JPG/PNG/WebP up to an agreed 5 MB input, validate actual MIME and dimensions, crop to 16:9, and produce optimized responsive WebP/AVIF derivatives plus a social-share image.
- Store private image sources separately from public derivatives and deliver public images through an R2 custom domain/CDN. Do not expose video-master storage paths.
- Instructor portraits are optional but use the same validation/derivative pipeline.
- Sanitize Markdown server-side against an allowlist. Disallow arbitrary HTML, script, iframes, event handlers, and unsafe URL schemes. External links receive safe target/rel behavior.
- Treat filenames, Telegram captions, provider errors, and imported metadata as untrusted input.

## 11. Analytics, privacy, and legal launch gate

Basic analytics come from Mux Data and are aggregated by provider video ID into daily per-content views and watched minutes. Cache provider responses, display the last refresh time, and retain no student progress record or Devs-specific personal viewing identifier. Counts are operational/editorial signals, not proof of course completion.

Before production launch, an authorized KStacks reviewer must supply or approve:

- **Privacy notice:** what admin identity data and anonymous playback/device/network telemetry are processed; the roles of KStacks, Mux, Cloudflare, and any observability provider; retention and contact route.
- **Terms of use:** public educational access, acceptable use, availability disclaimer, prohibited scraping/rehosting, and governing organization details.
- **Copyright/content policy:** KStacks’ rights to publish, reporting/takedown contact, and instructor attribution policy.
- **Analytics/cookie determination:** whether the selected deployment and Mux configuration require consent UI or only disclosure. This must be a legal/product determination, not an engineering guess.

The frontend includes final footer routes and accessible placeholders, but production DNS traffic is not opened until approved text is present. This is a launch gate, not legal advice.

## 12. Deferred text and AI phase

Keep V1 video-only while avoiding a dead-end schema: `ContentUnit` owns media through a relation that can later coexist with reviewed article content and caption tracks. Do not expose empty text/caption features in V1.

A later phase may:

1. Extract/compress audio from the R2 master.
2. Send it through a replaceable transcription adapter evaluated on representative Saudi/Arabic speech with English programming terms.
3. Produce timed captions/transcript, then a second adapter creates a structured Markdown lesson/article.
4. Require Editor review before either output is public.
5. Preserve the source language first and offer translation as a separate reviewed action.

Current official OpenAI guidance has a 25 MB file-upload limit and model-dependent timestamp support, so long videos require audio chunking and the provider must remain swappable; see [OpenAI speech-to-text](https://developers.openai.com/api/docs/guides/speech-to-text). Deepgram documents Arabic models, key-term prompting, and WebVTT/SRT workflows and is a candidate for timed Arabic captions; see [Deepgram Arabic update](https://developers.deepgram.com/changelog/2026/1/27), [keyterm prompting](https://developers.deepgram.com/docs/keyterm), and [caption generation](https://developers.deepgram.com/docs/automatically-generating-webvtt-and-srt-captions). Select the future provider only after a measured bilingual technical-audio evaluation.

## 13. Deployment discovery and rollout

The public `infra` repository does not reveal the live production topology. The implementation must begin with a short, read-only infrastructure discovery rather than inventing hosting details.

### Phase 0 — discovery and approvals

- Inspect private CI/CD, DNS/TLS, secret management, container runtime, Eureka, gateway, PostgreSQL, backups, observability, and current frontend hosting with the KStacks infrastructure owner.
- Default deployment shape: frontend on the same supported platform as the portal, backend on the same container platform as existing Spring services, and public hostname `devs.kstacks.org`. If the actual environment cannot support that shape, amend the deployment section before provisioning.
- Confirm Mux/R2 accounts, regions/configuration, monthly budget owner, spend alerts, and credentials.
- Produce the design token sheet/mockups and obtain visual approval.
- Draft marketing copy and legal placeholders; assign approvers.

### Phase 1 — foundations

- Create `devs-service` and `devs-frontend` repositories with the organization’s current build, lint, test, Docker, CI, and dependency-update conventions.
- Add gateway route/security rules and Eureka registration.
- Implement migrations, content/taxonomy/instructor domains, public read APIs, local admin identity/RBAC, audit events, error envelope, health/readiness endpoints, and seed-first-Owner process.
- Add R2/Mux adapters and local fake adapters for tests/development.

### Phase 2 — public experience

- Implement localization, theme/tokens, responsive shell, landing page, conditional featured rail, catalog search/filters, Course/Series/lesson routes, player adapter, SEO, legal routes, and error/empty/loading states.
- Use reviewed bilingual draft copy and approved visual references.

### Phase 3 — admin and media

- Implement candidate sign-in and team management, dashboard, content editor, sanitized Markdown preview, lesson ordering, cover processing, instructor/taxonomy management, direct R2 uploads, Mux processing/webhooks, analytics cache, publication checks, archive, and safe deletion.

### Phase 4 — Telegram migration

- Build and dry-run the importer against a small authorized sample.
- Import all selected channels, reconcile message/file/media counts, retry failures, verify R2 checksums and Mux readiness, and hand the resulting media inbox to Editors.
- Do not publish or infer series grouping as part of migration.

### Phase 5 — acceptance and launch

- Complete security, accessibility, visual, Arabic RTL, mobile, load, SEO, backup/restore, and Saudi playback checks.
- Confirm monitoring, alert ownership, support runbooks, budget thresholds, legal text, and initial Owner recovery process.
- Publish only KStacks-reviewed content. The featured rail appears automatically once four eligible featured items are published.

## 14. Test plan and acceptance scenarios

### Automated backend tests

- Unit tests for Course/Series invariants, status transitions, translation fallback, slug redirects, featured threshold/order, role capabilities, safe deletion, and metrics aggregation.
- PostgreSQL integration tests with Testcontainers for constraints, pagination, Arabic/Latin normalization, filters, optimistic locking, and migrations.
- Contract tests with fake/WireMock Mux and R2 adapters: multipart completion, remote ingest, signature verification, duplicate/out-of-order webhooks, retries, provider errors, and orphan cleanup.
- Security tests for anonymous/admin route boundaries, Owner vs Editor, revoked roles, CSRF/Origin, unsafe Markdown, upload MIME spoofing, rate limits, and import-token scope/expiry.

### Automated frontend tests

- Component tests for cards, filters, featured visibility at 0/3/4+ items, translations, theme, RTL, forms, upload/processing states, and next-lesson prompt.
- Playwright flows for anonymous browse/watch, deep-linked series lesson, URL filters, language/theme persistence, known-candidate denial, Editor CRUD/publish/archive, Owner team/deletion, and failure recovery.
- Automated accessibility checks plus manual keyboard/player/screen-reader checks.
- Visual regression at the five target widths in English/light, English/dark, Arabic/light, and Arabic/dark.

### Importer tests

- Dry run without downloads, multiple channels, pagination, interrupted downloads/uploads, duplicate message IDs, duplicate checksums, unexpected MIME, missing captions, temp cleanup, expired import token, rerun idempotency, and reconciliation report.

### Non-functional acceptance

- Core public pages meet Core Web Vitals targets under the agreed mobile test profile; player loading is measured separately from page LCP.
- No public/admin application response contains R2 credentials, private object URLs, master keys, Mux signing secrets, Telegram sessions, or raw stack traces.
- Health/readiness distinguishes application health from unavailable optional analytics refresh.
- Backup restore is rehearsed for PostgreSQL; R2 master inventory can be reconciled against database checksums and provider assets.
- Saudi-region tests record video start time, rebuffer ratio, and achieved resolution across representative mobile and desktop connections.

## 15. Operations and observability

- Structured logs with correlation IDs; redact tokens, cookies, presigned URLs, captions where sensitive, and provider payload secrets.
- Metrics/alerts for API errors/latency, failed login/authorization, upload failures, stuck processing, webhook signature/retry failures, database pool/health, Mux/R2 failures, and importer reconciliation.
- Dashboards for media lifecycle counts, publishing health, provider usage/spend, and public performance.
- Daily PostgreSQL backups with documented retention/restore owner. R2 originals have no lifecycle expiry; deletion follows the Owner-only safe-delete workflow.
- Runbooks: failed Mux ingest, stuck multipart upload, webhook replay, compromised admin/import token, R2/Mux outage, restore, provider migration, and admin lockout/first-Owner recovery.

## 16. Deliverables

1. `devs-service` and `devs-frontend` source, migrations, tests, CI, deployment artifacts, and operating documentation.
2. One-time Telegram importer with dry-run, SQLite resume ledger, inventory/reconciliation output, and operator guide.
3. Approved design-token sheet and responsive public/admin mockups.
4. API/OpenAPI documentation, environment variable reference, content/editor guide, and incident/migration runbooks.
5. Initial authorized Telegram library migrated into private dashboard drafts.
6. `docs/project-plan.md` — this detailed implementation plan.
7. `docs/prototype.html` — self-contained, mobile-responsive, print-friendly team brief with inline CSS/JavaScript/SVG, no external runtime dependencies, project summary, architecture, phases, provider/download comparisons, launch gates, and source links.

## 17. Assumptions and required inputs

- KStacks owns or has sufficient license for the Telegram videos and instructor/cover assets.
- Initial migration is fewer than 100 videos across multiple accessible channels.
- The KStacks auth cookie is configured for the production subdomain, and the gateway/JWKS integration remains the identity path.
- KStacks supplies access to private infrastructure information, provider accounts, DNS, budget ownership, first-Owner identity, Telegram API credentials/session operator, and final legal approvers.
- The supplied Behance screenshots and repository Devs SVG are the current brand authority; web-component execution still requires the documented mockup approval.
- V1 is streaming-only unless KStacks selects another download option before its implementation milestone.
- Paid resources, DNS changes, and production publication require explicit KStacks approval; the plan does not authorize them by itself.
