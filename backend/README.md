# Jarvis VIP Secure ChatGPT Backend Proxy

This Node.js server keeps your `OPENAI_API_KEY` completely off the Android APK and proxies chat requests from the Jarvis VIP Android app via `POST /api/jarvis/chat`.

## Local Setup

```bash
cd backend
npm install
cp .env.example .env
# Edit .env and set OPENAI_API_KEY plus JARVIS_SHARED_TOKEN (or GOOGLE_APPLICATION_CREDENTIALS)
npm start
```

## Endpoints

- `GET /health` — Readiness check (`ok`, `openAiConfigured`, auth mode flags).
- `POST /api/jarvis/chat` — Authenticated OpenAI ChatGPT proxy (`Authorization: Bearer <token>` or `X-Jarvis-Token`).

## Deployment

Deploy to **Cloud Run**, **Render**, or **Railway** over HTTPS, then paste the deployed base URL (and optional `JARVIS_SHARED_TOKEN`) inside the Android app under **Settings > OpenAI ChatGPT Secure Backend**.
