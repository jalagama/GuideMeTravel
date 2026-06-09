import { GoogleGenerativeAI } from "@google/generative-ai";
import { HttpsError } from "firebase-functions/v2/https";
import { db } from "./firebaseAdmin";
import { fetchWikipediaSummary } from "./wikipedia";
import {
  countryNameFromCode,
  extractJsonArray,
  fetchPlacesAttractionsInCountry,
  optimizeRoute,
  slugify,
} from "./mapsHelpers";
import { logEvent, validateLanguageCode } from "./utils";

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

const MAX_ATTRACTIONS = 5;
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

  logEvent("itinerary_generated", {
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
  const placesAttractions = await fetchPlacesAttractionsInCountry(
    destination,
    countryCode,
    MAX_ATTRACTIONS
  );
  if (placesAttractions.length > 0) {
    return enrichWithWikipedia(
      await optimizeRoute(placesAttractions),
      languageCode
    );
  }

  const geminiAttractions = await fetchGeminiAttractions(destination, countryCode);
  if (geminiAttractions.length > 0) {
    return enrichWithWikipedia(
      await optimizeRoute(geminiAttractions),
      languageCode
    );
  }

  throw new HttpsError(
    "not-found",
    `No tourist attractions found near "${destination}". Try a more specific destination name.`
  );
}

async function fetchGeminiAttractions(
  destination: string,
  countryCode: string
): Promise<AttractionDoc[]> {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    throw new HttpsError(
      "failed-precondition",
      "Gemini API key is not configured. Cannot curate attractions."
    );
  }

  const countryName = countryNameFromCode(countryCode);
  const genAI = new GoogleGenerativeAI(apiKey);
  const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });
  const prompt = `
You are a travel planner for ${countryName} (${countryCode}).
Return JSON array only for major tourist attractions near ${destination} inside ${countryName}.
Each item must include: id (slug), name, description, latitude, longitude, orderIndex, estimatedMinutes.
Use only real places in ${countryName}. Limit to ${MAX_ATTRACTIONS} attractions.
Never include places outside ${countryName}.
`;

  try {
    const result = await model.generateContent(prompt);
    const text = extractJsonArray(result.response.text());
    const parsed = JSON.parse(text) as AttractionDoc[];
    return parsed.slice(0, MAX_ATTRACTIONS).map((item, index) => ({
      ...item,
      id: item.id || slugify(item.name),
      orderIndex: index,
      estimatedMinutes: item.estimatedMinutes ?? 45,
    }));
  } catch (error) {
    logEvent("gemini_curation_failed", {
      destination,
      error: error instanceof Error ? error.message : "unknown",
    });
    return [];
  }
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

