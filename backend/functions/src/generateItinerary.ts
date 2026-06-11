import { HttpsError } from "firebase-functions/v2/https";
import { db } from "./firebaseAdmin";
import { curateDestinationAttractions } from "./curateAttractions";
import { fetchWikipediaSummary } from "./wikipedia";
import { getGuideMeLogger } from "./logging/loggerContext";
import { validateLanguageCode } from "./utils";

type GenerateItineraryInput = {
  userId: string;
  origin: string;
  destination: string;
  languageCode: string;
  countryCode: string;
};

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
const ESTIMATED_AUDIO_MB_PER_ATTRACTION = 2.5;
const ESTIMATED_MAP_TILES_MB = 45;

export async function generateItineraryForDestination(input: GenerateItineraryInput) {
  const languageCode = validateLanguageCode(input.languageCode);
  const tripRef = db.collection("trips").doc();
  const attractions = await curateAttractions(
    input.destination,
    input.countryCode,
    languageCode
  );
  const offlinePackSizeMb = estimatePackSizeMb(attractions.length);

  const tripDoc = {
    userId: input.userId,
    origin: input.origin,
    destination: input.destination,
    languageCode,
    status: "READY",
    attractions,
    createdAtMillis: Date.now(),
    offlinePackSizeMb,
    offlinePackDownloaded: false,
  };

  await tripRef.set(tripDoc);
  await cacheGlobalAttractions(attractions);

  getGuideMeLogger().info("itinerary_generated", {
    tripId: tripRef.id,
    userId: input.userId,
    destination: input.destination,
    attractionCount: attractions.length,
    offlinePackSizeMb,
  });

  return {
    tripId: tripRef.id,
    ...tripDoc,
  };
}

function estimatePackSizeMb(attractionCount: number): number {
  const audioMb = attractionCount * ESTIMATED_AUDIO_MB_PER_ATTRACTION;
  return Math.ceil(audioMb + ESTIMATED_MAP_TILES_MB);
}

async function curateAttractions(
  destination: string,
  countryCode: string,
  languageCode: string
): Promise<AttractionDoc[]> {
  getGuideMeLogger().info("curate_attractions_started", { destination, countryCode });

  const curated = await curateDestinationAttractions({
    destination,
    countryCode,
    targetCount: MAX_ATTRACTIONS,
    operationPrefix: "itinerary",
  });

  if (curated.length === 0) {
    throw new HttpsError(
      "not-found",
      `No tourist attractions found near "${destination}". Try a more specific destination name.`
    );
  }

  getGuideMeLogger().info("curate_attractions_complete", {
    destination,
    count: curated.length,
  });

  return enrichWithWikipedia(curated, languageCode);
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
