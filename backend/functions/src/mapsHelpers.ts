import { HttpsError } from "firebase-functions/v2/https";
import {
  EXCLUDED_PLACE_TYPES,
  filterCuratedAttractions,
  isCommercialAttraction,
} from "./attractionCuration";
import { fetchWithTimeout, logEvent } from "./utils";

export type AttractionDoc = {
  id: string;
  name: string;
  description: string;
  latitude: number;
  longitude: number;
  imageUrl?: string;
  orderIndex: number;
  estimatedMinutes: number;
  transcript?: string;
  day?: number;
  whyChosen?: string;
  previewSnippet?: string;
  rating?: number;
  userRatingsTotal?: number;
};

export const PLACES_RADIUS_METERS = 50000;
export const CURATED_SCHEMA_VERSION = 4;

type RawPlaceResult = {
  place_id: string;
  name: string;
  vicinity?: string;
  types?: string[];
  rating?: number;
  user_ratings_total?: number;
  geometry: { location: { lat: number; lng: number } };
  photos?: Array<{ photo_reference: string }>;
};

type GeocodeLocation = {
  lat: number;
  lng: number;
  formattedAddress?: string;
};

export function slugify(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

export function extractJsonArray(text: string): string {
  const start = text.indexOf("[");
  const end = text.lastIndexOf("]");
  if (start >= 0 && end > start) {
    return text.slice(start, end + 1);
  }
  const objStart = text.indexOf("{");
  const objEnd = text.lastIndexOf("}");
  if (objStart >= 0 && objEnd > objStart) {
    return text.slice(objStart, objEnd + 1);
  }
  return text;
}

export function countryNameFromCode(countryCode: string): string {
  const names: Record<string, string> = {
    IN: "India",
    US: "United States",
    GB: "United Kingdom",
    AU: "Australia",
    CA: "Canada",
    FR: "France",
    DE: "Germany",
    IT: "Italy",
    ES: "Spain",
    JP: "Japan",
    TH: "Thailand",
    AE: "United Arab Emirates",
    SG: "Singapore",
  };
  return names[countryCode.toUpperCase()] ?? countryCode;
}

export function countryCodeFromGeocodeResult(result: {
  address_components?: Array<{ short_name: string; types?: string[] }>;
}): string | null {
  const country = result.address_components?.find((component) =>
    component.types?.includes("country")
  );
  return country?.short_name?.toUpperCase() ?? null;
}

async function geocodeDestinationRaw(
  destination: string,
  countryCode?: string
): Promise<GeocodeLocation> {
  const mapsKey = process.env.GOOGLE_MAPS_API_KEY;
  if (!mapsKey) {
    throw new HttpsError("failed-precondition", "Google Maps API key is not configured.");
  }

  const countryComponent = countryCode
    ? `&components=country:${countryCode.trim().toUpperCase()}`
    : "";
  const geocodeUrl =
    `https://maps.googleapis.com/maps/api/geocode/json?address=${encodeURIComponent(destination)}` +
    `${countryComponent}&key=${mapsKey}`;
  const geocodeRes = await fetchWithTimeout(geocodeUrl);
  const geocodeData = await geocodeRes.json();

  if (geocodeData.status !== "OK" || !geocodeData.results?.[0]?.geometry?.location) {
    logEvent("geocode_failed", {
      destination,
      countryCode,
      status: geocodeData.status,
      errorMessage: geocodeData.error_message,
    });
    throw new HttpsError("not-found", `Could not geocode "${destination}".`);
  }

  const result = geocodeData.results[0];
  const location = result.geometry.location;
  return {
    lat: location.lat,
    lng: location.lng,
    formattedAddress: result.formatted_address,
  };
}

export async function geocodeDestination(destination: string): Promise<GeocodeLocation> {
  return geocodeDestinationRaw(destination);
}

export async function geocodeDestinationInCountry(
  destination: string,
  countryCode: string
): Promise<GeocodeLocation> {
  const normalizedCountry = countryCode.trim().toUpperCase();
  const countryName = countryNameFromCode(normalizedCountry);
  const queries = [
    destination,
    `${destination}, ${countryName}`,
  ];

  for (const query of queries) {
    try {
      const location = await geocodeDestinationRaw(query, normalizedCountry);
      const verified = await coordinatesAreInCountry(
        location.lat,
        location.lng,
        normalizedCountry
      );
      if (verified) {
        return location;
      }
    } catch {
      // Try the next query variant.
    }
  }

  throw new HttpsError(
    "not-found",
    `"${destination}" is outside ${countryName}. Search only within your current country.`
  );
}

export async function coordinatesAreInCountry(
  lat: number,
  lng: number,
  countryCode: string
): Promise<boolean> {
  const mapsKey = process.env.GOOGLE_MAPS_API_KEY;
  if (!mapsKey) return true;

  const reverseUrl =
    `https://maps.googleapis.com/maps/api/geocode/json?latlng=${lat},${lng}&result_type=country&key=${mapsKey}`;
  const reverseRes = await fetchWithTimeout(reverseUrl);
  const reverseData = await reverseRes.json();
  const resolvedCountry = countryCodeFromGeocodeResult(reverseData.results?.[0] ?? {});
  return resolvedCountry === countryCode.trim().toUpperCase();
}

export function placeTypesByPlaceId(places: RawPlaceResult[]): Map<string, string[]> {
  const map = new Map<string, string[]>();
  for (const place of places) {
    map.set(place.place_id, place.types ?? []);
  }
  return map;
}

function isLowValuePlace(place: RawPlaceResult): boolean {
  const types = place.types ?? [];
  if (isCommercialAttraction(place.name, types, place.vicinity)) {
    return true;
  }
  if (types.length === 0) return false;
  const hasExcludedOnly = types.every(
    (type) => EXCLUDED_PLACE_TYPES.has(type) || type === "point_of_interest"
  );
  return hasExcludedOnly && types.some((type) => EXCLUDED_PLACE_TYPES.has(type));
}

async function mapPlacesResults(
  places: RawPlaceResult[],
  maxResults: number,
  mapsKey: string
): Promise<AttractionDoc[]> {
  const curated = places
    .filter((place) => !isLowValuePlace(place))
    .slice(0, maxResults)
    .map((place, index) => ({
      id: place.place_id,
      name: place.name,
      description: place.vicinity ?? "Popular tourist attraction",
      latitude: place.geometry.location.lat,
      longitude: place.geometry.location.lng,
      orderIndex: index,
      estimatedMinutes: 45,
      rating: place.rating,
      userRatingsTotal: place.user_ratings_total,
      imageUrl: place.photos?.[0]
        ? `https://maps.googleapis.com/maps/api/place/photo?maxwidth=640&photo_reference=${place.photos[0].photo_reference}&key=${mapsKey}`
        : undefined,
    }));
  return curated;
}

async function fetchPlacesNearbyPage(
  lat: number,
  lng: number,
  mapsKey: string,
  type?: string
): Promise<RawPlaceResult[]> {
  const typeParam = type ? `&type=${type}` : "";
  const placesUrl =
    `https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=${lat},${lng}` +
    `&radius=${PLACES_RADIUS_METERS}${typeParam}&key=${mapsKey}`;
  const placesRes = await fetchWithTimeout(placesUrl);
  const placesData = await placesRes.json();

  if (placesData.status !== "OK" && placesData.status !== "ZERO_RESULTS") {
    return [];
  }
  return (placesData.results ?? []) as RawPlaceResult[];
}

async function fetchPlacesTextSearch(
  query: string,
  mapsKey: string,
  location?: { lat: number; lng: number }
): Promise<RawPlaceResult[]> {
  const locationBias = location
    ? `&location=${location.lat},${location.lng}&radius=${PLACES_RADIUS_METERS}`
    : "";
  const placesUrl =
    `https://maps.googleapis.com/maps/api/place/textsearch/json?query=${encodeURIComponent(query)}` +
    `${locationBias}&key=${mapsKey}`;
  const placesRes = await fetchWithTimeout(placesUrl);
  const placesData = await placesRes.json();

  if (placesData.status !== "OK" && placesData.status !== "ZERO_RESULTS") {
    return [];
  }
  return (placesData.results ?? []) as RawPlaceResult[];
}

function dedupeRawPlaces(places: RawPlaceResult[]): RawPlaceResult[] {
  const seen = new Set<string>();
  const deduped: RawPlaceResult[] = [];
  for (const place of places) {
    const key = place.place_id || place.name.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    deduped.push(place);
  }
  return deduped;
}

export async function fetchPlacesAttractions(
  destination: string,
  maxResults = 25
): Promise<AttractionDoc[]> {
  const mapsKey = process.env.GOOGLE_MAPS_API_KEY;
  if (!mapsKey) {
    throw new HttpsError("failed-precondition", "Google Maps API key is not configured.");
  }

  const location = await geocodeDestination(destination);
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

  return mapPlacesResults(placesData.results ?? [], maxResults, mapsKey);
}

export async function fetchPlacesAttractionsInCountry(
  destination: string,
  countryCode: string,
  maxResults = 25
): Promise<AttractionDoc[]> {
  const { attractions } = await fetchCuratedPlacesAttractionsInCountry(
    destination,
    countryCode,
    maxResults
  );
  return attractions;
}

export async function fetchCuratedPlacesAttractionsInCountry(
  destination: string,
  countryCode: string,
  maxResults = 25
): Promise<{ attractions: AttractionDoc[]; placeTypesById: Map<string, string[]> }> {
  const mapsKey = process.env.GOOGLE_MAPS_API_KEY;
  if (!mapsKey) {
    throw new HttpsError("failed-precondition", "Google Maps API key is not configured.");
  }

  const countryName = countryNameFromCode(countryCode);
  const location = await geocodeDestinationInCountry(destination, countryCode);

  const searchQueries = [
    `top tourist attractions in ${destination}, ${countryName}`,
    `must visit landmarks in ${destination}, ${countryName}`,
    `famous sights in ${destination}, ${countryName}`,
  ];

  const rawPlaces = dedupeRawPlaces([
    ...(await fetchPlacesNearbyPage(location.lat, location.lng, mapsKey, "tourist_attraction")),
    ...(await fetchPlacesNearbyPage(location.lat, location.lng, mapsKey, "museum")),
    ...(await fetchPlacesNearbyPage(location.lat, location.lng, mapsKey, "park")),
    ...(await Promise.all(
      searchQueries.map((query) => fetchPlacesTextSearch(query, mapsKey, location))
    )).flat(),
  ]);

  const placeTypesById = placeTypesByPlaceId(rawPlaces);
  const mapped = await mapPlacesResults(rawPlaces, maxResults * 3, mapsKey);
  const filtered: AttractionDoc[] = [];
  for (const spot of filterCuratedAttractions(mapped, placeTypesById)) {
    const inCountry = await coordinatesAreInCountry(
      spot.latitude,
      spot.longitude,
      countryCode
    );
    if (inCountry) {
      filtered.push({ ...spot, orderIndex: filtered.length });
    }
    if (filtered.length >= maxResults * 2) break;
  }

  const ranked = filtered
    .sort((a, b) => qualityScore(b) - qualityScore(a))
    .slice(0, maxResults)
    .map((spot, index) => ({ ...spot, orderIndex: index }));

  return { attractions: ranked, placeTypesById };
}

export async function geocodeAttractionInCountry(
  attractionName: string,
  destination: string,
  countryCode: string
): Promise<{ latitude: number; longitude: number } | null> {
  const queries = [
    `${attractionName}, ${destination}`,
    `${attractionName}`,
  ];
  for (const query of queries) {
    try {
      const location = await geocodeDestinationInCountry(query, countryCode);
      return { latitude: location.lat, longitude: location.lng };
    } catch {
      // Try the next query variant.
    }
  }
  return null;
}

export async function optimizeRoute(attractions: AttractionDoc[]): Promise<AttractionDoc[]> {
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

export function haversineKm(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number
): number {
  const toRad = (v: number) => (v * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export function dedupeNearbySpots(spots: AttractionDoc[], minKm = 2): AttractionDoc[] {
  const kept: AttractionDoc[] = [];
  for (const spot of spots) {
    const tooClose = kept.some(
      (existing) =>
        haversineKm(existing.latitude, existing.longitude, spot.latitude, spot.longitude) < minKm
    );
    if (!tooClose) kept.push(spot);
  }
  return kept;
}

export function qualityScore(spot: AttractionDoc): number {
  const rating = spot.rating ?? 3.5;
  const reviews = spot.userRatingsTotal ?? 0;
  const reviewFactor = Math.min(Math.log10(reviews + 1) * 2.5, 8);
  const iconicBonus = /\b(unesco|fort|palace|temple|museum|beach|cathedral|monument|national park)\b/i.test(
    spot.name
  )
    ? 4
    : 0;
  return rating * 2 + reviewFactor + iconicBonus;
}

export function passesQualityGate(spot: AttractionDoc): boolean {
  const rating = spot.rating ?? 0;
  const reviews = spot.userRatingsTotal ?? 0;
  const iconic = /\b(unesco|fort|palace|temple|museum|beach|cathedral|monument|falls|viewpoint|ruins)\b/i.test(
    spot.name
  );
  return iconic || rating >= 4.0 || reviews >= 100;
}

export function targetSpotCount(days: number): number {
  const scaled = Math.round(days * 4);
  return Math.max(3, Math.min(25, scaled));
}

export type NearbyPlaceResult = {
  name: string;
  description: string;
  latitude: number;
  longitude: number;
  rating?: number;
};

export async function fetchPlacesNearbyByType(
  lat: number,
  lng: number,
  type: "lodging" | "restaurant",
  maxResults = 5
): Promise<NearbyPlaceResult[]> {
  const mapsKey = process.env.GOOGLE_MAPS_API_KEY;
  if (!mapsKey) return [];

  const placesUrl =
    `https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=${lat},${lng}` +
    `&radius=${PLACES_RADIUS_METERS}&type=${type}&key=${mapsKey}`;
  const placesRes = await fetchWithTimeout(placesUrl);
  const placesData = await placesRes.json();

  if (placesData.status !== "OK" && placesData.status !== "ZERO_RESULTS") {
    return [];
  }

  return (placesData.results ?? [])
    .filter((place: { rating?: number }) => (place.rating ?? 0) >= 3.5)
    .sort((a: { rating?: number }, b: { rating?: number }) => (b.rating ?? 0) - (a.rating ?? 0))
    .slice(0, maxResults)
    .map((place: {
      name: string;
      vicinity?: string;
      geometry: { location: { lat: number; lng: number } };
      rating?: number;
    }) => ({
      name: place.name,
      description: place.vicinity ?? (type === "lodging" ? "Hotel" : "Restaurant"),
      latitude: place.geometry.location.lat,
      longitude: place.geometry.location.lng,
      rating: place.rating,
    }));
}

export function computeRouteCentroid(spots: AttractionDoc[]): { lat: number; lng: number } | null {
  if (spots.length === 0) return null;
  const lat = spots.reduce((sum, s) => sum + s.latitude, 0) / spots.length;
  const lng = spots.reduce((sum, s) => sum + s.longitude, 0) / spots.length;
  return { lat, lng };
}

export function assignDays(spots: AttractionDoc[], days: number): AttractionDoc[] {
  if (days <= 1) {
    return spots.map((spot, index) => ({ ...spot, day: 1, orderIndex: index }));
  }
  const perDay = Math.ceil(spots.length / days);
  return spots.map((spot, index) => ({
    ...spot,
    day: Math.min(days, Math.floor(index / perDay) + 1),
    orderIndex: index,
  }));
}
