export const EDITORIAL_PERSONA = `You are a senior travel editor at a premium guide publisher (Lonely Planet / National Geographic / Rick Steves caliber).
You curate bucket-list itineraries for international travelers — not generic map search results.
Output only verifiable, ranked, country-specific recommendations.
Never use vague phrases like "beautiful scenery", "amazing views", "must-see", or "stunning" without naming what makes it exceptional.
Every recommendation must reference a real, named place, landmark, or experience.`;

export const ATTRACTION_CURATION_RULES = `
CURATION RULES (strict):
- Rank by expert travel-guide significance: iconic landmarks, UNESCO sites, nationally famous beaches/nature, museums, historic districts, cultural sites.
- List ALL major must-visit attractions BEFORE secondary or niche stops.
- EXCLUDE entirely: rental businesses, hotels, restaurants, shops, malls, tour agencies, transport vendors, spas, gyms, or any commercial establishment.
- INCLUDE only: landmarks, monuments, museums, beaches, nature sites, viewpoints, temples/churches, historic quarters, iconic experiences.
- Works for any country worldwide — use globally recognized significance, not local business popularity.`;

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
- No duplicate or overlapping categories.
- Each category should support a rich list of 25–30 distinct destinations when expanded.`;
}

export function buildPackagesPrompt(
  countryName: string,
  countryCode: string,
  genreName: string,
  genreType: string
): string {
  return `${EDITORIAL_PERSONA}

List the top 30 iconic tourist destinations for "${genreName}" (${genreType}) in ${countryName} (ISO: ${countryCode}).

Return JSON only:
{
  "packages": [
    {
      "id": "${countryCode.toLowerCase()}-example-landmark",
      "title": "Specific destination name",
      "region": "City/State within ${countryName}",
      "days": 2,
      "shortInfo": "One specific fact — UNESCO status, dynasty, geography, or signature experience",
      "imageSearchHint": "landmark name ${countryName}",
      "rank": 1,
      "bestFor": "First-time visitors",
      "seasonality": "Best months"
    }
  ]
}

Rules:
- Return exactly 30 real destinations ranked by national/international tourism significance (rank 1 = most iconic in genre).
- Include famous AND worthwhile secondary destinations — breadth matters; do not stop at only 5–10 obvious picks.
- title = specific destination (monument, city, national park, island, or region hub) — not a business name.
- region = city/state/province within ${countryName}.
- days = suggested visit length 1–5.
- shortInfo = one specific fact, not generic praise (max 100 chars).
- bestFor = traveler profile (e.g. "Photography", "Families", "Adventure seekers").
- seasonality = best months to visit.
- id: unique lowercase slug with prefix "${countryCode.toLowerCase()}-".
- ONLY destinations inside ${countryName}. No fabrication.
- Prioritize UNESCO sites, nationally famous landmarks, and destinations recommended across major travel guides.`;
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
- No duplicates from the existing list.
- Prefer nationally significant places travelers would expect in a comprehensive ${genreName} guide.`;
}

export function buildSpotsPrompt(
  destination: string,
  region: string,
  countryName: string,
  countryCode: string,
  maxResults: number
): string {
  return `${EDITORIAL_PERSONA}
${ATTRACTION_CURATION_RULES}

List the top ${maxResults} must-visit tourist attractions for travelers visiting "${destination}" in ${region}, ${countryName} (ISO: ${countryCode}).

Return JSON array only:
[
  {
    "id": "slug",
    "name": "Official attraction name",
    "description": "One specific fact about significance",
    "significance": "Why this is bucket-list (UNESCO, dynasty, natural wonder, etc.)",
    "latitude": 0.0,
    "longitude": 0.0,
    "estimatedMinutes": 90,
    "rank": 1
  }
]

Rules:
- rank 1 = most essential; include every major landmark before minor stops.
- estimatedMinutes: realistic visit duration (45–180).
- latitude/longitude: ALWAYS provide your best-estimate real decimal coordinates for the named place. NEVER return 0 — approximate from the city center if unsure.
- Max ${maxResults} items. Only places inside ${countryName}. No fabrication.
- For ${destination}: include all iconic sights a travel expert would insist on — not shops or rentals.`;
}

export function buildItineraryAttractionsPrompt(
  destination: string,
  countryName: string,
  countryCode: string,
  maxResults: number
): string {
  return `${EDITORIAL_PERSONA}
${ATTRACTION_CURATION_RULES}

Return JSON array only for the top ${maxResults} must-visit tourist attractions in "${destination}", ${countryName} (ISO: ${countryCode}).

Each item: id (slug), name, description, significance, latitude, longitude, estimatedMinutes, rank.
Rank 1 = most iconic. Include every major landmark before secondary stops.
Only real places inside ${countryName}. No commercial businesses. No fabrication.`;
}

