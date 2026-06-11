import { GoogleGenerativeAI } from "@google/generative-ai";
import { HttpsError } from "firebase-functions/v2/https";
import {
  buildGeminiRankMap,
  enrichGeminiWithPlacesMetadata,
  namesLikelyMatch,
  selectEditorialAttractions,
} from "./attractionCuration";
import {
  AttractionDoc,
  countryNameFromCode,
  extractJsonArray,
  fetchCuratedPlacesAttractionsInCountry,
  geocodeAttractionInCountry,
  geocodeDestinationInCountry,
  hasValidCoordinates,
  optimizeRoute,
  slugify,
} from "./mapsHelpers";
import { buildSpotsPrompt } from "./prompts/curatedPrompts";
import { generateGeminiText } from "./logging/geminiLogging";
import { getGuideMeLogger } from "./logging/loggerContext";
import { GEMINI_MODEL } from "./geminiConfig";

type GeminiSpot = AttractionDoc & { rank?: number; significance?: string };

export async function curateDestinationAttractions(input: {
  destination: string;
  region?: string;
  countryCode: string;
  targetCount: number;
  operationPrefix: string;
}): Promise<AttractionDoc[]> {
  const countryName = countryNameFromCode(input.countryCode);
  const region = await resolveRegion(input.destination, input.region, input.countryCode, countryName);
  const destinationQuery = `${input.destination}, ${region}, ${countryName}`;
  const expertTarget = Math.max(input.targetCount * 2, 20);
  const minCount = Math.min(3, input.targetCount);

  const [geminiSpots, placesResult] = await Promise.all([
    fetchExpertGeminiSpots(
      input.destination,
      region,
      countryName,
      input.countryCode,
      expertTarget,
      input.operationPrefix
    ),
    fetchCuratedPlacesAttractionsInCountry(destinationQuery, input.countryCode, expertTarget).catch(
      () => ({ attractions: [] as AttractionDoc[], placeTypesById: new Map<string, string[]>() })
    ),
  ]);

  if (geminiSpots.length === 0) {
    getGuideMeLogger().error("expert_spots_empty", {
      destination: input.destination,
      region,
      countryCode: input.countryCode,
      operationPrefix: input.operationPrefix,
    });
    throw new HttpsError(
      "unavailable",
      `Could not curate expert attractions for "${input.destination}" right now. Please try again shortly.`
    );
  }

  const geminiRanks = buildGeminiRankMap(
    geminiSpots.map((spot, index) => ({ name: spot.name, rank: spot.rank ?? index + 1 }))
  );

  // Google Places is used ONLY to enrich editorial picks (coordinates, photos, ratings).
  // It never contributes new candidates to the itinerary.
  let enrichedGemini = enrichGeminiWithPlacesMetadata(geminiSpots, placesResult.attractions);
  enrichedGemini = await enrichGeminiSpotsWithCoordinates(
    enrichedGemini,
    input.destination,
    region,
    input.countryCode,
    placesResult.attractions
  );

  const droppedForCoordinates = enrichedGemini.filter((spot) => !hasValidCoordinates(spot));
  if (droppedForCoordinates.length > 0) {
    getGuideMeLogger().warn("expert_spots_dropped_missing_coordinates", {
      destination: input.destination,
      region,
      countryCode: input.countryCode,
      droppedCount: droppedForCoordinates.length,
      droppedNames: droppedForCoordinates.map((spot) => spot.name),
    });
  }

  const selected = selectEditorialAttractions(enrichedGemini, {
    geminiRanks,
    targetCount: input.targetCount,
    minCount,
    placeTypesById: placesResult.placeTypesById,
  });

  if (selected.length === 0) {
    getGuideMeLogger().error("expert_spots_all_dropped", {
      destination: input.destination,
      region,
      countryCode: input.countryCode,
      geminiCount: geminiSpots.length,
    });
    throw new HttpsError(
      "not-found",
      `Could not resolve verified locations for "${input.destination}". Try a more specific destination name.`
    );
  }

  getGuideMeLogger().info("curate_destination_attractions_complete", {
    destination: input.destination,
    region,
    countryCode: input.countryCode,
    geminiCount: geminiSpots.length,
    placesEnrichmentCount: placesResult.attractions.length,
    selectedCount: selected.length,
    selectedNames: selected.map((spot) => spot.name),
  });

  const ordered = selected.map((spot, index) => ({ ...spot, orderIndex: index }));
  return optimizeRoute(ordered);
}

