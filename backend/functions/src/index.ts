import "./firebaseAdmin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { generateItineraryForDestination } from "./generateItinerary";
import { enforceRateLimit } from "./rateLimit";
import { logEvent, toHttpsError, validateLanguageCode } from "./utils";

const geminiApiKey = defineSecret("GEMINI_API_KEY");
const googleMapsApiKey = defineSecret("GOOGLE_MAPS_API_KEY");

export const generateItinerary = onCall(
  {
    region: "asia-south1",
    timeoutSeconds: 120,
    secrets: [geminiApiKey, googleMapsApiKey],
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Authentication required.");
    }

    const origin = String(request.data.origin ?? "").trim();
    const destination = String(request.data.destination ?? "").trim();
    const languageCode = validateLanguageCode(String(request.data.languageCode ?? "en"));

    if (!origin || !destination) {
      throw new HttpsError("invalid-argument", "Origin and destination are required.");
    }

    await enforceRateLimit({
      key: `itinerary:${request.auth.uid}`,
      maxRequests: 10,
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
