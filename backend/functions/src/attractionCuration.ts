import type { AttractionDoc } from "./mapsHelpers";

/** Commercial / low-value place names — not curated itinerary stops. */
const COMMERCIAL_NAME_PATTERNS =
  /\b(rentals?|rent-a|rental\s*(shop|service|center|centre)?|hire|leasing|bike\s*(rental|rentals|shop|hire)|scooter\s*(rental|rentals|hire)?|moped|car\s*rental|vehicle\s*rental|tour\s*operator|travel\s*agency|taxi\s*service|cab\s*service|hostel|guest\s*house|motel|inn\b|hotel|resort|homestay|restaurant|cafe|café|coffee\s*shop|bar\b|pub\b|nightclub|club\b|shop|store|boutique|mall|market\s*stall|supermarket|grocery|bakery|salon|barber|laundry|dry\s*clean|pharmacy|clinic|hospital|dentist|atm\b|bank\b|garage|workshop|dealer|showroom|parking|fuel\s*station|petrol|gas\s*station|car\s*wash|driving\s*school|real\s*estate|insurance|courier|logistics|warehouse|factory|office|coworking|gym\b|fitness\s*center|yoga\s*studio|massage\s*parlour)\b/i;

/** Google Place types that are never curated tourist attractions. */
export const EXCLUDED_PLACE_TYPES = new Set([
  "car_rental",
  "car_dealer",
  "car_repair",
  "car_wash",
  "travel_agency",
  "real_estate_agency",
  "insurance_agency",
  "store",
  "shopping_mall",
  "supermarket",
  "convenience_store",
  "department_store",
  "clothing_store",
  "electronics_store",
  "furniture_store",
  "home_goods_store",
  "jewelry_store",
  "shoe_store",
  "book_store",
  "pet_store",
  "lodging",
  "restaurant",
  "cafe",
  "bar",
  "night_club",
  "meal_takeaway",
  "meal_delivery",
  "beauty_salon",
  "hair_care",
  "spa",
  "gym",
  "atm",
  "bank",
  "pharmacy",
  "hospital",
  "doctor",
  "dentist",
  "veterinary_care",
  "gas_station",
  "parking",
  "bus_station",
  "subway_station",
  "train_station",
  "transit_station",
  "taxi_stand",
  "moving_company",
  "storage",
  "lawyer",
  "accounting",
  "local_government_office",
  "post_office",
  "school",
  "university",
  "primary_school",
  "secondary_school",
]);

/** Types that strongly indicate a genuine tourist sight. */
const PREFERRED_TOURIST_TYPES = new Set([
  "tourist_attraction",
  "museum",
  "art_gallery",
  "church",
  "hindu_temple",
  "mosque",
  "synagogue",
  "place_of_worship",
  "park",
  "national_park",
  "natural_feature",
  "amusement_park",
  "aquarium",
  "zoo",
  "stadium",
  "cemetery",
  "city_hall",
  "library",
  "landmark",
  "point_of_interest",
]);

const SIGNIFICANCE_NAME_PATTERNS =
  /\b(unesco|world\s*heritage|heritage|fort|palace|temple|cathedral|basilica|church|mosque|shrine|monument|memorial|museum|gallery|beach|bay\b|cove|falls|waterfall|cascade|national\s*park|sanctuary|reserve|wildlife|viewpoint|lookout|overlook|ruins|archaeological|historic|citadel|amphitheatre|amphitheater|garden|botanical|zoo|aquarium|lake|river|cave|cliff|peak|summit|lighthouse|castle|chapel|monastery|abbey|mausoleum|tomb|pagoda|stupa|grotto|canyon|valley|glacier|volcano|island|reef|market\s*square|old\s*town|historic\s*district|square\b|bridge|tower|statue|observatory|planetarium|science\s*center|cultural\s*center|art\s*center|opera|theatre|theater)\b/i;

/** Always commercial — never treated as a tourist sight even if the name mentions a beach or view. */
const STRONG_COMMERCIAL_PATTERNS =
  /\b(rentals?|rent-a|hire|leasing|restaurant|cafe|café|hostel|hotel|resort|shop|store|boutique|mall|supermarket|agency|dealer|garage|salon|pharmacy|clinic|hospital|gym\b|fitness|laundry|atm\b|bank\b|parking|taxi|cab\s*service)\b/i;

