import { HttpsError } from "firebase-functions/v2/https";
import type { DocumentReference } from "firebase-admin/firestore";
import { db } from "./firebaseAdmin";
import {
  GEMINI_ADMIN_EXPERT_MODEL,
  GEMINI_ADMIN_MODEL,
  getTtsVoiceTier,
  shouldUseGeminiBatch,
} from "./geminiConfig";
import {
  createJobId,
  DEFAULT_ADMIN_CONTEXT,
  type CurationJobDoc,
  type CurationMode,
} from "./curationTypes";
import {
  ensureTourPackageDetail,
  getCountryGenres,
  getGenrePackages,
} from "./curatedContent";
import { buildDestinationIndex } from "./destinationIndex";
import {
  collectSpotsForCountry,
  pregenGuideContentBatch,
  validateLanguages,
} from "./pregenGuideContent";
import { getGuideMeLogger } from "./logging/loggerContext";
import { CURATED_SCHEMA_VERSION } from "./mapsHelpers";
import { validateLanguageCode } from "./utils";

type StartCurationInput = {
  countryCode: string;
  languages: string[];
  mode: CurationMode;
  startedBy: string;
};

type PackageSummaryRef = {
  id: string;
  title: string;
  region: string;
  days: number;
  heroImageUrl: string;
  shortInfo: string;
  rank: number;
  bestFor: string;
  seasonality?: string;
};

export async function startCountryCuration(input: StartCurationInput): Promise<{ jobId: string }> {
  const countryCode = input.countryCode.trim().toUpperCase();
  const languages =
    input.mode === "catalog_only" ? [] : validateLanguages(input.languages);
  const jobId = createJobId(countryCode);

  const job: CurationJobDoc = {
    countryCode,
    languages,
    mode: input.mode,
    status: "queued",
    phase: "queued",
    qualityProfile: "admin",
    geminiModel: GEMINI_ADMIN_MODEL,
    geminiExpertModel: GEMINI_ADMIN_EXPERT_MODEL,
    geminiUseBatch: shouldUseGeminiBatch(DEFAULT_ADMIN_CONTEXT),
    ttsVoiceTier: getTtsVoiceTier(),
    costSaver: false,
    genreIds: [],
    pendingGenreIds: [],
    pendingPackageIds: [],
    pendingGuideTasks: [],
    phaseProgress: {},
    total: 0,
    done: 0,
    failed: 0,
    errors: [],
    startedBy: input.startedBy,
    startedAtMillis: Date.now(),
    updatedAtMillis: Date.now(),
    autoRun: true,
  };

  await db.collection("curationJobs").doc(jobId).set(job);
  getGuideMeLogger().info("curation_job_started", { jobId, countryCode, mode: input.mode });

  return { jobId };
}

