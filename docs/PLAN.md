---
name: AI Travel Companion
overview: Build a global, AI-powered offline travel-guide Android app (Kotlin/Compose) with a fully serverless Google Cloud/Firebase backend that pre-generates curated itineraries and multilingual audio guides, triggered hands-free by geofencing during the trip.
todos:
  - id: firebase-setup
    content: "Set up Firebase/GCP project: Auth, Firestore, Cloud Storage, enable Maps Platform (Places/Directions), Vertex AI/Gemini, Cloud TTS, Cloud Translation APIs. Define Firestore data model (users, trips, attractions, guideContent) and security rules."
    status: completed
  - id: itinerary-fn
    content: "Build generateItinerary Cloud Function: Places nearby search -> Gemini curation/ordering -> Directions route optimization -> write itinerary to Firestore."
    status: completed
  - id: guidepack-fn
    content: "Build generateGuidePack on Cloud Run: per-attraction RAG (Wikipedia) -> Gemini script -> Translate -> Cloud TTS -> MP3 to Cloud Storage, with global content caching keyed by POI id."
    status: completed
  - id: android-scaffold
    content: "Scaffold Android app: Kotlin, Compose, Material 3, MVVM + Clean Architecture, Firebase SDK, Room, DataStore, navigation, dependency injection (Hilt)."
    status: completed
  - id: ui-screens
    content: "Implement professional UI (Airbnb/Polarsteps-inspired Material 3): onboarding/auth, home/search, itinerary timeline, offline-pack download, map/navigation, guide player, trip summary."
    status: completed
  - id: offline-map
    content: Integrate MapLibre SDK for offline tile download, route + attraction markers, and offline navigation.
    status: in_progress
  - id: geofence-audio
    content: Implement geofencing engine in a foreground service that triggers ExoPlayer playback of the local pre-downloaded MP3 as the user approaches each spot, with manual-play fallback.
    status: completed
  - id: lifecycle-cleanup
    content: "Implement offline-pack lifecycle: WorkManager downloads, storage estimate UI, and cleanup of tiles + audio on trip completion or user delete."
    status: completed
  - id: hardening-compliance
    content: Harden background location across OEMs, add battery-optimization prompts, privacy policy and GDPR/CCPA/India DPDP consent flows, and prepare Play Store background-location justification.
    status: in_progress
isProject: false
---

# AI Travel Companion - High-Level Development Plan

A global, AI-powered "automatic tour guide" Android app. Users enter a destination, get an AI-curated itinerary, download an offline pack (map + multilingual audio), and on the trip the app narrates each attraction automatically as they approach it.

## Core Architectural Principles

1. **Pre-generate, don't compute live.** All AI/TTS work happens online before the trip; on the trip the app only plays local files. Solves offline + latency + cost.
2. **Ground content in real data (RAG).** Gemini curates/writes from Places + Wikipedia facts, never invents attractions. Avoids hallucination + licensing issues.
3. **Cache content globally, share across users.** A given attraction's guide is generated once and reused for every user. Marginal cost per user trends to zero.
4. **Serverless only.** No servers to manage; Android dev writes Kotlin + a few small Cloud Functions.

## System Architecture

```mermaid
flowchart TD
    subgraph client [Android App - Kotlin/Compose]
        ui[UI: Planner, Map, Guide Player]
        room[Room DB + File Cache]
        geo[Geofencing + Foreground Service]
        player[ExoPlayer audio]
        maplibre[MapLibre offline tiles]
    end

    subgraph gcp [Google Cloud / Firebase - Serverless]
        auth[Firebase Auth]
        fs[Firestore: users, trips, attractions, guideContent]
        storage[Cloud Storage: audio MP3s]
        fn1[Cloud Function: generateItinerary]
        fn2[Cloud Run: generateGuidePack]
    end

    subgraph google [Google APIs]
        places[Places + Directions API]
        gemini[Gemini / Vertex AI]
        tts[Cloud Text-to-Speech]
        translate[Cloud Translation]
    end

    ui -->|Firebase SDK| fn1
    fn1 --> places
    fn1 --> gemini
    fn1 --> fs
    ui -->|read| fs
    ui -->|request pack| fn2
    fn2 --> gemini
    fn2 --> tts
    fn2 --> translate
    fn2 --> storage
    ui -->|download MP3s + tiles| storage
    geo --> player
    player --> room
```

## Trip Lifecycle (data flow)

