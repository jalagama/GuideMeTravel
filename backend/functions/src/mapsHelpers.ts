import { HttpsError } from "firebase-functions/v2/https";
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
export const CURATED_SCHEMA_VERSION = 3;

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

async function mapPlacesResults(
  places: any[],
  maxResults: number,
  mapsKey: string
): Promise<AttractionDoc[]> {
  return places.slice(0, maxResults).map((place: any, index: number) => ({
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
  const mapsKey = process.env.GOOGLE_MAPS_API_KEY;
  if (!mapsKey) {
    throw new HttpsError("failed-precondition", "Google Maps API key is not configured.");
  }

  const location = await geocodeDestinationInCountry(destination, countryCode);
  const placesUrl = `https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=${location.lat},${location.lng}&radius=${PLACES_RADIUS_METERS}&type=tourist_attraction&key=${mapsKey}`;
  const placesRes = await fetchWithTimeout(placesUrl);
  const placesData = await placesRes.json();

  if (placesData.status !== "OK" && placesData.status !== "ZERO_RESULTS") {
    logEvent("places_api_failed", {
      destination,
      countryCode,
      status: placesData.status,
      errorMessage: placesData.error_message,
    });
    return [];
  }

  const mapped = await mapPlacesResults(placesData.results ?? [], maxResults * 2, mapsKey);
  const filtered: AttractionDoc[] = [];
  for (const spot of mapped) {
    const inCountry = await coordinatesAreInCountry(
      spot.latitude,
      spot.longitude,
      countryCode
    );
    if (inCountry) {
      filtered.push({ ...spot, orderIndex: filtered.length });
    }
    if (filtered.length >= maxResults) break;
  }
  return filtered;
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
  const reviewFactor = Math.min(Math.log10(reviews + 1), 3);
  return rating * 2 + reviewFactor;
}

export function passesQualityGate(spot: AttractionDoc): boolean {
  const rating = spot.rating ?? 0;
  const reviews = spot.userRatingsTotal ?? 0;
  return rating >= 3.8 || reviews >= 50;
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
