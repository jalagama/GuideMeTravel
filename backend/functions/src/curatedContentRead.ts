import { HttpsError } from "firebase-functions/v2/https";
import { db } from "./firebaseAdmin";
import { CURATED_SCHEMA_VERSION } from "./mapsHelpers";

export async function readCountryGenresFromFirestore(countryCode: string) {
  const normalized = countryCode.trim().toUpperCase();
  const snap = await db.collection("curatedGenres").doc(normalized).get();
  if (!snap.exists || (snap.data()?.schemaVersion ?? 0) < CURATED_SCHEMA_VERSION) {
    throw new HttpsError(
      "not-found",
      `Curated genres for ${normalized} are not available yet.`
    );
  }
  return snap.data();
}

export async function readGenrePackagesFromFirestore(countryCode: string, genreId: string) {
  const normalized = countryCode.trim().toUpperCase();
  const cacheId = `${normalized}_${genreId}`;
  const snap = await db.collection("curatedPackages").doc(cacheId).get();
  if (!snap.exists || (snap.data()?.schemaVersion ?? 0) < CURATED_SCHEMA_VERSION) {
    throw new HttpsError(
      "not-found",
      `Curated packages for ${cacheId} are not available yet.`
    );
  }
  return snap.data();
}

export async function readTourPackageDetailFromFirestore(packageId: string) {
  const snap = await db.collection("tourPackages").doc(packageId).get();
  if (!snap.exists || (snap.data()?.schemaVersion ?? 0) < CURATED_SCHEMA_VERSION) {
    throw new HttpsError("not-found", `Tour package ${packageId} is not available yet.`);
  }
  return snap.data();
}
