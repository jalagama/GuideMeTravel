import { GoogleGenerativeAI } from "@google/generative-ai";
import { HttpsError } from "firebase-functions/v2/https";
import { db } from "./firebaseAdmin";
import {
  dedupeByTitle,
  validatePackageExtras,
} from "./curatedValidators";
import { fetchWikipediaSummary } from "./wikipedia";
import {
  AttractionDoc,
  assignDays,
  computeRouteCentroid,
  countryNameFromCode,
  CURATED_SCHEMA_VERSION,
  dedupeNearbySpots,
  extractJsonArray,
  fetchPlacesAttractionsInCountry,
  fetchPlacesNearbyByType,
  geocodeDestinationInCountry,
  optimizeRoute,
  passesQualityGate,
  qualityScore,
  slugify,
  targetSpotCount,
} from "./mapsHelpers";
import {
  buildGenresPrompt,
  buildHotelDescriptionPrompt,
  buildPackageExtrasPrompt,
  buildPackagesFillPrompt,
  buildPackagesPrompt,
  buildPreviewSnippetPrompt,
  buildRestaurantDescriptionPrompt,
  buildSpotsPrompt,
  buildWhyChosenPrompt,
} from "./prompts/curatedPrompts";
import { logEvent } from "./utils";

type GenreDoc = {
  id: string;
  name: string;
  type: string;
  imageUrl: string;
  blurb: string;
  rank: number;
};

type PackageSummaryDoc = {
  id: string;
  title: string;
  region: string;
  days: number;
  heroImageUrl: string;
  shortInfo: string;
  rank: number;
  bestFor: string;
  seasonality?: string;
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
  daySummaries: Record<string, string>;
  spots: AttractionDoc[];
  tips: string[];
  essentials: string[];
  highlights: string[];
  hotels: NearbyPlaceDoc[];
  restaurants: NearbyPlaceDoc[];
  schemaVersion: number;
  createdAtMillis: number;
};

