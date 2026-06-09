import { GoogleGenerativeAI } from "@google/generative-ai";
import { HttpsError } from "firebase-functions/v2/https";
import { db } from "./firebaseAdmin";
import { fetchWikipediaSummary } from "./wikipedia";
import {
  AttractionDoc,
  assignDays,
  countryNameFromCode,
  CURATED_SCHEMA_VERSION,
  dedupeNearbySpots,
  extractJsonArray,
  fetchPlacesAttractionsInCountry,
  optimizeRoute,
  passesQualityGate,
  qualityScore,
  slugify,
  targetSpotCount,
} from "./mapsHelpers";
import { logEvent } from "./utils";

type GenreDoc = {
  id: string;
  name: string;
  type: string;
  imageUrl: string;
  blurb: string;
};

type PackageSummaryDoc = {
  id: string;
  title: string;
  region: string;
  days: number;
  heroImageUrl: string;
  shortInfo: string;
};

type NearbyPlaceDoc = {
  name: string;
  description: string;
  latitude?: number;
  longitude?: number;
  rating?: number;
};

type TourPackageDetailDoc = {
  id: string;
  countryCode: string;
  genreId: string;
  title: string;
  region: string;
  days: number;
  heroImageUrl: string;
  overview: string;
  spots: AttractionDoc[];
  tips: string[];
  essentials: string[];
  highlights: string[];
  hotels: NearbyPlaceDoc[];
  restaurants: NearbyPlaceDoc[];
  createdAtMillis: number;
};

export async function getCountryGenres(countryCode: string) {
  const normalized = countryCode.trim().toUpperCase();
  const cacheRef = db.collection("curatedGenres").doc(normalized);
  const cached = await cacheRef.get();
  if (cached.exists && (cached.data()?.schemaVersion ?? 1) >= CURATED_SCHEMA_VERSION) {
    return cached.data();
  }

  const countryName = countryNameFromCode(normalized);
  const genres = await generateGenresWithGemini(countryName, normalized);
  const doc = {
    countryCode: normalized,
    countryName,
    genres,
    schemaVersion: CURATED_SCHEMA_VERSION,
    updatedAtMillis: Date.now(),
  };
  await cacheRef.set(doc);
  logEvent("country_genres_generated", { countryCode: normalized, count: genres.length });
  return doc;
}

export async function getGenrePackages(countryCode: string, genreId: string) {
  const normalized = countryCode.trim().toUpperCase();
  const cacheId = `${normalized}_${genreId}`;
  const cacheRef = db.collection("curatedPackages").doc(cacheId);
  const cached = await cacheRef.get();
  if (cached.exists && (cached.data()?.schemaVersion ?? 1) >= CURATED_SCHEMA_VERSION) {
    return cached.data();
  }

  const genresDoc = await getCountryGenres(normalized);
  const genre = (genresDoc?.genres as GenreDoc[] | undefined)?.find((g) => g.id === genreId);
  if (!genre) {
    throw new HttpsError("not-found", `Genre ${genreId} not found for ${normalized}`);
  }

  const packages = await generatePackagesWithGemini(
    countryNameFromCode(normalized),
    normalized,
    genre
  );
  const doc = {
    countryCode: normalized,
    genreId,
    genreName: genre.name,
    packages,
    schemaVersion: CURATED_SCHEMA_VERSION,
    updatedAtMillis: Date.now(),
  };
  await cacheRef.set(doc);
  logEvent("genre_packages_generated", { countryCode: normalized, genreId, count: packages.length });
  return doc;
}

export async function getTourPackageDetail(
  packageId: string,
  countryCode?: string,
  genreId?: string
) {
  const cacheRef = db.collection("tourPackages").doc(packageId);
  const cached = await cacheRef.get();
  if (cached.exists) {
    return cached.data();
  }

  const resolved = await resolvePackageSummary(packageId, countryCode, genreId);
  if (!resolved) {
    throw new HttpsError(
      "not-found",
      `Tour package ${packageId} not found. Browse packages from a genre tile first.`
    );
  }

  const detail = await ensureTourPackageDetail(
    resolved.countryCode,
    resolved.genreId,
    resolved.summary
  );
  return detail;
}

