import type { SupportedLanguageCode } from "./utils";

export type CurationQuality = "user" | "admin";

export type CurationMode = "full" | "catalog_only" | "languages_only";

export type CurationJobPhase =
  | "queued"
  | "genres"
  | "packages"
  | "details"
  | "index"
  | "guides"
  | "completed"
  | "failed";

export type CurationContext = {
  quality: CurationQuality;
};

export const DEFAULT_USER_CONTEXT: CurationContext = { quality: "user" };
export const DEFAULT_ADMIN_CONTEXT: CurationContext = { quality: "admin" };

export type CurationJobDoc = {
  countryCode: string;
  languages: SupportedLanguageCode[];
  mode: CurationMode;
  status: "queued" | "running" | "completed" | "failed";
  phase: CurationJobPhase;
  qualityProfile: "admin";
  geminiModel: string;
  geminiExpertModel: string;
  geminiUseBatch: boolean;
  ttsVoiceTier: string;
  costSaver: false;
  genreIds: string[];
  pendingGenreIds: string[];
  pendingPackageIds: Array<{ packageId: string; genreId: string }>;
  pendingGuideTasks: Array<{ spotId: string; lang: SupportedLanguageCode }>;
  phaseProgress: {
    genres?: { done: number; total: number };
    packages?: { done: number; total: number };
    details?: { done: number; total: number };
    index?: { done: number; total: number };
    guides?: Record<string, { done: number; total: number }>;
  };
  total: number;
  done: number;
  failed: number;
  errors: Array<{ at: number; phase: string; message: string }>;
  startedBy: string;
  startedAtMillis: number;
  updatedAtMillis: number;
  /** When true, server + admin UI auto-continue batches until complete */
  autoRun?: boolean;
};

export function createJobId(countryCode: string): string {
  return `${countryCode.toUpperCase()}_${Date.now()}`;
}
