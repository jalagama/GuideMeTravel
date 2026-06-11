# Firebase & Google Cloud Setup

Follow these steps to connect the GuideMe Travel app to your Firebase/GCP project.

## 1. Create Firebase project

1. Open [Firebase Console](https://console.firebase.google.com/)
2. Create project (e.g. `guideme-travel`)
3. Add Android app with package name `com.guideme.travel`
4. Download `google-services.json` into `app/`

## 2. Enable Firebase products

| Product | Purpose |
|---------|---------|
| **Authentication** | Anonymous sign-in (MVP) |
| **Cloud Firestore** | Trips, attractions, guide content cache |
| **Cloud Storage** | Audio MP3 files |
| **Cloud Functions** | `generateItinerary`, `generateGuidePack` |

## 3. Enable Google Cloud APIs

In [Google Cloud Console](https://console.cloud.google.com/) → APIs & Services:

- Places API
- Geocoding API
- Directions API
- Cloud Text-to-Speech API
- Cloud Translation API
- Vertex AI API (Gemini)

## 4. Firestore data model

```
users/{userId}
  - displayName, languageCode, createdAtMillis

trips/{tripId}
  - userId, origin, destination, languageCode, status
  - attractions[], offlinePackDownloaded, createdAtMillis

attractions/{poiId}                    # global shared cache
  - name, description, latitude, longitude, imageUrl

attractions/{poiId}/guideContent/{lang}
  - transcript, audioUrl, source, updatedAtMillis
```

Security rules: see `backend/firestore.rules` and `backend/storage.rules`.

## 5. Deploy backend

```bash
cd backend/functions
npm install --cache ./.npm-cache
npm run build

firebase login
firebase use travelguide-47f80
firebase functions:secrets:set GEMINI_API_KEY
firebase functions:secrets:set GOOGLE_MAPS_API_KEY

cd ..
firebase deploy --only functions
```

### Gemini API key (required for attraction curation)

`GEMINI_API_KEY` must be a **Gemini API key** from [Google AI Studio](https://aistudio.google.com/apikey). It must start with `AIza...`.

**Do not use:** Google Maps keys, Firebase keys, GCP service-account JSON, or OAuth tokens (values starting with `AQ.` are invalid for the Gemini REST API).

Verify locally before deploy:

```bash
cd backend/functions
GEMINI_API_KEY=your_key_here npm run smoke:gemini
```

If you see `API_KEY_INVALID`, create a new key in AI Studio and update the secret:

```bash
firebase functions:secrets:set GEMINI_API_KEY --project travelguide-47f80
firebase deploy --only functions
```

Confirm the secret value (should start with `AIza...` and work in AI Studio):

```bash
firebase functions:secrets:access GEMINI_API_KEY --project travelguide-47f80
```

If the Android app shows **"The request was not authorized to invoke this service"**, Cloud Run IAM is blocking the callable. Fix it:

```bash
gcloud run services add-iam-policy-binding generateitinerary \
  --region=asia-south1 \
  --member="allUsers" \
  --role="roles/run.invoker" \
  --project=travelguide-47f80
```

List services if the name differs:

```bash
gcloud run services list --region=asia-south1 --project=travelguide-47f80
```

`allUsers` + `roles/run.invoker` is required for Firebase Callable from the mobile app. App Check and Firebase Auth still protect the function logic.

## 6. Optional: Cloud Run for heavy guide packs

See [backend/cloud-run/README.md](../backend/cloud-run/README.md).

## 7. Android app configuration

1. Replace `app/google-services.json` with your project file
2. Enable Anonymous Auth in Firebase Console
3. Sync Gradle and run:

```bash
./gradlew :app:assembleDebug
```

## Cost tips

- Guide content is cached globally per POI + language — generate once, serve many users
- Use Gemini Flash tier for curation and scripts
- Monitor Maps Platform usage against the monthly free credit
