import "./firebaseAdmin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { generateItineraryForDestination } from "./generateItinerary";
import { generateGuidePackForTrip } from "./generateGuidePack";
import { enforceRateLimit } from "./rateLimit";
import { logEvent, toHttpsError, validateLanguageCode } from "./utils";

const geminiApiKey = defineSecret("GEMINI_API_KEY");
const googleMapsApiKey = defineSecret("GOOGLE_MAPS_API_KEY");

export const generateItinerary = onCall(
  {
    region: "asia-south1",
    timeoutSeconds: 120,
    secrets: [geminiApiKey, googleMapsApiKey],
    enforceAppCheck: false,
    invoker: "public",
  },
  async (request) => {
    if (!request.auth) {
      console.error("generateItinerary rejected: missing Firebase Auth token", {
        hasAppCheck: Boolean(request.app),
      });
      throw new HttpsError(
        "unauthenticated",
        "Authentication required. Sign in before calling generateItinerary."
      );
    }

    const origin = String(request.data.origin ?? "").trim();
    const destination = String(request.data.destination ?? "").trim();
    const languageCode = validateLanguageCode(String(request.data.languageCode ?? "en"));

    if (!origin || !destination) {
      throw new HttpsError("invalid-argument", "Origin and destination are required.");
    }

    await enforceRateLimit({
      key: `itinerary:${request.auth.uid}`,
      maxRequests: 30,
      windowMs: 60 * 60 * 1000,
    });

    try {
      return await generateItineraryForDestination({
        userId: request.auth.uid,
        origin,
        destination,
        languageCode,
      });
    } catch (error) {
      logEvent("generate_itinerary_failed", {
        userId: request.auth.uid,
        destination,
        error: error instanceof Error ? error.message : "unknown",
      });
      throw toHttpsError(error);
    }
  }
);

export const generateGuidePack = onCall(
  {
    region: "asia-south1",
    timeoutSeconds: 540,
    secrets: [geminiApiKey],
    enforceAppCheck: false,
    invoker: "public",
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "Authentication required. Sign in before calling generateGuidePack."
      );
    }

    const tripId = String(request.data.tripId ?? "").trim();
    if (!tripId) {
      throw new HttpsError("invalid-argument", "tripId is required.");
    }

    await enforceRateLimit({
      key: `guidepack:${request.auth.uid}`,
      maxRequests: 5,
      windowMs: 60 * 60 * 1000,
    });

    try {
      return await generateGuidePackForTrip({
        userId: request.auth.uid,
        tripId,
      });
    } catch (error) {
      logEvent("generate_guide_pack_failed", {
        userId: request.auth.uid,
        tripId,
        error: error instanceof Error ? error.message : "unknown",
      });
      throw toHttpsError(error);
    }
  }
);
