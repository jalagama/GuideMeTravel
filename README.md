# GuideMe Travel

AI-powered offline travel companion for Android with a serverless Google Cloud / Firebase backend.

**Development plan:** see [docs/PLAN.md](docs/PLAN.md)

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

## Phase 1 MVP (current)

Implemented per plan:

- Onboarding → Privacy consent → Firebase anonymous auth → Home
- Trip creation via Cloud Function (`generateItinerary`) with local Hampi fallback
- Offline pack download via `generateGuidePack` + local transcript/audio cache
- MapLibre map with attraction markers
- Geofence foreground service + ExoPlayer MP3 (TTS fallback)
- Trip summary with offline pack deletion
- Battery optimization prompt for reliable background guides

### Open in Android Studio

1. Open `/Users/malatheshkrishnappa/Documents/GuideMeTravel`
2. Replace `app/google-services.json` with your Firebase project file
3. Enable Anonymous Auth in Firebase Console
4. Sync Gradle and run on device/emulator

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
| `generateGuidePack` | Gemini script + Translate + Cloud TTS → Storage, globally cached |

## Next steps (Phase 2+)

See [docs/PLAN.md](docs/PLAN.md) for multilingual polish, global destination scaling, and monetization.
