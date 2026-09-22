# TipsyBuddy Feedback Worker

Cloudflare Worker that proxies the TipsyBuddy Android in-app **Support & Feedback**
reporter to the GitHub REST API. The Android app is untrusted and holds **no**
GitHub credentials; it only knows this Worker's URL.

```
Android App --HTTPS/JSON--> Worker --GitHub REST API--> chartmann1590/TipsyBuddy
   (no token)                (GITHUB_TOKEN secret)        Issues / Comments / feedback-assets/
```

## Routes (allow-list only — not a generic GitHub proxy)

- `GET /health`
- `POST /api/issues`
- `GET /api/issues/:number`
- `GET /api/issues/:number/comments`
- `POST /api/issues/:number/comments`
- `POST /api/assets` (Base64 image -> `feedback-assets/` via Contents API)

## Setup

```bash
cd cloudflare/feedback-worker
npm install
npx wrangler login
# Set the GitHub PAT as a secret (never commit it):
printf '%s' '<GITHUB_PAT>' | npx wrangler secret put GITHUB_TOKEN
npx wrangler deploy
```

Then point Android at the deployed URL via Gradle property
`feedback.worker.url` (or env `FEEDBACK_WORKER_URL`).

## Security notes

- `GITHUB_TOKEN` exists only as a Worker secret (`env.GITHUB_TOKEN`).
- Filenames are sanitized; uploads always land under `feedback-assets/`.
- Request validation: method, Content-Type, issue numbers, title/body limits,
  Base64 image limits (~8MB), best-effort per-IP rate limiting.
- No `Access-Control-Allow-Origin: *` (native Android needs no browser CORS).
- Errors are safe: no stack traces, tokens, or auth headers leak to clients.
