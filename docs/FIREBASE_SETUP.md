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

Confirm the secret value (valid formats: `AIza...` or `AQ....`):

```bash
firebase functions:secrets:access GEMINI_API_KEY --project travelguide-47f80
```

### Reduce Gemini API cost (default: cost-saver ON)

The backend defaults to **cost-saver mode** to minimize API spend:

| Setting | Default | Effect |
|---------|---------|--------|
| `GEMINI_COST_SAVER` | `true` (unset) | Skips per-spot transcripts, hotel copy, package extras LLM; uses Wikipedia + templates |
| `GEMINI_MODEL` | `gemini-2.5-flash-lite` | Cheapest model for JSON curation |
| Destination cache | Firestore `destinationCurations` | One Gemini call per destination for all users |

**Typical Gemini calls per user action (cost-saver ON):**

- Search trip (e.g. Hampi): **0–1** calls (cached after first user)
- Package detail (first time): **1** call (attractions only)
- Offline guide pack download: **1 call per attraction** (lite model)

To enable full AI transcripts and marketing copy (higher cost):

```bash
firebase functions:secrets:set GEMINI_COST_SAVER
# enter: false
firebase deploy --only functions
```

**Free tier option:** Create a separate AI Studio project **without** paid billing for development. Free tier has rate limits but no prepay credits. Use that project's key in `GEMINI_API_KEY`.

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

## 8. Admin bulk curation (catalog seeding)

Admin jobs pre-populate Firestore so **new users read cached content** instead of triggering Gemini on every install.

### Bootstrap admin access

1. Deploy functions (see section 5).
2. Set bootstrap UID (optional, for first admin):

```bash
firebase functions:secrets:set ADMIN_BOOTSTRAP_UIDS
# enter your Firebase Auth UID (comma-separated for multiple)
firebase deploy --only functions
```

3. Sign in to the app or Firebase Console, copy your UID, then call once (authenticated):

```bash
# From Firebase shell or a small script using adminSetAdminClaim callable
```

4. Or use Firebase Admin SDK locally to set `{ admin: true }` on your user.

### Admin console (hosting)

Static UI at `backend/admin/public/index.html`. Deploy hosting from the `backend/` directory (see below).

```bash
cd backend
firebase deploy --only hosting
```

Open the hosting URL, sign in with Google (admin account), start curation for a country.

### Curation modes

| Mode | Use |
|------|-----|
| `catalog_only` | Genres, packages, tour skeleton, destination index — **no** per-language guide TTS |
| `languages_only` | Add languages to existing catalog (e.g. `hi` after English QA) |
| `full` | Catalog + all selected languages in one job |

### Admin quality (env on Cloud Run / Functions)

```bash
GEMINI_ADMIN_MODEL=gemini-2.5-flash
GEMINI_ADMIN_EXPERT_MODEL=gemini-2.5-pro
GEMINI_COST_SAVER=false
TTS_VOICE_TIER=wavenet
GEMINI_USE_BATCH=true
```

Expert spots (e.g. Hampi core monuments) use Pro; bulk spots use Flash. User-facing search misses still use cost-saver lite models.

### Pilot: India (English then Hindi)

1. `adminStartCountryCuration({ countryCode: "IN", mode: "catalog_only", languages: ["en"] })`
2. Call `adminAdvanceCurationJob({ jobId })` repeatedly until `hasMore: false` (or use Admin UI **Advance**).
3. QA `tourPackages/in-hampi` and `destinationIndex` in Firestore Console.
4. `adminStartCountryCuration({ countryCode: "IN", mode: "languages_only", languages: ["hi"] })` and advance again.
5. Deploy rules/indexes if not already:

```bash
firebase deploy --only firestore:rules,firestore:indexes
```

### Firestore collections (read by app)

| Collection | Purpose |
|------------|---------|
| `curatedGenres/{country}` | Browse genres |
| `curatedPackages/{country}` | Package cards per genre |
| `tourPackages/{packageId}` | Full itinerary |
| `destinationIndex/{normalizedQuery}` | Search → trip without AI |
| `attractions/{id}/guideContent/{lang}` | Offline scripts + TTS URLs |
| `curationJobs/{jobId}` | Admin-only job status |

Android reads genres/packages/tour packages from Firestore first; callables remain as fallback until catalog is fully seeded.