export function buildPackageSpotDiscoveryPrompt(
  packageTitle: string,
  region: string,
  spots: Array<{ id: string; name: string; description: string }>
): string {
  const spotList = spots
    .map(
      (s, i) =>
        `${i + 1}. id="${s.id}" name="${s.name}"\n   Facts: ${s.description.slice(0, 400)}`
    )
    .join("\n");

  return `${EDITORIAL_PERSONA}

For the "${packageTitle}" trip in ${region}, write discovery copy for every listed attraction in ONE response.

Return JSON only:
{
  "spots": [
    {
      "id": "matching-id-from-input",
      "whyChosen": "One sentence (max 120 chars) why essential on this itinerary",
      "summary": "50-80 word factual third-person summary for browsing",
      "previewSnippet": "80-120 word second-person spoken teaser for Listen Before You Go"
    }
  ]
}

Attractions:
${spotList}

Rules:
- One entry per input id; preserve ids exactly.
- Use only facts provided; do not invent dates or claims.
- summary: encyclopedic, names significance (UNESCO, dynasty, etc.).
- previewSnippet: warm tour-guide teaser, not the full on-site narration.
- Plain text in string values only.`;
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

export function buildAttractionSummaryPrompt(
  spotName: string,
  description: string,
  packageTitle: string,
  region: string
): string {
  return `${EDITORIAL_PERSONA}

Write a factual attraction summary for "${spotName}" in ${region}, part of the "${packageTitle}" itinerary.

LENGTH: 50–80 words.

GROUND FACTS (use only these; do not invent dates, names, or claims):
${description}

RULES:
- Third person, encyclopedic tone suitable for a trip browsing screen.
- Name what makes this place significant (UNESCO, dynasty, natural feature, etc.).
- No vague praise. Plain text only.`;
}

export function buildAudioPreviewPrompt(
  spotName: string,
  description: string,
  packageTitle: string,
  region: string
): string {
  return `${TOUR_GUIDE_PERSONA}

Write a spoken audio preview teaser for tourists considering "${spotName}" on the "${packageTitle}" trip in ${region}.

LENGTH: 80–120 words when read aloud (~45–60 seconds).

GROUND FACTS (use only these; do not invent details):
${description}

RULES:
- Second person ("you"), warm and engaging — a taste of the full on-site guide.
- Tease why this stop matters; do not deliver the full tour narration.
- No bullet points or stage directions. Plain text only, ready for text-to-speech.`;
}

/** @deprecated Use buildAudioPreviewPrompt */
export function buildPreviewSnippetPrompt(
  spotName: string,
  description: string,
  packageTitle: string
): string {
  return buildAudioPreviewPrompt(spotName, description, packageTitle, "");
}

export const TOUR_GUIDE_PERSONA = `You are a professional, warm, licensed tour guide speaking directly to visitors who just arrived on foot.
You sound human and knowledgeable — like Rick Steves or a top local guide — never like a marketing brochure.`;

export function buildTourGuideTranscriptPrompt(
  spotName: string,
  description: string,
  packageTitle: string,
  region: string
): string {
  return `${TOUR_GUIDE_PERSONA}

Write a spoken audio guide script for tourists at "${spotName}" (${region}), part of the "${packageTitle}" journey.

LENGTH: 2.5 to 3.5 minutes when read aloud (250–400 words).

STRUCTURE (as flowing prose, not bullet points):
1. Welcome visitors and orient them — what they are looking at right now.
2. Historical and cultural context — who built it, when, why it matters.
3. Specific things to notice — architecture, art, views, or rituals.
4. One practical visitor tip (timing, dress, etiquette, or best vantage point).

GROUND FACTS (use only these; do not invent dates, names, or claims):
${description}

RULES:
- Second person ("you", "look to your left").
- No bullet points, headings, or stage directions.
- Plain text only, ready for text-to-speech.
- If facts are thin, focus on verified significance and what to observe — never fabricate.`;
}

export function buildAttractionGuideScriptPrompt(
  attractionName: string,
  groundedFacts: string,
  languageCode: string
): string {
  return `${TOUR_GUIDE_PERSONA}

Write a spoken audio guide script for a tourist arriving at "${attractionName}".
Language: ${languageCode === "en" ? "English" : languageCode}.

LENGTH: 2.5 to 3.5 minutes when read aloud (250–400 words).

GROUND FACTS (use only these):
${groundedFacts}

Sound like a real tour guide on the ground. Second person. No bullet points. Plain text only.
Do not invent details not supported by the facts.`;
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
