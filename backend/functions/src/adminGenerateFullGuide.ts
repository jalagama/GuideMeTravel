import { HttpsError } from "firebase-functions/v2/https";
import { db } from "./firebaseAdmin";
import { getGuideMeLogger } from "./logging/loggerContext";
import {
  pregenGuideContentBatch,
  validateLanguages,
  type GuideAttractionInput,
} from "./pregenGuideContent";
import { DEFAULT_ADMIN_CONTEXT, type GenerationStatus } from "./curationTypes";
import { listPopularDestinations, syncPopularityGenerationStatus } from "./destinationPopularity";

export async function generateFullGuideForPackage(input: {
  packageId: string;
  languages: string[];
}): Promise<{
  packageId: string;
  guidesGenerated: number;
  generationStatus: GenerationStatus;
  languages: string[];
}> {
  const packageId = input.packageId.trim();
  if (!packageId) {
    throw new HttpsError("invalid-argument", "packageId is required.");
  }

  const languages = validateLanguages(input.languages.length > 0 ? input.languages : ["en"]);
  const packageRef = db.collection("tourPackages").doc(packageId);
  const packageSnap = await packageRef.get();
  if (!packageSnap.exists) {
    throw new HttpsError("not-found", `Tour package ${packageId} not found.`);
  }

  const packageData = packageSnap.data()!;
  const spots = ((packageData.spots ?? []) as Array<{
    id?: string;
    name?: string;
    description?: string;
    summary?: string;
    previewSnippet?: string;
    transcript?: string;
    latitude?: number;
    longitude?: number;
  }>).map((spot, index) => ({
    id: String(spot.id ?? `${packageId}-spot-${index}`),
    name: String(spot.name ?? spot.id ?? "Spot"),
    description: String(spot.summary ?? spot.description ?? ""),
    transcript: spot.transcript,
    latitude: spot.latitude,
    longitude: spot.longitude,
  })) as GuideAttractionInput[];

  if (spots.length === 0) {
    throw new HttpsError("failed-precondition", `Package ${packageId} has no spots to guide.`);
  }

  await packageRef.set(
    {
      generationStatus: "GUIDE_GENERATING",
      updatedAtMillis: Date.now(),
    },
    { merge: true }
  );
  await syncPopularityGenerationStatus(packageId, "GUIDE_GENERATING");

  let guidesGenerated = 0;
  try {
    for (const lang of languages) {
      guidesGenerated += await pregenGuideContentBatch(spots, lang, DEFAULT_ADMIN_CONTEXT);
    }

    await packageRef.set(
      {
        generationStatus: "GUIDE_READY",
        updatedAtMillis: Date.now(),
      },
      { merge: true }
    );
    await syncPopularityGenerationStatus(packageId, "GUIDE_READY");

    getGuideMeLogger().info("admin_full_guide_generated", {
      packageId,
      languages,
      spotCount: spots.length,
      guidesGenerated,
    });

    return {
      packageId,
      guidesGenerated,
      generationStatus: "GUIDE_READY",
      languages,
    };
  } catch (error) {
    await packageRef.set(
      {
        generationStatus: "FAILED",
        updatedAtMillis: Date.now(),
      },
      { merge: true }
    );
    await syncPopularityGenerationStatus(packageId, "FAILED");
    throw error;
  }
}

export async function listPopularDestinationsForAdmin(input: {
  countryCode?: string;
  limit?: number;
}) {
  return listPopularDestinations({
    countryCode: input.countryCode,
    limit: input.limit ?? 50,
  });
}
