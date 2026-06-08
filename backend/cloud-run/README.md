# Cloud Run: generateGuidePack

Long-running guide-pack generation service (Gemini + Translate + Cloud TTS). Use this when Cloud Functions timeout is insufficient for large trips or many languages.

## Deploy (from repo root)

```bash
cd backend
gcloud builds submit -f cloud-run/Dockerfile --tag gcr.io/YOUR_PROJECT_ID/guideme-guidepack .
gcloud run deploy guideme-guidepack \
  --image gcr.io/YOUR_PROJECT_ID/guideme-guidepack \
  --region asia-south1 \
  --allow-unauthenticated=false \
  --memory 1Gi \
  --timeout 900 \
  --set-secrets GEMINI_API_KEY=GEMINI_API_KEY:latest
```

## API

`POST /generateGuidePack`

Headers:
- `Authorization: Bearer <Firebase ID token>`

Body:
```json
{ "tripId": "abc123" }
```

The callable Cloud Function `generateGuidePack` remains available for MVP; point the Android app to Cloud Run for production scale.