const TARGET_PACKAGE_COUNT = 20;
const MIN_PACKAGE_COUNT = 15;

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
  if (cached.exists && (cached.data()?.schemaVersion ?? 1) >= CURATED_SCHEMA_VERSION) {
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
  if (cached.exists && (cached.data()?.schemaVersion ?? 1) >= CURATED_SCHEMA_VERSION) {
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

  try {
    const result = await model.generateContent(buildGenresPrompt(countryName, countryCode));
    const parsed = JSON.parse(extractJsonArray(result.response.text())) as {
      genres: Array<GenreDoc & { imageSearchHint?: string; rank?: number }>;
    };
    const mapsKey = process.env.GOOGLE_MAPS_API_KEY;
    const genres = (parsed.genres ?? [])
      .slice(0, 12)
      .sort((a, b) => (a.rank ?? 99) - (b.rank ?? 99));

    return Promise.all(
      genres.map(async (genre, index) => ({
        id: genre.id || slugify(genre.name),
        name: genre.name,
        type: genre.type || "region",
        blurb: genre.blurb || `Explore ${genre.name} in ${countryName}`,
        rank: genre.rank ?? index + 1,
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

  try {
    let rawPackages = await fetchPackagesFromGemini(model, countryName, countryCode, genre);
    rawPackages = dedupeByTitle(rawPackages);
    rawPackages = await verifyPackagesInCountry(rawPackages, countryCode, countryName);

    if (rawPackages.length < MIN_PACKAGE_COUNT) {
      const fill = await fetchPackagesFillFromGemini(
        model,
        countryName,
        countryCode,
        genre,
        rawPackages.map((p) => p.title),
        TARGET_PACKAGE_COUNT - rawPackages.length
      );
      const merged = dedupeByTitle([...rawPackages, ...fill]);
      rawPackages = await verifyPackagesInCountry(merged, countryCode, countryName);
    }

    if (rawPackages.length < MIN_PACKAGE_COUNT) {
      const fallback = await buildFallbackPackages(countryCode, genre);
      rawPackages = dedupeByTitle([...rawPackages, ...fallback]).slice(0, TARGET_PACKAGE_COUNT);
    }

    const mapsKey = process.env.GOOGLE_MAPS_API_KEY;
    const enriched = await Promise.all(
      rawPackages
        .sort((a, b) => a.rank - b.rank)
        .slice(0, TARGET_PACKAGE_COUNT)
        .map(async (pkg, index) => {
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
            rank: pkg.rank ?? index + 1,
            bestFor: pkg.bestFor || "All travelers",
            seasonality: pkg.seasonality,
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

async function fetchPackagesFromGemini(
  model: ReturnType<GoogleGenerativeAI["getGenerativeModel"]>,
  countryName: string,
  countryCode: string,
  genre: GenreDoc
): Promise<PackageSummaryDoc[]> {
  const result = await model.generateContent(
    buildPackagesPrompt(countryName, countryCode, genre.name, genre.type)
  );
  const parsed = JSON.parse(extractJsonArray(result.response.text())) as {
    packages: PackageSummaryDoc[];
  };
  return (parsed.packages ?? []).map((pkg, index) => ({
    ...pkg,
    rank: pkg.rank ?? index + 1,
    bestFor: pkg.bestFor || "All travelers",
    heroImageUrl: "",
  }));
}

async function fetchPackagesFillFromGemini(
  model: ReturnType<GoogleGenerativeAI["getGenerativeModel"]>,
  countryName: string,
  countryCode: string,
  genre: GenreDoc,
  existingTitles: string[],
  needed: number
): Promise<PackageSummaryDoc[]> {
  if (needed <= 0) return [];
  const result = await model.generateContent(
    buildPackagesFillPrompt(countryName, countryCode, genre.name, existingTitles, needed)
  );
  const parsed = JSON.parse(extractJsonArray(result.response.text())) as {
    packages: PackageSummaryDoc[];
  };
  return (parsed.packages ?? []).map((pkg, index) => ({
    ...pkg,
    rank: existingTitles.length + index + 1,
    bestFor: pkg.bestFor || "All travelers",
    heroImageUrl: "",
  }));
}

async function verifyPackagesInCountry(
  packages: PackageSummaryDoc[],
  countryCode: string,
  countryName: string
): Promise<PackageSummaryDoc[]> {
  const verified: PackageSummaryDoc[] = [];
  for (const pkg of packages) {
    try {
      await geocodeDestinationInCountry(`${pkg.title}, ${pkg.region}`, countryCode);
      verified.push(pkg);
    } catch {
      logEvent("package_geocode_failed", { title: pkg.title, countryCode });
    }
    if (verified.length >= TARGET_PACKAGE_COUNT) break;
  }
  return verified.length > 0 ? verified : packages.slice(0, TARGET_PACKAGE_COUNT);
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
      const description = wiki || spot.description;
      const whyChosen = await generateWhyChosen(spot, summary.title);
      const previewSnippet = await generatePreviewSnippet(spot.name, description, summary.title);
      return {
        ...spot,
        orderIndex: index,
        description,
        whyChosen,
        previewSnippet,
        transcript: wiki
          ? `Welcome to ${spot.name}. ${wiki}`
          : `Welcome to ${spot.name}. ${spot.description}`,
      };
    })
  );

  const extras = await generatePackageExtras(summary, countryName, curated);
  const centroid = computeRouteCentroid(curated);
  const hotels = centroid
    ? await fetchVerifiedHotels(centroid.lat, centroid.lng, summary.region)
    : [];
  const restaurants = centroid
    ? await fetchVerifiedRestaurants(centroid.lat, centroid.lng, summary.region)
    : [];

  return {
    id: summary.id,
    countryCode,
    genreId,
    title: summary.title,
    region: summary.region,
    days,
    heroImageUrl: summary.heroImageUrl,
    overview: extras.overview,
    daySummaries: extras.daySummaries,
    spots: curated,
    tips: extras.tips,
    essentials: extras.essentials,
    highlights: extras.highlights,
    hotels: hotels.length > 0 ? hotels : extras.hotels,
    restaurants: restaurants.length > 0 ? restaurants : extras.restaurants,
    schemaVersion: CURATED_SCHEMA_VERSION,
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

  try {
    const result = await model.generateContent(
      buildSpotsPrompt(destination, countryName, countryCode, maxResults)
    );
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
    return `Essential stop on the ${packageTitle} route — ${spot.name} ranks among the region's top-rated attractions.`;
  }

  const genAI = new GoogleGenerativeAI(apiKey);
  const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });

  try {
    const result = await model.generateContent(
      buildWhyChosenPrompt(spot.name, packageTitle, spot.rating, spot.userRatingsTotal)
    );
    return result.response.text().trim();
  } catch {
    return `Top-reviewed highlight on the ${packageTitle} itinerary.`;
  }
}

async function generatePreviewSnippet(
  spotName: string,
  description: string,
  packageTitle: string
): Promise<string> {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    return `Discover ${spotName}, a highlight of the ${packageTitle} journey. ${description.slice(0, 120)}`;
  }

  const genAI = new GoogleGenerativeAI(apiKey);
  const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });

  try {
    const result = await model.generateContent(
      buildPreviewSnippetPrompt(spotName, description, packageTitle)
    );
    return result.response.text().trim();
  } catch {
    return `Discover ${spotName} on the ${packageTitle} route.`;
  }
}

async function generatePackageExtras(
  summary: PackageSummaryDoc,
  countryName: string,
  spots: AttractionDoc[]
) {
  const spotNames = spots.map((s) => s.name);
  const daySpotGroups: Record<number, string[]> = {};
  for (const spot of spots) {
    const day = spot.day ?? 1;
    if (!daySpotGroups[day]) daySpotGroups[day] = [];
    daySpotGroups[day].push(spot.name);
  }

  const fallback = {
    overview: `A ${summary.days}-day curated journey through ${summary.region} in ${countryName}, covering ${spots.length} top-rated attractions including ${spotNames.slice(0, 2).join(" and ")}.`,
    daySummaries: Object.fromEntries(
      Object.entries(daySpotGroups).map(([day, names]) => [
        day,
        `Day ${day}: Explore ${names.join(", ")}.`,
      ])
    ) as Record<string, string>,
    tips: [
      `Start early at ${spotNames[0] ?? summary.title} to avoid peak crowds.`,
      "Carry water, sun protection, and comfortable walking shoes.",
      `Check ${summary.region} weather before outdoor segments.`,
      "Keep a government-issued photo ID for monument entry.",
      "Use offline maps — mobile signal can be weak at remote sites.",
      `Best season for ${summary.title}: ${summary.seasonality ?? "check local forecasts"}.`,
    ],
    essentials: ["Comfortable walking shoes", "Government ID", "Local SIM or offline maps", "Reusable water bottle"],
    highlights: spotNames.slice(0, Math.min(8, spotNames.length)),
    hotels: [] as NearbyPlaceDoc[],
    restaurants: [] as NearbyPlaceDoc[],
  };

  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) return fallback;

  const genAI = new GoogleGenerativeAI(apiKey);
  const model = genAI.getGenerativeModel({ model: "gemini-2.0-flash" });

  try {
    const result = await model.generateContent(
      buildPackageExtrasPrompt(
        summary.title,
        summary.region,
        countryName,
        summary.days,
        spotNames,
        daySpotGroups
      )
    );
    const parsed = JSON.parse(extractJsonArray(result.response.text())) as typeof fallback;
    const merged = {
      overview: parsed.overview || fallback.overview,
      daySummaries: parsed.daySummaries ?? fallback.daySummaries,
      tips: parsed.tips?.length >= 5 ? parsed.tips : fallback.tips,
      essentials: parsed.essentials?.length ? parsed.essentials : fallback.essentials,
      highlights: parsed.highlights?.length ? parsed.highlights : fallback.highlights,
      hotels: [] as NearbyPlaceDoc[],
      restaurants: [] as NearbyPlaceDoc[],
    };

    if (!validatePackageExtras(merged)) {
      logEvent("package_extras_validation_failed", { packageId: summary.id });
      return fallback;
    }
    return merged;
  } catch {
    return fallback;
  }
}

async function fetchVerifiedHotels(
  lat: number,
  lng: number,
  region: string
): Promise<NearbyPlaceDoc[]> {
  const places = await fetchPlacesNearbyByType(lat, lng, "lodging", 5);
  const apiKey = process.env.GEMINI_API_KEY;
  const genAI = apiKey ? new GoogleGenerativeAI(apiKey) : null;
  const model = genAI?.getGenerativeModel({ model: "gemini-2.0-flash" });

  return Promise.all(
    places.map(async (place) => {
      let description = place.description;
      if (model) {
        try {
          const result = await model.generateContent(
            buildHotelDescriptionPrompt(place.name, region)
          );
          description = result.response.text().trim();
        } catch {
          // keep vicinity description
        }
      }
      return {
        name: place.name,
        description,
        latitude: place.latitude,
        longitude: place.longitude,
        rating: place.rating,
      };
    })
  );
}

async function fetchVerifiedRestaurants(
  lat: number,
  lng: number,
  region: string
): Promise<NearbyPlaceDoc[]> {
  const places = await fetchPlacesNearbyByType(lat, lng, "restaurant", 5);
  const apiKey = process.env.GEMINI_API_KEY;
  const genAI = apiKey ? new GoogleGenerativeAI(apiKey) : null;
  const model = genAI?.getGenerativeModel({ model: "gemini-2.0-flash" });

  return Promise.all(
    places.map(async (place) => {
      let description = place.description;
      if (model) {
        try {
          const result = await model.generateContent(
            buildRestaurantDescriptionPrompt(place.name, region)
          );
          description = result.response.text().trim();
        } catch {
          // keep vicinity description
        }
      }
      return {
        name: place.name,
        description,
        latitude: place.latitude,
        longitude: place.longitude,
        rating: place.rating,
      };
    })
  );
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
  const base = countryCode === "IN"
    ? [
        { id: "historical-heritage", name: "Historical & Heritage Destinations", type: "heritage", blurb: "Forts, palaces & UNESCO sites", rank: 1 },
        { id: "hill-stations", name: "Hill Stations & Mountains", type: "mountain", blurb: "Cool retreats & scenic peaks", rank: 2 },
        { id: "beaches-coastal", name: "Beaches & Coastal Getaways", type: "beach", blurb: "Sun, sand & seaside towns", rank: 3 },
        { id: "spiritual-ghats", name: "Spiritual & Ghats", type: "spiritual", blurb: "Temples & riverside ghats", rank: 4 },
        { id: "wildlife-parks", name: "Wildlife & National Parks", type: "wildlife", blurb: "Safaris & nature reserves", rank: 5 },
        { id: "food-culture", name: "Food & Cultural Cities", type: "culture", blurb: "Markets, cuisine & festivals", rank: 6 },
        { id: "adventure-sports", name: "Adventure & Outdoor Sports", type: "adventure", blurb: "Trekking, rafting & paragliding", rank: 7 },
        { id: "urban-metros", name: "Urban & Metro Experiences", type: "urban", blurb: "Skyline cities & modern culture", rank: 8 },
        { id: "wellness-retreats", name: "Wellness & Ayurveda Retreats", type: "wellness", blurb: "Spa, yoga & holistic healing", rank: 9 },
        { id: "festival-season", name: "Festivals & Celebrations", type: "festival", blurb: "Diwali, Holi & regional fairs", rank: 10 },
        { id: "offbeat-trails", name: "Offbeat & Hidden Gems", type: "offbeat", blurb: "Unspoiled villages & secret trails", rank: 11 },
        { id: "family-getaways", name: "Family-Friendly Getaways", type: "family", blurb: "Theme parks, zoos & kid-safe fun", rank: 12 },
      ]
    : [
        { id: "landmarks", name: "Iconic Landmarks", type: "monument", blurb: "Must-see national monuments", rank: 1 },
        { id: "nature", name: "Nature & National Parks", type: "nature", blurb: "Parks & scenic escapes", rank: 2 },
        { id: "culture", name: "Culture & Heritage", type: "culture", blurb: "Museums & historic quarters", rank: 3 },
        { id: "beaches", name: "Beaches & Coastlines", type: "beach", blurb: "Seaside towns & coastal drives", rank: 4 },
        { id: "food-cities", name: "Food & Culinary Cities", type: "culture", blurb: "Markets, cuisine & street food", rank: 5 },
        { id: "adventure", name: "Adventure & Outdoors", type: "adventure", blurb: "Hiking, skiing & water sports", rank: 6 },
      ];

  return base.map((genre, index) => ({
    ...genre,
    imageUrl: fallbackGenreImage(index),
  }));
}

function fallbackPackageSummaries(countryCode: string, genre: GenreDoc): PackageSummaryDoc[] {
  const prefix = countryCode.toLowerCase();
  const destinationsByGenre: Record<string, PackageSummaryDoc[]> = {
    "historical-heritage": [
      { id: `${prefix}-taj-mahal`, title: "Taj Mahal", region: "Agra, Uttar Pradesh", days: 2, heroImageUrl: fallbackGenreImage(0), shortInfo: "Iconic Mughal marble mausoleum", rank: 1, bestFor: "First-time visitors" },
      { id: `${prefix}-jaipur`, title: "Jaipur", region: "Rajasthan", days: 3, heroImageUrl: fallbackGenreImage(1), shortInfo: "Pink City forts and palaces", rank: 2, bestFor: "History lovers" },
      { id: `${prefix}-hampi`, title: "Hampi", region: "Karnataka", days: 3, heroImageUrl: fallbackGenreImage(2), shortInfo: "UNESCO Vijayanagara ruins", rank: 3, bestFor: "Archaeology enthusiasts" },
      { id: `${prefix}-khajuraho`, title: "Khajuraho Group of Monuments", region: "Madhya Pradesh", days: 2, heroImageUrl: fallbackGenreImage(3), shortInfo: "Medieval temple architecture", rank: 4, bestFor: "Art & architecture" },
      { id: `${prefix}-fatehpur-sikri`, title: "Fatehpur Sikri", region: "Uttar Pradesh", days: 1, heroImageUrl: fallbackGenreImage(0), shortInfo: "Mughal ghost city near Agra", rank: 5, bestFor: "Day trips" },
      { id: `${prefix}-mahabalipuram`, title: "Mahabalipuram", region: "Tamil Nadu", days: 2, heroImageUrl: fallbackGenreImage(1), shortInfo: "Pallava rock-cut temples by the sea", rank: 6, bestFor: "Coastal heritage" },
    ],
    "hill-stations": [
      { id: `${prefix}-manali`, title: "Manali", region: "Himachal Pradesh", days: 4, heroImageUrl: fallbackGenreImage(0), shortInfo: "Snow peaks and adventure hub", rank: 1, bestFor: "Adventure seekers" },
      { id: `${prefix}-shimla`, title: "Shimla", region: "Himachal Pradesh", days: 3, heroImageUrl: fallbackGenreImage(1), shortInfo: "Colonial hill town charm", rank: 2, bestFor: "Families" },
      { id: `${prefix}-munnar`, title: "Munnar", region: "Kerala", days: 3, heroImageUrl: fallbackGenreImage(2), shortInfo: "Tea plantations and misty hills", rank: 3, bestFor: "Nature lovers" },
      { id: `${prefix}-darjeeling`, title: "Darjeeling", region: "West Bengal", days: 3, heroImageUrl: fallbackGenreImage(3), shortInfo: "Toy train and Himalayan views", rank: 4, bestFor: "Tea & mountain views" },
      { id: `${prefix}-ooty`, title: "Ooty", region: "Tamil Nadu", days: 3, heroImageUrl: fallbackGenreImage(0), shortInfo: "Nilgiri hill station lakes", rank: 5, bestFor: "Leisure travelers" },
      { id: `${prefix}-nainital`, title: "Nainital", region: "Uttarakhand", days: 2, heroImageUrl: fallbackGenreImage(1), shortInfo: "Lake town in the Kumaon hills", rank: 6, bestFor: "Weekend getaways" },
    ],
    "beaches-coastal": [
      { id: `${prefix}-goa`, title: "Goa", region: "Goa", days: 4, heroImageUrl: fallbackGenreImage(0), shortInfo: "Beaches, churches and nightlife", rank: 1, bestFor: "Beach lovers" },
      { id: `${prefix}-andaman`, title: "Andaman Islands", region: "Andaman & Nicobar", days: 5, heroImageUrl: fallbackGenreImage(1), shortInfo: "Turquoise waters and coral reefs", rank: 2, bestFor: "Diving & snorkeling" },
      { id: `${prefix}-gokarna`, title: "Gokarna", region: "Karnataka", days: 3, heroImageUrl: fallbackGenreImage(2), shortInfo: "Quiet beaches and coastal temples", rank: 3, bestFor: "Offbeat beach seekers" },
      { id: `${prefix}-kovalam`, title: "Kovalam", region: "Kerala", days: 3, heroImageUrl: fallbackGenreImage(3), shortInfo: "Lighthouse beach and Ayurveda", rank: 4, bestFor: "Wellness & beach" },
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
      rank: 1,
      bestFor: "All travelers",
    },
  ];
}
