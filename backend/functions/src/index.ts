import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { generateItineraryForDestination } from "./generateItinerary";
import { generateGuidePackForTrip } from "./generateGuidePack";

admin.initializeApp();

export const generateItinerary = onCall(
  { region: "asia-south1", timeoutSeconds: 120 },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Authentication required.");
    }

    const origin = String(request.data.origin ?? "").trim();
    const destination = String(request.data.destination ?? "").trim();
    const languageCode = String(request.data.languageCode ?? "en").trim();

    if (!origin || !destination) {
      throw new HttpsError("invalid-argument", "Origin and destination are required.");
    }

    return generateItineraryForDestination({
      userId: request.auth.uid,
      origin,
      destination,
      languageCode,
    });
  }
);

export const generateGuidePack = onCall(
  { region: "asia-south1", timeoutSeconds: 540, memory: "1GiB" },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Authentication required.");
    }

    const tripId = String(request.data.tripId ?? "").trim();
    if (!tripId) {
      throw new HttpsError("invalid-argument", "tripId is required.");
    }

    return generateGuidePackForTrip({
      userId: request.auth.uid,
      tripId,
    });
  }
);
