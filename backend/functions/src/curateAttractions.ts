import { GoogleGenerativeAI } from "@google/generative-ai";
import {
  buildGeminiRankMap,
  mergeAttractionLists,
  normalizeAttractionKey,
  rankCuratedAttractions,
} from "./attractionCuration";
import { fetchWikipediaSummary } from "./wikipedia";
import {
  AttractionDoc,
  countryNameFromCode,
  dedupeNearbySpots,
  extractJsonArray,
  fetchCuratedPlacesAttractionsInCountry,
  geocodeAttractionInCountry,
  optimizeRoute,
  slugify,
} from "./mapsHelpers";
import { buildSpotsPrompt } from "./prompts/curatedPrompts";
import { generateGeminiText } from "./logging/geminiLogging";
import { getGuideMeLogger } from "./logging/loggerContext";

type GeminiSpot = AttractionDoc & { rank?: number; significance?: string };

export async function curateDestinationAttractions(input: {
  destination: string;
  region?: string;
  countryCode: string;
  targetCount: number;
  operationPrefix: string;
}): Promise<AttractionDoc[]> {
  const countryName = countryNameFromCode(input.countryCode);
  const region = input.region ?? input.destination;
  const destinationQuery = `${input.destination}, ${region}, ${countryName}`;
  const expertTarget = Math.max(input.targetCount * 2, 20);

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

  const geocodedGemini = await enrichGeminiSpotsWithCoordinates(
    geminiSpots,
    input.destination,
    input.countryCode
  );

  const merged = mergeAttractionLists(geocodedGemini, placesResult.attractions);
  const geminiRanks = buildGeminiRankMap(
    geminiSpots.map((spot, index) => ({ name: spot.name, rank: spot.rank ?? index + 1 }))
  );

  const wikipediaNames = await collectWikipediaMatches(merged.slice(0, expertTarget));
  const ranked = rankCuratedAttractions(
    merged.filter((spot) => hasValidCoordinates(spot)),
    {
      geminiRanks,
      wikipediaNames,
      placeTypesById: placesResult.placeTypesById,
      targetCount: input.targetCount,
      minCount: Math.min(3, input.targetCount),
    }
  );

  const deduped = dedupeNearbySpots(ranked, 1.5);
  return optimizeRoute(deduped.map((spot, index) => ({ ...spot, orderIndex: index })));
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
  const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });
  const prompt = buildSpotsPrompt(destination, region, countryName, countryCode, maxResults);

  try {
    const responseText = await generateGeminiText(
      `${operationPrefix}_expert_spots`,
      model,
      prompt,
      { destination, countryCode, maxResults }
    );
    const parsed = JSON.parse(extractJsonArray(responseText)) as GeminiSpot[];
    return parsed
      .slice(0, maxResults)
      .sort((a, b) => (a.rank ?? 999) - (b.rank ?? 999))
      .map((item, index) => ({
        ...item,
        id: item.id || slugify(item.name),
        orderIndex: index,
        estimatedMinutes: item.estimatedMinutes ?? 60,
        description: item.significance || item.description || item.name,
      }));
  } catch (error) {
    getGuideMeLogger().error("expert_spots_failed", {
      destination,
      countryCode,
      error: error instanceof Error ? error.message : "unknown",
    });
    return [];
  }
}

async function enrichGeminiSpotsWithCoordinates(
  spots: GeminiSpot[],
  destination: string,
  countryCode: string
): Promise<AttractionDoc[]> {
  return Promise.all(
    spots.map(async (spot) => {
      if (spot.latitude && spot.longitude && spot.latitude !== 0 && spot.longitude !== 0) {
        return spot;
      }
      const geocoded = await geocodeAttractionInCountry(spot.name, destination, countryCode);
      if (!geocoded) {
        return spot;
      }
      return { ...spot, latitude: geocoded.latitude, longitude: geocoded.longitude };
    })
  );
}

function hasValidCoordinates(spot: AttractionDoc): boolean {
  return (
    Number.isFinite(spot.latitude) &&
    Number.isFinite(spot.longitude) &&
    Math.abs(spot.latitude) <= 90 &&
    Math.abs(spot.longitude) <= 180 &&
    !(spot.latitude === 0 && spot.longitude === 0)
  );
}

async function collectWikipediaMatches(spots: AttractionDoc[]): Promise<Set<string>> {
  const names = new Set<string>();
  await Promise.all(
    spots.map(async (spot) => {
      const summary = await fetchWikipediaSummary(spot.name, "en");
      if (summary) {
        names.add(normalizeAttractionKey(spot.name));
      }
    })
  );
  return names;
}