export function normalizeAttractionKey(name: string): string {
  return name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

export function namesLikelyMatch(a: string, b: string): boolean {
  const keyA = normalizeAttractionKey(a);
  const keyB = normalizeAttractionKey(b);
  if (!keyA || !keyB) return false;
  if (keyA === keyB) return true;
  if (keyA.includes(keyB) || keyB.includes(keyA)) return true;
  const tokensA = keyA.split(" ").filter((t) => t.length > 2);
  const tokensB = new Set(keyB.split(" ").filter((t) => t.length > 2));
  const overlap = tokensA.filter((t) => tokensB.has(t)).length;
  const minTokens = Math.min(tokensA.length, tokensB.size);
  return minTokens > 0 && overlap / minTokens >= 0.6;
}

export function isCommercialAttraction(
  name: string,
  types: string[] = [],
  vicinity?: string
): boolean {
  const combined = `${name} ${vicinity ?? ""}`;
  if (STRONG_COMMERCIAL_PATTERNS.test(combined)) {
    return true;
  }
  if (COMMERCIAL_NAME_PATTERNS.test(combined)) {
    if (SIGNIFICANCE_NAME_PATTERNS.test(name)) {
      return false;
    }
    return true;
  }
  if (types.length === 0) return false;
  const hasPreferred = types.some((t) => PREFERRED_TOURIST_TYPES.has(t));
  const hasExcluded = types.some((t) => EXCLUDED_PLACE_TYPES.has(t));
  return hasExcluded && !hasPreferred;
}

export function touristSignificanceScore(
  spot: AttractionDoc,
  options: {
    geminiRank?: number;
    hasWikipedia?: boolean;
    placeTypes?: string[];
  } = {}
): number {
  let score = 0;

  const reviews = spot.userRatingsTotal ?? 0;
  const rating = spot.rating ?? 0;
  score += Math.min(Math.log10(reviews + 1) * 4, 12);
  score += rating * 1.5;

  if (SIGNIFICANCE_NAME_PATTERNS.test(spot.name)) {
    score += 8;
  }
  if (options.hasWikipedia) {
    score += 10;
  }
  if (options.geminiRank !== undefined) {
    score += Math.max(0, 25 - options.geminiRank);
  }
  if (options.placeTypes?.some((t) => PREFERRED_TOURIST_TYPES.has(t))) {
    score += 5;
  }
  if (isCommercialAttraction(spot.name, options.placeTypes ?? [], spot.description)) {
    score -= 50;
  }

  return score;
}

export function filterCuratedAttractions(
  spots: AttractionDoc[],
  placeTypesById?: Map<string, string[]>
): AttractionDoc[] {
  return spots.filter((spot) => {
    const types = placeTypesById?.get(spot.id) ?? [];
    return !isCommercialAttraction(spot.name, types, spot.description);
  });
}

export function mergeAttractionLists(...lists: AttractionDoc[][]): AttractionDoc[] {
  const merged: AttractionDoc[] = [];
  for (const list of lists) {
    for (const spot of list) {
      const duplicate = merged.find((existing) => namesLikelyMatch(existing.name, spot.name));
      if (duplicate) {
        duplicate.rating = Math.max(duplicate.rating ?? 0, spot.rating ?? 0);
        duplicate.userRatingsTotal = Math.max(
          duplicate.userRatingsTotal ?? 0,
          spot.userRatingsTotal ?? 0
        );
        if (!duplicate.imageUrl && spot.imageUrl) {
          duplicate.imageUrl = spot.imageUrl;
        }
        if (
          duplicate.description === "Popular tourist attraction" &&
          spot.description &&
          spot.description !== "Popular tourist attraction"
        ) {
          duplicate.description = spot.description;
        }
        continue;
      }
      merged.push({ ...spot });
    }
  }
  return merged;
}

export function rankCuratedAttractions(
  spots: AttractionDoc[],
  options: {
    geminiRanks?: Map<string, number>;
    wikipediaNames?: Set<string>;
    placeTypesById?: Map<string, string[]>;
    targetCount: number;
    minCount?: number;
  }
): AttractionDoc[] {
  const { geminiRanks, wikipediaNames, placeTypesById, targetCount, minCount = 3 } = options;

  const scored = spots
    .map((spot) => {
      const matchedGeminiRank = findGeminiRank(spot.name, geminiRanks);
      return {
        spot,
        score: touristSignificanceScore(spot, {
          geminiRank: matchedGeminiRank,
          hasWikipedia: wikipediaNames?.has(normalizeAttractionKey(spot.name)),
          placeTypes: placeTypesById?.get(spot.id),
        }),
        geminiRank: matchedGeminiRank,
      };
    })
    .filter(({ spot, score }) => {
      const types = placeTypesById?.get(spot.id) ?? [];
      return score > -20 && !isCommercialAttraction(spot.name, types, spot.description);
    })
    .sort((a, b) => {
      const rankA = a.geminiRank ?? 999;
      const rankB = b.geminiRank ?? 999;
      if (rankA !== rankB) return rankA - rankB;
      return b.score - a.score;
    });

  const selected = scored.slice(0, targetCount).map(({ spot }) => spot);
  if (selected.length >= minCount) {
    return selected;
  }

  return scored
    .slice(0, Math.max(minCount, targetCount))
    .map(({ spot }) => spot);
}

function findGeminiRank(
  spotName: string,
  geminiRanks?: Map<string, number>
): number | undefined {
  if (!geminiRanks) return undefined;
  for (const [name, rank] of geminiRanks.entries()) {
    if (namesLikelyMatch(name, spotName)) {
      return rank;
    }
  }
  return undefined;
}

export function buildGeminiRankMap(
  spots: Array<{ name: string; rank?: number }>
): Map<string, number> {
  const ranks = new Map<string, number>();
  spots.forEach((spot, index) => {
    ranks.set(spot.name, spot.rank ?? index + 1);
  });
  return ranks;
}
