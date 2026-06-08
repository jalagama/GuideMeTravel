import { GoogleGenerativeAI } from "@google/generative-ai";
import { HttpsError } from "firebase-functions/v2/https";
import { db } from "./firebaseAdmin";
import { fetchWikipediaSummary } from "./wikipedia";
import { fetchWithTimeout, logEvent, validateLanguageCode } from "./utils";

type GenerateItineraryInput = {
  userId: string;
  origin: string;
  destination: string;
  languageCode: string;
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
const PLACES_RADIUS_METERS = 50000;
const ESTIMATED_AUDIO_MB_PER_ATTRACTION = 2.5;
const ESTIMATED_MAP_TILES_MB = 45;

export async function generateItineraryForDestination(input: GenerateItineraryInput) {
  const languageCode = validateLanguageCode(input.languageCode);
  const tripRef = db.collection("trips").doc();
  const attractions = await curateAttractions(input.destination, languageCode);
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
  languageCode: string
): Promise<AttractionDoc[]> {
  const placesAttractions = await fetchPlacesAttractions(destination);
  if (placesAttractions.length > 0) {
    return enrichWithWikipedia(
      await optimizeRoute(placesAttractions),
      languageCode
    );
  }

  const geminiAttractions = await fetchGeminiAttractions(destination);
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

async function fetchGeminiAttractions(destination: string): Promise<AttractionDoc[]> {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    throw new HttpsError(
      "failed-precondition",
      "Gemini API key is not configured. Cannot curate attractions."
    );
  }

  const genAI = new GoogleGenerativeAI(apiKey);
  const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });
  const prompt = `
You are a travel planner. Return JSON array only for major tourist attractions near ${destination}.
Each item must include: id (slug), name, description, latitude, longitude, orderIndex, estimatedMinutes.
Use only real places. Limit to ${MAX_ATTRACTIONS} attractions.
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

async function optimizeRoute(attractions: AttractionDoc[]): Promise<AttractionDoc[]> {
  const mapsKey = process.env.GOOGLE_MAPS_API_KEY;
  if (!mapsKey || attractions.length < 2) {
    return attractions.map((item, index) => ({ ...item, orderIndex: index }));
  }

  const origin = `${attractions[0].latitude},${attractions[0].longitude}`;
  const destination = `${attractions[attractions.length - 1].latitude},${attractions[attractions.length - 1].longitude}`;
  const waypoints = attractions
    .slice(1, -1)
    .map((item) => `${item.latitude},${item.longitude}`)
    .join("|");

  const url = `https://maps.googleapis.com/maps/api/directions/json?origin=${origin}&destination=${destination}&waypoints=optimize:true|${encodeURIComponent(waypoints)}&key=${mapsKey}`;
  const response = await fetchWithTimeout(url);
  const data = await response.json();

  if (data.status !== "OK") {
    logEvent("directions_api_warning", {
      status: data.status,
      errorMessage: data.error_message,
    });
    return attractions.map((item, index) => ({ ...item, orderIndex: index }));
  }

  const order = data.routes?.[0]?.waypoint_order as number[] | undefined;
  if (!order || order.length === 0) {
    return attractions.map((item, index) => ({ ...item, orderIndex: index }));
  }

  const middle = attractions.slice(1, -1);
  const reordered = [
    attractions[0],
    ...order.map((index) => middle[index]),
    attractions[attractions.length - 1],
  ];
  return reordered.map((item, index) => ({ ...item, orderIndex: index }));
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

async function fetchPlacesAttractions(destination: string): Promise<AttractionDoc[]> {
  const mapsKey = process.env.GOOGLE_MAPS_API_KEY;
  if (!mapsKey) {
    throw new HttpsError(
      "failed-precondition",
      "Google Maps API key is not configured."
    );
  }

  const geocodeUrl = `https://maps.googleapis.com/maps/api/geocode/json?address=${encodeURIComponent(destination)}&key=${mapsKey}`;
  const geocodeRes = await fetchWithTimeout(geocodeUrl);
  const geocodeData = await geocodeRes.json();

  if (geocodeData.status !== "OK" || !geocodeData.results?.[0]?.geometry?.location) {
    logEvent("geocode_failed", {
      destination,
      status: geocodeData.status,
      errorMessage: geocodeData.error_message,
    });
    return [];
  }

  const location = geocodeData.results[0].geometry.location;
  const placesUrl = `https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=${location.lat},${location.lng}&radius=${PLACES_RADIUS_METERS}&type=tourist_attraction&key=${mapsKey}`;
  const placesRes = await fetchWithTimeout(placesUrl);
  const placesData = await placesRes.json();

  if (placesData.status !== "OK" && placesData.status !== "ZERO_RESULTS") {
    logEvent("places_api_failed", {
      destination,
      status: placesData.status,
      errorMessage: placesData.error_message,
    });
    return [];
  }

  return (placesData.results ?? []).slice(0, MAX_ATTRACTIONS).map((place: any, index: number) => ({
    id: place.place_id,
    name: place.name,
    description: place.vicinity ?? "Popular tourist attraction",
    latitude: place.geometry.location.lat,
    longitude: place.geometry.location.lng,
    orderIndex: index,
    estimatedMinutes: 45,
    imageUrl: place.photos?.[0]
      ? `https://maps.googleapis.com/maps/api/place/photo?maxwidth=640&photo_reference=${place.photos[0].photo_reference}&key=${mapsKey}`
      : undefined,
  }));
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

function extractJsonArray(text: string): string {
  const start = text.indexOf("[");
  const end = text.lastIndexOf("]");
  if (start >= 0 && end > start) {
    return text.slice(start, end + 1);
  }
  return text;
}

function slugify(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}
