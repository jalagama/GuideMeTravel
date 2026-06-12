import "./firebaseAdmin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { createTripFromSearch } from "./resolveSearchTrip";
import { generateGuidePackForTrip, getGuidePackForTrip } from "./generateGuidePack";
import {
  createTripFromPackage as createTripFromPackageService,
  getCountryGenres as getCountryGenresService,
  getGenrePackages as getGenrePackagesService,
  getTourPackageDetail as getTourPackageDetailService,
} from "./curatedContent";
import { advanceCurationJob, getCurationJob, listCurationJobs, startCountryCuration } from "./bulkCurateCountry";
import { requireAdmin } from "./admin/requireAdmin";
import { setAdminClaimForUid } from "./admin/setAdminClaim";
import type { CurationMode } from "./curationTypes";
import { enforceRateLimit } from "./rateLimit";
import { logEvent, toHttpsError, validateLanguageCode } from "./utils";

const geminiApiKey = defineSecret("GEMINI_API_KEY");
const googleMapsApiKey = defineSecret("GOOGLE_MAPS_API_KEY");

function bindGeminiSecret(): void {
  process.env.GEMINI_API_KEY = geminiApiKey.value();
}

function bindMapsSecret(): void {
  process.env.GOOGLE_MAPS_API_KEY = googleMapsApiKey.value();
}

/** Bind Firebase secrets into process.env for shared backend modules. */
function bindCallableSecrets(): void {
  bindGeminiSecret();
  bindMapsSecret();
}

export const generateItinerary = onCall(
  {
    region: "asia-south1",
    timeoutSeconds: 120,
    secrets: [geminiApiKey, googleMapsApiKey],
    enforceAppCheck: false,
    invoker: "public",
  },
  async (request) => {
    bindCallableSecrets();

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
    const countryCode = String(request.data.countryCode ?? "").trim().toUpperCase();

    if (!origin || !destination) {
      throw new HttpsError("invalid-argument", "Origin and destination are required.");
    }
    if (!countryCode) {
      throw new HttpsError("invalid-argument", "countryCode is required.");
    }

    await enforceRateLimit({
      key: `itinerary:${request.auth.uid}`,
      maxRequests: 30,
      windowMs: 60 * 60 * 1000,
    });

    try {
      return await createTripFromSearch({
        userId: request.auth.uid,
        origin,
        destination,
        languageCode,
        countryCode,
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
    bindGeminiSecret();

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

export const getCountryGenres = onCall(
  {
    region: "asia-south1",
    timeoutSeconds: 120,
    secrets: [geminiApiKey, googleMapsApiKey],
    enforceAppCheck: false,
    invoker: "public",
  },
  async (request) => {
    bindCallableSecrets();

    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Authentication required.");
    }
    const countryCode = String(request.data.countryCode ?? "").trim();
    if (!countryCode) {
      throw new HttpsError("invalid-argument", "countryCode is required.");
    }
    await enforceRateLimit({
      key: `genres:${request.auth.uid}`,
      maxRequests: 20,
      windowMs: 60 * 60 * 1000,
    });
    try {
      return await getCountryGenresService(countryCode);
    } catch (error) {
      throw toHttpsError(error);
    }
  }
);

export const getGenrePackages = onCall(
  {
    region: "asia-south1",
    timeoutSeconds: 180,
    secrets: [geminiApiKey, googleMapsApiKey],
    enforceAppCheck: false,
    invoker: "public",
  },
  async (request) => {
    bindCallableSecrets();

    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Authentication required.");
    }
    const countryCode = String(request.data.countryCode ?? "").trim();
    const genreId = String(request.data.genreId ?? "").trim();
    if (!countryCode || !genreId) {
      throw new HttpsError("invalid-argument", "countryCode and genreId are required.");
    }
    await enforceRateLimit({
      key: `packages:${request.auth.uid}`,
      maxRequests: 30,
      windowMs: 60 * 60 * 1000,
    });
    try {
      return await getGenrePackagesService(countryCode, genreId);
    } catch (error) {
      throw toHttpsError(error);
    }
  }
);

export const getTourPackageDetail = onCall(
  {
    region: "asia-south1",
    timeoutSeconds: 300,
    secrets: [geminiApiKey, googleMapsApiKey],
    enforceAppCheck: false,
    invoker: "public",
  },
  async (request) => {
    bindCallableSecrets();

    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Authentication required.");
    }
    const packageId = String(request.data.packageId ?? "").trim();
    const countryCode = String(request.data.countryCode ?? "").trim();
    const genreId = String(request.data.genreId ?? "").trim();
    if (!packageId) {
      throw new HttpsError("invalid-argument", "packageId is required.");
    }
    try {
      return await getTourPackageDetailService(
        packageId,
        countryCode || undefined,
        genreId || undefined
      );
    } catch (error) {
      throw toHttpsError(error);
    }
  }
);

export const createTripFromPackage = onCall(
  {
    region: "asia-south1",
    timeoutSeconds: 300,
    secrets: [geminiApiKey, googleMapsApiKey],
    enforceAppCheck: false,
    invoker: "public",
  },
  async (request) => {
    bindCallableSecrets();

    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Authentication required.");
    }
    const packageId = String(request.data.packageId ?? "").trim();
    const countryCode = String(request.data.countryCode ?? "").trim();
    const genreId = String(request.data.genreId ?? "").trim();
    const origin = String(request.data.origin ?? "Current location").trim();
    const languageCode = validateLanguageCode(String(request.data.languageCode ?? "en"));
    if (!packageId) {
      throw new HttpsError("invalid-argument", "packageId is required.");
    }
    await enforceRateLimit({
      key: `package-trip:${request.auth.uid}`,
      maxRequests: 20,
      windowMs: 60 * 60 * 1000,
    });
    try {
      return await createTripFromPackageService({
        userId: request.auth.uid,
        packageId,
        origin,
        languageCode,
        countryCode: countryCode || undefined,
        genreId: genreId || undefined,
      });
    } catch (error) {
      throw toHttpsError(error);
    }
  }
);

export const getGuidePackForTripCallable = onCall(
  {
    region: "asia-south1",
    timeoutSeconds: 120,
    secrets: [geminiApiKey],
    enforceAppCheck: false,
    invoker: "public",
  },
  async (request) => {
    bindGeminiSecret();

    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Authentication required.");
    }

    const tripId = String(request.data.tripId ?? "").trim();
    if (!tripId) {
      throw new HttpsError("invalid-argument", "tripId is required.");
    }

    try {
      return await getGuidePackForTrip({
        userId: request.auth.uid,
        tripId,
      });
    } catch (error) {
      throw toHttpsError(error);
    }
  }
);