export async function ensureTourPackageDetail(
  countryCode: string,
  genreId: string,
  packageSummary: PackageSummaryDoc
): Promise<TourPackageDetailDoc> {
  const cacheRef = db.collection("tourPackages").doc(packageSummary.id);
  const cached = await cacheRef.get();
  if (cached.exists) {
    return cached.data() as TourPackageDetailDoc;
  }

  const detail = await generateTourPackageDetail(countryCode, genreId, packageSummary);
  await cacheRef.set(detail);
  logEvent("tour_package_detail_generated", { packageId: packageSummary.id });
  return detail;
}

export async function createTripFromPackage(input: {
  userId: string;
  packageId: string;
  origin: string;
  languageCode: string;
  countryCode?: string;
  genreId?: string;
}) {
  const detailData = await getTourPackageDetail(
    input.packageId,
    input.countryCode,
    input.genreId
  );
  const detail = detailData as TourPackageDetailDoc;
  const tripRef = db.collection("trips").doc();
  const attractions = detail.spots.map((spot, index) => ({
    id: spot.id,
    name: spot.name,
    description: spot.description,
    latitude: spot.latitude,
    longitude: spot.longitude,
    imageUrl: spot.imageUrl ?? null,
    orderIndex: spot.orderIndex ?? index,
    estimatedMinutes: spot.estimatedMinutes ?? 45,
    transcript: spot.transcript ?? `Welcome to ${spot.name}. ${spot.description}`,
  }));

  const offlinePackSizeMb = Math.ceil(attractions.length * 2.5 + 45);
  const tripDoc = {
    userId: input.userId,
    origin: input.origin,
    destination: detail.title,
    languageCode: input.languageCode,
    status: "READY",
    attractions,
    createdAtMillis: Date.now(),
    offlinePackSizeMb,
    offlinePackDownloaded: false,
    packageId: input.packageId,
  };

  await tripRef.set(tripDoc);
  await Promise.all(
    attractions.map((attraction) =>
      db.collection("attractions").doc(attraction.id).set(
        {
          name: attraction.name,
          description: attraction.description,
          latitude: attraction.latitude,
          longitude: attraction.longitude,
          imageUrl: attraction.imageUrl,
          estimatedMinutes: attraction.estimatedMinutes,
          updatedAtMillis: Date.now(),
        },
        { merge: true }
      )
    )
  );

  return { tripId: tripRef.id, ...tripDoc };
}

async function generateGenresWithGemini(countryName: string, countryCode: string): Promise<GenreDoc[]> {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    return fallbackGenres(countryCode);
  }

  const genAI = new GoogleGenerativeAI(apiKey);
  const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });
  const prompt = `
You are an AI travel curator for ${countryName} (ISO country code: ${countryCode}).
Return JSON only:
{ "genres": [ { "id": "historical-heritage", "name": "Historical & Heritage Destinations", "type": "heritage", "blurb": "short sentence", "imageSearchHint": "heritage landmark ${countryName}" } ] }
Rules:
- Return 10 to 12 top tourist experience categories for ${countryName} only.
- Names should read like travel themes, e.g. "Hill Stations & Mountains", "Beaches & Coastal Getaways", "Wildlife & National Parks".
- Cover the most popular tourist regions and experiences within ${countryName}.
- NEVER include categories for other countries.
- id must be lowercase slug.
- No fabricated places.
`;

  try {
    const result = await model.generateContent(prompt);
    const parsed = JSON.parse(extractJsonArray(result.response.text())) as { genres: GenreDoc[] };
    const mapsKey = process.env.GOOGLE_MAPS_API_KEY;
    return Promise.all(
      (parsed.genres ?? []).slice(0, 12).map(async (genre, index) => ({
        id: genre.id || slugify(genre.name),
        name: genre.name,
        type: genre.type || "region",
        blurb: genre.blurb || `Explore ${genre.name} in ${countryName}`,
        imageUrl: mapsKey
          ? await resolveGenreImage(`${genre.name} ${countryName}`, mapsKey)
          : fallbackGenreImage(index),
      }))
    );
  } catch (error) {
    logEvent("genre_generation_failed", {
      countryCode,
      error: error instanceof Error ? error.message : "unknown",
    });
    return fallbackGenres(countryCode);
  }
}

