import { FieldValue } from "firebase-admin/firestore";
import { db } from "./firebaseAdmin";
import type { GenerationStatus } from "./curationTypes";

export type DestinationPopularityDoc = {
  packageId: string;
  countryCode: string;
  title?: string;
  viewCount: number;
  downloadCount: number;
  lastViewedAtMillis: number;
  lastDownloadedAtMillis?: number;
  generationStatus?: GenerationStatus;
  updatedAtMillis: number;
};

const VIEW_THRESHOLD = 100;
const DOWNLOAD_THRESHOLD = 100;

export async function recordPackageView(
  packageId: string,
  countryCode?: string,
  title?: string
): Promise<void> {
  if (!packageId) return;

  const ref = db.collection("destination_popularity").doc(packageId);
  const now = Date.now();
  const payload: Record<string, unknown> = {
    packageId,
    viewCount: FieldValue.increment(1),
    lastViewedAtMillis: now,
    updatedAtMillis: now,
  };
  if (countryCode) payload.countryCode = countryCode.trim().toUpperCase();
  if (title) payload.title = title;

  await ref.set(payload, { merge: true });
}

export async function recordPackageDownload(
  packageId: string,
  countryCode?: string,
  title?: string,
  generationStatus?: GenerationStatus
): Promise<void> {
  if (!packageId) return;

  const ref = db.collection("destination_popularity").doc(packageId);
  const now = Date.now();
  const payload: Record<string, unknown> = {
    packageId,
    downloadCount: FieldValue.increment(1),
    lastDownloadedAtMillis: now,
    updatedAtMillis: now,
  };
  if (countryCode) payload.countryCode = countryCode.trim().toUpperCase();
  if (title) payload.title = title;
  if (generationStatus) payload.generationStatus = generationStatus;

  await ref.set(payload, { merge: true });
}

export async function syncPopularityGenerationStatus(
  packageId: string,
  generationStatus: GenerationStatus
): Promise<void> {
  if (!packageId) return;
  await db.collection("destination_popularity").doc(packageId).set(
    {
      generationStatus,
      updatedAtMillis: Date.now(),
    },
    { merge: true }
  );
}

export async function listPopularDestinations(input: {
  countryCode?: string;
  minViews?: number;
  minDownloads?: number;
  limit?: number;
}): Promise<DestinationPopularityDoc[]> {
  const minViews = input.minViews ?? VIEW_THRESHOLD;
  const minDownloads = input.minDownloads ?? DOWNLOAD_THRESHOLD;
  const limit = input.limit ?? 50;

  let query = db.collection("destination_popularity").orderBy("downloadCount", "desc");

  if (input.countryCode) {
    query = query.where("countryCode", "==", input.countryCode.trim().toUpperCase());
  }

  const snap = await query.limit(limit * 3).get();
  return snap.docs
    .map((doc) => doc.data() as DestinationPopularityDoc)
    .filter((row) => row.viewCount >= minViews || row.downloadCount >= minDownloads)
    .slice(0, limit);
}

export { VIEW_THRESHOLD, DOWNLOAD_THRESHOLD };
