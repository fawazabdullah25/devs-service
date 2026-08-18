# Devs service

Spring Boot 4 / Java 25 service for the Devs catalog, publication workflow, access policy, and media orchestration. It supports validated static HLS delivery as well as the original R2-to-Mux workflow.

## Local development

Copy the example environment, start PostgreSQL, then run the service:

```bash
cp .env.example .env
docker compose up -d postgres
mvn spring-boot:run
```

Run the automated tests with:

```bash
mvn test
```

Public endpoints remain available with JWT disabled. Admin endpoints deny all unless local-only `DEVS_ALLOW_INSECURE_ADMIN=true` is set. The `production` profile overrides that escape hatch to false.

## API groups

- `/devs/api/v1/public/**`: published catalog/home/content.
- `/devs/api/v1/admin/content/**`: metadata, units, publish, archive, analytics summary.
- `/devs/api/v1/admin/media/**`: static HLS registration, presigned source uploads, Mux ingest, and processing status.
- `/devs/api/v1/webhooks/mux`: signature-verified Mux events.
- `/actuator/health/liveness` and `/actuator/health/readiness`: Kubernetes probes.

Flyway owns the schema. Hibernate runs with `ddl-auto=validate`; never switch production to schema mutation.

## Static HLS

Set `STATIC_HLS_ENABLED=true`, `STATIC_HLS_BASE_URL` to the public media origin, and optionally constrain registrations with `STATIC_HLS_ALLOWED_PATH_PREFIX`. The admin endpoint `POST /devs/api/v1/admin/media/static-hls` accepts only relative, immutable manifest and VTT paths. Before creating a `READY` media record, the service fetches the master playlist, every declared rendition playlist, and every caption track; redirects and host-changing URLs are rejected.

The database stores relative paths. Public APIs resolve them against `STATIC_HLS_BASE_URL`, so changing CDN hosts does not require rewriting catalog rows.

Example registration body:

```json
{
  "manifestPath": "lessons/example/2026-08-18-v1/master.m3u8",
  "durationSeconds": 3600,
  "encodingVersion": "2026-08-18-v1",
  "checksumSha256": null,
  "captions": [
    {
      "language": "en",
      "label": "English",
      "path": "lessons/example/2026-08-18-v1/captions/en.vtt",
      "defaultTrack": false
    }
  ]
}
```

## Repository contents

- `src/`: Spring Boot application, Flyway migrations, and tests.
- `tools/telegram-import/`: resumable Telegram-to-R2/Mux migration utility.
- `deploy/kubernetes/`: Kustomize-ready frontend and service deployment base.
- `compose.yml`: local PostgreSQL, service, and frontend stack. The frontend repository must be cloned beside this repository as `../devs-frontend`.
- `docs/architecture.md`: complete architecture, environment, gateway, media, and launch guidance.
- `docs/project-plan.md`: detailed product and implementation plan.
- `docs/prototype.html`: standalone design brief.
- `docs/r2-hls-pilot-guide.md`: reproducible Oracle-to-R2 HLS pilot procedure.
- `docs/r2-hls-pilot-results.md`: measured encoding, storage, playback, and cost results.

See [the architecture guide](docs/architecture.md) for all environment variables, KStacks JWT/gateway integration, media provisioning, deployment, and launch gates.