async function generatePackagesWithGemini(
  countryName: string,
  countryCode: string,
  genre: GenreDoc
): Promise<PackageSummaryDoc[]> {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    return buildFallbackPackages(countryCode, genre);
  }

  const genAI = new GoogleGenerativeAI(apiKey);
  const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });
  const prompt = `
You are an AI travel curator for ${countryName} (ISO country code: ${countryCode}).
List iconic tourist destinations for the category "${genre.name}" (${genre.type}).
Return JSON only:
{ "packages": [ { "id": "in-taj-mahal", "title": "Taj Mahal", "region": "Agra, Uttar Pradesh", "days": 2, "shortInfo": "UNESCO marble mausoleum", "imageSearchHint": "Taj Mahal Agra ${countryName}" } ] }
Rules:
- Return 6 to 10 famous real places or cities ONLY inside ${countryName}.
- title = destination name (e.g. Taj Mahal, Jaipur, Hampi, Khajuraho Group of Monuments).
- region = city/state within ${countryName}.
- days = suggested visit length between 1 and 5.
- NEVER include destinations outside ${countryName}.
- id must be unique lowercase slug with prefix "${countryCode.toLowerCase()}-".
- No fabricated places.
`;

  try {
    const result = await model.generateContent(prompt);
    const parsed = JSON.parse(extractJsonArray(result.response.text())) as {
      packages: PackageSummaryDoc[];
    };
    const mapsKey = process.env.GOOGLE_MAPS_API_KEY;
    const packages = parsed.packages ?? [];
    const enriched = await Promise.all(
      packages.slice(0, 10).map(async (pkg, index) => {
        const id = pkg.id?.startsWith(countryCode.toLowerCase())
          ? pkg.id
          : `${countryCode.toLowerCase()}-${pkg.id || slugify(pkg.title)}`;
        const imageQuery = `${pkg.title} ${pkg.region} ${countryName}`;
        return {
          id,
          title: pkg.title,
          region: pkg.region,
          days: Math.max(1, Math.min(5, pkg.days || 2)),
          heroImageUrl: mapsKey
            ? await resolveGenreImage(imageQuery, mapsKey)
            : fallbackGenreImage(index),
          shortInfo: pkg.shortInfo || pkg.title,
        };
      })
    );
    return enriched;
  } catch (error) {
    logEvent("package_generation_failed", {
      countryCode,
      genreId: genre.id,
      error: error instanceof Error ? error.message : "unknown",
    });
    return await buildFallbackPackages(countryCode, genre);
  }
}

async function resolvePackageSummary(
  packageId: string,
  countryCode?: string,
  genreId?: string
): Promise<{ countryCode: string; genreId: string; summary: PackageSummaryDoc } | null> {
  if (countryCode && genreId) {
    const found = await findPackageInCuratedList(
      countryCode.trim().toUpperCase(),
      genreId.trim(),
      packageId
    );
    if (found) return found;
  }

  const snapshot = await db.collection("curatedPackages").get();
  for (const doc of snapshot.docs) {
    const data = doc.data();
    const packages = (data.packages as PackageSummaryDoc[] | undefined) ?? [];
    const summary = packages.find((pkg) => pkg.id === packageId);
    if (summary) {
      return {
        countryCode: String(data.countryCode ?? ""),
        genreId: String(data.genreId ?? ""),
        summary,
      };
    }
  }
  return null;
}

async function findPackageInCuratedList(
  countryCode: string,
  genreId: string,
  packageId: string
): Promise<{ countryCode: string; genreId: string; summary: PackageSummaryDoc } | null> {
  const cacheRef = db.collection("curatedPackages").doc(`${countryCode}_${genreId}`);
  const cached = await cacheRef.get();
  if (!cached.exists) return null;

  const data = cached.data()!;
  const packages = (data.packages as PackageSummaryDoc[] | undefined) ?? [];
  const summary = packages.find((pkg) => pkg.id === packageId);
  if (!summary) return null;

  return { countryCode, genreId, summary };
}

async function buildFallbackPackages(
  countryCode: string,
  genre: GenreDoc
): Promise<PackageSummaryDoc[]> {
  return fallbackPackageSummaries(countryCode, genre);
}

