import { HttpsError } from "firebase-functions/v2/https";
import { db } from "./firebaseAdmin";
import { curateDestinationAttractions } from "./curateAttractions";
import { buildDestinationIndex, resolveDestination } from "./destinationIndex";
import { fetchWikipediaSummary } from "./wikipedia";
import { getGuideMeLogger } from "./logging/loggerContext";
import { validateLanguageCode } from "./utils";

type AttractionDoc = {
  id: string;
  name: string;
  description: string;
  latitude: number;
  longitude: number;
  imageUrl?: string;
  orderIndex: number;
  estimatedMinutes: number;
  transcript?: string;
};

const MAX_ATTRACTIONS = 8;

export async function createTripFromSearch(input: {
  userId: string;
  origin: string;
  destination: string;
  languageCode: string;
  countryCode: string;
}) {
  const languageCode = validateLanguageCode(input.languageCode);
  const countryCode = input.countryCode.trim().toUpperCase();
  const resolved = await resolveDestination(countryCode, input.destination);

  let attractions: AttractionDoc[];
  let tripDestination = input.destination;

  if (resolved?.packageId) {
    getGuideMeLogger().info("search_resolved_package", {
      destination: input.destination,
      packageId: resolved.packageId,
    });
    const packageSnap = await db.collection("tourPackages").doc(resolved.packageId).get();
    if (!packageSnap.exists) {
      throw new HttpsError("not-found", `Package ${resolved.packageId} not found in catalog.`);
    }
    const detail = packageSnap.data()!;
    tripDestination = String(detail.title ?? input.destination);
    attractions = ((detail.spots ?? []) as AttractionDoc[]).map((spot, index) => ({
      ...spot,
      orderIndex: spot.orderIndex ?? index,
      transcript:
        spot.transcript ??
        `Welcome to ${spot.name}. ${spot.description ?? ""}`.trim(),
    }));
  } else {
    getGuideMeLogger().info("search_miss_curation", {
      destination: input.destination,
      countryCode,
    });
    const curated = await curateDestinationAttractions({
      destination: input.destination,
      countryCode,
      targetCount: MAX_ATTRACTIONS,
      operationPrefix: "search",
      context: { quality: "user" },
    });
    if (curated.length === 0) {
      throw new HttpsError(
        "not-found",
        `No tourist attractions found near "${input.destination}".`
      );
    }
    attractions = await enrichWithWikipedia(curated, languageCode);
    await buildDestinationIndex(countryCode);
  }

  const tripRef = db.collection("trips").doc();
  const offlinePackSizeMb = Math.ceil(attractions.length * 2.5 + 45);
  const tripDoc = {
    userId: input.userId,
    origin: input.origin,
    destination: tripDestination,
    languageCode,
    status: "READY",
    attractions,
    createdAtMillis: Date.now(),
    offlinePackSizeMb,
    offlinePackDownloaded: false,
    packageId: resolved?.packageId ?? null,
  };

  await tripRef.set(tripDoc);
  await cacheGlobalAttractions(attractions);

  return { tripId: tripRef.id, ...tripDoc };
}

async function cacheGlobalAttractions(attractions: AttractionDoc[]): Promise<void> {
  await Promise.all(
    attractions.map((attraction) =>
      db.collection("attractions").doc(attraction.id).set(
        {
          name: attraction.name,
          description: attraction.description,
          latitude: attraction.latitude,
          longitude: attraction.longitude,
          imageUrl: attraction.imageUrl ?? null,
          estimatedMinutes: attraction.estimatedMinutes,
          updatedAtMillis: Date.now(),
        },
        { merge: true }
      )
    )
  );
}

async function enrichWithWikipedia(
  attractions: AttractionDoc[],
  languageCode: string
): Promise<AttractionDoc[]> {
  return Promise.all(
    attractions.map(async (attraction, index) => {
      const wikiSummary = await fetchWikipediaSummary(attraction.name, languageCode);
      return {
        ...attraction,
        orderIndex: index,
        description: wikiSummary || attraction.description,
        transcript: wikiSummary
          ? `Welcome to ${attraction.name}. ${wikiSummary}`
          : `Welcome to ${attraction.name}. ${attraction.description}`,
      };
    })
  );
}
