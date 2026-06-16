# workfitai-platform

## Observability (Prometheus + Grafana)

- Prometheus UI: http://localhost:9090
- Grafana UI: http://localhost:3001

### Datasource
Grafana → Connections → Prometheus → URL: `http://prometheus:9090`

### Scrape targets (inside Docker network)
- `api-gateway:9005`, `auth-service:9005`
- `application-service:8080`, `monitoring-service:8080`, `job-service:8080`, `user-service:8080`, `cv-service:8080`

### Verify endpoints
```bash
curl -s http://api-gateway:9005/actuator/prometheus | head
for s in application-service monitoring-service job-service user-service cv-service; do
  curl -s http://$s:8080/actuator/prometheus | head
done
```

## Deployment Env: dev vs prod (CORS + cookies)

A single `APP_ENV` flag in `.env.local` drives CORS origin validation (api-gateway)
and the refresh-token (RT) cookie `Secure` flag (auth-service). FE `workfitai.uk` and
BE `be.workfitai.uk` share the same registrable domain (same-site), so `SameSite=Lax`
is used — it is sent on cross-origin fetches while keeping CSRF protection on `/auth/refresh`.

### `.env.local` keys

| Key | dev | prod |
|-----|-----|------|
| `APP_ENV` | `dev` | `prod` |
| `ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:3001` | `https://workfitai.uk` |
| `WS_ALLOWED_ORIGINS` | same as above | `https://workfitai.uk` |
| `APP_FRONTEND_BASE_URL` | `http://localhost:3000` | `https://workfitai.uk` |
| `APP_BACKEND_BASE_URL` | `http://localhost:9085` | `https://be.workfitai.uk` |
| `APP_COOKIE_SECURE` *(optional)* | unset → `false` | unset → `true` |
| `APP_COOKIE_SAMESITE` *(optional)* | `Lax` | `Lax` |
| `APP_COOKIE_PATH` *(optional)* | `/auth` | `/auth` |

Cookie `Secure` defaults from `APP_ENV` (`prod` → on); set `APP_COOKIE_SECURE` only to override.
RT cookie attributes are centralized in `auth-service` `RefreshCookieFactory` (single source for
login, 2FA login, refresh rotation, logout-delete, OAuth exchange); `Path=/auth` by default.

### Behavior

- `APP_ENV=prod` with empty or `*` `ALLOWED_ORIGINS` → api-gateway **fails fast** at startup
  (credentials are enabled, so wildcard origins are rejected).
- CORS always echoes the exact request origin + `Access-Control-Allow-Credentials: true`
  (never `*`).

### Prod checklist

- Set the prod values above in the prod-host `.env.local`.
- OAuth provider consoles: redirect URIs → `https://be.workfitai.uk/oauth/callback/{google,github}`.
- Serve both FE and BE over HTTPS (required for `Secure` cookies).
