import { db } from "./firebaseAdmin";
import { slugify } from "./mapsHelpers";
import { getGuideMeLogger } from "./logging/loggerContext";

export type DestinationIndexEntry = {
  title: string;
  aliases: string[];
  searchTokens: string[];
  packageId?: string;
  destinationCurationId?: string;
  region: string;
  genreIds: string[];
  spotNames: string[];
  countryCode: string;
  updatedAtMillis: number;
};

function tokenize(text: string): string[] {
  return text
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s]/gu, " ")
    .split(/\s+/)
    .map((t) => t.trim())
    .filter((t) => t.length >= 2);
}

function entrySlug(title: string): string {
  return slugify(title);
}

export function destinationCurationDocId(
  destination: string,
  region: string,
  countryCode: string
): string {
  return `${countryCode.toLowerCase()}_${slugify(destination)}_${slugify(region)}`;
}

/** Build search index from all tourPackages for a country. */
export async function buildDestinationIndex(countryCode: string): Promise<number> {
  const normalized = countryCode.trim().toUpperCase();
  const prefix = `${normalized.toLowerCase()}-`;
  const packagesSnap = await db.collection("tourPackages").get();
  const entries = new Map<string, DestinationIndexEntry>();

  for (const doc of packagesSnap.docs) {
    const data = doc.data();
    if (data.countryCode !== normalized && !doc.id.startsWith(prefix)) {
      continue;
    }

    const title = String(data.title ?? doc.id);
    const region = String(data.region ?? "");
    const genreId = String(data.genreId ?? "");
    const slug = entrySlug(title);
    const aliases = [title, `${title}, ${region}`, `${title}, ${region}, ${countryCode}`];
    const spots = (data.spots ?? []) as Array<{ name?: string }>;
    const spotNames = spots.map((s) => String(s.name ?? "")).filter(Boolean);

    const curationId = destinationCurationDocId(title, region, normalized);
    const searchTokens = [
      ...tokenize(title),
      ...tokenize(region),
      ...spotNames.flatMap(tokenize),
    ];

    const existing = entries.get(slug);
    if (existing) {
      existing.genreIds = [...new Set([...existing.genreIds, genreId])];
      existing.spotNames = [...new Set([...existing.spotNames, ...spotNames])];
      existing.searchTokens = [...new Set([...existing.searchTokens, ...searchTokens])];
      continue;
    }

    entries.set(slug, {
      title,
      aliases,
      searchTokens: [...new Set(searchTokens)],
      packageId: doc.id,
      destinationCurationId: curationId,
      region,
      genreIds: genreId ? [genreId] : [],
      spotNames,
      countryCode: normalized,
      updatedAtMillis: Date.now(),
    });

    for (const spotName of spotNames) {
      const spotSlug = entrySlug(spotName);
      if (entries.has(spotSlug)) continue;
      entries.set(spotSlug, {
        title: spotName,
        aliases: [spotName, `${spotName}, ${region}`],
        searchTokens: tokenize(spotName),
        packageId: doc.id,
        destinationCurationId: curationId,
        region,
        genreIds: genreId ? [genreId] : [],
        spotNames: [spotName],
        countryCode: normalized,
        updatedAtMillis: Date.now(),
      });
    }
  }

  const batch = db.batch();
  const collectionRef = db.collection("destinationIndex").doc(normalized).collection("entries");
  for (const [slug, entry] of entries) {
    batch.set(collectionRef.doc(slug), entry, { merge: true });
  }
  await batch.commit();

  getGuideMeLogger().info("destination_index_built", {
    countryCode: normalized,
    entryCount: entries.size,
  });
  return entries.size;
}

export async function resolveDestination(
  countryCode: string,
  query: string
): Promise<DestinationIndexEntry | null> {
  const normalized = countryCode.trim().toUpperCase();
  const tokens = tokenize(query);
  if (tokens.length === 0) return null;

  const slug = entrySlug(query.split(",")[0]?.trim() ?? query);
  const directRef = db
    .collection("destinationIndex")
    .doc(normalized)
    .collection("entries")
    .doc(slug);
  const directSnap = await directRef.get();
  if (directSnap.exists) {
    return directSnap.data() as DestinationIndexEntry;
  }

  const entriesSnap = await db
    .collection("destinationIndex")
    .doc(normalized)
    .collection("entries")
    .where("searchTokens", "array-contains", tokens[0])
    .limit(20)
    .get();

  let best: DestinationIndexEntry | null = null;
  let bestScore = 0;
  for (const doc of entriesSnap.docs) {
    const entry = doc.data() as DestinationIndexEntry;
    const score = tokens.filter((t) => entry.searchTokens.includes(t)).length;
    if (score > bestScore) {
      bestScore = score;
      best = entry;
    }
  }
  return bestScore >= 1 ? best : null;
}
