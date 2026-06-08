# GuideMe Travel

AI-powered offline travel companion for Android with a serverless Google Cloud / Firebase backend.

**Development plan:** see [docs/PLAN.md](docs/PLAN.md)

**Setup:** [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md) | **Privacy:** [docs/PRIVACY.md](docs/PRIVACY.md) | **Play Store:** [docs/PLAY_STORE_BACKGROUND_LOCATION.md](docs/PLAY_STORE_BACKGROUND_LOCATION.md)

## Project structure

```
GuideMeTravel/
├── app/                    # Android app (Kotlin, Compose, Hilt)
├── backend/
│   ├── firebase.json
│   ├── firestore.rules
│   ├── storage.rules
│   └── functions/          # Cloud Functions (TypeScript)
├── docs/
│   └── PLAN.md             # Full architecture + phased roadmap
└── README.md
```

## Production features (current)

- Strict MVVM Clean Architecture with domain use-case layer
- Global destination itinerary via `generateItinerary` (Places + Gemini + Wikipedia RAG)
- Offline pack via Cloud Run `generateGuidePack` (parallel guide generation, Translate + TTS)
- MapTiler + MapLibre offline tiles with live location puck and route markers
- Geofence-triggered localized audio guides with deduplication and Media3 session
- Google + Email + anonymous auth with Firestore `users/` profile sync
- Trip lifecycle: offline cleanup with freed-space reporting, full trip delete
- App Check, Crashlytics, Analytics, rate limiting, R8 release minification

### Open in Android Studio

1. Open `/Users/malatheshkrishnappa/Documents/GuideMeTravel`
2. Copy `local.properties.example` → `local.properties` and set:
   - `MAPTILER_API_KEY` — from [MapTiler](https://www.maptiler.com/)
   - `GUIDE_PACK_BASE_URL` — your Cloud Run service URL (no trailing slash)
3. Replace `app/google-services.json` with your Firebase project file
4. Enable Anonymous, Google, and Email/Password auth in Firebase Console
5. Enable App Check (Play Integrity) in Firebase Console
6. Sync Gradle and run on device/emulator

### Build from terminal

```bash
cd /Users/malatheshkrishnappa/Documents/GuideMeTravel
./gradlew :app:assembleDebug
```

## Firebase backend setup

1. Create a Firebase project in [Firebase Console](https://console.firebase.google.com/)
2. Enable **Anonymous Authentication**, Firestore, Storage, and Cloud Functions
3. Enable Google Cloud APIs:
   - Places API
   - Geocoding API
   - Directions API
   - Cloud Text-to-Speech
   - Cloud Translation
   - Vertex AI / Gemini API
4. Download `google-services.json` into `app/`
5. Deploy functions (region: `asia-south1`):

```bash
cd backend/functions
npm install
firebase functions:secrets:set GEMINI_API_KEY
firebase functions:secrets:set GOOGLE_MAPS_API_KEY
cd ..
firebase deploy
```

## Cloud Functions

| Function | Purpose |
|---|---|
| `generateItinerary` | Places + Wikipedia RAG + Gemini curation → Firestore trip |
| Cloud Run `/generateGuidePack` | Gemini script + Translate + Cloud TTS → Storage (storage paths, fresh signed URLs) |

## Next steps (Phase 2+)

See [docs/PLAN.md](docs/PLAN.md) for multilingual polish, global destination scaling, and monetization.
