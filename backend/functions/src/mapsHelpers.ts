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
  rating?: number;
  userRatingsTotal?: number;
};

export const PLACES_RADIUS_METERS = 50000;

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

export async function geocodeDestination(destination: string): Promise<{
  lat: number;
  lng: number;
  formattedAddress?: string;
}> {
  const mapsKey = process.env.GOOGLE_MAPS_API_KEY;
  if (!mapsKey) {
    throw new HttpsError("failed-precondition", "Google Maps API key is not configured.");
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
    throw new HttpsError("not-found", `Could not geocode "${destination}".`);
  }

  const location = geocodeData.results[0].geometry.location;
  return {
    lat: location.lat,
    lng: location.lng,
    formattedAddress: geocodeData.results[0].formatted_address,
  };
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

  return (placesData.results ?? []).slice(0, maxResults).map((place: any, index: number) => ({
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
