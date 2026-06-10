export const EDITORIAL_PERSONA = `You are a senior travel editor at a premium guide publisher (Lonely Planet / National Geographic caliber).
Output only verifiable, ranked, country-specific recommendations.
Never use vague phrases like "beautiful scenery", "amazing views", "must-see", or "stunning" without naming what makes it exceptional.
Every recommendation must reference a real, named place, landmark, or experience.`;

export function buildGenresPrompt(countryName: string, countryCode: string): string {
  return `${EDITORIAL_PERSONA}

Curate exactly 12 top tourist experience categories for ${countryName} (ISO: ${countryCode}).

Return JSON only:
{
  "genres": [
    {
      "id": "historical-heritage",
      "name": "Historical & Heritage Destinations",
      "type": "heritage",
      "blurb": "UNESCO forts, palaces & ancient capitals",
      "imageSearchHint": "heritage landmark ${countryName}",
      "rank": 1
    }
  ]
}

Rules:
- Return exactly 12 categories ranked by tourism significance (rank 1 = most popular).
- Cover diverse dimensions: heritage, mountains, beaches, wildlife, spiritual, food/culture, adventure, urban, wellness, festivals, offbeat, family.
- Names must be specific (e.g. "Western Ghats Hill Stations", not "Nature").
- blurb: max 90 characters, marketing-grade, names a signature experience.
- type: one of heritage, mountain, beach, wildlife, spiritual, culture, adventure, urban, wellness, festival, offbeat, family.
- id: lowercase slug, unique, no spaces.
- ONLY categories within ${countryName}. No fabricated places.
- No duplicate or overlapping categories.`;
}

export function buildPackagesPrompt(
  countryName: string,
  countryCode: string,
  genreName: string,
  genreType: string
): string {
  return `${EDITORIAL_PERSONA}

List the top 20 iconic tourist destinations for "${genreName}" (${genreType}) in ${countryName} (ISO: ${countryCode}).

Return JSON only:
{
  "packages": [
    {
      "id": "${countryCode.toLowerCase()}-taj-mahal",
      "title": "Taj Mahal",
      "region": "Agra, Uttar Pradesh",
      "days": 2,
      "shortInfo": "UNESCO marble mausoleum built by Shah Jahan",
      "imageSearchHint": "Taj Mahal Agra ${countryName}",
      "rank": 1,
      "bestFor": "First-time visitors",
      "seasonality": "Oct–Mar"
    }
  ]
}

Rules:
- Return exactly 20 real destinations ranked by national/international tourism significance (rank 1 = best in genre).
- title = specific destination name (monument, city, national park, or region hub).
- region = city/state within ${countryName}.
- days = suggested visit length 1–5.
- shortInfo = one specific fact, not generic praise (max 100 chars).
- bestFor = traveler profile (e.g. "Photography", "Families", "Adventure seekers").
- seasonality = best months to visit (e.g. "Oct–Mar").
- id: unique lowercase slug with prefix "${countryCode.toLowerCase()}-".
- ONLY destinations inside ${countryName}. No fabrication.`;
}

export function buildPackagesFillPrompt(
  countryName: string,
  countryCode: string,
  genreName: string,
  existingTitles: string[],
  needed: number
): string {
  return `${EDITORIAL_PERSONA}

For "${genreName}" in ${countryName}, suggest ${needed} additional real tourist destinations NOT in this list:
${existingTitles.join(", ")}

Return JSON only:
{ "packages": [ { "id", "title", "region", "days", "shortInfo", "rank", "bestFor", "seasonality" } ] }

Rules:
- Only destinations inside ${countryName}.
- rank continues from ${existingTitles.length + 1}.
- id prefix "${countryCode.toLowerCase()}-".
- No duplicates from the existing list.`;
}

export function buildSpotsPrompt(
  destination: string,
  countryName: string,
  countryCode: string,
  maxResults: number
): string {
  return `${EDITORIAL_PERSONA}

Return JSON array only for major real tourist attractions near ${destination} in ${countryName}.
Each item: id, name, description, latitude, longitude, estimatedMinutes, rating, userRatingsTotal.
Max ${maxResults} items. Only places inside ${countryName} (${countryCode}). No fabrication.`;
}

export function buildWhyChosenPrompt(
  spotName: string,
  packageTitle: string,
  rating?: number,
  reviews?: number
): string {
  return `${EDITORIAL_PERSONA}

In one concise sentence (max 120 chars), explain why "${spotName}" is essential on the "${packageTitle}" itinerary.
Cite specific significance: UNESCO status, architectural style, historical role, or review data (rating ${rating ?? "unknown"}, ${reviews ?? 0} reviews).
No vague praise. Plain text only.`;
}

export function buildPreviewSnippetPrompt(
  spotName: string,
  description: string,
  packageTitle: string
): string {
  return `${EDITORIAL_PERSONA}

Write a 30-second audio teaser script (60–80 words) for a tourist previewing "${spotName}" on the "${packageTitle}" trip.
Ground facts: ${description}
Conversational, engaging, factual. Do not invent details not in the facts. Plain text only.`;
}

export function buildPackageExtrasPrompt(
  summaryTitle: string,
  region: string,
  countryName: string,
  days: number,
  spotNames: string[],
  daySpotGroups: Record<number, string[]>
): string {
  const daySummaries = Object.entries(daySpotGroups)
    .map(([day, spots]) => `Day ${day}: ${spots.join(", ")}`)
    .join("\n");

  return `${EDITORIAL_PERSONA}

Curate a ${days}-day trip plan for "${summaryTitle}" in ${region}, ${countryName}.

Itinerary:
${daySummaries}

All spots: ${spotNames.join(", ")}

Return JSON only:
{
  "overview": "3-4 sentences: who this trip is for, pace, signature moments. Name specific places.",
  "daySummaries": { "1": "Curator narrative for day 1 referencing only listed spots", "2": "..." },
  "tips": ["6-8 actionable tips: crowds, transport, dress codes, season-specific"],
  "essentials": ["packing, documents, local etiquette"],
  "highlights": ["5-8 bullets, each naming a specific place or feature"]
}

Rules:
- overview: 120–400 characters, names at least 2 specific places.
- tips: minimum 6, region-specific, actionable.
- highlights: each bullet names a specific place or feature.
- daySummaries: one narrative per day, only reference spots listed for that day.
- No vague language. No fabrication.`;
}

export function buildHotelDescriptionPrompt(hotelName: string, region: string): string {
  return `In one sentence (max 80 chars), describe why "${hotelName}" in ${region} is a good base for exploring. Factual, no fabrication. Plain text only.`;
}

export function buildRestaurantDescriptionPrompt(
  restaurantName: string,
  region: string,
  cuisine?: string
): string {
  return `In one sentence (max 80 chars), describe "${restaurantName}" in ${region}${cuisine ? ` (${cuisine})` : ""}. Mention cuisine type. Factual. Plain text only.`;
}
