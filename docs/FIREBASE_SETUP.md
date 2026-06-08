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
firebase use YOUR_PROJECT_ID
firebase functions:secrets:set GEMINI_API_KEY
firebase functions:secrets:set GOOGLE_MAPS_API_KEY

cd ..
firebase deploy
```

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