async function generateTourPackageDetail(
  countryCode: string,
  genreId: string,
  summary: PackageSummaryDoc
): Promise<TourPackageDetailDoc> {
  const countryName = countryNameFromCode(countryCode);
  const days = summary.days;
  const targetCount = targetSpotCount(days);

  const destinationQuery = `${summary.title}, ${summary.region}, ${countryName}`;
  let candidates = await fetchPlacesAttractionsInCountry(destinationQuery, countryCode, 40);
  if (candidates.length < 3) {
    candidates = await fetchGeminiSpots(destinationQuery, countryName, countryCode, targetCount);
  }

  let curated = dedupeNearbySpots(
    candidates
      .filter(passesQualityGate)
      .sort((a, b) => qualityScore(b) - qualityScore(a))
      .slice(0, targetCount)
  );

  if (curated.length < 3) {
    curated = dedupeNearbySpots(
      candidates.sort((a, b) => qualityScore(b) - qualityScore(a)).slice(0, Math.max(3, targetCount))
    );
  }

  curated = await optimizeRoute(curated);
  curated = assignDays(curated, days);

  curated = await Promise.all(
    curated.map(async (spot, index) => {
      const wiki = await fetchWikipediaSummary(spot.name, "en");
      const whyChosen = await generateWhyChosen(spot, summary.title);
      return {
        ...spot,
        orderIndex: index,
        description: wiki || spot.description,
        whyChosen,
        transcript: wiki
          ? `Welcome to ${spot.name}. ${wiki}`
          : `Welcome to ${spot.name}. ${spot.description}`,
      };
    })
  );

  const extras = await generatePackageExtras(summary, countryName, curated);

  return {
    id: summary.id,
    countryCode,
    genreId,
    title: summary.title,
    region: summary.region,
    days,
    heroImageUrl: summary.heroImageUrl,
    overview: extras.overview,
    spots: curated,
    tips: extras.tips,
    essentials: extras.essentials,
    highlights: extras.highlights,
    hotels: extras.hotels,
    restaurants: extras.restaurants,
    createdAtMillis: Date.now(),
  };
}

async function fetchGeminiSpots(
  destination: string,
  countryName: string,
  countryCode: string,
  maxResults: number
): Promise<AttractionDoc[]> {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) return [];

  const genAI = new GoogleGenerativeAI(apiKey);
  const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });
  const prompt = `
Return JSON array only for major real tourist attractions near ${destination} in ${countryName}.
Each item: id, name, description, latitude, longitude, estimatedMinutes, rating, userRatingsTotal.
Use only real places located inside ${countryName} (${countryCode}). Max ${maxResults} items. No fabrication.
Never include attractions outside ${countryName}.
`;

  try {
    const result = await model.generateContent(prompt);
    const parsed = JSON.parse(extractJsonArray(result.response.text())) as AttractionDoc[];
    return parsed.slice(0, maxResults).map((item, index) => ({
      ...item,
      id: item.id || slugify(item.name),
      orderIndex: index,
      estimatedMinutes: item.estimatedMinutes ?? 45,
    }));
  } catch {
    return [];
  }
}

async function generateWhyChosen(spot: AttractionDoc, packageTitle: string): Promise<string> {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    return `Highly rated stop on the ${packageTitle} route.`;
  }

  const genAI = new GoogleGenerativeAI(apiKey);
  const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });
  const prompt = `
In one concise sentence, explain why "${spot.name}" is a must-visit on "${packageTitle}".
Use rating ${spot.rating ?? "unknown"} and ${spot.userRatingsTotal ?? 0} reviews if helpful.
No fabrication. Plain text only.
`;

  try {
    const result = await model.generateContent(prompt);
    return result.response.text().trim();
  } catch {
    return `Top-reviewed highlight on the ${packageTitle} itinerary.`;
  }
}

