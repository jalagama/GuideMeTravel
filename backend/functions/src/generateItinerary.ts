import * as admin from "firebase-admin";
import { GoogleGenerativeAI } from "@google/generative-ai";

const db = admin.firestore();

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

const HAMPI_ATTRACTIONS: AttractionDoc[] = [
  {
    id: "virupaksha",
    name: "Virupaksha Temple",
    description: "A 7th-century temple dedicated to Lord Shiva and the spiritual heart of Hampi.",
    latitude: 15.335,
    longitude: 76.46,
    imageUrl: "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8e/Virupaksha_Temple_Hampi.jpg/640px-Virupaksha_Temple_Hampi.jpg",
    orderIndex: 0,
    estimatedMinutes: 60,
  },
  {
    id: "vittala",
    name: "Vittala Temple",
    description: "Famous for its iconic stone chariot and musical pillars.",
    latitude: 15.342,
    longitude: 76.478,
    imageUrl: "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4a/Stone_Chariot_Hampi.jpg/640px-Stone_Chariot_Hampi.jpg",
    orderIndex: 1,
    estimatedMinutes: 75,
  },
  {
    id: "lotus",
    name: "Lotus Mahal",
    description: "An elegant Indo-Islamic pavilion in the Zenana enclosure.",
    latitude: 15.3185,
    longitude: 76.4715,
    imageUrl: "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1f/Lotus_Mahal_Hampi.jpg/640px-Lotus_Mahal_Hampi.jpg",
    orderIndex: 2,
    estimatedMinutes: 40,
  },
  {
    id: "matanga",
    name: "Matanga Hill",
    description: "Panoramic sunrise and sunset views over the Hampi boulder landscape.",
    latitude: 15.3375,
    longitude: 76.4675,
    imageUrl: "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5e/Hampi_sunrise.jpg/640px-Hampi_sunrise.jpg",
    orderIndex: 3,
    estimatedMinutes: 50,
  },
  {
    id: "hemakuta",
    name: "Hemakuta Hill Temples",
    description: "Cluster of early temples with sweeping views near Virupaksha.",
    latitude: 15.3338,
    longitude: 76.4588,
    imageUrl: "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9a/Hemakuta_Hill_Hampi.jpg/640px-Hemakuta_Hill_Hampi.jpg",
    orderIndex: 4,
    estimatedMinutes: 45,
  },
];

export async function generateItineraryForDestination(input: GenerateItineraryInput) {
  const tripRef = db.collection("trips").doc();
  const attractions = await curateAttractions(input.destination);

  const tripDoc = {
    userId: input.userId,
    origin: input.origin,
    destination: input.destination,
    languageCode: input.languageCode,
    status: "READY",
    attractions,
    createdAtMillis: Date.now(),
    offlinePackSizeMb: 120,
    offlinePackDownloaded: false,
  };

  await tripRef.set(tripDoc);

  return {
    tripId: tripRef.id,
    ...tripDoc,
  };
}

async function curateAttractions(destination: string): Promise<AttractionDoc[]> {
  const normalized = destination.toLowerCase();
  if (normalized.includes("hampi")) {
    return enrichWithWikipedia(HAMPI_ATTRACTIONS);
  }

  const placesAttractions = await fetchPlacesAttractions(destination);
  if (placesAttractions.length > 0) {
    return enrichWithWikipedia(placesAttractions);
  }

  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    return enrichWithWikipedia(HAMPI_ATTRACTIONS);
  }

  const genAI = new GoogleGenerativeAI(apiKey);
  const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });
  const prompt = `
You are a travel planner. Return JSON array only for major tourist attractions near ${destination}.
Each item must include: id, name, description, latitude, longitude, orderIndex, estimatedMinutes.
Use only real places. Limit to 5 attractions.
`;

  const result = await model.generateContent(prompt);
  const text = extractJsonArray(result.response.text());
  try {
    const parsed = JSON.parse(text) as AttractionDoc[];
    return enrichWithWikipedia(parsed.slice(0, 5));
  } catch {
    return enrichWithWikipedia(HAMPI_ATTRACTIONS);
  }
}

async function fetchPlacesAttractions(destination: string): Promise<AttractionDoc[]> {
  const mapsKey = process.env.GOOGLE_MAPS_API_KEY;
  if (!mapsKey) return [];

  const geocodeUrl = `https://maps.googleapis.com/maps/api/geocode/json?address=${encodeURIComponent(destination)}&key=${mapsKey}`;
  const geocodeRes = await fetch(geocodeUrl);
  const geocodeData = await geocodeRes.json();
  const location = geocodeData.results?.[0]?.geometry?.location;
  if (!location) return [];

  const placesUrl = `https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=${location.lat},${location.lng}&radius=50000&type=tourist_attraction&key=${mapsKey}`;
  const placesRes = await fetch(placesUrl);
  const placesData = await placesRes.json();

  return (placesData.results ?? []).slice(0, 5).map((place: any, index: number) => ({
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

async function enrichWithWikipedia(attractions: AttractionDoc[]): Promise<AttractionDoc[]> {
  return Promise.all(
    attractions.map(async (attraction, index) => {
      const wikiSummary = await fetchWikipediaSummary(attraction.name);
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

async function fetchWikipediaSummary(title: string): Promise<string> {
  const url = `https://en.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(title)}`;
  const response = await fetch(url);
  if (!response.ok) return "";
  const data = await response.json();
  return typeof data.extract === "string" ? data.extract : "";
}

function extractJsonArray(text: string): string {
  const start = text.indexOf("[");
  const end = text.lastIndexOf("]");
  if (start >= 0 && end > start) {
    return text.slice(start, end + 1);
  }
  return text;
}
