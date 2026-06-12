import type { CurationMode } from "./curationTypes";
import { SUPPORTED_LANGUAGE_CODES, type SupportedLanguageCode } from "./utils";

/** Planning constants — aligned with TARGET_PACKAGE_COUNT in curatedContent.ts */
const EST_GENRE_COUNT = 12;
const EST_PACKAGES_PER_GENRE = 30;
const EST_SPOTS_PER_PACKAGE = 18;
/** Spots dedupe across packages when collecting for guide pregen */
const EST_UNIQUE_SPOT_RATIO = 0.35;

const USD_TO_INR = 96;

export type CurationEstimate = {
  countryCode: string;
  mode: CurationMode;
  languages: SupportedLanguageCode[];
  data: {
    genres: number;
    packageCards: number;
    tourPackageDetails: number;
    uniqueSpots: number;
    guideAudioFiles: number;
    firestoreCollections: string[];
  };
  costUsd: {
    geminiCatalog: number;
    geminiGuides: number;
    cloudTts: number;
    cloudTranslate: number;
    mapsPlaces: number;
    total: number;
  };
  costInr: {
    total: number;
    geminiPrepay: number;
    gcpBilling: number;
  };
  duration: {
    catalogHoursMin: number;
    catalogHoursMax: number;
    guidesHoursPerLangMin: number;
    guidesHoursPerLangMax: number;
    totalHoursMin: number;
    totalHoursMax: number;
  };
  summary: string;
};

export function estimateCountryCuration(input: {
  countryCode: string;
  mode: CurationMode;
  languages: string[];
}): CurationEstimate {
  const countryCode = input.countryCode.trim().toUpperCase();
  const languages =
    input.mode === "catalog_only"
      ? []
      : (input.languages.length > 0 ? input.languages : ["en"]).map((l) =>
          l.trim().toLowerCase() as SupportedLanguageCode
        );

  const packageCards = EST_GENRE_COUNT * EST_PACKAGES_PER_GENRE;
  const tourDetails =
    input.mode === "languages_only" ? 0 : packageCards;
  const uniqueSpots = Math.round(packageCards * EST_SPOTS_PER_PACKAGE * EST_UNIQUE_SPOT_RATIO);
  const guideAudioFiles =
    input.mode === "catalog_only" ? 0 : uniqueSpots * languages.length;

  const geminiCatalog =
    input.mode === "languages_only" ? 0 : 35 + EST_GENRE_COUNT * 4 + packageCards * 0.22;
  const geminiGuides =
    input.mode === "catalog_only" ? 0 : uniqueSpots * languages.length * 0.015;
  const cloudTts =
    input.mode === "catalog_only" ? 0 : uniqueSpots * languages.length * 0.006;
  const cloudTranslate =
    input.mode === "catalog_only"
      ? 0
      : languages.filter((l) => l !== "en").length * uniqueSpots * 0.002;
  const mapsPlaces = input.mode === "languages_only" ? 0 : 25 + packageCards * 0.08;

  const totalUsd =
    geminiCatalog + geminiGuides + cloudTts + cloudTranslate + mapsPlaces;
  const geminiPrepayUsd = geminiCatalog + geminiGuides;
  const gcpBillingUsd = cloudTts + cloudTranslate + mapsPlaces;

  const catalogHoursMin = input.mode === "languages_only" ? 0 : 2;
  const catalogHoursMax = input.mode === "languages_only" ? 0 : 6;
  const guidesHoursPerLangMin = input.mode === "catalog_only" ? 0 : 2;
  const guidesHoursPerLangMax = input.mode === "catalog_only" ? 0 : 8;
  const totalHoursMin =
    catalogHoursMin + guidesHoursPerLangMin * Math.max(languages.length, 0);
  const totalHoursMax =
    catalogHoursMax + guidesHoursPerLangMax * Math.max(languages.length, 0);

  const collections = [
    "curatedGenres",
    "curatedPackages",
    "tourPackages",
    "destinationIndex",
  ];
  if (guideAudioFiles > 0) {
    collections.push("attractions/.../guideContent", "Cloud Storage guide-audio/");
  }

  const langLabel = languages.length > 0 ? languages.join(", ") : "none";
  const summary =
    input.mode === "full"
      ? `Full ${countryCode}: ~${packageCards} trips, ~${guideAudioFiles} audio files (${langLabel}). Est. ₹${Math.round(totalUsd * USD_TO_INR).toLocaleString()} and ${totalHoursMin}–${totalHoursMax} hours automated runtime.`
      : input.mode === "catalog_only"
        ? `Catalog ${countryCode}: ~${packageCards} trips, no audio. Est. ₹${Math.round(totalUsd * USD_TO_INR).toLocaleString()} and ${catalogHoursMin}–${catalogHoursMax} hours.`
        : `Guides ${countryCode}: ~${guideAudioFiles} audio files (${langLabel}). Est. ₹${Math.round(totalUsd * USD_TO_INR).toLocaleString()} and ${totalHoursMin}–${totalHoursMax} hours.`;

  return {
    countryCode,
    mode: input.mode,
    languages: languages.filter((l) =>
      SUPPORTED_LANGUAGE_CODES.includes(l as SupportedLanguageCode)
    ) as SupportedLanguageCode[],
    data: {
      genres: input.mode === "languages_only" ? 0 : EST_GENRE_COUNT,
      packageCards: input.mode === "languages_only" ? 0 : packageCards,
      tourPackageDetails: tourDetails,
      uniqueSpots,
      guideAudioFiles,
      firestoreCollections: collections,
    },
    costUsd: {
      geminiCatalog: round2(geminiCatalog),
      geminiGuides: round2(geminiGuides),
      cloudTts: round2(cloudTts),
      cloudTranslate: round2(cloudTranslate),
      mapsPlaces: round2(mapsPlaces),
      total: round2(totalUsd),
    },
    costInr: {
      total: Math.round(totalUsd * USD_TO_INR),
      geminiPrepay: Math.round(geminiPrepayUsd * USD_TO_INR),
      gcpBilling: Math.round(gcpBillingUsd * USD_TO_INR),
    },
    duration: {
      catalogHoursMin,
      catalogHoursMax,
      guidesHoursPerLangMin,
      guidesHoursPerLangMax,
      totalHoursMin,
      totalHoursMax,
    },
    summary,
  };
}