async function generatePackageExtras(
  summary: PackageSummaryDoc,
  countryName: string,
  spots: AttractionDoc[]
) {
  const apiKey = process.env.GEMINI_API_KEY;
  const spotNames = spots.map((s) => s.name).join(", ");
  const fallback = {
    overview: `A ${summary.days}-day curated journey through ${summary.region} in ${countryName}, covering ${spots.length} top-rated attractions.`,
    tips: [
      "Start early to avoid crowds at popular spots.",
      "Carry water and sun protection.",
      "Check local weather before heading to outdoor locations.",
    ],
    essentials: ["Comfortable walking shoes", "Government ID", "Local SIM or offline maps"],
    highlights: spots.slice(0, 5).map((s) => s.name),
    hotels: [] as NearbyPlaceDoc[],
    restaurants: [] as NearbyPlaceDoc[],
  };

  if (!apiKey) return fallback;

  const genAI = new GoogleGenerativeAI(apiKey);
  const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });
  const prompt = `
You are curating a ${summary.days}-day trip plan for the destination "${summary.title}" in ${summary.region}, ${countryName}.
Spots: ${spotNames}
Return JSON only:
{
  "overview": "2-3 sentences",
  "tips": ["..."],
  "essentials": ["..."],
  "highlights": ["..."],
  "hotels": [{ "name": "", "description": "", "rating": 4.2 }],
  "restaurants": [{ "name": "", "description": "local cuisine", "rating": 4.3 }]
}
Rules:
- tips/essentials/highlights must be practical and accurate for this region.
- hotels/restaurants optional but if included must be real local establishments only.
- No fabrication.
`;

  try {
    const result = await model.generateContent(prompt);
    const parsed = JSON.parse(extractJsonArray(result.response.text())) as typeof fallback;
    return {
      overview: parsed.overview || fallback.overview,
      tips: parsed.tips?.length ? parsed.tips : fallback.tips,
      essentials: parsed.essentials?.length ? parsed.essentials : fallback.essentials,
      highlights: parsed.highlights?.length ? parsed.highlights : fallback.highlights,
      hotels: parsed.hotels ?? [],
      restaurants: parsed.restaurants ?? [],
    };
  } catch {
    return fallback;
  }
}

async function resolveGenreImage(query: string, mapsKey: string): Promise<string> {
  const url = `https://maps.googleapis.com/maps/api/place/findplacefromtext/json?input=${encodeURIComponent(query)}&inputtype=textquery&fields=photos&key=${mapsKey}`;
  const response = await fetch(url);
  const data = await response.json();
  const photoRef = data.candidates?.[0]?.photos?.[0]?.photo_reference;
  if (!photoRef) return fallbackGenreImage(0);
  return `https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photo_reference=${photoRef}&key=${mapsKey}`;
}

function fallbackGenreImage(index: number): string {
  const images = [
    "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=800",
    "https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=800",
    "https://images.unsplash.com/photo-1501593346470-b175b7b2f5c0?w=800",
    "https://images.unsplash.com/photo-1476514525535-07fb3b4fcc5f?w=800",
  ];
  return images[index % images.length];
}

function fallbackGenres(countryCode: string): GenreDoc[] {
  if (countryCode === "IN") {
    return [
      {
        id: "historical-heritage",
        name: "Historical & Heritage Destinations",
        type: "heritage",
        blurb: "Forts, palaces & UNESCO sites",
        imageUrl: fallbackGenreImage(0),
      },
      {
        id: "hill-stations",
        name: "Hill Stations & Mountains",
        type: "mountain",
        blurb: "Cool retreats & scenic peaks",
        imageUrl: fallbackGenreImage(1),
      },
      {
        id: "beaches-coastal",
        name: "Beaches & Coastal Getaways",
        type: "beach",
        blurb: "Sun, sand & seaside towns",
        imageUrl: fallbackGenreImage(2),
      },
      {
        id: "spiritual-ghats",
        name: "Spiritual & Ghats",
        type: "spiritual",
        blurb: "Temples & riverside ghats",
        imageUrl: fallbackGenreImage(3),
      },
      {
        id: "wildlife-parks",
        name: "Wildlife & National Parks",
        type: "wildlife",
        blurb: "Safaris & nature reserves",
        imageUrl: fallbackGenreImage(0),
      },
      {
        id: "food-culture",
        name: "Food & Cultural Cities",
        type: "culture",
        blurb: "Markets, cuisine & festivals",
        imageUrl: fallbackGenreImage(1),
      },
    ];
  }
  return [
    { id: "landmarks", name: "Iconic Landmarks", type: "monument", blurb: "Must-see sights", imageUrl: fallbackGenreImage(0) },
    { id: "nature", name: "Nature & Outdoors", type: "nature", blurb: "Parks & scenic escapes", imageUrl: fallbackGenreImage(1) },
    { id: "culture", name: "Culture & Heritage", type: "culture", blurb: "Museums & historic quarters", imageUrl: fallbackGenreImage(2) },
  ];
}

