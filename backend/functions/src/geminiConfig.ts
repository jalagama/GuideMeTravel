/**
 * Gemini model + cost configuration.
 *
 * User path: cost-saver ON, gemini-2.5-flash-lite.
 * Admin bulk curation: cost-saver OFF, Pro for expert_spots, Flash for bulk copy.
 */

import type { CurationContext, CurationQuality } from "./curationTypes";

export const GEMINI_LITE_MODEL = "gemini-2.5-flash-lite";
export const GEMINI_STANDARD_MODEL = "gemini-2.5-flash";
export const GEMINI_ADMIN_MODEL =
  process.env.GEMINI_ADMIN_MODEL?.trim() || GEMINI_STANDARD_MODEL;
export const GEMINI_ADMIN_EXPERT_MODEL =
  process.env.GEMINI_ADMIN_EXPERT_MODEL?.trim() || "gemini-2.5-pro";

export const GEMINI_MODEL_FALLBACKS = [
  GEMINI_LITE_MODEL,
  GEMINI_STANDARD_MODEL,
] as const;

export const GEMINI_MODEL =
  process.env.GEMINI_MODEL?.trim() || GEMINI_LITE_MODEL;

export type GeminiModelTier = "lite" | "standard";

export type ResolvedCurationOptions = {
  tier: GeminiModelTier;
  model: string;
  costSaver: boolean;
  expertSpotMultiplier: number;
  useBatch: boolean;
};

export function isCostSaverMode(context?: CurationContext): boolean {
  if (context?.quality === "admin") {
    return false;
  }
  const flag = process.env.GEMINI_COST_SAVER?.trim().toLowerCase();
  if (flag === "false" || flag === "0" || flag === "off") {
    return false;
  }
  return true;
}

export function shouldUseGeminiBatch(context?: CurationContext): boolean {
  if (context?.quality !== "admin") {
    return false;
  }
  const flag = process.env.GEMINI_USE_BATCH?.trim().toLowerCase();
  return flag === "true" || flag === "1" || flag === "on";
}

/** Bulk copy ops use Flash-Lite in admin jobs to cut Gemini prepay ~40–60%. */
const ADMIN_LITE_COPY_OPERATIONS = new Set([
  "generate_genres",
  "fetch_packages",
  "fetch_packages_fill",
  "generate_package_extras",
  "generate_package_spot_discovery",
  "generate_why_chosen",
  "generate_attraction_summary",
  "generate_audio_preview",
]);

export function resolveCurationOptions(
  quality: CurationQuality = "user",
  operation?: string
): ResolvedCurationOptions {
  if (quality === "admin") {
    const usePro = operation === "expert_spots";
    const useLiteCopy = Boolean(operation && ADMIN_LITE_COPY_OPERATIONS.has(operation));
    return {
      tier: useLiteCopy ? "lite" : "standard",
      model: usePro
        ? GEMINI_ADMIN_EXPERT_MODEL
        : useLiteCopy
          ? GEMINI_LITE_MODEL
          : GEMINI_ADMIN_MODEL,
      costSaver: false,
      expertSpotMultiplier: 2,
      useBatch: operation !== "expert_spots" && shouldUseGeminiBatch({ quality: "admin" }),
    };
  }
  return {
    tier: "lite",
    model: GEMINI_LITE_MODEL,
    costSaver: isCostSaverMode(),
    expertSpotMultiplier: 1,
    useBatch: false,
  };
}

export function geminiModelsToTry(
  tier: GeminiModelTier = "lite",
  primaryModel?: string
): string[] {
  if (primaryModel) {
    return [primaryModel, GEMINI_LITE_MODEL, GEMINI_STANDARD_MODEL].filter(
      (model, index, list) => list.indexOf(model) === index
    );
  }
  if (tier === "standard") {
    const primary = process.env.GEMINI_MODEL?.trim() || GEMINI_STANDARD_MODEL;
    return [primary, GEMINI_LITE_MODEL, GEMINI_STANDARD_MODEL].filter(
      (model, index, list) => list.indexOf(model) === index
    );
  }

  const primary = process.env.GEMINI_MODEL?.trim() || GEMINI_LITE_MODEL;
  return [primary, GEMINI_LITE_MODEL, GEMINI_STANDARD_MODEL].filter(
    (model, index, list) => list.indexOf(model) === index
  );
}

export function expertSpotTarget(targetCount: number, context?: CurationContext): number {
  if (!isCostSaverMode(context)) {
    return Math.max(targetCount * 2, 20);
  }
  return Math.max(targetCount + 4, 12);
}

export function getTtsVoiceTier(): "wavenet" | "neural2" {
  const tier = process.env.TTS_VOICE_TIER?.trim().toLowerCase();
  return tier === "neural2" ? "neural2" : "wavenet";
}