export async function advanceCurationJob(jobId: string): Promise<boolean> {
  const jobRef = db.collection("curationJobs").doc(jobId);
  const snap = await jobRef.get();
  if (!snap.exists) {
    throw new HttpsError("not-found", `Curation job ${jobId} not found.`);
  }

  const job = snap.data() as CurationJobDoc;
  if (job.status === "completed" || job.status === "failed") {
    return false;
  }

  await jobRef.update({ status: "running", updatedAtMillis: Date.now() });
  const ctx = DEFAULT_ADMIN_CONTEXT;

  try {
    if (job.mode === "languages_only") {
      await runGuidesPhase(jobRef, job, ctx);
      await jobRef.update({
        status: "completed",
        phase: "completed",
        updatedAtMillis: Date.now(),
      });
      return false;
    }

    if (job.phase === "queued" || job.phase === "genres") {
      await runGenresPhase(jobRef, job, ctx);
      const refreshed = (await jobRef.get()).data() as CurationJobDoc;
      job.phase = refreshed.phase;
      job.pendingGenreIds = refreshed.pendingGenreIds;
    }

    const current = (await jobRef.get()).data() as CurationJobDoc;
    if (current.phase === "packages" && current.pendingGenreIds.length > 0) {
      await runNextGenrePackages(jobRef, current, ctx);
      return true;
    }

    const afterPackages = (await jobRef.get()).data() as CurationJobDoc;
    if (afterPackages.phase === "details" && afterPackages.pendingPackageIds.length > 0) {
      await runNextPackageDetail(jobRef, afterPackages, ctx);
      return true;
    }

    const afterDetails = (await jobRef.get()).data() as CurationJobDoc;
    if (afterDetails.phase === "index") {
      await runIndexPhase(jobRef, afterDetails);
      const postIndex = (await jobRef.get()).data() as CurationJobDoc;
      if (postIndex.mode === "catalog_only") {
        await jobRef.update({
          status: "completed",
          phase: "completed",
          updatedAtMillis: Date.now(),
        });
        return false;
      }
    }

    const preGuides = (await jobRef.get()).data() as CurationJobDoc;
    if (preGuides.phase === "guides") {
      const hasMore = await runGuidesPhase(jobRef, preGuides, ctx);
      if (hasMore) {
        return true;
      }
    }

    await jobRef.update({
      status: "completed",
      phase: "completed",
      updatedAtMillis: Date.now(),
    });
    return false;
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    await jobRef.update({
      status: "failed",
      phase: "failed",
      failed: job.failed + 1,
      errors: [...job.errors, { at: Date.now(), phase: job.phase, message }].slice(-50),
      updatedAtMillis: Date.now(),
    });
    getGuideMeLogger().error("curation_job_failed", { jobId, message });
    throw error;
  }
}

async function runGenresPhase(
  jobRef: DocumentReference,
  job: CurationJobDoc,
  ctx: typeof DEFAULT_ADMIN_CONTEXT
): Promise<void> {
  const genresDoc = await getCountryGenres(job.countryCode, ctx);
  const genreIds = ((genresDoc?.genres as Array<{ id: string }>) ?? []).map((g) => g.id);

  await jobRef.update({
    phase: "packages",
    genreIds,
    pendingGenreIds: genreIds,
    "phaseProgress.genres": { done: 1, total: 1 },
    done: job.done + 1,
    updatedAtMillis: Date.now(),
  });
}

async function runNextGenrePackages(
  jobRef: DocumentReference,
  job: CurationJobDoc,
  ctx: typeof DEFAULT_ADMIN_CONTEXT
): Promise<void> {
  const genreId = job.pendingGenreIds[0];
  const packagesDoc = await getGenrePackages(job.countryCode, genreId, ctx);
  const packages = (packagesDoc?.packages as PackageSummaryRef[]) ?? [];

  const newPending = packages.map((pkg) => ({
    packageId: pkg.id,
    genreId,
  }));

  const remainingGenres = job.pendingGenreIds.slice(1);
  const allPackages = [...job.pendingPackageIds, ...newPending];

  await jobRef.update({
    pendingGenreIds: remainingGenres,
    pendingPackageIds: allPackages,
    ...(remainingGenres.length === 0 ? { phase: "details" } : {}),
    "phaseProgress.packages": {
      done: job.genreIds.length - remainingGenres.length,
      total: job.genreIds.length,
    },
    done: job.done + 1,
    updatedAtMillis: Date.now(),
  });
}

async function runNextPackageDetail(
  jobRef: DocumentReference,
  job: CurationJobDoc,
  ctx: typeof DEFAULT_ADMIN_CONTEXT
): Promise<void> {
  const next = job.pendingPackageIds[0];
  const packagesDoc = await db.collection("curatedPackages").doc(`${job.countryCode}_${next.genreId}`).get();
  const summaries = (packagesDoc.data()?.packages as PackageSummaryRef[]) ?? [];
  const summary = summaries.find((p) => p.id === next.packageId);
  if (!summary) {
    await jobRef.update({
      pendingPackageIds: job.pendingPackageIds.slice(1),
      failed: job.failed + 1,
      updatedAtMillis: Date.now(),
    });
    return;
  }

  await ensureTourPackageDetail(job.countryCode, next.genreId, summary, ctx);
  const remaining = job.pendingPackageIds.slice(1);
  const totalDetails = job.pendingPackageIds.length;

  await jobRef.update({
    pendingPackageIds: remaining,
    ...(remaining.length === 0 ? { phase: "index" } : {}),
    "phaseProgress.details": {
      done: totalDetails - remaining.length,
      total: totalDetails,
    },
    done: job.done + 1,
    updatedAtMillis: Date.now(),
  });
}