function fallbackPackageSummaries(countryCode: string, genre: GenreDoc): PackageSummaryDoc[] {
  const prefix = countryCode.toLowerCase();
  const destinationsByGenre: Record<string, PackageSummaryDoc[]> = {
    "historical-heritage": [
      {
        id: `${prefix}-taj-mahal`,
        title: "Taj Mahal",
        region: "Agra, Uttar Pradesh",
        days: 2,
        heroImageUrl: fallbackGenreImage(0),
        shortInfo: "Iconic Mughal marble mausoleum",
      },
      {
        id: `${prefix}-jaipur`,
        title: "Jaipur",
        region: "Rajasthan",
        days: 3,
        heroImageUrl: fallbackGenreImage(1),
        shortInfo: "Pink City forts and palaces",
      },
      {
        id: `${prefix}-hampi`,
        title: "Hampi",
        region: "Karnataka",
        days: 3,
        heroImageUrl: fallbackGenreImage(2),
        shortInfo: "UNESCO Vijayanagara ruins",
      },
      {
        id: `${prefix}-khajuraho`,
        title: "Khajuraho Group of Monuments",
        region: "Madhya Pradesh",
        days: 2,
        heroImageUrl: fallbackGenreImage(3),
        shortInfo: "Medieval temple architecture",
      },
    ],
    "hill-stations": [
      {
        id: `${prefix}-manali`,
        title: "Manali",
        region: "Himachal Pradesh",
        days: 4,
        heroImageUrl: fallbackGenreImage(0),
        shortInfo: "Snow peaks and adventure hub",
      },
      {
        id: `${prefix}-shimla`,
        title: "Shimla",
        region: "Himachal Pradesh",
        days: 3,
        heroImageUrl: fallbackGenreImage(1),
        shortInfo: "Colonial hill town charm",
      },
      {
        id: `${prefix}-munnar`,
        title: "Munnar",
        region: "Kerala",
        days: 3,
        heroImageUrl: fallbackGenreImage(2),
        shortInfo: "Tea plantations and misty hills",
      },
      {
        id: `${prefix}-darjeeling`,
        title: "Darjeeling",
        region: "West Bengal",
        days: 3,
        heroImageUrl: fallbackGenreImage(3),
        shortInfo: "Toy train and Himalayan views",
      },
    ],
    "beaches-coastal": [
      {
        id: `${prefix}-goa`,
        title: "Goa",
        region: "Goa",
        days: 4,
        heroImageUrl: fallbackGenreImage(0),
        shortInfo: "Beaches, churches and nightlife",
      },
      {
        id: `${prefix}-andaman`,
        title: "Andaman Islands",
        region: "Andaman & Nicobar",
        days: 5,
        heroImageUrl: fallbackGenreImage(1),
        shortInfo: "Turquoise waters and coral reefs",
      },
      {
        id: `${prefix}-gokarna`,
        title: "Gokarna",
        region: "Karnataka",
        days: 3,
        heroImageUrl: fallbackGenreImage(2),
        shortInfo: "Quiet beaches and coastal temples",
      },
    ],
  };

  const matched =
    destinationsByGenre[genre.id] ??
    destinationsByGenre[slugify(genre.name)] ??
    [];

  if (matched.length > 0) {
    return matched;
  }

  return [
    {
      id: `${prefix}-${genre.id}-${slugify(genre.name)}-1`,
      title: `${genre.name} highlight`,
      region: countryNameFromCode(countryCode),
      days: 2,
      heroImageUrl: fallbackGenreImage(0),
      shortInfo: `Top pick for ${genre.name.toLowerCase()}`,
    },
  ];
}
