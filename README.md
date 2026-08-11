# Devs service

Spring Boot 4 / Java 25 service for the Devs catalog, publication workflow, access policy, and media orchestration.

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
- `/devs/api/v1/admin/media/**`: presigned uploads, imported R2 objects, ingest, processing status.
- `/devs/api/v1/webhooks/mux`: signature-verified Mux events.
- `/actuator/health/liveness` and `/actuator/health/readiness`: Kubernetes probes.

Flyway owns the schema. Hibernate runs with `ddl-auto=validate`; never switch production to schema mutation.

## Repository contents

- `src/`: Spring Boot application, Flyway migrations, and tests.
- `tools/telegram-import/`: resumable Telegram-to-R2/Mux migration utility.
- `deploy/kubernetes/`: Kustomize-ready frontend and service deployment base.
- `compose.yml`: local PostgreSQL, service, and frontend stack. The frontend repository must be cloned beside this repository as `../devs-frontend`.
- `docs/architecture.md`: complete architecture, environment, gateway, media, and launch guidance.
- `docs/project-plan.md`: detailed product and implementation plan.
- `docs/prototype.html`: standalone design brief.

See [the architecture guide](docs/architecture.md) for all environment variables, KStacks JWT/gateway integration, media provisioning, deployment, and launch gates.