async function runIndexPhase(
  jobRef: DocumentReference,
  job: CurationJobDoc
): Promise<void> {
  const entryCount = await buildDestinationIndex(job.countryCode);
  const spots = await collectSpotsForCountry(job.countryCode);
  const guideTasks = job.languages.flatMap((lang) =>
    spots.map((spot) => ({ spotId: spot.id, lang }))
  );

  await jobRef.update({
    phase: job.mode === "catalog_only" ? "completed" : "guides",
    pendingGuideTasks: job.mode === "catalog_only" ? [] : guideTasks,
    "phaseProgress.index": { done: 1, total: 1 },
    total: job.total + entryCount + guideTasks.length,
    done: job.done + 1,
    updatedAtMillis: Date.now(),
  });
}

async function runGuidesPhase(
  jobRef: DocumentReference,
  job: CurationJobDoc,
  ctx: typeof DEFAULT_ADMIN_CONTEXT
): Promise<boolean> {
  if (job.languages.length === 0) {
    return false;
  }

  if (job.mode === "languages_only") {
    const catalogSnap = await db.collection("curatedGenres").doc(job.countryCode).get();
    if (!catalogSnap.exists || (catalogSnap.data()?.schemaVersion ?? 0) < CURATED_SCHEMA_VERSION) {
      throw new HttpsError(
        "failed-precondition",
        `Catalog for ${job.countryCode} must be curated before adding languages.`
      );
    }
  }

  const spots = await collectSpotsForCountry(job.countryCode);
  const batchSize = 15;
  let tasks = job.pendingGuideTasks;

  if (tasks.length === 0 && job.mode === "languages_only") {
    tasks = job.languages.flatMap((lang) => spots.map((spot) => ({ spotId: spot.id, lang })));
    await jobRef.update({ pendingGuideTasks: tasks, phase: "guides" });
  }

  if (tasks.length === 0) {
    return false;
  }

  const batch = tasks.slice(0, batchSize);
  const remaining = tasks.slice(batchSize);
  const spotMap = new Map(spots.map((s) => [s.id, s]));

  for (const task of batch) {
    const spot = spotMap.get(task.spotId);
    if (!spot) continue;
    const lang = validateLanguageCode(task.lang);
    await pregenGuideContentBatch([spot], lang, ctx);
  }

  const progressKey = `phaseProgress.guides.${batch[0]?.lang ?? "en"}`;
  const langProgress = job.phaseProgress.guides?.[batch[0]?.lang ?? "en"] ?? { done: 0, total: spots.length };

  await jobRef.update({
    pendingGuideTasks: remaining,
    [progressKey]: {
      done: langProgress.done + batch.length,
      total: spots.length * job.languages.length,
    },
    done: job.done + batch.length,
    updatedAtMillis: Date.now(),
  });

  return remaining.length > 0;
}

export async function getCurationJob(jobId: string): Promise<CurationJobDoc | null> {
  const snap = await db.collection("curationJobs").doc(jobId).get();
  return snap.exists ? (snap.data() as CurationJobDoc) : null;
}

export async function listCurationJobs(countryCode: string, limit = 10): Promise<CurationJobDoc[]> {
  const snap = await db
    .collection("curationJobs")
    .where("countryCode", "==", countryCode.trim().toUpperCase())
    .orderBy("startedAtMillis", "desc")
    .limit(limit)
    .get();
  return snap.docs.map((doc) => doc.data() as CurationJobDoc);
}
