import { db } from "./firebaseAdmin";
import { slugify } from "./mapsHelpers";
import { getGuideMeLogger } from "./logging/loggerContext";

/** Bump when cached spot shape or curation logic changes materially. */
export const DESTINATION_CURATION_CACHE_VERSION = 1;

type CachedGeminiSpot = {
  id: string;
  name: string;
  description: string;
  significance?: string;
  latitude: number;
  longitude: number;
  estimatedMinutes: number;
  rank?: number;
};

type DestinationCurationCacheDoc = {
  destination: string;
  region: string;
  countryCode: string;
  spots: CachedGeminiSpot[];
  schemaVersion: number;
  updatedAtMillis: number;
};

export function destinationCurationDocId(
  destination: string,
  region: string,
  countryCode: string
): string {
  return `${countryCode.toLowerCase()}_${slugify(destination)}_${slugify(region)}`;
}

/** Shared Firestore cache — one Gemini curation per destination serves all users. */
export async function readDestinationCurationCache(
  destination: string,
  region: string,
  countryCode: string
): Promise<CachedGeminiSpot[] | null> {
  const docId = destinationCurationDocId(destination, region, countryCode);
  const snap = await db.collection("destinationCurations").doc(docId).get();
  if (!snap.exists) {
    return null;
  }

  const data = snap.data() as DestinationCurationCacheDoc;
  if ((data.schemaVersion ?? 0) < DESTINATION_CURATION_CACHE_VERSION) {
    return null;
  }

  const spots = data.spots ?? [];
  if (spots.length === 0) {
    return null;
  }

  getGuideMeLogger().logCacheHit("destinationCurations", docId, {
    spotCount: spots.length,
    destination,
    region,
  });
  return spots;
}

export async function writeDestinationCurationCache(
  destination: string,
  region: string,
  countryCode: string,
  spots: CachedGeminiSpot[]
): Promise<void> {
  if (spots.length === 0) {
    return;
  }

  const docId = destinationCurationDocId(destination, region, countryCode);
  await db.collection("destinationCurations").doc(docId).set({
    destination,
    region,
    countryCode: countryCode.toUpperCase(),
    spots,
    schemaVersion: DESTINATION_CURATION_CACHE_VERSION,
    updatedAtMillis: Date.now(),
  } satisfies DestinationCurationCacheDoc);

  getGuideMeLogger().info("destination_curation_cached", {
    docId,
    destination,
    region,
    spotCount: spots.length,
  });
}
