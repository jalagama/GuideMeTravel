import test from "node:test";
import assert from "node:assert/strict";
import {
  buildGeminiRankMap,
  enrichGeminiWithPlacesMetadata,
  isCommercialAttraction,
  mergeAttractionLists,
  namesLikelyMatch,
  rankCuratedAttractions,
  selectEditorialAttractions,
} from "./attractionCuration";
import type { AttractionDoc } from "./mapsHelpers";

test("isCommercialAttraction rejects rentals and shops", () => {
  assert.equal(isCommercialAttraction("Goa Bike Rental"), true);
  assert.equal(isCommercialAttraction("Scooter Hire Panaji"), true);
  assert.equal(isCommercialAttraction("Beach View Restaurant"), true);
});

test("isCommercialAttraction keeps iconic landmarks", () => {
  assert.equal(isCommercialAttraction("Basilica of Bom Jesus"), false);
  assert.equal(isCommercialAttraction("Fort Aguada"), false);
  assert.equal(isCommercialAttraction("Hampi Archaeological Ruins"), false);
});

test("namesLikelyMatch links variant spellings", () => {
  assert.equal(namesLikelyMatch("Taj Mahal", "The Taj Mahal"), true);
  assert.equal(namesLikelyMatch("Basilica of Bom Jesus", "Bom Jesus Basilica"), true);
  assert.equal(namesLikelyMatch("Fort Aguada", "Goa Bike Rental"), false);
});

test("mergeAttractionLists dedupes and preserves richer metadata", () => {
  const merged = mergeAttractionLists(
    [
      {
        id: "a",
        name: "Fort Aguada",
        description: "Popular tourist attraction",
        latitude: 1,
        longitude: 2,
        orderIndex: 0,
        estimatedMinutes: 45,
        rating: 4.5,
        userRatingsTotal: 1000,
      },
    ],
    [
      {
        id: "b",
        name: "Aguada Fort",
        description: "17th-century Portuguese fort",
        latitude: 1,
        longitude: 2,
        orderIndex: 0,
        estimatedMinutes: 45,
        rating: 4.7,
        userRatingsTotal: 2000,
        imageUrl: "https://example.com/fort.jpg",
      },
    ]
  );
  assert.equal(merged.length, 1);
  assert.equal(merged[0].rating, 4.7);
  assert.equal(merged[0].userRatingsTotal, 2000);
  assert.equal(merged[0].imageUrl, "https://example.com/fort.jpg");
});

test("rankCuratedAttractions prioritizes gemini rank over places noise", () => {
  const spots: AttractionDoc[] = [
    {
      id: "rental",
      name: "Beach Scooter Rental",
      description: "Rent scooters",
      latitude: 15.5,
      longitude: 73.8,
      orderIndex: 0,
      estimatedMinutes: 45,
      rating: 4.9,
      userRatingsTotal: 500,
    },
    {
      id: "fort",
      name: "Fort Aguada",
      description: "Portuguese fort",
      latitude: 15.49,
      longitude: 73.77,
      orderIndex: 1,
      estimatedMinutes: 90,
      rating: 4.6,
      userRatingsTotal: 18000,
    },
    {
      id: "basilica",
      name: "Basilica of Bom Jesus",
      description: "UNESCO church",
      latitude: 15.5,
      longitude: 73.91,
      orderIndex: 2,
      estimatedMinutes: 60,
      rating: 4.7,
      userRatingsTotal: 22000,
    },
  ];

  const geminiRanks = buildGeminiRankMap([
    { name: "Basilica of Bom Jesus", rank: 1 },
    { name: "Fort Aguada", rank: 2 },
  ]);

  const ranked = rankCuratedAttractions(spots, {
    geminiRanks,
    targetCount: 2,
    minCount: 2,
  });

  assert.equal(ranked.length, 2);
  assert.equal(ranked[0].name, "Basilica of Bom Jesus");
  assert.equal(ranked[1].name, "Fort Aguada");
});

test("selectEditorialAttractions keeps only Gemini picks in rank order", () => {
  const geminiSpots: AttractionDoc[] = [
    {
      id: "virupaksha",
      name: "Virupaksha Temple",
      description: "UNESCO centerpiece",
      latitude: 15.3357,
      longitude: 76.4606,
      orderIndex: 0,
      estimatedMinutes: 90,
    },
    {
      id: "vittala",
      name: "Vijaya Vittala Temple",
      description: "Stone chariot",
      latitude: 15.3352,
      longitude: 76.4755,
      orderIndex: 1,
      estimatedMinutes: 120,
    },
  ];

  const placesNoise: AttractionDoc[] = [
    {
      id: "sunset",
      name: "Sunset Point",
      description: "Popular tourist attraction",
      latitude: 15.33,
      longitude: 76.46,
      orderIndex: 0,
      estimatedMinutes: 45,
      rating: 4.9,
      userRatingsTotal: 12000,
    },
    {
      id: "museum",
      name: "Hampi Archaeological Museum",
      description: "Museum",
      latitude: 15.32,
      longitude: 76.47,
      orderIndex: 1,
      estimatedMinutes: 60,
      rating: 4.5,
      userRatingsTotal: 8000,
    },
  ];

  const geminiRanks = buildGeminiRankMap([
    { name: "Virupaksha Temple", rank: 1 },
    { name: "Vijaya Vittala Temple", rank: 2 },
  ]);

  const selected = selectEditorialAttractions(geminiSpots, {
    geminiRanks,
    targetCount: 8,
    minCount: 2,
    fallbackSpots: placesNoise,
  });

  assert.equal(selected.length, 2);
  assert.equal(selected[0].name, "Virupaksha Temple");
  assert.equal(selected[1].name, "Vijaya Vittala Temple");
});

test("enrichGeminiWithPlacesMetadata borrows coordinates without adding candidates", () => {
  const enriched = enrichGeminiWithPlacesMetadata(
    [
      {
        id: "vittala",
        name: "Vijaya Vittala Temple",
        description: "Stone chariot",
        latitude: 0,
        longitude: 0,
        orderIndex: 0,
        estimatedMinutes: 120,
      },
    ],
    [
      {
        id: "places-vittala",
        name: "Vittala Temple",
        description: "Popular tourist attraction",
        latitude: 15.3352,
        longitude: 76.4755,
        orderIndex: 0,
        estimatedMinutes: 60,
        imageUrl: "https://example.com/vittala.jpg",
        rating: 4.8,
        userRatingsTotal: 15000,
      },
    ]
  );

  assert.equal(enriched.length, 1);
  assert.equal(enriched[0].latitude, 15.3352);
  assert.equal(enriched[0].longitude, 76.4755);
  assert.equal(enriched[0].imageUrl, "https://example.com/vittala.jpg");
  assert.equal(enriched[0].userRatingsTotal, 15000);
});