export const adminStartCountryCuration = onCall(
  {
    region: "asia-south1",
    timeoutSeconds: 120,
    secrets: [geminiApiKey, googleMapsApiKey],
    enforceAppCheck: false,
    invoker: "public",
  },
  async (request) => {
    bindCallableSecrets();
    const uid = requireAdmin(request);

    const countryCode = String(request.data.countryCode ?? "").trim();
    const mode = String(request.data.mode ?? "full") as CurationMode;
    const languagesRaw = request.data.languages;
    const languages = Array.isArray(languagesRaw)
      ? languagesRaw.map((lang) => String(lang))
      : ["en"];

    if (!countryCode) {
      throw new HttpsError("invalid-argument", "countryCode is required.");
    }
    if (!["full", "catalog_only", "languages_only"].includes(mode)) {
      throw new HttpsError("invalid-argument", "Invalid curation mode.");
    }

    return startCountryCuration({ countryCode, languages, mode, startedBy: uid });
  }
);

export const adminAdvanceCurationJob = onCall(
  {
    region: "asia-south1",
    timeoutSeconds: 540,
    secrets: [geminiApiKey, googleMapsApiKey],
    enforceAppCheck: false,
    invoker: "public",
  },
  async (request) => {
    bindCallableSecrets();
    requireAdmin(request);

    const jobId = String(request.data.jobId ?? "").trim();
    if (!jobId) {
      throw new HttpsError("invalid-argument", "jobId is required.");
    }

    let hasMore = true;
    let steps = 0;
    while (hasMore && steps < 25) {
      hasMore = await advanceCurationJob(jobId);
      steps += 1;
    }

    return { jobId, stepsProcessed: steps, hasMore };
  }
);

export const adminGetCurationStatus = onCall(
  {
    region: "asia-south1",
    timeoutSeconds: 30,
    enforceAppCheck: false,
    invoker: "public",
  },
  async (request) => {
    requireAdmin(request);
    const jobId = String(request.data.jobId ?? "").trim();
    if (!jobId) {
      throw new HttpsError("invalid-argument", "jobId is required.");
    }
    const job = await getCurationJob(jobId);
    if (!job) {
      throw new HttpsError("not-found", `Job ${jobId} not found.`);
    }
    return job;
  }
);

export const adminListCurationJobs = onCall(
  {
    region: "asia-south1",
    timeoutSeconds: 30,
    enforceAppCheck: false,
    invoker: "public",
  },
  async (request) => {
    requireAdmin(request);
    const countryCode = String(request.data.countryCode ?? "").trim();
    const limit = Number(request.data.limit ?? 10);
    if (!countryCode) {
      throw new HttpsError("invalid-argument", "countryCode is required.");
    }
    const jobs = await listCurationJobs(countryCode, limit);
    return { jobs };
  }
);

export const adminSetAdminClaim = onCall(
  {
    region: "asia-south1",
    timeoutSeconds: 30,
    enforceAppCheck: false,
    invoker: "public",
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Authentication required.");
    }
    const targetUid = String(request.data.uid ?? request.auth.uid).trim();
    await setAdminClaimForUid(targetUid, request.auth.uid);
    return { uid: targetUid, admin: true };
  }
);