```mermaid
sequenceDiagram
    participant U as User
    participant A as Android App
    participant F as Cloud Functions
    participant G as Google AI APIs
    U->>A: Enter destination (Hampi)
    A->>F: generateItinerary(origin, dest, lang)
    F->>G: Places + Gemini curation + Directions
    F-->>A: Itinerary saved to Firestore
    U->>A: Download offline pack
    A->>F: generateGuidePack(tripId)
    F->>G: Gemini script + Translate + TTS per spot
    F-->>A: MP3s in Cloud Storage + map tiles
    Note over A: ON TRIP (offline)
    A->>A: Geofence ENTER -> play local MP3
    U->>A: Trip complete -> delete pack
```

## Tech Stack

- **Client:** Kotlin, Jetpack Compose, Material 3, MVVM + Clean Architecture, Room, DataStore, WorkManager (downloads), Foreground Service (geofencing), MapLibre Android SDK (offline maps), Media3/ExoPlayer (audio), Firebase SDK, Coil (images).
- **Backend (serverless):** Firebase Auth, Cloud Firestore, Cloud Storage, Cloud Functions (itinerary), Cloud Run (heavier guide-pack generation to avoid timeouts), Firebase Cloud Messaging.
- **AI / Google APIs:** Gemini (Vertex AI), Cloud Text-to-Speech, Cloud Translation, Google Maps Platform (Places, Directions), Wikipedia/Wikivoyage as RAG source.

## UI Design Direction

Reference blend: **Airbnb** (clean white cards, large rounded corners, photography-forward, generous whitespace) + **Polarsteps** (trip timeline) + **Google Maps** (map interaction patterns).

- Material 3 dynamic color, rounded 16-20dp cards, bold display typography, hero imagery per attraction.
- Key screens: Onboarding/Auth, Home/Search (destination entry), Itinerary (timeline + map preview), Offline-pack download (progress + storage estimate), Map/Navigation (full-screen MapLibre with route + spot markers), Guide Player (now-playing card, transcript, language selector), Trip summary + cleanup.

## Backend Pieces (the only "backend code")

- `generateItinerary(origin, destination, language)` - Cloud Function (`https.onCall`): Places nearby search -> Gemini curation/ordering -> Directions optimize -> write `trips/{id}` + `attractions[]` to Firestore.
- `generateGuidePack(tripId)` - Cloud Run job: per attraction -> check cache in `attractions/{poiId}/guideContent/{lang}`; if missing, Wikipedia fetch -> Gemini script -> Translate -> TTS -> MP3 to Cloud Storage -> cache. Returns signed URLs.
- Firestore model: `users`, `trips`, `attractions` (global shared cache keyed by POI id), `guideContent` (per attraction per language).

## Phased Roadmap

- **Phase 0 - Validation:** Manually build one Hampi experience, test the "audio triggers as I approach" UX with real travelers.
- **Phase 1 - MVP (single region, Android, English):** Auth, itinerary generation, offline pack download, geofence-triggered local audio, manual play fallback, MapLibre offline map, trip cleanup.
- **Phase 2 - Multilingual + polish:** TTS in 3-5 languages, OEM background-service hardening (Xiaomi/Samsung/Oppo), storage lifecycle UI, professional UI pass.
- **Phase 3 - Global scale + monetization:** Auto-generate content for any destination, server-side shared cache, freemium / destination packs / subscription, analytics.
- **Phase 4 - Moat:** Personalization (history vs food vs architecture), reviews/community, B2B white-label, iOS.

## Key Challenges and Mitigations

- **Background location killed by OEMs** -> Foreground service + persistent notification + battery-optimization exemption prompt; test on real devices.
- **No connectivity at remote sites** -> Pre-download everything (non-negotiable).
- **LLM hallucination** -> RAG grounding + show sources.
- **Cloud Function timeout on multi-spot/multi-language packs** -> Cloud Run + per-spot parallel jobs.
- **Cost** -> Global shared content cache; Maps free credit; cheap Gemini Flash tier.
- **Play Store background-location review** -> Clear justification + privacy policy; GDPR/CCPA/India DPDP consent flows.
- **Google Maps weak offline** -> MapLibre hybrid for tiles only, Google for Places/Directions.

## Decisions To Confirm

- Maps: MapLibre hybrid (assumed) vs pure Google Maps (simpler, weaker offline).
- Pilot region for MVP (assumed Hampi).
- Whether to use Cloud Run for guide-pack generation (assumed yes) vs chained Cloud Functions.