async function resolveRegion(
  destination: string,
  region: string | undefined,
  countryCode: string,
  countryName: string
): Promise<string> {
  if (region && region.trim() && region.trim().toLowerCase() !== destination.trim().toLowerCase()) {
    return region.trim();
  }

  try {
    const geocoded = await geocodeDestinationInCountry(
      `${destination}, ${countryName}`,
      countryCode
    );
    if (geocoded.adminArea) {
      return geocoded.adminArea;
    }
  } catch {
    // Fall back to the destination label when geocoding cannot resolve an admin area.
  }

  return destination;
}

async function fetchExpertGeminiSpots(
  destination: string,
  region: string,
  countryName: string,
  countryCode: string,
  maxResults: number,
  operationPrefix: string
): Promise<GeminiSpot[]> {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) return [];

  const genAI = new GoogleGenerativeAI(apiKey);
  const model = genAI.getGenerativeModel({ model: GEMINI_MODEL });
  const prompt = buildSpotsPrompt(destination, region, countryName, countryCode, maxResults);

  const maxAttempts = 2;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      const responseText = await generateGeminiText(
        `${operationPrefix}_expert_spots`,
        model,
        prompt,
        { destination, region, countryCode, maxResults, attempt }
      );
      const parsed = JSON.parse(extractJsonArray(responseText)) as GeminiSpot[];
      const spots = parsed
        .filter((item) => item && typeof item.name === "string" && item.name.trim().length > 0)
        .slice(0, maxResults)
        .sort((a, b) => (a.rank ?? 999) - (b.rank ?? 999))
        .map((item, index) => ({
          ...item,
          id: item.id || slugify(item.name),
          orderIndex: index,
          estimatedMinutes: item.estimatedMinutes ?? 60,
          description: item.significance || item.description || item.name,
        }));
      if (spots.length > 0) {
        return spots;
      }
      getGuideMeLogger().warn("expert_spots_empty_response", {
        destination,
        countryCode,
        attempt,
      });
    } catch (error) {
      getGuideMeLogger().error("expert_spots_failed", {
        destination,
        countryCode,
        attempt,
        error: error instanceof Error ? error.message : "unknown",
      });
    }
  }

  return [];
}

async function enrichGeminiSpotsWithCoordinates(
  spots: AttractionDoc[],
  destination: string,
  region: string,
  countryCode: string,
  placesSpots: AttractionDoc[] = []
): Promise<AttractionDoc[]> {
  return Promise.all(
    spots.map(async (spot) => {
      // Prefer authoritative Google Places coordinates when a confident name match exists,
      // because Gemini coordinates can be approximate.
      const placeMatch = placesSpots.find((place) => namesLikelyMatch(place.name, spot.name));
      if (placeMatch && hasValidCoordinates(placeMatch)) {
        return {
          ...spot,
          latitude: placeMatch.latitude,
          longitude: placeMatch.longitude,
          imageUrl: spot.imageUrl ?? placeMatch.imageUrl,
        };
      }

      if (hasValidCoordinates(spot)) {
        return spot;
      }

      const geocoded = await geocodeAttractionInCountry(
        spot.name,
        destination,
        countryCode,
        region
      );
      if (!geocoded) {
        return spot;
      }
      return { ...spot, latitude: geocoded.latitude, longitude: geocoded.longitude };
    })
  );
}