export type CurationProgressView = {
  percent: number;
  phaseLabel: string;
  detailLabel: string;
  isComplete: boolean;
  isFailed: boolean;
};

export function buildCurationProgressView(job: {
  status: string;
  phase: string;
  mode: string;
  pendingGenreIds?: string[];
  pendingPackageIds?: Array<{ packageId: string }>;
  pendingGuideTasks?: unknown[];
  phaseProgress?: {
    genres?: { done: number; total: number };
    packages?: { done: number; total: number };
    details?: { done: number; total: number };
    index?: { done: number; total: number };
    guides?: Record<string, { done: number; total: number }>;
  };
  languages?: string[];
}): CurationProgressView {
  if (job.status === "completed") {
    return {
      percent: 100,
      phaseLabel: "Completed",
      detailLabel: "All data is in Firestore. The Android app can read everything.",
      isComplete: true,
      isFailed: false,
    };
  }
  if (job.status === "failed") {
    return {
      percent: 0,
      phaseLabel: "Failed",
      detailLabel: `Job stopped in phase "${job.phase}". Top up credits and start a new run.`,
      isComplete: false,
      isFailed: true,
    };
  }

  const pp = job.phaseProgress ?? {};
  let percent = 0;
  let detailLabel = "";

  const genreW = 5;
  const packageW = 8;
  const detailsW = 42;
  const indexW = 5;
  const guidesW = 40;

  if (pp.genres?.total) {
    percent += genreW * (pp.genres.done / pp.genres.total);
  }
  if (pp.packages?.total) {
    percent += packageW * (pp.packages.done / pp.packages.total);
  }
  if (pp.details?.total) {
    percent += detailsW * (pp.details.done / pp.details.total);
    detailLabel = `Trip details ${pp.details.done}/${pp.details.total}`;
  } else if (job.pendingPackageIds?.length) {
    const total = job.pendingPackageIds.length + (pp.details?.done ?? 0);
    const done = pp.details?.done ?? 0;
    if (total > 0) {
      percent += detailsW * (done / total);
      detailLabel = `Trip details ${done}/${total}`;
    }
  }
  if (pp.index?.total) {
    percent += indexW * (pp.index.done / pp.index.total);
  }

  const guideEntries = Object.entries(pp.guides ?? {});
  if (guideEntries.length > 0) {
    const avg =
      guideEntries.reduce((sum, [, v]) => sum + (v.total ? v.done / v.total : 0), 0) /
      guideEntries.length;
    percent += guidesW * avg;
    const totalDone = guideEntries.reduce((s, [, v]) => s + v.done, 0);
    const totalAll = guideEntries.reduce((s, [, v]) => s + v.total, 0);
    detailLabel = `Guide audio ${totalDone}/${totalAll}`;
  } else if (job.pendingGuideTasks?.length) {
    detailLabel = `Guide audio queued: ${job.pendingGuideTasks.length} tasks`;
  }

  if (job.phase === "packages" && job.pendingGenreIds?.length) {
    detailLabel = `Package lists: ${pp.packages?.done ?? 0}/${pp.packages?.total ?? "?"} genres`;
  }

  const phaseLabels: Record<string, string> = {
    queued: "Starting",
    genres: "Genres",
    packages: "Package lists",
    details: "Trip details",
    index: "Search index",
    guides: "Guide audio",
    completed: "Completed",
    failed: "Failed",
  };

  return {
    percent: Math.min(99, Math.round(percent)),
    phaseLabel: phaseLabels[job.phase] ?? job.phase,
    detailLabel: detailLabel || "Working…",
    isComplete: false,
    isFailed: false,
  };
}

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}
