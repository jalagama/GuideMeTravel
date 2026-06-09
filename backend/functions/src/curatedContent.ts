import { GoogleGenerativeAI } from "@google/generative-ai";
import { HttpsError } from "firebase-functions/v2/https";
import { db } from "./firebaseAdmin";
import { fetchWikipediaSummary } from "./wikipedia";
import {
  AttractionDoc,
  assignDays,
  countryNameFromCode,
  dedupeNearbySpots,
  extractJsonArray,
  fetchPlacesAttractions,
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
  if (cached.exists) {
    return cached.data();
  }

  const countryName = countryNameFromCode(normalized);
  const genres = await generateGenresWithGemini(countryName, normalized);
  const doc = {
    countryCode: normalized,
    countryName,
    genres,
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
  if (cached.exists) {
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
    updatedAtMillis: Date.now(),
  };
  await cacheRef.set(doc);
  logEvent("genre_packages_generated", { countryCode: normalized, genreId, count: packages.length });
  return doc;
}

export async function getTourPackageDetail(packageId: string) {
  const cacheRef = db.collection("tourPackages").doc(packageId);
  const cached = await cacheRef.get();
  if (cached.exists) {
    return cached.data();
  }

  throw new HttpsError(
    "not-found",
    `Tour package ${packageId} not found. Open it from a genre package list first.`
  );
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
}) {
  const packageRef = db.collection("tourPackages").doc(input.packageId);
  const packageDoc = await packageRef.get();
  if (!packageDoc.exists) {
    throw new HttpsError("not-found", `Tour package ${input.packageId} not found.`);
  }

  const detail = packageDoc.data() as TourPackageDetailDoc;
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
You are a travel curator for ${countryName}.
Return JSON only with this shape:
{ "genres": [ { "id": "beaches-goa", "name": "Beaches", "type": "beach", "blurb": "short sentence", "imageSearchHint": "Goa beach India" } ] }
Rules:
- Return 10 to 12 genres/regions covering beaches, mountains, monuments, ghats, wildlife, heritage, etc.
- Use only real regions/attraction types in ${countryName}.
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
    return fallbackPackages(countryCode, genre);
  }

  const genAI = new GoogleGenerativeAI(apiKey);
  const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });
  const prompt = `
You are a travel planner for ${countryName}.
Create curated tour packages for the genre "${genre.name}" (${genre.type}).
Return JSON only:
{ "packages": [ { "id": "goa-beach-3d", "title": "Goa Beach Escape", "region": "Goa", "days": 3, "shortInfo": "one line", "imageSearchHint": "Goa beach sunset" } ] }
Rules:
- Return 6 to 10 realistic packages.
- days between 1 and 7.
- Use only real regions in ${countryName}.
- id must be unique lowercase slug including country code prefix "${countryCode.toLowerCase()}-".
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
        const detailRef = db.collection("tourPackages").doc(id);
        if (!(await detailRef.get()).exists) {
          const summary = {
            id,
            title: pkg.title,
            region: pkg.region,
            days: Math.max(1, Math.min(7, pkg.days || 3)),
            heroImageUrl: "",
            shortInfo: pkg.shortInfo || pkg.title,
          };
          summary.heroImageUrl = mapsKey
            ? await resolveGenreImage(`${pkg.region} ${countryName}`, mapsKey)
            : fallbackGenreImage(index);
          await ensureTourPackageDetail(countryCode, genre.id, summary);
        }
        return {
          id,
          title: pkg.title,
          region: pkg.region,
          days: Math.max(1, Math.min(7, pkg.days || 3)),
          heroImageUrl: mapsKey
            ? await resolveGenreImage(`${pkg.region} ${countryName}`, mapsKey)
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
    return fallbackPackages(countryCode, genre);
  }
}

async function generateTourPackageDetail(
  countryCode: string,
  genreId: string,
  summary: PackageSummaryDoc
): Promise<TourPackageDetailDoc> {
  const countryName = countryNameFromCode(countryCode);
  const days = summary.days;
  const targetCount = targetSpotCount(days);

  let candidates = await fetchPlacesAttractions(`${summary.region}, ${countryName}`, 40);
  if (candidates.length < 3) {
    candidates = await fetchGeminiSpots(`${summary.region}, ${countryName}`, targetCount);
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

async function fetchGeminiSpots(destination: string, maxResults: number): Promise<AttractionDoc[]> {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) return [];

  const genAI = new GoogleGenerativeAI(apiKey);
  const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });
  const prompt = `
Return JSON array only for major real tourist attractions near ${destination}.
Each item: id, name, description, latitude, longitude, estimatedMinutes, rating, userRatingsTotal.
Use only real places. Max ${maxResults} items. No fabrication.
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
You are curating a ${summary.days}-day tour: "${summary.title}" in ${summary.region}, ${countryName}.
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
      { id: "beaches", name: "Beaches", type: "beach", blurb: "Coastal getaways", imageUrl: fallbackGenreImage(0) },
      { id: "mountains", name: "Mountains", type: "mountain", blurb: "Hill stations & peaks", imageUrl: fallbackGenreImage(1) },
      { id: "monuments", name: "Monuments", type: "monument", blurb: "Historic landmarks", imageUrl: fallbackGenreImage(2) },
      { id: "ghats", name: "Ghats", type: "spiritual", blurb: "Riverside ghats", imageUrl: fallbackGenreImage(3) },
      { id: "wildlife", name: "Wildlife", type: "wildlife", blurb: "National parks", imageUrl: fallbackGenreImage(0) },
      { id: "heritage", name: "Heritage", type: "heritage", blurb: "UNESCO & forts", imageUrl: fallbackGenreImage(1) },
    ];
  }
  return [
    { id: "landmarks", name: "Landmarks", type: "monument", blurb: "Iconic sights", imageUrl: fallbackGenreImage(0) },
    { id: "nature", name: "Nature", type: "nature", blurb: "Parks & outdoors", imageUrl: fallbackGenreImage(1) },
    { id: "culture", name: "Culture", type: "culture", blurb: "Museums & heritage", imageUrl: fallbackGenreImage(2) },
  ];
}

function fallbackPackages(countryCode: string, genre: GenreDoc): PackageSummaryDoc[] {
  const prefix = countryCode.toLowerCase();
  return [
    {
      id: `${prefix}-${genre.id}-classic-3d`,
      title: `${genre.name} Classic`,
      region: genre.name,
      days: 3,
      heroImageUrl: fallbackGenreImage(0),
      shortInfo: `A curated ${genre.name.toLowerCase()} experience`,
    },
    {
      id: `${prefix}-${genre.id}-explorer-5d`,
      title: `${genre.name} Explorer`,
      region: genre.name,
      days: 5,
      heroImageUrl: fallbackGenreImage(1),
      shortInfo: `Extended ${genre.name.toLowerCase()} route`,
    },
  ];
}
