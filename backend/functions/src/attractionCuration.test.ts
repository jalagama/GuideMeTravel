import test from "node:test";
import assert from "node:assert/strict";
import {
  buildGeminiRankMap,
  isCommercialAttraction,
  mergeAttractionLists,
  namesLikelyMatch,
  rankCuratedAttractions,
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
