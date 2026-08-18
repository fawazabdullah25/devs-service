# Kubernetes deployment base

These manifests provide Deployments, ClusterIP Services, health probes, rolling updates, resource bounds, non-root/read-only containers, and PodDisruptionBudgets. They deliberately do not guess the team's namespace, registry credentials, Ingress controller, public hosts, certificate issuer, or secret manager.

## 1. Set immutable images and origin

Replace both `replace-me` image tags in `kustomization.yaml` with the commit SHA. Replace the frontend and static-video origins in `configmap.yaml`, and choose the immutable HLS path prefix used by the encoder. The frontend image itself must already be built with the public gateway API URL; changing a Pod environment variable cannot change a Vite build-time URL.

## 2. Create the secret outside Git

The Deployment expects `devs-secrets` with these keys:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
DEVS_JWT_PUBLIC_KEY
DEVS_JWT_ISSUER
DEVS_ADMIN_SUBJECTS
R2_ENDPOINT
R2_BUCKET
R2_ACCESS_KEY_ID
R2_SECRET_ACCESS_KEY
MUX_TOKEN_ID (only when the legacy Mux path is enabled)
MUX_TOKEN_SECRET (only when the legacy Mux path is enabled)
MUX_WEBHOOK_SECRET (only when the legacy Mux path is enabled)
```

Use External Secrets/Sealed Secrets/the team's normal secret manager. For a temporary non-production namespace, `kubectl create secret generic devs-secrets --from-literal=...` is sufficient; do not commit the generated Secret.

## 3. Validate and apply

```bash
kubectl kustomize deploy/kubernetes
kubectl apply -k deploy/kubernetes
kubectl rollout status deployment/devs-service
kubectl rollout status deployment/devs-frontend
```

Then route the public frontend host to `devs-frontend:3000`, and add the gateway `/devs/api/**` route to `devs-service:8080`. Managed PostgreSQL and outbound HTTPS to the configured static-video origin must be reachable from the service Pods. If Mux is enabled, Mux must also reach the webhook path.

Run `kubectl diff -k deploy/kubernetes` in the actual namespace before applying to production.
